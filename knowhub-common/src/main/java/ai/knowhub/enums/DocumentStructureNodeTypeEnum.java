package ai.knowhub.enums;

/**
 * 【文档结构节点类型枚举】
 *
 * 作用：定义文档结构树中节点的类型，用于"基于文档结构切块"策略中表示文档的层次结构。
 *
 * 业务背景：
 *   当使用"基于文档结构切块"策略时，系统会先解析文档的结构树：
 *   - 文档根节点：整篇文档的根。
 *   - 章节节点：对应文档中的一级标题或章节。
 *   - 步骤节点：对应操作步骤或子章节。
 *   - 列表项节点：对应列表中的单个条目。
 *
 *   结构树解析完成后，系统根据节点层次进行智能切块。
 *
 * 使用示例：
 *   DocumentStructureNodeTypeEnum type = DocumentStructureNodeTypeEnum.getRc(2);  // 返回 SECTION
 */
public enum DocumentStructureNodeTypeEnum {

    /** 文档根节点：整篇文档的顶层节点 */
    DOCUMENT(1, "文档根节点"),

    /** 章节节点：对应一级标题或大章节 */
    SECTION(2, "章节节点"),

    /** 步骤节点：对应操作步骤或子章节 */
    STEP(3, "步骤节点"),

    /** 列表项节点：对应列表中的单个条目 */
    LIST_ITEM(4, "列表项节点");

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
    DocumentStructureNodeTypeEnum(Integer code, String msg) {
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
     * @return 对应的枚举实例，如果 code 为 null 或未找到则返回 null
     */
    public static DocumentStructureNodeTypeEnum getRc(Integer code) {
        if (code == null) {
            return null;
        }
        for (DocumentStructureNodeTypeEnum item : values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
