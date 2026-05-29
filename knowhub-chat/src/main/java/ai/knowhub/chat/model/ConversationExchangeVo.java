package ai.knowhub.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.chat.model.debug.ChatDebugTrace;
import ai.knowhub.enums.ChatTurnStatus;

import java.util.Date;
import java.util.List;

/**
 * 【对话交换展示】
 *
 * 作用：表示一次完整的「一问一答」对话交换（Exchange）。
 * 用户提出一个问题，AI 给出一个回答，这构成一次交换。
 *
 * 所属架构位置：属于会话管理模块的核心数据模型，是前端展示对话历史的基本单元。
 * 一次会话（Session）包含多次交换（Exchange）。
 *
 * 设计模式说明：「返回值对象 / DTO（Data Transfer Object）」模式，
 * 用于在服务层和前端之间传递对话数据。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationExchangeVo {

    /** 交换 ID，唯一标识一次问答交换，通常为数据库自增主键 */
    private long exchangeId;

    /** 用户提出的问题文本 */
    private String question;

    /** AI 生成的回答文本 */
    private String answer;

    /**
     * 思考步骤列表（thinkingSteps）
     * 记录 AI 在回答过程中的中间思考过程，例如：
     * "正在检索知识库..."、"正在分析问题意图..." 等。
     * 前端可用于展示 AI 的「思考链」动画效果。
     */
    private List<String> thinkingSteps;

    /**
     * 搜索引用列表（references）
     * AI 回答时参考的知识来源，包括文档片段、网页链接等。
     * 前端可用于展示「引用来源」或「参考文档」。
     */
    private List<SearchReference> references;

    /**
     * 推荐问题列表（recommendations）
     * AI 根据当前对话上下文推荐的后续问题，引导用户继续探索。
     */
    private List<String> recommendations;

    /**
     * 使用的工具列表（usedTools）
     * 记录本次回答过程中调用了哪些工具，如 "tavily_search"（网络搜索）、
     * "knowledge_retrieval"（知识检索）等。
     */
    private List<String> usedTools;

    /**
     * 调试追踪信息（debugTrace）
     * 包含详细的调试信息：问题改写、检索过程、模型调用、工具调用等全链路追踪数据。
     * 仅在调试模式下展示，帮助开发者排查问题。
     */
    private ChatDebugTrace debugTrace;

    /**
     * 对话轮次状态（status）
     * 标识本次交换的处理状态，如：进行中、已完成、失败等。
     */
    private ChatTurnStatus status;

    /** 如果处理失败，记录错误信息 */
    private String errorMessage;

    /**
     * 首次响应时间（毫秒）
     * 从用户发送问题到 AI 开始输出第一个字的时间间隔，
     * 用于衡量系统的「首字延迟」（Time to First Token, TTFT）。
     */
    private Long firstResponseTimeMs;

    /**
     * 总响应时间（毫秒）
     * 从用户发送问题到 AI 完成回答的总耗时。
     */
    private Long totalResponseTimeMs;

    /** 对话交换创建时间 */
    private Date createTime;

    /** 对话交换最后编辑时间 */
    private Date editTime;
}
