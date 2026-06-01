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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 结构图定位后取证执行器 —— 先用结构图定位章节，再从章节中提取证据回答。
 *
 * 设计模式：策略模式的具体策略
 * 本类是 ConversationExecutor 接口的一个实现，对应 ExecutionMode#GRAPH_THEN_EVIDENCE 模式。
 *
 * 与 GraphOnlyExecutor 的区别
 * 
 *   <b>GraphOnlyExecutor</b>：只查结构关系（上一节、下一节、子章节列表），不读取章节内容
 *   <b>GraphThenEvidenceExecutor</b>：先通过结构图定位到目标章节，再读取章节正文或编号项中的具体内容作为证据
 * 使用场景
 * 当用户问题涉及文档中某个具体章节或编号项的内容时，例如：
 * 
 *   "第三章第 2 步是什么？" → 定位到第三章，再找到第 2 个编号项
 *   "哪一步要求执行 XX 操作？" → 定位章节后，在编号项中搜索关键词
 * 执行流程
 * 
 *   从执行计划中获取文档 ID、章节节点 ID、编号项索引
 *   调用 StructureGraphQueryEngine 在图数据库中定位章节并提取证据
 *   验证证据是否满足条件（必须找到目标章节和编号项）
 *   用 GraphAnswerRenderer 把结构化证据渲染成文本答案
 * @see GraphOnlyExecutor 只查结构关系的执行器
 * @see StructureGraphQueryEngine 结构图查询引擎
 * @see GraphAnswerRenderer 图查询结果渲染器
 */
@Component
@Slf4j
public class GraphThenEvidenceExecutor implements ConversationExecutor {

    /**
     * 结构图查询引擎，负责在 Neo4j 图数据库中查询章节结构和编号项证据。
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
    public GraphThenEvidenceExecutor(StructureGraphQueryEngine structureGraphQueryEngine,
                                     GraphAnswerRenderer graphAnswerRenderer,
                                     StreamEventWriter streamEventWriter) {
        this.structureGraphQueryEngine = structureGraphQueryEngine;
        this.graphAnswerRenderer = graphAnswerRenderer;
        this.streamEventWriter = streamEventWriter;
    }

    /**
     * 返回本执行器对应的执行模式：GRAPH_THEN_EVIDENCE（结构图定位后取证）。
     *
     * @return ExecutionMode#GRAPH_THEN_EVIDENCE
     */
    @Override
    public ExecutionMode mode() {
        return ExecutionMode.GRAPH_THEN_EVIDENCE;
    }

    /**
     * 执行"结构图定位 + 取证"逻辑。
     *
     * 先通过结构图定位目标章节和编号项，再把对应证据渲染成答案。
     * 与 GraphOnlyExecutor 不同，本执行器会读取章节正文或编号项的具体内容。
     *
     * @param taskInfo 任务上下文，包含执行计划和导航决策
     * @return 包含答案文本的 Flux
     */
    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        // GRAPH_THEN_EVIDENCE 先用图结构定位章节或编号项，再把对应证据渲染成答案。
        ConversationExecutionPlan plan = taskInfo.executionPlan();
        DocumentNavigationDecision decision = plan == null ? null : plan.getNavigationDecision();

        // 校验：必须有有效的执行计划、导航决策和章节节点 ID
        if (plan == null || decision == null || decision.getStructureAnchor() == null || decision.getStructureAnchor().getStructureNodeId() == null) {
            log.info("GRAPH_THEN_EVIDENCE 执行器直接返回无证据: planPresent={}, decisionPresent={}, structureNodeId={}",
                plan != null,
                decision != null,
                decision == null || decision.getStructureAnchor() == null ? null : decision.getStructureAnchor().getStructureNodeId());
            return Flux.just(StrUtil.blankToDefault(plan == null ? "" : plan.getNoEvidenceReply(), "当前没有足够证据支持明确回答。"));
        }

