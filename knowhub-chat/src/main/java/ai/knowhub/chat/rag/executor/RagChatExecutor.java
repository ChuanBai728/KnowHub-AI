package ai.knowhub.chat.rag.executor;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.chat.model.trace.ConversationTraceStageCode;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.rag.model.RagPromptAssemblyResult;
import ai.knowhub.chat.rag.model.RagRetrievalContext;
import ai.knowhub.chat.rag.service.RagPromptAssemblyService;
import ai.knowhub.chat.rag.service.RagRetrievalEngine;
import ai.knowhub.chat.rag.support.ExecutorEventSupport;
import ai.knowhub.chat.service.ConversationTraceRecorder;
import ai.knowhub.chat.service.ObservedChatModelService;
import ai.knowhub.chat.service.TaskInfo;
import ai.knowhub.chat.support.StreamEventWriter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索问答执行器 —— RAG 流水线的核心执行器，完成"检索证据 → 组装 Prompt → 生成答案"全流程。
 *
 * 设计模式：策略模式的具体策略
 * 本类是 ConversationExecutor 接口的一个实现，对应 ExecutionMode#RETRIEVAL 模式。
 * 这是最常用的执行模式，处理大多数需要基于知识文档回答的问题。
 *
 * RAG 流水线完整流程
 * 本执行器串联了 RAG 的三个核心阶段：
 * 
 *   <b>检索（Retrieve）</b>：调用 RagRetrievalEngine 执行多通道混合检索
 *       
 *         查询改写：把用户口语化问题改写成更适合检索的形式
 *         子问题拆分：复杂问题拆成多个子问题分别检索
 *         多通道并行检索：向量检索（语义相似）+ 关键词检索（精确匹配）
 *         RRF 融合：合并两个通道的结果，综合排序
 *         父块提升：命中子块时提升到父块以获取更完整上下文
 *         Rerank 重排序（可选）：用交叉编码器模型精排
 *   <b>组装 Prompt（Assemble）</b>：调用 RagPromptAssemblyService 把证据压缩进 Prompt 预算
 *       
 *         计算每个子问题的证据预算
 *         按优先级排序，超出预算的证据被截断或省略
 *         组装 system prompt 和 user prompt
 *   <b>生成答案（Generate）</b>：调用 ObservedChatModelService 基于证据流式生成答案
 *       
 *         将组装好的 Prompt 发送给大模型
 *         流式返回生成的文本，前端实时显示
 * @see ConversationExecutor 执行器接口
 * @see RagRetrievalEngine RAG 检索引擎
 * @see RagPromptAssemblyService Prompt 组装服务
 * @see ObservedChatModelService 大模型调用服务
 */
@Component
public class RagChatExecutor implements ConversationExecutor {

    /**
     * RAG 检索引擎，负责执行多通道混合检索（向量 + 关键词）、RRF 融合、rerank 等。
     */
    private final RagRetrievalEngine ragRetrievalEngine;

    /**
     * RAG Prompt 组装服务，负责把检索到的证据文本压缩进 Prompt 预算，组装 system/user prompt。
     */
    private final RagPromptAssemblyService ragPromptAssemblyService;

    /**
     * SSE 流式事件写入器，用于向前端推送思考步骤、状态变化等中间事件。
     */
    private final StreamEventWriter streamEventWriter;

    /**
     * 大模型调用服务（带可观测性），负责调用大模型并流式返回生成的文本。
     */
    private final ObservedChatModelService observedChatModelService;

    /**
     * 构造函数，注入依赖。
     *
     * @param ragRetrievalEngine       RAG 检索引擎
     * @param ragPromptAssemblyService Prompt 组装服务
     * @param streamEventWriter        SSE 事件写入器
     * @param observedChatModelService 大模型调用服务
     */
    public RagChatExecutor(RagRetrievalEngine ragRetrievalEngine,
                           RagPromptAssemblyService ragPromptAssemblyService,
                           StreamEventWriter streamEventWriter,
                           ObservedChatModelService observedChatModelService) {
        this.ragRetrievalEngine = ragRetrievalEngine;
        this.ragPromptAssemblyService = ragPromptAssemblyService;
        this.streamEventWriter = streamEventWriter;
        this.observedChatModelService = observedChatModelService;
    }

    /**
     * 返回本执行器对应的执行模式：RETRIEVAL（知识库检索问答）。
     *
     * @return ExecutionMode#RETRIEVAL
     */
    @Override
    public ExecutionMode mode() {
        return ExecutionMode.RETRIEVAL;
    }

