package ai.knowhub.document.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.enums.DocumentManageCode;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档管理模块的 MinIO 对象存储配置类。
 *
 * MinIO 是一个高性能的分布式对象存储服务，兼容 Amazon S3 API。
 * 在本系统中，MinIO 用于存储用户上传的原始文档文件（PDF、Word 等）
 * 以及解析后的纯文本文件。
 *
 * 本类负责：
 * 
 *   创建 MinIO 客户端 Bean，连接到 MinIO 服务器。
 *   在应用启动时自动检查并创建存储桶（Bucket），确保文件上传功能可用。
 * MinIO 核心概念：
 * 
 *   <b>Bucket（存储桶）</b>：类似于文件系统的顶层目录，用于组织对象。
 *   <b>Object（对象）</b>：存储在 Bucket 中的文件，通过 ObjectName（键）唯一标识。
 *   <b>Endpoint</b>：MinIO 服务器的访问地址。
 *   <b>AccessKey/SecretKey</b>：访问 MinIO 的认证凭据。
 * 设计模式：配置类模式 + 命令行运行器模式（CommandLineRunner）。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DocumentManageProperties.class)
public class DocumentManageMinioConfiguration {

    /**
     * 创建 MinIO 客户端 Bean。
     *
     * MinIO 客户端是线程安全的，整个应用共享一个实例即可。
     * 它封装了与 MinIO 服务器的所有交互，包括文件上传、下载、删除等操作。
     *
     * @param properties 文档管理配置属性（包含 endpoint、accessKey、secretKey）
     * @return MinIO 客户端实例
     */
    @Bean
    public MinioClient documentMinioClient(DocumentManageProperties properties) {
        return MinioClient.builder()
            .endpoint(properties.getMinio().getEndpoint())      // MinIO 服务器地址，如 http://127.0.0.1:9000
            .credentials(properties.getMinio().getAccessKey(),   // 访问密钥
                         properties.getMinio().getSecretKey())   // 秘密密钥
            .build();
    }

    /**
     * 创建 MinIO 存储桶初始化器。
     *
     * 使用 CommandLineRunner 在应用启动完成后自动执行。
     * 它会检查配置中指定的 Bucket 是否存在，不存在则自动创建。
     * 这样可以避免首次部署时因 Bucket 不存在导致文件上传失败。
     *
     * 注意：这里捕获异常后抛出自定义异常 KnowHubFrameException，
     * 使用 DocumentManageCode#DOCUMENT_STORAGE_FAILED 错误码，
     * 便于前端统一处理错误。
     *
     * @param documentMinioClient MinIO 客户端（由上面的 Bean 注入）
     * @param properties          文档管理配置属性（包含 bucketName）
     * @return 命令行运行器实例
     */
    @Bean
    public CommandLineRunner documentMinioBucketInitializer(MinioClient documentMinioClient,
                                                            DocumentManageProperties properties) {
        return args -> {
            String bucketName = properties.getMinio().getBucketName();
            try {
                // 检查 Bucket 是否已存在
                boolean exists = documentMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
                if (!exists) {
                    // Bucket 不存在，自动创建
                    documentMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                    log.info("文档管理模块 MinIO bucket 不存在，已自动创建，bucket={}", bucketName);
                }
                else {
                    log.info("文档管理模块 MinIO bucket 已存在，bucket={}", bucketName);
                }
            }
            catch (Exception exception) {
                // 初始化失败，抛出自定义异常，阻止应用继续启动（因为文件存储不可用）
                throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                    "初始化 MinIO bucket 失败: " + exception.getMessage(), exception);
            }
        };
    }
}
