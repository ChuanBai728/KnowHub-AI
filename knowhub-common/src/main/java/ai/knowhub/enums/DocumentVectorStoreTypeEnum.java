package ai.knowhub.enums;

/**
 * 【文档向量存储类型枚举】
 *
 * 作用：定义系统支持的向量数据库类型，用于存储文档切块的 Embedding 向量。
 *
 * 业务背景：
 *   RAG 系统需要将文本块的向量存储到专用的向量数据库中，以便进行语义相似度检索。
 *   系统支持三种主流的向量存储方案：
 *   - Milvus：专为向量检索设计的开源数据库，性能最优，适合大规模数据。
 *   - PGVector：PostgreSQL 的向量扩展，适合已有 PostgreSQL 基础设施的场景。
 *   - Elasticsearch：通过 dense_vector 字段支持向量检索，适合混合检索（关键词+向量）。
 *
 * 使用示例：
 *   DocumentVectorStoreTypeEnum type = DocumentVectorStoreTypeEnum.getRc(1);  // 返回 MILVUS
 */
public enum DocumentVectorStoreTypeEnum {

    /** Milvus 向量数据库（专为向量检索设计，性能最优） */
    MILVUS(1, "Milvus"),

    /** PGVector（PostgreSQL 向量扩展，兼容 SQL 生态） */
    PG_VECTOR(2, "PGVector"),

    /** Elasticsearch（支持混合检索：关键词 + 向量语义） */
    ELASTICSEARCH(3, "Elasticsearch");

    /**
     * 数字编码
     */
    private final Integer code;

    /**
     * 向量存储类型名称
     */
    private final String msg;

    /**
     * 枚举构造方法
     *
     * @param code 数字编码
     * @param msg  向量存储类型名称
     */
    DocumentVectorStoreTypeEnum(Integer code, String msg) {
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
     * 获取向量存储类型名称
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
    public static DocumentVectorStoreTypeEnum getRc(Integer code) {
        for (DocumentVectorStoreTypeEnum item : DocumentVectorStoreTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
