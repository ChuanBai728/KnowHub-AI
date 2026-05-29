package ai.knowhub.enums;

/**
 * 【文档处理日志级别枚举】
 *
 * 作用：定义文档处理流水线中日志的严重程度级别，用于日志分类和过滤。
 *
 * 业务背景：
 *   文档处理过程中会产生大量日志（如解析开始、切块完成、错误信息等），
 *   使用不同的日志级别可以帮助运维人员快速定位问题。
 *
 * 使用示例：
 *   DocumentLogLevelEnum level = DocumentLogLevelEnum.getRc(2);  // 返回 WARN
 */
public enum DocumentLogLevelEnum {

    /** 信息级别：记录正常的处理流程，如"开始解析文档""切块完成" */
    INFO(1, "INFO"),

    /** 警告级别：记录潜在问题，如"文档格式不规范，已自动修复" */
    WARN(2, "WARN"),

    /** 错误级别：记录处理失败信息，如"文件解析失败""向量化超时" */
    ERROR(3, "ERROR");

    /**
     * 数字编码，数值越大级别越高
     */
    private final Integer code;

    /**
     * 日志级别名称
     */
    private final String msg;

    /**
     * 枚举构造方法
     *
     * @param code 数字编码
     * @param msg  日志级别名称
     */
    DocumentLogLevelEnum(Integer code, String msg) {
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
     * 获取日志级别名称
     * @return 级别名称，如果 msg 为 null 则返回空字符串
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
    public static DocumentLogLevelEnum getRc(Integer code) {
        for (DocumentLogLevelEnum item : DocumentLogLevelEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
