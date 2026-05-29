package ai.knowhub.enums;

/**
 * 【文档切块来源类型枚举】
 *
 * 作用：标识文档切块（Chunk）的来源方式，用于 RAG 流水线中区分切块是如何产生的。
 *
 * 业务背景：
 *   RAG 系统需要将长文档切分成小块（Chunk）以便向量化和检索。
 *   切块来源有两种：
 *   - 原文切块：直接按规则（如固定长度、递归分割）从原文切出来的原始块。
 *   - 后处理补全文本：对原始切块进行后处理（如补充上下文、合并碎片等）后的增强块。
 *
 * 使用示例：
 *   DocumentChunkSourceTypeEnum type = DocumentChunkSourceTypeEnum.getRc(1);  // 返回 ORIGINAL
 */
public enum DocumentChunkSourceTypeEnum {

    /** 原文切块：直接从文档原文按规则切分得到的原始块 */
    ORIGINAL(1, "原文切块"),

    /** 后处理补全文本：经过后处理（如上下文补充、碎片合并等）增强后的切块 */
    ENRICHED(2, "后处理补全文本");

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
    DocumentChunkSourceTypeEnum(Integer code, String msg) {
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
    public static DocumentChunkSourceTypeEnum getRc(Integer code) {
        for (DocumentChunkSourceTypeEnum item : DocumentChunkSourceTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
