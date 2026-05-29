package ai.knowhub.chat.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import ai.knowhub.chat.dto.ChatRequestDto;
import ai.knowhub.chat.dto.ConversationExchangeDetailQueryDto;
import ai.knowhub.chat.dto.ConversationIdentityDto;
import ai.knowhub.chat.dto.ConversationSessionListQueryDto;
import ai.knowhub.chat.dto.RetrievalObserveQueryDto;
import ai.knowhub.chat.model.ChannelExecutionVo;
import ai.knowhub.chat.model.ConversationExchangeDetailVo;
import ai.knowhub.chat.model.ConversationMemorySummaryVo;
import ai.knowhub.chat.model.ConversationSessionVo;
import ai.knowhub.chat.model.KnowledgeDocumentOptionVo;
import ai.knowhub.chat.model.RetrievalResultVo;
import ai.knowhub.chat.model.StageBenchmarkVo;
import ai.knowhub.chat.service.BusinessChatService;
import ai.knowhub.chat.vo.ConversationResetVo;
import ai.knowhub.chat.vo.ConversationSessionListVo;
import ai.knowhub.chat.vo.ConversationStopVo;
import ai.knowhub.common.ApiResponse;
import ai.knowhub.web.ApiVersion;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 业务聊天的核心控制器。
 *
 * 该控制器是前端与 AI Agent 系统交互的入口，提供以下功能：
 * 
 *   <b>SSE 流式聊天</b>：前端发送问题，后端以 SSE（Server-Sent Events）流的形式
 *       边生成边推送 AI 回答，实现"打字机"效果
 *   <b>会话管理</b>：查看会话列表、获取会话详情、停止会话、重置会话
 *   <b>调试面板</b>：查看检索结果、通道执行详情、各阶段耗时统计
 * SSE（Server-Sent Events）流式推送
 * SSE 是 HTML5 提供的一种服务器向客户端单向推送数据的技术：
 * 
 *   基于 HTTP 协议，不需要 WebSocket 那样的协议升级
 *   服务器可以持续向客户端发送数据，客户端通过 EventSource API 接收
 *   非常适合 AI 聊天场景：模型生成一个 Token 就推送一个，用户无需等待完整回答
 * Flux&lt;String&gt; 返回类型
 * Flux<String> 是 Reactor 库提供的响应式类型，表示一个异步的数据流。
 * Spring MVC 会自动将其转换为 SSE 格式的 HTTP 响应。
 *
 * 涉及的注解
 * 
 *   @RestController —— 标记为 REST 控制器，返回值自动序列化为 JSON
 *   @AllArgsConstructor —— Lombok 注解，生成包含所有字段的构造函数（用于构造器注入）
 *   @RequestMapping(ApiVersion.V1_CHAT) —— 统一路径前缀
 *   @PostMapping —— 处理 HTTP POST 请求
 *   @Valid —— 触发参数校验
 *   @RequestBody —— 将请求体 JSON 反序列化为 Java 对象
 * 
 */
@AllArgsConstructor
@RestController
@RequestMapping(ApiVersion.V1_CHAT)
public class BusinessChatController {

    /** 业务聊天服务，处理具体的业务逻辑 */
    private final BusinessChatService businessChatService;

    /**
     * SSE 流式聊天接口 —— 聊天的主入口。
     *
     * 前端提交问题后，此接口返回一个 SSE 流（Flux<String>），
     * AI 生成的每个 Token 都会实时推送给前端，实现"打字机"般的流式输出效果。
     *
     * produces = "text/event-stream;charset=UTF-8" 表示响应的 Content-Type 为 SSE 格式。
     *
     * @param dto 聊天请求对象，包含用户问题、会话 ID 等信息
     * @return SSE 数据流，每个元素是一个 SSE 事件字符串
     */
    // 聊天主入口：前端提交问题后，这里返回 SSE 流，让答案可以边生成边推送给浏览器。
    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> stream(@Valid @RequestBody ChatRequestDto dto) {
        return businessChatService.openConversationStream(dto);
    }

    /**
     * 获取可检索的文档选项列表。
     *
     * 在进行文档问答之前，前端需要先查询哪些文档已经构建了索引、可以被检索。
     * 只有索引构建成功的文档才会出现在此列表中。
     *
     * @return 已就绪的知识文档列表
     */
    // 文档问答前先让前端查询可检索文档，只有索引构建成功的文档才会出现在这里。
    @PostMapping("/document/options")
    public ApiResponse<List<KnowledgeDocumentOptionVo>> documentOptions() {
        return ApiResponse.ok(businessChatService.listKnowledgeDocumentOptions());
    }

    /**
     * 停止当前会话的 AI 生成。
     *
     * 当用户不希望等待 AI 完成回答时，可以调用此接口中断生成过程。
     *
     * @param dto 包含会话 ID 的请求对象
     * @return 停止操作的结果
     */
    @PostMapping("/session/stop")
    public ApiResponse<ConversationStopVo> stop(@Valid @RequestBody ConversationIdentityDto dto) {
        return ApiResponse.ok(businessChatService.stopConversation(dto.getConversationId()));
    }

