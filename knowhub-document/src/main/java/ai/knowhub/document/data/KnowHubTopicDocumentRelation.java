package ai.knowhub.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

import java.math.BigDecimal;

/**
 * 主题-文档关联关系实体类。
 *
 * 对应数据库表 knowhub_topic_document_relation，记录知识主题与文档之间的多对多关联关系。
 *
 * 「主题-文档关联」是知识路由系统的核心数据结构。它定义了每个知识主题
 * 下有哪些文档，决定了 RAG 检索时的文档范围。
 *
 * 关联关系的作用：
 * 
 *   <b>检索范围限定</b>：用户提问路由到某个主题后，系统只在该主题关联的文档中检索。
 *   <b>知识组织</b>：将文档按照主题进行分类组织，便于知识管理。
 *   <b>路由优化</b>：关联关系被索引到 Elasticsearch 知识路由索引中，
 *       用于优化路由匹配的准确性。
 * 关联关系支持附加信息：
 * 
 *   relationScore：关联分数，表示文档与主题的相关程度（0-1）。
 *       分数越高表示文档越能代表该主题。
 *   relationSource：关联来源，标识关联是谁建立的：系统自动、人工配置等。
 *   reason：关联理由，说明为什么将此文档关联到该主题。
 * 一个文档可以关联到多个主题，一个主题也可以关联多个文档（多对多关系）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_topic_document_relation")
@EqualsAndHashCode(callSuper = true)
public class KnowHubTopicDocumentRelation extends BaseTableData {

    /**
     * 关联关系主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 主题编码。
     * 关联到 KnowHubKnowledgeTopicNode 表的 topicCode 字段。
     * 标识此关联关系中的主题方。
     */
    private String topicCode;

    /**
     * 文档 ID。
     * 关联到 KnowHubDocument 表。
     * 标识此关联关系中的文档方。
     */
    private Long documentId;

    /**
     * 关联分数。
     * 表示文档与主题的相关程度，取值范围 0-1。
     * 值越高表示文档越能代表该主题。在检索结果排序时可以作为权重因子。
     */
    private BigDecimal relationScore;

    /**
     * 关联来源。
     * 标识此关联关系的建立方式，如 "auto"（系统自动建立）、"manual"（人工配置）等。
     */
    private String relationSource;

    /**
     * 关联理由。
     * 说明为什么将此文档关联到该主题，如 "文档标题包含主题关键词"、"人工指定" 等。
     */
    private String reason;
}
