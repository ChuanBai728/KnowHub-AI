package ai.knowhub.chat.rag.executor;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.model.trace.ConversationTraceStageCode;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.DocumentNavigationDecision;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.rag.service.GraphAnswerRenderer;
import ai.knowhub.chat.rag.service.StructureGraphQueryEngine;
import ai.knowhub.chat.rag.support.ExecutorEventSupport;
import ai.knowhub.chat.service.ConversationTraceRecorder;
import ai.knowhub.chat.service.TaskInfo;
import ai.knowhub.chat.support.StreamEventWriter;
import ai.knowhub.document.model.graph.GraphQueryResult;
import ai.knowhub.document.model.graph.GraphSectionWithChildren;
import ai.knowhub.document.model.graph.GraphSectionWithSiblings;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 结构图直答执行器 —— 专门处理文档结构导航类问题，不调用大模型。
 *
 * 设计模式：策略模式的具体策略
 * 本类是 ConversationExecutor 接口的一个实现，对应 ExecutionMode#GRAPH_ONLY 模式。
 *
 * 使用场景
 * 当用户问的是文档的"结构关系"问题时，不需要检索文档内容或调用大模型生成答案，
 * 直接从文档结构图（Neo4j 图数据库）中查询即可。典型问题包括：
 * 
 *   "第三章包含哪些小节？" → 子章节查询
 *   "这一节的上一节/下一节是什么？" → 邻接查询
 *   "这个章节属于哪个父章节？" → 邻接查询
 * 执行流程
 * 
 *   从执行计划中获取文档 ID 和章节节点 ID
 *   根据导航动作类型选择查询方式：
 *       
 *         SECTION_ADJACENCY_LOOKUP：查询父章节、上一节、下一节
 *         其他：查询当前章节的子章节列表
 *   用 GraphAnswerRenderer 把图查询结果渲染成文本答案
 *   返回答案（不调用大模型）
 * @see ConversationExecutor 执行器接口
 * @see GraphThenEvidenceExecutor 需要查结构图 + 取证据的执行器
 * @see StructureGraphQueryEngine 结构图查询引擎
 * @see GraphAnswerRenderer 图查询结果渲染器
 */
@Component
@Slf4j
public class GraphOnlyExecutor implements ConversationExecutor {

    /**
     * 结构图查询引擎，负责在 Neo4j 图数据库中查询文档的章节结构关系。
     */
    private final StructureGraphQueryEngine structureGraphQueryEngine;

    /**
     * 图答案渲染器，负责把图查询结果转换成人类可读的文本答案。
     */
    private final GraphAnswerRenderer graphAnswerRenderer;

    /**
     * SSE 流式事件写入器，用于向前端推送思考步骤等中间事件。
     */
    private final StreamEventWriter streamEventWriter;

    /**
     * 构造函数，注入依赖。
     *
     * @param structureGraphQueryEngine 结构图查询引擎
     * @param graphAnswerRenderer       图答案渲染器
     * @param streamEventWriter         SSE 事件写入器
     */
    public GraphOnlyExecutor(StructureGraphQueryEngine structureGraphQueryEngine,
                             GraphAnswerRenderer graphAnswerRenderer,
                             StreamEventWriter streamEventWriter) {
        this.structureGraphQueryEngine = structureGraphQueryEngine;
        this.graphAnswerRenderer = graphAnswerRenderer;
        this.streamEventWriter = streamEventWriter;
    }

    /**
     * 返回本执行器对应的执行模式：GRAPH_ONLY（结构图直答）。
     *
     * @return ExecutionMode#GRAPH_ONLY
     */
    @Override
    public ExecutionMode mode() {
        return ExecutionMode.GRAPH_ONLY;
    }

    /**
     * 执行结构图直答逻辑。
     *
     * GRAPH_ONLY 用在"问章节关系"的场景，例如上一节、下一节、子章节列表，
     * 不再额外调大模型。直接查图数据库，把结构化结果渲染成文本返回。
     *
     * @param taskInfo 任务上下文，包含执行计划和导航决策
     * @return 包含答案文本的 Flux（通常是单元素，因为图查询结果是确定性的）
     */
    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        // GRAPH_ONLY 用在"问章节关系"的场景，例如上一节、下一节、子章节列表，不再额外调大模型。
        ConversationExecutionPlan plan = taskInfo.executionPlan();
        DocumentNavigationDecision decision = plan == null ? null : plan.getNavigationDecision();

