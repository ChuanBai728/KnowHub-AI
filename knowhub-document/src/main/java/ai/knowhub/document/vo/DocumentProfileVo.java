package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档画像返回值对象（Document Profile Vo）
 *
 * 【类的作用】
 * 展示文档的智能画像信息，由 LLM 分析文档内容后自动生成。
 * 包含文档摘要、类型、核心主题、示例问题以及图表友好性评估。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于文档画像页面的数据展示。
 * 文档画像是 RAG 系统中的重要元数据，用于辅助路由和检索。
 *
 * 【关键概念】
 * - graphFriendly：文档是否适合用图结构表示
 * - supportsGraphOutline：是否支持图大纲
 * - supportsItemLookup：是否支持条目查找
 * - supportsGraphAssist：是否支持图辅助
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProfileVo {

    /**
     * 文档ID（Document ID）
     * 文档的唯一标识
     */
    private String documentId;

    /**
     * 文档摘要（Document Summary）
     * LLM 生成的文档内容摘要
     */
    private String documentSummary;

    /**
     * 文档类型（Document Type）
     * LLM 判断的文档类型（如技术文档、用户手册、政策文件等）
     */
    private String documentType;

    /**
     * 核心主题（Core Topics）
     * 文档涉及的核心主题列表
     */
    private String coreTopics;

    /**
     * 示例问题（Example Questions）
     * 基于文档内容生成的典型查询问题
     */
    private String exampleQuestions;

    /**
     * 图表友好性（Graph Friendly）
     * 评估文档是否适合用图结构表示
     */
    private String graphFriendly;

    /**
     * 是否支持图大纲（Supports Graph Outline）
     * 评估文档是否支持生成图大纲
     */
    private String supportsGraphOutline;

    /**
     * 是否支持条目查找（Supports Item Lookup）
     * 评估文档是否支持按条目精确查找
     */
    private String supportsItemLookup;

    /**
     * 是否支持图辅助（Supports Graph Assist）
     * 评估文档是否支持图辅助检索
     */
    private String supportsGraphAssist;

    /**
     * 画像来源（Profile Source）
     * 画像数据的生成来源（如 LLM 分析、手动标注等）
     */
    private String profileSource;

    /**
     * 画像状态（Profile Status）
     * 画像的生成状态
     */
    private String profileStatus;

    /**
     * 错误信息（Error Message）
     * 画像生成失败时的错误描述
     */
    private String errorMsg;
}
