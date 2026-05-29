package ai.knowhub.document.service;

import ai.knowhub.document.support.StoredObjectInfo;

import java.util.List;

/**
 * 【文档存储服务接口】
 *
 * 作用：管理文档文件的存储操作，包括原始文件和解析后文本的上传、下载和删除。
 * 是文档存储层的抽象接口，底层通常对接对象存储服务（如 MinIO、AWS S3）。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"存储层"
 *   - 负责将文件持久化到对象存储系统
 *   - 提供文件的上传、下载、删除等基础操作
 *   - 分离原始文件存储和解析文本存储，便于独立管理
 *
 * 核心概念：
 *   - 对象存储（Object Storage）：以对象为单位的分布式存储系统，适合存储大文件
 *   - 对象名称（Object Name）：文件在存储系统中的唯一标识路径
 *   - 存储对象信息（StoredObjectInfo）：上传成功后返回的元数据，包含对象名称、大小等
 *   - 内容类型（Content Type）：文件的 MIME 类型，用于存储和下载时的类型标识
 */
public interface DocumentStorageService {

    /**
     * 上传原始文件
     *
     * 功能说明：
     *   - 将用户上传的原始文档文件存储到对象存储
     *   - 返回存储后的对象信息（对象名称、大小等）
     *   - 对象名称通常包含文档ID，便于关联和管理
     *
     * @param documentId     文档ID，用于生成存储路径和关联文档记录
     * @param originalFileName 原始文件名，保留文件的原始标识
     * @param bytes          文件的原始字节内容
     * @param contentType    文件的 MIME 类型（如 "application/pdf"）
     * @return 存储对象信息，包含对象名称（用于后续下载/删除）和文件大小
     */
    StoredObjectInfo uploadOriginalFile(Long documentId, String originalFileName, byte[] bytes, String contentType);

    /**
     * 上传解析后的纯文本
     *
     * 功能说明：
     *   - 将文档解析后提取的纯文本存储到对象存储
     *   - 与原始文件分开存储，便于独立访问和管理
     *   - 解析文本用于后续的分块和索引构建
     *
     * @param documentId 文档ID
     * @param parsedText 解析后的纯文本内容
     * @return 存储后的对象名称（用于后续下载/删除）
     */
    String uploadParsedText(Long documentId, String parsedText);

    /**
     * 下载对象文件（返回原始字节）
     *
     * @param objectName 对象名称（上传时返回的对象标识）
     * @return 文件的原始字节内容
     */
    byte[] downloadObject(String objectName);

    /**
     * 下载文本内容
     *
     * @param objectName 对象名称
     * @return 文本内容字符串
     */
    String downloadText(String objectName);

    /**
     * 批量删除对象
     *
     * 功能说明：
     *   - 根据对象名称列表批量删除存储中的文件
     *   - 通常在文档删除时调用，清理关联的存储文件
     *
     * @param objectNameList 要删除的对象名称列表
     */
    void deleteObjects(List<String> objectNameList);
}