        // 校验：必须有有效的执行计划、导航决策和章节节点 ID，否则返回无证据回复
        if (plan == null || decision == null || decision.getStructureAnchor() == null || decision.getStructureAnchor().getStructureNodeId() == null) {
            log.info("GRAPH_ONLY 执行器直接返回无证据: planPresent={}, decisionPresent={}, structureNodeId={}",
                plan != null,
                decision != null,
                decision == null || decision.getStructureAnchor() == null ? null : decision.getStructureAnchor().getStructureNodeId());
            return Flux.just(StrUtil.blankToDefault(plan == null ? "" : plan.getNoEvidenceReply(), "当前没有足够证据支持明确回答。"));
        }

        // 向前端推送思考事件，告知正在查询结构图
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "正在通过结构图直接查询章节关系。");

        // 开始追踪"图查询"阶段
        ConversationTraceRecorder.StageHandle graphStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(ConversationTraceStageCode.GRAPH_QUERY, mode().name(), "正在执行结构图查询。", null);

        // 提取文档 ID 和章节节点 ID
        Long documentId = plan.getSelectedDocumentId();
        Long sectionNodeId = decision.getStructureAnchor().getStructureNodeId();
        log.info("GRAPH_ONLY 执行开始: documentId={}, sectionNodeId={}, action={}, navigationSummary='{}'",
            documentId,
            sectionNodeId,
            decision.getNavigationAction(),
            decision.getSummaryText());

        GraphQueryResult graphResult;

        // 根据导航动作类型选择不同的图查询方式
        if (decision.getNavigationAction() == ai.knowhub.chat.rag.model.DocumentNavigationAction.SECTION_ADJACENCY_LOOKUP) {
            // 邻接查询：获取父章节、上一节、下一节，适合"上一节是什么"这类导航问题
            GraphSectionWithSiblings result = structureGraphQueryEngine.findSectionWithSiblings(documentId, sectionNodeId);
            graphResult = GraphQueryResult.builder()
                .targetSection(result.getSection())
                .parentSection(result.getParent())
                .previousSibling(result.getPreviousSibling())
                .nextSibling(result.getNextSibling())
                .build();
        }
        else {
            // 子章节查询：获取当前章节下的所有子节点，适合"这一章包含什么"这类问题
            GraphSectionWithChildren result = structureGraphQueryEngine.findSectionWithChildren(documentId, sectionNodeId);
            graphResult = GraphQueryResult.builder()
                .targetSection(result.getSection())
                .children(result.getChildren())
                .build();
        }

        // 把图查询结果渲染成人类可读的文本答案
        String answer = graphAnswerRenderer.renderGraphAnswer(mode(), decision, graphResult);
        log.info("GRAPH_ONLY 执行完成: documentId={}, sectionNodeId={}, targetSection='{}', answerLength={}",
            documentId,
            sectionNodeId,
            graphResult.getTargetSection() == null ? "" : graphResult.getTargetSection().displayTitle(),
            answer == null ? 0 : answer.length());

        // 记录追踪阶段完成信息
        if (taskInfo.traceRecorder() != null) {
            taskInfo.traceRecorder().completeStage(graphStage, "结构图查询完成。", Map.of(
                "targetSection", graphResult.getTargetSection() == null ? "" : StrUtil.blankToDefault(graphResult.getTargetSection().displayTitle(), ""),
                "parentSection", graphResult.getParentSection() == null ? "" : StrUtil.blankToDefault(graphResult.getParentSection().displayTitle(), ""),
                "childCount", graphResult.getChildren() == null ? 0 : graphResult.getChildren().size(),
                "previousSibling", graphResult.getPreviousSibling() == null ? "" : StrUtil.blankToDefault(graphResult.getPreviousSibling().displayTitle(), ""),
                "nextSibling", graphResult.getNextSibling() == null ? "" : StrUtil.blankToDefault(graphResult.getNextSibling().displayTitle(), ""),
                "answer", StrUtil.blankToDefault(answer, "")
            ));
        }

        // 返回答案。如果答案为空则返回兜底话术
        return Flux.fromIterable(answer.isBlank() ? List.of(StrUtil.blankToDefault(plan.getNoEvidenceReply(), "当前没有足够证据支持明确回答。")) : List.of(answer));
    }
}
