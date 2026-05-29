package ai.knowhub.enums;

/**
 * 【文档结构化程度枚举】
 *
 * 作用：评估文档内容的结构化程度，用于系统自动选择最合适的切块策略。
 *
 * 业务背景：
 *   不同文档的结构化程度差异很大：
 *   - 高结构化：有清晰的标题层级、章节划分（如技术手册、法律文档）。
 *   - 中结构化：有一定的段落划分，但层次不清晰（如博客文章）。
 *   - 低结构化：连续的大段文本，缺乏结构标记（如小说、聊天记录）。
 *
 *   系统根据结构化程度自动推荐切块策略：
 *   - 高结构化 -> 使用"基于文档结构切块"策略
 *   - 低结构化 -> 使用"递归分块"或"语义分块"策略
 *
 * 使用示例：
 *   DocumentStructureLevelEnum level = DocumentStructureLevelEnum.getRc(3);  // 返回 HIGH
 */
public enum DocumentStructureLevelEnum {

    /** 未知：尚未评估或无法判断结构化程度 */
    UNKNOWN(0, "未知"),

    /** 低结构化：缺乏层次结构的大段连续文本 */
    LOW(1, "低"),

    /** 中结构化：有基本段落划分但层次不清晰 */
    MEDIUM(2, "中"),

    /** 高结构化：有清晰的标题层级和章节划分 */
    HIGH(3, "高");

    /**
     * 数字编码，数值越大结构化程度越高
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
    DocumentStructureLevelEnum(Integer code, String msg) {
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
    public static DocumentStructureLevelEnum getRc(Integer code) {
        for (DocumentStructureLevelEnum item : DocumentStructureLevelEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