    /**
     * 执行 RAG 检索问答的完整流程。
     *
     * RETRIEVAL 模式的主流程：检索证据 → 组装 Prompt → 调模型流式生成答案。
     *
     * 检索阶段在 Schedulers#boundedElastic() 线程池中执行（因为涉及大量 IO），
     * 模型生成阶段返回 Flux 流式响应。
     *
     * @param taskInfo 任务上下文，包含执行计划、调试追踪等
     * @return 流式文本响应
     */
    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        // RETRIEVAL 模式的主流程：检索证据 -> 组装 Prompt -> 调模型流式生成答案。
        ConversationExecutionPlan plan = taskInfo.executionPlan();

        // 向前端推送"正在规划检索范围"的思考事件
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "正在根据问题规划知识检索范围。");

        // 开始追踪"RAG 检索"阶段
        ConversationTraceRecorder.StageHandle retrieveStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(ConversationTraceStageCode.RAG_RETRIEVE, mode().name(), "正在执行双通道混合检索。", null);

        // 在弹性线程池中执行检索（IO 密集型操作），然后流式生成答案
        return Mono.fromCallable(() -> ragRetrievalEngine.retrieve(plan, taskInfo.traceRecorder()))
            .subscribeOn(Schedulers.boundedElastic()) // 在弹性线程池中执行检索
            .doOnError(error -> {
                // 检索失败时记录追踪
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().failStage(retrieveStage, "RAG 检索失败。", error.getMessage(), null);
                }
            })
            .doOnSuccess(context -> {
                // 检索成功时记录详细的追踪信息
                if (taskInfo.traceRecorder() != null && context != null) {
                    taskInfo.traceRecorder().completeStage(retrieveStage, "RAG 检索完成。", Map.of(
                        "retrievalQuestion", StrUtil.blankToDefault(context.getRetrievalQuestion(), ""),
                        "usedChannels", context.getUsedChannels() == null ? List.of() : context.getUsedChannels(),
                        "retrievalNotes", context.getRetrievalNotes() == null ? List.of() : context.getRetrievalNotes(),
                        "referenceCount", context.flattenReferences().size(),
                        "subQuestionCount", context.getSubQuestionEvidenceList() == null ? 0 : context.getSubQuestionEvidenceList().size(),
                        "subQuestions", context.getSubQuestionEvidenceList() == null
                            ? List.of()
                            : context.getSubQuestionEvidenceList().stream().map(item -> Map.of(
                                "index", item.getSubQuestionIndex(),
                                "question", StrUtil.blankToDefault(item.getSubQuestion(), ""),
                                "referenceCount", item.getReferences() == null ? 0 : item.getReferences().size(),
                                "documentCount", item.getDocuments() == null ? 0 : item.getDocuments().size(),
                                "fusedCandidateCount", item.getFusedCandidateCount() == null ? 0 : item.getFusedCandidateCount(),
                                "parentCandidateCount", item.getParentCandidateCount() == null ? 0 : item.getParentCandidateCount(),
                                "rerankedCandidateCount", item.getRerankedCandidateCount() == null ? 0 : item.getRerankedCandidateCount(),
                                "channelTraces", item.getChannelTraces() == null
                                    ? List.of()
                                    : item.getChannelTraces().stream().map(trace -> Map.of(
                                        "channelName", StrUtil.blankToDefault(trace.getChannelName(), ""),
                                        "recalledCount", trace.getRecalledCount(),
                                        "acceptedCount", trace.getAcceptedCount()
                                    )).toList(),
                                "references", item.getReferences() == null
                                    ? List.of()
                                    : item.getReferences().stream().map(reference -> Map.of(
                                        "referenceId", StrUtil.blankToDefault(reference.getReferenceId(), ""),
                                        "documentName", StrUtil.blankToDefault(reference.getDocumentName(), reference.getTitle()),
                                        "sectionPath", StrUtil.blankToDefault(reference.getSectionPath(), ""),
                                        "channel", StrUtil.blankToDefault(reference.getChannel(), "")
                                    )).toList()
                            )).toList(),
                        "references", context.flattenReferences().stream().map(reference -> Map.of(
                            "referenceId", StrUtil.blankToDefault(reference.getReferenceId(), ""),
                            "documentName", StrUtil.blankToDefault(reference.getDocumentName(), reference.getTitle()),
                            "sectionPath", StrUtil.blankToDefault(reference.getSectionPath(), ""),
                            "channel", StrUtil.blankToDefault(reference.getChannel(), "")
                        )).toList()
                    ));
                }
            })
            // 检索完成后，进入"组装 Prompt + 生成答案"阶段
            .flatMapMany(context -> streamFromRetrievalContext(taskInfo, plan, context));
    }

    /**
     * 从检索结果出发，组装 Prompt 并流式生成答案。
     *
     * 这是 RAG 流水线的后半段：拿到检索证据后，先把证据压缩进 Prompt 预算，
     * 再调用大模型基于证据生成答案。
     *
     * @param taskInfo 任务上下文
     * @param plan     执行计划
     * @param context  检索上下文，包含所有子问题的证据、引用、检索备注等
     * @return 流式文本响应
     */
    private Flux<String> streamFromRetrievalContext(TaskInfo taskInfo,
                                                    ConversationExecutionPlan plan,
                                                    RagRetrievalContext context) {

        // 把检索引擎返回的过程说明作为"思考步骤"推给前端，让用户看到检索过程
        context.getRetrievalNotes().forEach(note -> ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, note));

        // 记录使用的检索通道和检索备注到调试追踪中
        taskInfo.usedTools().addAll(context.getUsedChannels());
        taskInfo.debugTrace().setRetrievalNotes(new ArrayList<>(context.getRetrievalNotes()));
        taskInfo.debugTrace().setUsedChannels(new ArrayList<>(context.getUsedChannels()));

        if (context.isEmpty()) {
            // 没有可靠证据时直接返回兜底话术，避免模型在没有依据时自由发挥（产生幻觉）
            ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "当前没有足够证据，直接返回无证据兜底回复。");
            return Flux.just(StrUtil.blankToDefault(plan.getNoEvidenceReply(), "当前没有足够证据支持明确回答。"));
        }

        // 将所有子问题的引用合并到任务上下文中，供前端展示引用来源
        taskInfo.references().addAll(context.flattenReferences());
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "证据整理完成，正在基于证据生成回答。");

        // --- 阶段：Prompt 预算组装 ---
        // 这里把检索证据压缩进 Prompt 预算，防止一次塞入过多文本导致上下文超限。
        ConversationTraceRecorder.StageHandle budgetStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(ConversationTraceStageCode.EVIDENCE_BUDGET, mode().name(), "正在组装证据与 Prompt 预算。", null);

        // 调用 Prompt 组装服务，把证据、历史上下文、系统提示词等组装成最终的 system prompt 和 user prompt
        RagPromptAssemblyResult promptAssemblyResult = ragPromptAssemblyService.assemble(plan, context);
        String systemPrompt = promptAssemblyResult.getSystemPrompt();
        String userPrompt = promptAssemblyResult.getUserPrompt();

        // 记录组装后的 Prompt 到调试追踪
        taskInfo.debugTrace().setRagSystemPrompt(systemPrompt);
        taskInfo.debugTrace().setRagUserPrompt(userPrompt);

        // 记录 Prompt 预算阶段的追踪信息
        if (taskInfo.traceRecorder() != null) {
            taskInfo.traceRecorder().completeStage(budgetStage, "证据预算与 Prompt 组装完成。", Map.of(
                "totalBudget", promptAssemblyResult.getTotalBudget(),
                "perSubQuestionBudget", promptAssemblyResult.getPerSubQuestionBudget(),
                "renderedReferenceCount", promptAssemblyResult.getRenderedReferenceCount(),
                "omittedReferenceCount", promptAssemblyResult.getOmittedReferenceCount(),
                "renderedReferenceDetails", promptAssemblyResult.getRenderedReferenceDetails() == null ? List.of() : promptAssemblyResult.getRenderedReferenceDetails(),
                "omittedReferenceDetails", promptAssemblyResult.getOmittedReferenceDetails() == null ? List.of() : promptAssemblyResult.getOmittedReferenceDetails(),
                "systemPrompt", StrUtil.blankToDefault(systemPrompt, ""),
                "userPrompt", StrUtil.blankToDefault(userPrompt, "")
            ));
        }

        // --- 阶段：答案生成 ---
        // 调用大模型，基于组装好的 Prompt 流式生成答案
        ConversationTraceRecorder.StageHandle answerStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(ConversationTraceStageCode.ANSWER_GENERATE, mode().name(), "正在基于证据生成回答。", null);
        return observedChatModelService.streamText("rag_answer", systemPrompt, userPrompt, taskInfo.traceRecorder())
            .doOnComplete(() -> {
                // 答案生成完成，记录响应时间和答案长度
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().completeStage(answerStage, "答案生成完成。", Map.of(
                        "firstResponseTimeMs", taskInfo.firstResponseTimeMs().get(),
                        "answerLength", taskInfo.answerBuffer().length()
                    ));
                }
            })
            .doOnError(error -> {
                // 答案生成失败，记录错误信息
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().failStage(answerStage, "答案生成失败。", error.getMessage(), null);
                }
            });
    }
}
