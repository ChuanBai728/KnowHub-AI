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
 * 文档主表实体类。
 *
 * 对应数据库表 knowhub_document，是文档管理模块的核心实体。
 * 记录了用户上传的每个文档的完整生命周期信息。
 *
 * 文档生命周期状态流转：
 * 
 *   上传 → 解析中 → 解析完成 → 策略推荐中 → 策略已确认 → 索引构建中 → 索引构建完成
 *                  ↓                                              ↓
 *              解析失败                                         索引构建失败
 * 本类继承自 BaseTableData，自动获得以下公共字段：
 * 
 *   createTime：记录创建时间
 *   updateTime：记录更新时间
 *   createBy：创建人
 *   updateBy：更新人
 *   deleted：逻辑删除标记
 * MyBatis-Plus 注解说明：
 * 
 *   TableName：映射到数据库表名。
 *   TableId：主键字段，IdType#INPUT 表示主键由外部输入（如百度 UID生成）。
 * Lombok 注解说明：
 * 
 *   Data：自动生成 getter/setter/toString/equals/hashCode。
 *   NoArgsConstructor：生成无参构造器（MyBatis-Plus 反射需要）。
 *   AllArgsConstructor：生成全参构造器。
 *   EqualsAndHashCode(callSuper = true)：equals/hashCode 包含父类字段。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocument extends BaseTableData {

    /**
     * 文档主键 ID。
     * 使用百度 UID生成的全局唯一 ID，由外部传入。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 文档显示名称。
     * 可以由用户自定义，默认使用上传文件的原始名称。
     */
    private String documentName;

    /**
     * 原始文件名（上传时的文件名）。
     * 保留原始文件名便于用户识别文档来源。
     */
    private String originalFileName;

    /**
     * 文件类型枚举值。
     * 标识文档的格式类型，如 PDF、Word、Markdown、HTML 等。
     */
    private Integer fileType;

    /**
     * MIME 类型。
     * 标准的互联网媒体类型，如 application/pdf、application/msword 等。
     */
    private String mimeType;

    /**
     * 文件大小（字节）。
     * 记录原始文件的大小，用于前端展示和存储配额管理。
     */
    private Long fileSize;

    /**
     * 存储类型枚举值。
     * 标识文件存储在哪里，如 MinIO 对象存储、本地文件系统等。
     */
    private Integer storageType;

    /**
     * MinIO 存储桶名称。
     * 文件存储在 MinIO 的哪个桶中。
     */
    private String bucketName;

    /**
     * MinIO 对象名称（文件路径）。
     * 文件在 MinIO 桶中的唯一标识路径。
     */
    private String objectName;

    /**
     * 文件访问 URL。
     * 文件的完整访问地址，可以直接用于下载。
     */
    private String objectUrl;

    /**
     * 解析状态枚举值。
     * 记录文档解析的当前状态：未解析、解析中、解析完成、解析失败等。
     */
    private Integer parseStatus;

    /**
     * 策略状态枚举值。
     * 记录切块策略的状态：未推荐、已推荐、已确认等。
     */
    private Integer strategyStatus;

    /**
     * 索引状态枚举值。
     * 记录索引构建的状态：未构建、构建中、构建完成、构建失败等。
     */
    private Integer indexStatus;

    /**
     * 文档字符总数。
     * 解析后统计的文档字符数量，用于评估文档大小和切块策略选择。
     */
    private Integer charCount;

    /**
     * 文档 Token 总数。
     * 解析后统计的 Token 数量，用于估算 LLM 处理成本。
     */
    private Integer tokenCount;

    /**
     * 结构层级数。
     * 文档解析后识别的标题层级深度（如 3 表示有三级标题）。
     */
    private Integer structureLevel;

    /**
     * 内容质量等级。
     * 系统对文档内容质量的评估结果，影响切块策略推荐。
     */
    private Integer contentQualityLevel;

    /**
     * 解析后纯文本文件的存储路径。
     * 文档解析完成后，纯文本存储在 MinIO 中的对象路径。
     */
    private String parseTextPath;

    /**
     * 解析错误信息。
     * 当解析失败时，记录失败原因，便于排查问题。
     */
    private String parseErrorMsg;

    /**
     * 知识范围编码。
     * 文档所属的知识范围标识，用于知识路由和分类管理。
     */
    private String knowledgeScopeCode;

    /**
     * 知识范围名称。
     * 文档所属知识范围的显示名称，冗余存储避免关联查询。
     */
    private String knowledgeScopeName;

    /**
     * 业务分类。
     * 文档的业务领域分类标签。
     */
    private String businessCategory;

    /**
     * 文档标签。
     * 逗号分隔的标签列表，用于文档的多维度分类。
     */
    private String documentTags;

    /**
     * 当前生效的策略方案 ID。
     * 关联到 KnowHubDocumentStrategyPlan 表，标识用户确认的切块策略。
     */
    private Long currentPlanId;

    /**
     * 最近一次解析任务 ID。
     * 关联到 KnowHubDocumentTask 表，记录最近一次解析任务。
     */
    private Long lastParseTaskId;

    /**
     * 结构节点总数。
     * 文档解析后生成的结构节点（标题、段落等）数量。
     */
    private Integer structureNodeCount;

    /**
     * 最近一次索引构建任务 ID。
     * 关联到 KnowHubDocumentTask 表，记录最近一次索引构建任务。
     */
    private Long lastIndexTaskId;
}
