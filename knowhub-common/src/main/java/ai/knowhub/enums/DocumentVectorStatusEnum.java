package ai.knowhub.enums;

/**
 * 【文档向量化状态枚举】
 *
 * 作用：跟踪文档切块的向量化（Embedding）处理进度。
 *
 * 业务背景：
 *   文档切块完成后，需要使用 Embedding 模型将每个文本块转换为高维向量，
 *   然后存入向量数据库（如 Milvus、PGVector）才能进行语义检索。
 *   向量化过程是计算密集型操作，通常异步执行。
 *
 * 状态流转：
 *   WAIT_VECTOR（待向量化）--> VECTORIZING（向量化中）--> VECTOR_SUCCESS（向量化成功）
 *                                                     --> VECTOR_FAILED（向量化失败）
 *
 * 使用示例：
 *   DocumentVectorStatusEnum status = DocumentVectorStatusEnum.getRc(3);  // 返回 VECTOR_SUCCESS
 */
public enum DocumentVectorStatusEnum {

    /** 待向量化：切块已完成，等待向量化处理 */
    WAIT_VECTOR(1, "待向量化"),

    /** 向量化中：正在使用 Embedding 模型转换向量 */
    VECTORIZING(2, "向量化中"),

    /** 向量化成功：所有切块已成功转换为向量 */
    VECTOR_SUCCESS(3, "向量化成功"),

    /** 向量化失败：向量化过程出错（如模型调用超时、内存不足等） */
    VECTOR_FAILED(4, "向量化失败");

    /**
     * 数字编码
     */
    private final Integer code;

    /**
     * 中文描述
     */
    private final String msg;

    /**
     * 枚举构造方法
     *
     * @param code 数字编码
     * @param msg  中文描述
     */
    DocumentVectorStatusEnum(Integer code, String msg) {
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
     * 获取中文描述
     * @return 描述文本，如果 msg 为 null 则返回空字符串
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
    public static DocumentVectorStatusEnum getRc(Integer code) {
        for (DocumentVectorStatusEnum item : DocumentVectorStatusEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
