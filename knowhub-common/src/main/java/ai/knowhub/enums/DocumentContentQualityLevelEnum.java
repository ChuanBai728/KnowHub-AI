package ai.knowhub.enums;

/**
 * 【文档内容质量等级枚举】
 *
 * 作用：对文档内容的质量进行分级评估，用于 RAG 流水线中决定是否需要对内容进行增强处理。
 *
 * 业务背景：
 *   不同来源的文档质量参差不齐，质量低的文档可能需要额外的处理（如用大模型补充上下文、
 *   纠错等）才能生成有效的切块。此枚举用于标记和过滤文档质量。
 *
 * 质量等级从低到高：UNKNOWN < LOW < MEDIUM < HIGH
 *
 * 使用示例：
 *   DocumentContentQualityLevelEnum level = DocumentContentQualityLevelEnum.getRc(3);  // 返回 HIGH
 */
public enum DocumentContentQualityLevelEnum {

    /** 未知质量：尚未评估或无法判断 */
    UNKNOWN(0, "未知"),

    /** 低质量：内容可能不完整、格式混乱或信息密度低 */
    LOW(1, "低"),

    /** 中等质量：内容基本可读，但可能存在一些问题 */
    MEDIUM(2, "中"),

    /** 高质量：内容完整、结构清晰、信息密度高 */
    HIGH(3, "高");

    /**
     * 数字编码，数值越大质量越高
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
    DocumentContentQualityLevelEnum(Integer code, String msg) {
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
    public static DocumentContentQualityLevelEnum getRc(Integer code) {
        for (DocumentContentQualityLevelEnum item : DocumentContentQualityLevelEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
