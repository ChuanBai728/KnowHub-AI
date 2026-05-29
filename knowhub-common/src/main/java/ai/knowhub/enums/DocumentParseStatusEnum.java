package ai.knowhub.enums;

/**
 * 【文档解析状态枚举】
 *
 * 作用：跟踪文档内容解析的进度状态。
 *
 * 业务背景：
 *   文档上传后需要经过解析（Parse）才能提取出纯文本内容。
 *   不同格式的文档使用不同的解析器（如 PDF 用 PDFBox、Word 用 POI），
 *   解析过程是异步的，因此需要状态来跟踪进度。
 *
 * 状态流转：
 *   WAIT_PARSE（待解析）--> PARSING（解析中）--> PARSE_SUCCESS（解析成功）
 *                                             --> PARSE_FAILED（解析失败）
 *
 * 使用示例：
 *   DocumentParseStatusEnum status = DocumentParseStatusEnum.getRc(3);  // 返回 PARSE_SUCCESS
 */
public enum DocumentParseStatusEnum {

    /** 待解析：文档已上传，等待解析器处理 */
    WAIT_PARSE(1, "待解析"),

    /** 解析中：正在使用对应格式的解析器提取文本 */
    PARSING(2, "解析中"),

    /** 解析成功：已成功提取出纯文本内容 */
    PARSE_SUCCESS(3, "解析成功"),

    /** 解析失败：解析器无法读取文件，可能是文件损坏或格式不支持 */
    PARSE_FAILED(4, "解析失败");

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
    DocumentParseStatusEnum(Integer code, String msg) {
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
    public static DocumentParseStatusEnum getRc(Integer code) {
        for (DocumentParseStatusEnum item : DocumentParseStatusEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
