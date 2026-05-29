package ai.knowhub.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.chat.model.trace.ConversationTraceStageVo;

import java.util.List;

/**
 * 【对话交换详情展示】
 *
 * 作用：展示某次对话交换（一问一答）的完整详情，包括对话基本信息和各阶段的追踪信息。
 * 这是比 ConversationExchangeVo 更详细的展示，额外包含了阶段追踪数据。
 *
 * 所属架构位置：属于会话管理模块的查询层，用于前端展示单次对话的详细调试/追踪信息。
 *
 * 设计模式说明：「返回值对象（Value Object）」模式，聚合了对话交换信息和阶段追踪信息，
 * 提供一个完整的展示供前端消费。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationExchangeDetailVo {

    /**
     * 会话 ID（conversationId）
     * 标识这条对话交换属于哪个会话。一个会话可以包含多次问答交换。
     */
    private String conversationId;

    /**
     * 对话交换展示（exchange）
     * 包含本次问答的基本信息：问题、回答、思考步骤、引用来源、使用的工具等。
     */
    private ConversationExchangeVo exchange;

    /**
     * 阶段追踪列表（stageTraces）
     * 记录本次对话交换在各个处理阶段（如记忆加载、意图分析、问题改写、RAG检索、
     * 回答生成等）的执行详情，用于性能分析和问题排查。
     */
    private List<ConversationTraceStageVo> stageTraces;
}
