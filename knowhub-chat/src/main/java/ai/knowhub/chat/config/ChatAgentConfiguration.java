package ai.knowhub.chat.config;

import javax.sql.DataSource;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import ai.knowhub.chat.support.DashScopeCompatibilityInterceptor;
import ai.knowhub.chat.support.TavilyToolInputFallbackInterceptor;
import ai.knowhub.chat.tool.TavilySearchDto;
import ai.knowhub.chat.tool.TavilySearchTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话代理（ReactAgent）的核心配置类。
 *
 * 该类是整个 AI Agent 系统的"大脑"配置中心。它负责：
 * 
 *   配置 MySQL 检查点存储（用于保存 Agent 的执行状态，支持断点续传）
 *   注册联网搜索工具（Tavily Search）
 *   组装完整的 ReactAgent（包含模型、工具、限流、重试等机制）
 * ReactAgent 概念说明
 * ReactAgent 是 Spring AI Alibaba 提供的 ReAct（Reasoning + Acting）模式 Agent。
 * ReAct 模式的工作流程：
 * 
 *   <b>思考（Reason）</b>：大模型分析用户问题，决定是否需要调用工具
 *   <b>行动（Act）</b>：如果需要，调用搜索工具等获取外部信息
 *   <b>观察（Observe）</b>：接收工具返回的结果
 *   <b>循环</b>：重复思考-行动-观察，直到得出最终答案
 * 涉及的注解
 * 
 *   @Configuration —— Spring 配置类，定义 Bean 的创建规则
 *   @Bean —— 标记方法为 Bean 工厂方法，返回的对象会被注册到 Spring 容器
 *   @EnableConfigurationProperties —— 启用配置属性类的自动绑定
 * 
 */
@Configuration
@EnableConfigurationProperties({ChatAgentProperties.class, TavilySearchProperties.class})
public class ChatAgentConfiguration {

    /**
     * 创建 MySQL 检查点存储器 Bean。
     *
     * 检查点（Checkpoint）机制用于保存 Agent 执行过程中的状态快照。
     * 当 Agent 执行多步推理时，每一步的状态都会被保存到 MySQL 数据库中。
     * 这样即使服务重启，也能恢复到之前的执行状态。
     *
     * CREATE_IF_NOT_EXISTS 选项表示如果表不存在则自动创建，
     * 方便开发和部署。
     *
     * @param dataSource 数据源，由 Spring Boot 自动配置（基于 application.yml 中的数据源配置）
     * @return MySQL 检查点存储器实例
     */
    @Bean
    public MysqlSaver mysqlCheckpointSaver(DataSource dataSource) {

        return MysqlSaver.builder()
            .dataSource(dataSource)
            .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
            .build();
    }

    /**
     * 创建 Tavily 联网搜索工具的回调 Bean。
     *
     * Spring AI 的工具（Tool）机制允许大模型在推理过程中调用外部函数。
     * FunctionToolCallback 将一个 Java 函数包装成模型可调用的工具。
     *
     * 工具的三个关键要素：
     * 
     *   <b>名称（name）</b>：模型通过此名称引用工具，如 "tavily_search"
     *   <b>描述（description）</b>：告诉模型这个工具能做什么、何时使用、参数格式
     *   <b>输入类型（inputType）</b>：定义工具接受的参数结构
     * @param tavilySearchTool Tavily 搜索工具的实现类
     * @return 工具回调对象，供 ReactAgent 使用
     */
    @Bean
    public ToolCallback tavilySearchToolCallback(TavilySearchTool tavilySearchTool) {

        return FunctionToolCallback
            .builder("tavily_search", tavilySearchTool::search)
            .description("联网搜索最新信息、事实资料和网页来源。调用时必须传 JSON 参数，且至少包含非空 query；可选 topic 和 maxResults，其中 topic 仅允许 general、news、finance。")
            .inputType(TavilySearchDto.class)
            .build();
    }

