package ai.knowhub.enums;

/**
 * 【文档策略流水线类型枚举】
 *
 * 作用：区分切块策略流水线是针对父块（Parent Chunk）还是子块（Child Chunk）。
 *
 * 业务背景：
 *   RAG 系统中常采用"父子分块"策略：
 *   - 父块（Parent）：较大的文本块，保留更多上下文，用于检索时提供完整信息。
 *   - 子块（Child）：较小的文本块，更精确，用于向量匹配。
 *   检索时先匹配子块，再返回其所属的父块，兼顾精确性和上下文完整性。
 *
 * 使用示例：
 *   DocumentStrategyPipelineTypeEnum type = DocumentStrategyPipelineTypeEnum.getRc("PARENT");
 *   // 返回 PARENT（父块流水线）
 */
public enum DocumentStrategyPipelineTypeEnum {

    /** 父块流水线：处理较大文本块的切块流程 */
    PARENT("PARENT", "父块流水线"),

    /** 子块流水线：处理较小文本块的切块流程 */
    CHILD("CHILD", "子块流水线");

    /**
     * 字符串编码（使用字符串而非数字，便于理解和配置）
     */
    private final String code;

    /**
     * 中文描述
     */
    private final String msg;

    /**
     * 枚举构造方法
     *
     * @param code 字符串编码
     * @param msg  中文描述
     */
    DocumentStrategyPipelineTypeEnum(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取字符串编码
     * @return 编码值
     */
    public String getCode() {
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
     * 根据字符串编码查找对应的枚举实例（大小写不敏感）
     *
     * @param code 字符串编码
     * @return 对应的枚举实例，如果 code 为 null 或未找到则返回 null
     */
    public static DocumentStrategyPipelineTypeEnum getRc(String code) {
        if (code == null) {
            return null;
        }
        for (DocumentStrategyPipelineTypeEnum item : DocumentStrategyPipelineTypeEnum.values()) {
            // equalsIgnoreCase 忽略大小写，提高容错性
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }
}