    /**
     * 获取单个会话的详情。
     *
     * 返回会话的基本信息，如会话状态、创建时间、选中的文档等。
     *
     * @param dto 包含会话 ID 的请求对象
     * @return 会话详情
     */
    @PostMapping("/session/detail")
    public ApiResponse<ConversationSessionVo> session(@Valid @RequestBody ConversationIdentityDto dto) {
        return ApiResponse.ok(businessChatService.getSession(dto.getConversationId()));
    }

    /**
     * 获取单次对话交换（Exchange）的详情。
     *
     * 一次"交换"指用户问一个问题、AI 回答一次的完整过程。
     * 此接口返回该次交换的详细信息，包括问题、回答、思考步骤、引用来源等。
     *
     * @param dto 包含会话 ID 和交换 ID 的请求对象
     * @return 对话交换的详细信息
     */
    @PostMapping("/exchange/detail")
    public ApiResponse<ConversationExchangeDetailVo> exchange(@Valid @RequestBody ConversationExchangeDetailQueryDto dto) {
        return ApiResponse.ok(businessChatService.getExchangeDetail(dto.getConversationId(), dto.getExchangeId()));
    }

    /**
     * 获取会话列表。
     *
     * 返回当前用户的所有会话列表，支持分页查询。
     * 前端通常用此接口展示"历史对话"列表。
     *
     * @param dto 查询条件（可选），支持分页参数
     * @return 会话列表
     */
    @PostMapping("/session/list")
    public ApiResponse<ConversationSessionListVo> sessions(@RequestBody(required = false) ConversationSessionListQueryDto dto) {
        return ApiResponse.ok(businessChatService.listSessions(dto));
    }

    /**
     * 重置会话。
     *
     * 清空会话的所有历史对话记录，相当于开启一个全新的对话。
     * 常用于用户想要切换话题或重新开始时。
     *
     * @param dto 包含会话 ID 的请求对象
     * @return 重置操作的结果
     */
    @PostMapping("/session/reset")
    public ApiResponse<ConversationResetVo> reset(@Valid @RequestBody ConversationIdentityDto dto) {
        return ApiResponse.ok(businessChatService.resetConversation(dto.getConversationId()));
    }

    /**
     * 重建会话的对话记忆摘要。
     *
     * 当对话历史较长时，系统会自动压缩历史消息为摘要以节省 Token。
     * 此接口可以手动触发重新生成摘要，通常在摘要质量不佳时使用。
     *
     * @param dto 包含会话 ID 的请求对象
     * @return 重新生成的记忆摘要
     */
    @PostMapping("/session/summary/rebuild")
    public ApiResponse<ConversationMemorySummaryVo> rebuildSummary(@Valid @RequestBody ConversationIdentityDto dto) {
        return ApiResponse.ok(businessChatService.rebuildConversationSummary(dto.getConversationId()));
    }

    /**
     * 获取某次对话交换的 RAG 检索结果。
     *
     * 主要用于调试面板，展示 AI 在回答某个问题时从知识库中检索到了哪些文档片段，
     * 以及每个片段的相似度分数、排名等信息。
     *
     * @param dto 包含会话 ID 和交换 ID 的请求对象
     * @return 检索结果列表，包含文档片段、分数、排名等详细信息
     */
    // 下面三个接口主要服务调试面板：查看本轮命中的证据、检索通道和各阶段耗时。
    @PostMapping("/exchange/retrieval/results")
    public ApiResponse<List<RetrievalResultVo>> retrievalResults(@Valid @RequestBody RetrievalObserveQueryDto dto) {
        return ApiResponse.ok(businessChatService.getRetrievalResults(dto.getConversationId(), Long.parseLong(dto.getExchangeId())));
    }

    /**
     * 获取某次对话交换的检索通道执行详情。
     *
     * RAG 系统支持多通道检索（如关键词检索 + 向量检索），
     * 此接口展示每个通道的执行状态、召回数量、耗时等信息。
     *
     * @param dto 包含会话 ID 和交换 ID 的请求对象
     * @return 通道执行详情列表
     */
    @PostMapping("/exchange/channel/executions")
    public ApiResponse<List<ChannelExecutionVo>> channelExecutions(@Valid @RequestBody RetrievalObserveQueryDto dto) {
        return ApiResponse.ok(businessChatService.getChannelExecutions(dto.getConversationId(), Long.parseLong(dto.getExchangeId())));
    }

    /**
     * 获取各阶段的性能基准统计数据。
     *
     * 展示 RAG 管道中各阶段（如查询改写、检索、重排序等）的耗时统计，
     * 包括 P50、P90、P99 分位数和平均值，用于性能监控和优化。
     *
     * @return 各阶段的性能基准数据
     */
    @PostMapping("/stage/benchmarks")
    public ApiResponse<List<StageBenchmarkVo>> stageBenchmarks() {
        return ApiResponse.ok(businessChatService.getStageBenchmarks());
    }
}
