package ai.knowhub.enums;

/**
 * 【文档存储类型枚举】
 *
 * 作用：定义文档原始文件的存储后端类型。
 *
 * 业务背景：
 *   上传的文档原始文件需要持久化存储，系统使用 MinIO（兼容 S3 协议的对象存储）作为文件存储后端。
 *   预留了枚举扩展点，未来可以支持其他存储类型（如阿里云 OSS、AWS S3 等）。
 *
 * 使用示例：
 *   DocumentStorageTypeEnum type = DocumentStorageTypeEnum.getRc(1);  // 返回 MINIO
 */
public enum DocumentStorageTypeEnum {

    /** MinIO 对象存储（兼容 S3 协议，可私有化部署） */
    MINIO(1, "MinIO");

    /**
     * 数字编码
     */
    private final Integer code;

    /**
     * 存储类型名称
     */
    private final String msg;

    /**
     * 枚举构造方法
     *
     * @param code 数字编码
     * @param msg  存储类型名称
     */
    DocumentStorageTypeEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取数字编码
     * @return 编码值
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取存储类型名称
     * @return 名称文本，如果 msg 为 null 则返回空字符串
     */
    public String getMsg() {
        return msg == null ? "" : msg;
    }

    /**
     * 根据数字编码查找对应的枚举实例
     *
     * @param code 数字编码
     * @return 对应的枚举实例，未找到则返回 null
     */
    public static DocumentStorageTypeEnum getRc(Integer code) {
        for (DocumentStorageTypeEnum item : DocumentStorageTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
