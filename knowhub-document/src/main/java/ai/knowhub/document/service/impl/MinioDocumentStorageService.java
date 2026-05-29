package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.service.DocumentStorageService;
import ai.knowhub.document.support.StoredObjectInfo;
import ai.knowhub.enums.DocumentManageCode;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 【文档对象存储服务 - MinIO 实现】
 *
 * 设计模式：适配器模式（Adapter Pattern）
 *
 * 这个类是 DocumentStorageService 接口的 MinIO 实现，负责所有文件的上传、下载和删除操作。
 *
 * MinIO 是什么：
 *   MinIO 是一个高性能的分布式对象存储服务，兼容 Amazon S3 API。
 *   本项目用它来存储：
 *     - 原始上传文件（PDF、Word、Markdown 等）
 *     - 解析后的纯文本（.txt 文件）
 *
 * 存储路径设计：
 *   - 原始文件：{objectPrefix}/{documentId}/{timestamp}-{originalFileName}
 *   - 解析文本：{parsedTextPrefix}/{documentId}.txt
 *
 * 桶（Bucket）管理：
 *   - 所有文件存储在同一个桶中，桶名通过配置文件指定
 *   - 上传前会自动检查桶是否存在，不存在则自动创建
 *
 * 依赖组件：
 *   - MinioClient：MinIO 的 Java SDK 客户端
 *   - DocumentManageProperties：配置属性，包含桶名、前缀、endpoint 等
 */
@AllArgsConstructor
@Service
public class MinioDocumentStorageService implements DocumentStorageService {

    /** MinIO 客户端，通过 Spring 自动注入 */
    private final MinioClient minioClient;

    /** 文档管理配置属性（包含 MinIO 的桶名、前缀、endpoint 等） */
    private final DocumentManageProperties properties;

    /**
     * 上传原始文件到 MinIO
     *
     * 存储路径格式：{objectPrefix}/{documentId}/{timestamp}-{originalFileName}
     * 例如：documents/1234567890/1716800000000-用户手册.pdf
     *
     * @param documentId      文档ID
     * @param originalFileName 原始文件名
     * @param bytes            文件字节内容
     * @param contentType      MIME 类型（如 application/pdf）
     * @return 存储对象信息（桶名、对象名、访问 URL）
     */
    @Override
    public StoredObjectInfo uploadOriginalFile(Long documentId, String originalFileName, byte[] bytes, String contentType) {

        String objectName = properties.getMinio().getObjectPrefix() + "/" + documentId + "/" + System.currentTimeMillis() + "-" + originalFileName;
        upload(objectName, bytes, contentType);
        return new StoredObjectInfo(properties.getMinio().getBucketName(), objectName, buildObjectUrl(objectName));
    }

    /**
     * 上传解析后的纯文本到 MinIO
     *
     * 存储路径格式：{parsedTextPrefix}/{documentId}.txt
     * 例如：parsed-text/1234567890.txt
     *
     * @param documentId 文档ID
     * @param parsedText 解析后的纯文本内容
     * @return 对象存储路径（objectName），后续可通过此路径下载
     */
    @Override
    public String uploadParsedText(Long documentId, String parsedText) {

        String objectName = properties.getMinio().getParsedTextPrefix() + "/" + documentId + ".txt";
        upload(objectName, parsedText.getBytes(StandardCharsets.UTF_8), "text/plain;charset=UTF-8");
        return objectName;
    }

    /**
     * 从 MinIO 下载文件的字节内容
     *
     * @param objectName 对象存储路径
     * @return 文件的字节数组
     */
    @Override
    public byte[] downloadObject(String objectName) {
        try (InputStream inputStream = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(properties.getMinio().getBucketName())
                .object(objectName)
                .build())) {

            return inputStream.readAllBytes();
        }
        catch (Exception exception) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                "下载 MinIO 文件失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 从 MinIO 下载文本文件并返回字符串
     *
     * @param objectName 对象存储路径
     * @return 文本内容字符串
     */
    @Override
    public String downloadText(String objectName) {

        return new String(downloadObject(objectName), StandardCharsets.UTF_8);
    }

    /**
     * 批量删除 MinIO 中的对象
     *
     * @param objectNameList 待删除的对象路径列表
     */
    @Override
    public void deleteObjects(List<String> objectNameList) {
        if (CollUtil.isEmpty(objectNameList)) {
            return;
        }

        // 过滤、去重空白路径
        List<String> validObjectNameList = objectNameList.stream()
            .filter(StrUtil::isNotBlank)
            .map(String::trim)
            .distinct()
            .toList();
        if (validObjectNameList.isEmpty()) {
            return;
        }

        try {

            // 如果桶不存在，说明没有任何文件，直接返回
            if (!bucketExists()) {
                return;
            }

            // 逐个删除对象（MinIO SDK 不支持单次批量删除多个对象的简单接口）
            for (String objectName : validObjectNameList) {
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(properties.getMinio().getBucketName())
                        .object(objectName)
                        .build()
                );
            }
        }
        catch (Exception exception) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                "删除 MinIO 文件失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 通用上传方法：确保桶存在后上传文件
     *
     * @param objectName  对象存储路径
     * @param bytes       文件字节内容
     * @param contentType MIME 类型
     */
    private void upload(String objectName, byte[] bytes, String contentType) {
        try {

            ensureBucketExists();
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucketName())
                    .object(objectName)
                    .contentType(StrUtil.isNotBlank(contentType) ? contentType : "application/octet-stream")
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .build()
            );
        }
        catch (Exception exception) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                "上传 MinIO 文件失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 确保桶存在，不存在则创建
     */
    private void ensureBucketExists() throws Exception {
        if (!bucketExists()) {

            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getMinio().getBucketName()).build());
        }
    }

    /**
     * 检查桶是否存在
     * @return true 表示桶已存在
     */
    private boolean bucketExists() throws Exception {
        String bucketName = properties.getMinio().getBucketName();
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 构建对象的完整访问 URL
     * 格式：{endpoint}/{bucketName}/{objectName}
     *
     * @param objectName 对象存储路径
     * @return 完整 URL
     */
    private String buildObjectUrl(String objectName) {
        String endpoint = properties.getMinio().getEndpoint();
        if (endpoint.endsWith("/")) {

            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + properties.getMinio().getBucketName() + "/" + objectName;
    }
}