    /**
     * 创建业务聊天的 ReactAgent Bean。
     *
     * 这是整个 AI 对话系统的核心组件。通过 Builder 模式配置以下能力：
     *
     * 1. 基础配置
     * 
     *   name：Agent 名称，用于标识和日志
     *   model：底层的 ChatModel（大语言模型），由 Spring AI 自动配置
     *   instruction：系统提示词（System Prompt），定义 Agent 的行为和角色
     * 2. 工具配置
     * 
     *   tools：注册可用工具（如联网搜索）
     *   parallelToolExecution：允许并行调用工具，提高效率
     *   maxParallelTools：最多同时调用 4 个工具
     * 3. 状态持久化
     * 
     *   saver：检查点存储器，将执行状态保存到 MySQL
     * 4. Hooks（钩子）—— 执行限制
     * 
     *   ModelCallLimitHook：限制模型调用次数，防止无限循环
     *   ToolCallLimitHook：限制工具调用次数，防止过度搜索
     * 5. Interceptors（拦截器）—— 增强和容错
     * 
     *   DashScopeCompatibilityInterceptor：兼容阿里云 DashScope API 的特殊处理
     *   TavilyToolInputFallbackInterceptor：搜索工具输入参数的兜底处理
     *   ToolRetryInterceptor：工具调用失败时自动重试（最多 2 次，带抖动退避）
     *   ToolErrorInterceptor：工具调用最终失败时的错误处理
     * @param chatModel                            Spring AI 自动配置的 ChatModel（大语言模型）
     * @param mysqlCheckpointSaver                 MySQL 检查点存储器
     * @param tavilySearchToolCallback             Tavily 搜索工具回调
     * @param chatAgentProperties                  Agent 配置属性（系统提示词、调用限制等）
     * @param dashScopeCompatibilityInterceptor    DashScope 兼容性拦截器
     * @param tavilyToolInputFallbackInterceptor   Tavily 工具输入兜底拦截器
     * @return 配置完成的 ReactAgent 实例
     */
    @Bean
    public ReactAgent businessChatReactAgent(ChatModel chatModel,
                                             MysqlSaver mysqlCheckpointSaver,
                                             ToolCallback tavilySearchToolCallback,
                                             ChatAgentProperties chatAgentProperties,
                                             DashScopeCompatibilityInterceptor dashScopeCompatibilityInterceptor,
                                             TavilyToolInputFallbackInterceptor tavilyToolInputFallbackInterceptor) {
        return ReactAgent.builder()

            // 基础配置
            .name("business_chat_agent")
            .model(chatModel)
            .instruction(chatAgentProperties.getSystemPrompt())

            // 工具和持久化
            .tools(tavilySearchToolCallback)
            .saver(mysqlCheckpointSaver)

            // 并行工具执行：提高多工具调用的效率
            .parallelToolExecution(true)
            .maxParallelTools(4)

            // Hooks：限制模型和工具的调用次数，防止无限循环
            .hooks(
                ModelCallLimitHook.builder()
                    .runLimit(chatAgentProperties.getMaxModelCallsPerRun())      // 单次运行最大模型调用次数
                    .threadLimit(chatAgentProperties.getMaxModelCallsPerThread()) // 整个线程最大模型调用次数
                    .exitBehavior(ModelCallLimitHook.ExitBehavior.END)            // 超限后结束执行
                    .build(),
                ToolCallLimitHook.builder()
                    .toolName("tavily_search")                                    // 限制 tavily_search 工具
                    .runLimit(chatAgentProperties.getMaxToolCallsPerRun())        // 单次运行最大工具调用次数
                    .threadLimit(chatAgentProperties.getMaxToolCallsPerThread())  // 整个线程最大工具调用次数
                    .exitBehavior(ToolCallLimitHook.ExitBehavior.END)             // 超限后结束执行
                    .build()
            )

            // Interceptors：增强和容错机制
            .interceptors(
                dashScopeCompatibilityInterceptor,      // DashScope API 兼容性处理
                tavilyToolInputFallbackInterceptor,     // Tavily 工具输入兜底
                ToolRetryInterceptor.builder()
                    .toolName("tavily_search")          // 针对 tavily_search 工具
                    .maxRetries(2)                      // 最多重试 2 次
                    .initialDelay(200L)                 // 初始延迟 200ms
                    .maxDelay(1200L)                    // 最大延迟 1200ms
                    .jitter(true)                       // 启用抖动，避免惊群效应
                    .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE) // 最终失败时返回错误消息
                    .build(),
                ToolErrorInterceptor.builder().build()  // 通用工具错误处理
            )
            .build();
    }
}
