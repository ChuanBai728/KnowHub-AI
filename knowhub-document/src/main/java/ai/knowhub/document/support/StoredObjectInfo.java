package ai.knowhub.document.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储对象信息（Stored Object Info）
 *
 * 【类的作用】
 * 表示存储在对象存储服务（如 MinIO、AWS S3）中的文件信息。
 * 包含文件所在的存储桶名称、对象名称和访问 URL。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的文件存储层。当用户上传文档时，文件首先被存储到
 * MinIO 等对象存储服务中，然后通过本对象记录存储位置信息。
 * 后续的文档处理流程会使用这些信息来读取原始文件。
 *
 * 【关键概念】
 * - 存储桶（Bucket）：对象存储中的逻辑容器，类似于文件系统的目录
 * - 对象名称（Object Name）：文件在存储桶中的唯一标识
 * - 对象 URL：文件的访问地址
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoredObjectInfo {

    /**
     * 存储桶名称（Bucket Name）
     * 文件所在的存储桶名称
     */
    private String bucketName;

    /**
     * 对象名称（Object Name）
     * 文件在存储桶中的唯一标识路径
     */
    private String objectName;

    /**
     * 对象 URL（Object URL）
     * 文件的完整访问地址
     */
    private String objectUrl;
}