        // 向前端推送思考事件
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "正在通过结构图定位目标章节和编号项。");

        // 开始追踪"图查询"阶段
        ConversationTraceRecorder.StageHandle graphStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(ConversationTraceStageCode.GRAPH_QUERY, mode().name(), "正在执行结构图定位与取证。", null);

        log.info("GRAPH_THEN_EVIDENCE 执行开始: documentId={}, sectionNodeId={}, itemIndex={}, navigationSummary='{}'",
            plan.getSelectedDocumentId(),
            decision.getStructureAnchor().getStructureNodeId(),
            decision.getItemAnchor() == null ? null : decision.getItemAnchor().getItemIndex(),
            decision.getSummaryText());

        // 构建图查询参数并执行查询
        GraphQueryResult graphResult = buildGraphResult(plan, decision);

        // 验证图查询结果是否包含有效证据
        if (!hasGraphEvidence(graphResult, decision)) {
            log.info("GRAPH_THEN_EVIDENCE 证据校验失败: documentId={}, sectionNodeId={}, notes={}",
                plan.getSelectedDocumentId(),
                decision.getStructureAnchor().getStructureNodeId(),
                List.of("结构图未定位到满足条件的章节或编号项。"));
            if (taskInfo.traceRecorder() != null) {
                taskInfo.traceRecorder().completeStage(graphStage, "结构图定位完成，但证据不满足约束。", Map.of(
                    "targetSection", graphResult == null || graphResult.getTargetSection() == null ? "" : StrUtil.blankToDefault(graphResult.getTargetSection().displayTitle(), ""),
                    "targetItemIndex", graphResult == null || graphResult.getTargetItem() == null || graphResult.getTargetItem().getItemIndex() == null ? "" : String.valueOf(graphResult.getTargetItem().getItemIndex()),
                    "notes", List.of("结构图未定位到满足条件的章节或编号项。")
                ));
            }
            // 证据不足，返回兜底话术
            return Flux.just(StrUtil.blankToDefault(plan.getNoEvidenceReply(), "当前没有足够证据支持明确回答。"));
        }

        // 把图查询结果渲染成文本答案
        String answer = graphAnswerRenderer.renderGraphAnswer(mode(), decision, graphResult);
        log.info("GRAPH_THEN_EVIDENCE 执行完成: documentId={}, sectionNodeId={}, targetSection='{}', targetItemIndex={}, answerLength={}",
            plan.getSelectedDocumentId(),
            decision.getStructureAnchor().getStructureNodeId(),
            graphResult.getTargetSection() == null ? "" : graphResult.getTargetSection().displayTitle(),
            graphResult.getTargetItem() == null ? null : graphResult.getTargetItem().getItemIndex(),
            answer == null ? 0 : answer.length());

        // 记录追踪阶段完成信息
        if (taskInfo.traceRecorder() != null) {
            taskInfo.traceRecorder().completeStage(graphStage, "结构图取证完成。", Map.of(
                "targetSection", graphResult.getTargetSection() == null ? "" : StrUtil.blankToDefault(graphResult.getTargetSection().displayTitle(), ""),
                "targetItemIndex", graphResult.getTargetItem() == null || graphResult.getTargetItem().getItemIndex() == null ? "" : String.valueOf(graphResult.getTargetItem().getItemIndex()),
                "matchedItemCount", graphResult.getMatchedItems() == null ? 0 : graphResult.getMatchedItems().size(),
                "answer", StrUtil.blankToDefault(answer, "")
            ));
        }

        // 返回答案。如果答案为空则返回兜底话术
        return Flux.fromIterable(answer.isBlank() ? List.of(StrUtil.blankToDefault(plan.getNoEvidenceReply(), "当前没有足够证据支持明确回答。")) : List.of(answer));
    }

    /**
     * 构建图查询参数。
     *
     * 把导航决策中的文档 ID、章节节点 ID、编号项索引等信息提取出来，
     * 交给 StructureGraphQueryEngine 执行查询。
     *
     * @param plan     执行计划，包含选中的文档 ID
     * @param decision 导航决策，包含章节锚点和编号项锚点
     * @return 图查询结果，包含目标章节、编号项和匹配项
     */
    private GraphQueryResult buildGraphResult(ConversationExecutionPlan plan, DocumentNavigationDecision decision) {
        // 这里把导航决策转换成图查询参数：文档、章节节点、编号项和可选关键词。
        Long documentId = plan.getSelectedDocumentId();
        Long sectionNodeId = decision.getStructureAnchor().getStructureNodeId();
        Integer itemIndex = decision.getItemAnchor() == null ? null : decision.getItemAnchor().getItemIndex();
        // 尝试从用户问题中提取编号项关键词，用于在编号项列表中精确定位
        String itemKeyword = extractItemKeyword(plan.getOriginalQuestion(), decision);
        return structureGraphQueryEngine.buildGraphResult(documentId, sectionNodeId, itemIndex, itemKeyword);
    }

    /**
     * 从用户问题中提取编号项关键词。
     *
     * 当用户问"哪一步要求执行 XX"时，需要从问题中提取"XX"作为关键词，
     * 以便在编号项列表中找到包含该关键词的步骤。
     *
     * 提取逻辑：找到"哪一步"或"哪一项"后面的文本，去掉常见动词（要求、需要、执行等），
     * 剩下的就是关键词。
     *
     * @param question 用户原始问题
     * @param decision 导航决策（预留，当前未使用）
     * @return 提取到的关键词，如果没有匹配则返回空字符串
     */
    private String extractItemKeyword(String question, DocumentNavigationDecision decision) {
        String normalized = StrUtil.blankToDefault(question, "");
        if (normalized.contains("哪一步") || normalized.contains("哪一项")) {
            // 取"哪一步"或"哪一项"后面的文本
            String keyword = normalized.contains("哪一步")
                ? StrUtil.subAfter(normalized, "哪一步", false)
                : StrUtil.subAfter(normalized, "哪一项", false);
            // 去掉常见动词和标点，留下核心关键词
            keyword = keyword
                .replace("要求", "")
                .replace("需要", "")
                .replace("执行", "")
                .replace("进行", "")
                .replace("包含", "")
                .replace("的是", "")
                .replace("是什么", "")
                .replace("什么", "")
                .replace("？", "")
                .replace("?", "")
                .replace("。", "")
                .replace("，", "")
                .trim();
            if (StrUtil.isNotBlank(keyword)) {
                return keyword;
            }
        }
        return "";
    }

    /**
     * 验证图查询结果是否包含有效证据。
     *
     * 判断逻辑：
     * 
     *   必须有目标章节（targetSection 不为空）
     *   如果用户指定了编号项索引，必须找到对应的编号项或匹配项
     *   如果没有指定编号项，目标章节必须有正文内容或匹配项
     * @param graphResult 图查询结果
     * @param decision    导航决策，包含编号项锚点信息
     * @return true 表示证据满足条件，false 表示证据不足
     */
    private boolean hasGraphEvidence(GraphQueryResult graphResult, DocumentNavigationDecision decision) {
        // 必须有目标章节
        if (graphResult == null || graphResult.getTargetSection() == null) {
            return false;
        }
        // 如果指定了编号项索引，必须找到对应的编号项
        if (decision != null && decision.getItemAnchor() != null && decision.getItemAnchor().getItemIndex() != null) {
            return graphResult.getTargetItem() != null
                || (graphResult.getMatchedItems() != null && !graphResult.getMatchedItems().isEmpty());
        }
        // 没有指定编号项时，目标章节必须有正文或匹配项
        return StrUtil.isNotBlank(graphResult.getTargetSection().getContentText())
            || (graphResult.getMatchedItems() != null && !graphResult.getMatchedItems().isEmpty());
    }
}
