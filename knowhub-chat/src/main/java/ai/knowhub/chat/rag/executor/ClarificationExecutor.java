package ai.knowhub.chat.rag.executor;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.chat.model.trace.ConversationTraceStageCode;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.rag.support.ExecutorEventSupport;
import ai.knowhub.chat.service.TaskInfo;
import ai.knowhub.chat.support.StreamEventWriter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 澄清执行器 —— 当系统无法确定用户意图时，引导用户补充信息。
 *
 * 设计模式：策略模式的具体策略
 * 本类是 ConversationExecutor 接口的一个实现，对应 ExecutionMode#CLARIFICATION 模式。
 *
 * 使用场景
 * 在 RAG 路由阶段，系统会分析用户问题并尝试匹配候选文档。当出现以下歧义情况时，
 * 路由器会选择 CLARIFICATION 模式：
 * 
 *   候选文档过多且分数相近，无法稳定选出"最相关"的文档
 *   用户问题过于模糊（例如"那个东西怎么用"），无法判断指的是哪个文档
 *   多个知识库都有可能匹配，需要用户明确指定范围
 * 执行流程
 * 
 *   从执行计划中获取澄清话术和原因
 *   向前端推送"思考中"事件，让用户知道系统在处理
 *   记录追踪信息
 *   直接返回澄清问题（不调用大模型，不做检索）
 * @see ConversationExecutor 执行器接口
 * @see ExecutionMode#CLARIFICATION 澄清模式
 */
@Component
public class ClarificationExecutor implements ConversationExecutor {

    /**
     * SSE 流式事件写入器，用于向前端推送思考步骤、状态变化等中间事件。
     */
    private final StreamEventWriter streamEventWriter;

    /**
     * 构造函数，注入依赖。
     *
     * @param streamEventWriter SSE 事件写入器，用于向前端推送中间事件
     */
    public ClarificationExecutor(StreamEventWriter streamEventWriter) {
        this.streamEventWriter = streamEventWriter;
    }

    /**
     * 返回本执行器对应的执行模式：CLARIFICATION（澄清）。
     *
     * @return ExecutionMode#CLARIFICATION
     */
    @Override
    public ExecutionMode mode() {
        return ExecutionMode.CLARIFICATION;
    }

    /**
     * 执行澄清逻辑，直接返回引导用户补充信息的话术。
     *
     * 与 RagChatExecutor 不同，澄清执行器不会调用大模型或检索引擎，
     * 而是直接返回路由阶段已经准备好的澄清话术。
     *
     * @param taskInfo 任务上下文，包含执行计划（其中存储了澄清话术和原因）
     * @return 包含澄清话术的单元素 Flux
     */
    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        // 从任务上下文中获取执行计划
        ConversationExecutionPlan plan = taskInfo.executionPlan();

        // 获取澄清回复话术。如果计划为空或话术为空，使用默认兜底话术
        String clarificationReply = plan == null
            ? "当前我无法稳定判断你想问哪份知识文档，请补充更具体的文档名、主题或关键词。"
            : StrUtil.blankToDefault(plan.getClarificationReply(),
                "当前我无法稳定判断你想问哪份知识文档，请补充更具体的文档名、主题或关键词。");

        // 获取澄清原因（说明为什么需要澄清），用于调试和日志
        String clarificationReason = plan == null ? "" : StrUtil.blankToDefault(plan.getClarificationReason(), "");

        // 如果有调试追踪器，把澄清原因记录到检索备注中，方便排查问题
        if (taskInfo.debugTrace() != null && StrUtil.isNotBlank(clarificationReason)) {
            taskInfo.debugTrace().getRetrievalNotes().add(clarificationReason);
        }

        // 向前端推送"思考中"事件，让用户知道系统识别到了歧义
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "当前问题涉及多份候选文档，先向你确认知识范围。");

        // 如果有澄清原因，也推送给前端作为状态提示
        if (StrUtil.isNotBlank(clarificationReason)) {
            ExecutorEventSupport.publishStatus(taskInfo, streamEventWriter, clarificationReason);
        }

        // 记录追踪阶段：路由阶段完成，返回了澄清问题
        if (taskInfo.traceRecorder() != null) {
            taskInfo.traceRecorder().completeStage(
                taskInfo.traceRecorder().startStage(ConversationTraceStageCode.ROUTE, mode().name(), "当前候选存在歧义，先返回澄清问题。", null),
                "已返回澄清问题。",
                Map.of(
                    "clarificationReply", clarificationReply,
                    "clarificationReason", clarificationReason,
                    "clarificationOptions", plan == null || plan.getClarificationOptions() == null ? List.of() : plan.getClarificationOptions()
                )
            );
        }

        // 直接返回澄清话术（单元素 Flux），不调用模型或检索
        return Flux.just(clarificationReply);
    }
}
