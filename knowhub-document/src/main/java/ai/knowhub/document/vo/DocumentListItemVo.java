package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 文档列表项返回值对象（Document List Item Vo）
 *
 * 【类的作用】
 * 用于展示文档列表中单个文档的完整信息，包括文档基本信息、
 * 解析状态、策略状态、索引状态、知识范围分类等。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于文档管理列表页面的数据展示。
 * 是文档管理中最常用的返回值对象之一。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentListItemVo {

    /** 文档ID */
    private Long documentId;

    /** 文档名称（处理后的标准化名称） */
    private String documentName;

    /** 原始文件名（用户上传时的文件名） */
    private String originalFileName;

    /** 文件类型编码 */
    private Integer fileType;

    /** 文件类型名称（如 PDF、Word、Markdown 等） */
    private String fileTypeName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 字符数 */
    private Integer charCount;

    /** Token 数量 */
    private Integer tokenCount;

    /** 解析状态编码 */
    private Integer parseStatus;

    /** 解析状态名称 */
    private String parseStatusName;

    /** 策略状态编码 */
    private Integer strategyStatus;

    /** 策略状态名称 */
    private String strategyStatusName;

    /** 索引状态编码 */
    private Integer indexStatus;

    /** 索引状态名称 */
    private String indexStatusName;

    /** 解析错误信息（解析失败时的错误描述） */
    private String parseErrorMsg;

    /** 知识范围编码 */
    private String knowledgeScopeCode;

    /** 知识范围名称 */
    private String knowledgeScopeName;

    /** 业务分类 */
    private String businessCategory;

    /** 文档标签（多个标签用逗号分隔） */
    private String documentTags;

    /** 当前策略计划ID */
    private Long currentPlanId;

    /** 最后一次索引任务ID */
    private Long lastIndexTaskId;

    /** 最新任务ID */
    private Long latestTaskId;

    /** 最新任务类型编码 */
    private Integer latestTaskType;

    /** 最新任务类型名称 */
    private String latestTaskTypeName;

    /** 最新任务状态编码 */
    private Integer latestTaskStatus;

    /** 最新任务状态名称 */
    private String latestTaskStatusName;

    /** 创建时间 */
    private Date createTime;

    /** 编辑时间 */
    private Date editTime;
}
