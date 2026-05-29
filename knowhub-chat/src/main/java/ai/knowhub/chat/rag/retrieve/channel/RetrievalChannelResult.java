package ai.knowhub.chat.rag.retrieve.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 检索通道结果 —— 单个检索通道返回的匹配文档列表。
 *
 * 在 RAG 流水线中的角色
 * 每个 RetrievalChannel 执行检索后返回一个 RetrievalChannelResult，
 * 包含通道名称和检索到的文档列表。多个通道的结果会在后续通过 RRF 融合算法合并。
 *
 * @see RetrievalChannel 检索通道接口
 * @see VectorRetrievalChannel 向量检索通道
 * @see KeywordRetrievalChannel 关键词检索通道
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalChannelResult {

    /**
     * 检索通道名称。
     *
     * 标识结果来自哪个通道，例如 "vector"（向量通道）、"keyword"（关键词通道）。
     * 在 RRF 融合时用于区分不同通道的结果。
     *
     * @see RetrievalChannel#channelName()
     */
    private String channelName;

    /**
     * 检索到的文档列表。
     *
     * 本通道返回的匹配文档，按相关性排序（向量通道按相似度排序，关键词通道按匹配分数排序）。
     * 文档类型为 org.springframework.ai.document.Document，包含文本内容和元数据。
     *
     * 在后续的 RRF 融合中，这些文档会与其它通道的文档合并，按综合排名选出最终候选。
     */
    private List<Document> documents;
}
