package ai.knowhub.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

/**
 * 文档画像实体类。
 *
 * 对应数据库表 knowhub_document_profile，记录文档的结构化画像信息。
 *
 * 「文档画像」是使用 LLM 对文档进行深度分析后生成的结构化摘要。
 * 它是知识路由系统的重要输入，帮助系统在用户提问时快速定位到相关文档。
 *
 * 画像包含以下关键信息：
 * 
 *   <b>文档摘要</b>：文档核心内容的简要概述。
 *   <b>文档类型</b>：如教程、API 文档、政策规范等。
 *   <b>核心主题</b>：文档涉及的主要话题。
 *   <b>示例问题</b>：文档可以回答的典型问题，用于优化路由匹配。
 *   <b>图谱友好度</b>：评估文档是否适合构建知识图谱。
 * 画像生成流程：
 * 
 *   文档解析完成后，系统自动触发画像生成。
 *   将文档摘要文本发送给 LLM，要求其分析文档特征。
 *   LLM 返回结构化的画像信息，存入此表。
 *   画像信息被索引到 Elasticsearch 知识路由索引中，用于检索匹配。
 * 版本管理：通过 profileVersion 字段支持画像的版本管理，
 * 每次重新生成画像时版本号递增，保留历史画像记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document_profile")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocumentProfile extends BaseTableData {

    /**
     * 画像主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 关联的文档 ID。
     * 关联到 KnowHubDocument 表。
     */
    private Long documentId;

    /**
     * 画像版本号。
     * 每次重新生成画像时递增，支持画像的历史追踪和回滚。
     */
    private Integer profileVersion;

    /**
     * 文档摘要。
     * LLM 生成的文档核心内容概述，通常 100-300 字。
     * 用于知识路由时快速判断文档是否与用户问题相关。
     */
    private String documentSummary;

    /**
     * 文档类型。
     * LLM 判断的文档类型，如「教程」「API 文档」「政策规范」「FAQ」等。
     * 不同类型的文档在检索时可能采用不同的策略。
     */
    private String documentType;

    /**
     * 核心主题。
     * LLM 提取的文档涉及的主要话题列表，JSON 格式存储。
     * 用于知识路由时的主题匹配。
     */
    private String coreTopics;

    /**
     * 示例问题。
     * LLM 生成的文档可以回答的典型问题列表，JSON 格式存储。
     * 用于优化知识路由的准确性——如果用户问题与示例问题相似度高，
     * 说明此文档很可能包含答案。
     */
    private String exampleQuestions;

    /**
     * 图谱友好度。
     * LLM 评估文档是否适合构建知识图谱的评分。
     * 结构化程度高的文档（如 API 文档）图谱友好度更高。
     */
    private Integer graphFriendly;

    /**
     * 是否支持图谱大纲。
     * 标识文档是否可以生成知识图谱的大纲结构。
     */
    private Integer supportsGraphOutline;

    /**
     * 是否支持条目查找。
     * 标识文档是否包含可以精确查找的条目（如 API 列表、术语表等）。
     */
    private Integer supportsItemLookup;

     /**
     * 是否支持图谱辅助。
     * 标识文档是否可以利用图谱关系进行辅助检索。
     */
    private Integer supportsGraphAssist;

    /**
     * 画像来源。
     * 标识画像的生成方式，如 "llm"（LLM 自动生成）、"manual"（人工录入）等。
     */
    private String profileSource;

    /**
     * 画像状态枚举值。
     * 标识画像的当前状态：生成中、已完成、生成失败等。
     */
    private Integer profileStatus;

    /**
     * 错误信息。
     * 当画像生成失败时，记录失败原因。
     */
    private String errorMsg;
}
