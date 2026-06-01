package ai.knowhub.chat.rag.executor;

import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.service.TaskInfo;
import reactor.core.publisher.Flux;

/**
 * 会话执行器接口 —— 策略模式（Strategy Pattern）的核心抽象。
 *
 * 设计模式：策略模式
 * 本接口定义了"如何执行一次对话"的统一契约。系统中存在多种执行策略
 * （如 RAG 检索问答、结构图直答、ReAct Agent 自主推理、澄清等），
 * 每种策略实现本接口，提供不同的执行逻辑。
 *
 * 上层编排器（ConversationExecutorRegistry）根据路由决策得到的 ExecutionMode
 * 选择对应的执行器，调用 #execute(TaskInfo) 方法。这样新增执行模式只需：
 * 
 *   在 ExecutionMode 枚举中添加新值
 *   实现本接口
 *   加上 @Component 注解让 Spring 自动注册
 * 
 * 不需要修改任何调用方代码，符合开闭原则（对扩展开放，对修改关闭）。
 *
 * 各实现类一览
 * 
 *   RagChatExecutor — 普通知识库检索问答（RETRIEVAL 模式）
 *   GraphOnlyExecutor — 结构图直答（GRAPH_ONLY 模式）
 *   GraphThenEvidenceExecutor — 结构图定位后取证（GRAPH_THEN_EVIDENCE 模式）
 *   ReactAgentExecutor — 开放式 ReAct Agent（REACT_AGENT 模式）
 *   ClarificationExecutor — 澄清模式（CLARIFICATION 模式）
 * @see ConversationExecutorRegistry 执行器注册表，负责根据模式分发到具体实现
 * @see ExecutionMode 执行模式枚举
 */
public interface ConversationExecutor {

    /**
     * 返回本执行器对应的执行模式。
     *
     * 注册表会用这个方法把执行器和 ExecutionMode 关联起来。
     * 每个实现类返回一个固定的枚举值，例如 RagChatExecutor 返回 RETRIEVAL。
     *
     * @return 本执行器处理的执行模式
     */
    ExecutionMode mode();

    /**
     * 执行对话任务，返回流式文本响应。
     *
     * 这是策略模式的核心方法。每个实现类在这里定义自己的执行逻辑：
     * 
     *   有的会先检索文档再生成答案（RagChatExecutor）
     *   有的直接查结构图返回结果（GraphOnlyExecutor）
     *   有的交给 Agent 自主推理（ReactAgentExecutor）
     *   有的直接返回澄清问题（ClarificationExecutor）
     * 返回 Flux<String> 而不是 String，是为了支持 SSE（Server-Sent Events）
     * 流式输出——模型一边生成，前端一边显示，用户体验更好。
     *
     * @param taskInfo 任务上下文信息，包含执行计划、调试追踪、引用记录等
     * @return 流式文本响应，每个元素是一小段文本（通常是几个字到一句话）
     */
    Flux<String> execute(TaskInfo taskInfo);
}
