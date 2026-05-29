package ai.knowhub.enums;

/**
 * 【文档文件类型枚举】
 *
 * 作用：定义系统支持的文档文件格式，用于文件上传时的类型校验和解析器路由。
 *
 * 业务背景：
 *   RAG 系统需要处理多种格式的文档，每种格式使用不同的解析器：
 *   - PDF 使用 Apache PDFBox 解析
 *   - DOC/DOCX 使用 Apache POI 解析
 *   - TXT/MD 直接读取文本
 *   - HTML 使用 Jsoup 或 Tika 解析
 *
 * 使用示例：
 *   DocumentFileTypeEnum type = DocumentFileTypeEnum.fromFileName("report.pdf");  // 返回 PDF
 *   DocumentFileTypeEnum type2 = DocumentFileTypeEnum.getRc(4);                   // 返回 TXT
 */
public enum DocumentFileTypeEnum {

    /** PDF 文档格式 */
    PDF(1, "PDF"),

    /** Word 97-2003 文档格式（旧版 Word） */
    DOC(2, "DOC"),

    /** Word 2007+ 文档格式（新版 Word，基于 XML） */
    DOCX(3, "DOCX"),

    /** 纯文本格式 */
    TXT(4, "TXT"),

    /** Markdown 格式（常用于技术文档） */
    MD(5, "MD"),

    /** HTML 网页格式 */
    HTML(6, "HTML");

    /**
     * 数字编码
     */
    private final Integer code;

    /**
     * 文件类型名称（大写扩展名）
     */
    private final String msg;

    /**
     * 枚举构造方法
     *
     * @param code 数字编码
     * @param msg  文件类型名称
     */
    DocumentFileTypeEnum(Integer code, String msg) {
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
     * 获取文件类型名称
     * @return 类型名称，如果 msg 为 null 则返回空字符串
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
    public static DocumentFileTypeEnum getRc(Integer code) {
        for (DocumentFileTypeEnum item : DocumentFileTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }

    /**
     * 根据文件名自动识别文件类型
     * 通过文件扩展名来判断，支持大小写不敏感匹配。
     * 使用了 Java 14+ 的 switch 表达式语法。
     *
     * @param fileName 文件名，例如 "report.pdf" 或 "data.CSV"
     * @return 对应的文件类型枚举，无法识别则返回 null
     */
    public static DocumentFileTypeEnum fromFileName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        // 提取文件扩展名并转为小写
        String suffix = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        // Java 14+ 的 switch 表达式：箭头语法更简洁，不需要 break
        return switch (suffix) {
            case "pdf" -> PDF;
            case "doc" -> DOC;
            case "docx" -> DOCX;
            case "txt" -> TXT;
            case "md", "markdown" -> MD;    // .md 和 .markdown 都识别为 MD 类型
            case "html", "htm" -> HTML;     // .html 和 .htm 都识别为 HTML 类型
            default -> null;                // 不支持的格式返回 null
        };
    }
}
