package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子问题通道追踪 —— 记录单个子问题在单个检索通道中的检索统计。
 *
 * 在 RAG 流水线中的角色
 * 每个子问题会经过多个检索通道（如向量通道、关键词通道），每个通道会召回一批文档。
 * 本类记录单个通道的召回统计，用于：
 * 
 *   调试：了解每个通道贡献了多少文档
 *   监控：追踪各通道的召回率和采纳率
 *   优化：根据统计数据调整通道权重或阈值
 * @see SubQuestionEvidence#channelTraces 在子问题证据中引用
 * @see ai.knowhub.chat.rag.retrieve.channel.RetrievalChannel 检索通道接口
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubQuestionChannelTrace {

    /**
     * 检索通道名称。
     *
     * 例如 "vector"（向量通道）、"keyword"（关键词通道）。
     * 对应 ai.knowhub.chat.rag.retrieve.channel.RetrievalChannel#channelName() 的返回值。
     */
    private String channelName;

    /**
     * 召回的文档数量。
     *
     * 该通道在本次检索中返回的文档总数（未经过滤和融合）。
     */
    private int recalledCount;

    /**
     * 被采纳的文档数量。
     *
     * 经过质量筛选（如相似度阈值、相对得分过滤）后被保留的文档数。
     * adoptedCount / recalledCount 可以反映该通道的"命中率"。
     */
    private int acceptedCount;
}
