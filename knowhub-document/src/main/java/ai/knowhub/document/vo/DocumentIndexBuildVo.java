package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档索引构建返回值对象（Document Index Build Vo）
 *
 * 【类的作用】
 * 用于展示文档索引构建任务的状态信息，包括任务类型、任务状态和索引状态。
 * 索引构建是将文档切片向量化并存储到向量数据库的过程。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于索引构建任务的状态展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIndexBuildVo {

    /**
     * 文档ID（Document ID）
     * 关联的文档标识
     */
    private Long documentId;

    /**
     * 任务ID（Task ID）
     * 索引构建任务的唯一标识
     */
    private Long taskId;

    /**
     * 任务类型编码（Task Type）
     * 任务类型的编码值
     */
    private Integer taskType;

    /**
     * 任务类型名称（Task Type Name）
     * 任务类型的可读名称
     */
    private String taskTypeName;

    /**
     * 任务状态编码（Task Status）
     * 任务状态的编码值（如：待执行、执行中、已完成、失败）
     */
    private Integer taskStatus;

    /**
     * 任务状态名称（Task Status Name）
     * 任务状态的可读名称
     */
    private String taskStatusName;

    /**
     * 索引状态编码（Index Status）
     * 索引构建状态的编码值
     */
    private Integer indexStatus;

    /**
     * 索引状态名称（Index Status Name）
     * 索引构建状态的可读名称
     */
    private String indexStatusName;
}
