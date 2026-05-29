package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主题-文档关联项返回值对象（Topic Document Relation Item Vo）
 *
 * 【类的作用】
 * 用于展示知识主题与文档之间的关联关系信息，包括关联分数、
 * 关联来源和关联理由。帮助理解某个主题与哪些文档相关以及相关程度。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于主题-文档关联管理页面的数据展示。
 *
 * 【关键概念】
 * - relationScore：关联分数，表示主题与文档的相关程度
 * - relationSource：关联来源，标识关联是如何建立的（如 LLM 分析、手动标注等）
 * - reason：关联理由，说明为什么该文档与该主题相关
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicDocumentRelationItemVo {

    /**
     * 主题编码（Topic Code）
     * 关联的知识主题编码
     */
    private String topicCode;

    /**
     * 文档ID（Document ID）
     * 关联的文档标识
     */
    private String documentId;

    /**
     * 文档名称（Document Name）
     * 关联的文档名称
     */
    private String documentName;

    /**
     * 知识范围编码（Knowledge Scope Code）
     * 文档所属的知识范围编码
     */
    private String knowledgeScopeCode;

    /**
     * 知识范围名称（Knowledge Scope Name）
     * 文档所属的知识范围名称
     */
    private String knowledgeScopeName;

    /**
     * 业务分类（Business Category）
     * 文档的业务分类
     */
    private String businessCategory;

    /**
     * 文档标签（Document Tags）
     * 文档的标签信息
     */
    private String documentTags;

    /**
     * 关联分数（Relation Score）
     * 主题与文档的相关程度分数
     */
    private String relationScore;

    /**
     * 关联来源（Relation Source）
     * 关联的建立方式（如 LLM 分析、手动标注等）
     */
    private String relationSource;

    /**
     * 关联理由（Reason）
     * 说明为什么该文档与该主题相关
     */
    private String reason;
}
