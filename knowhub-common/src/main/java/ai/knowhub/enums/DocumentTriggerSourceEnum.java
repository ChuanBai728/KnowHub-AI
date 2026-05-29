package ai.knowhub.enums;

/**
 * 【文档任务触发来源枚举】
 *
 * 作用：标识文档处理任务是由谁触发的，用于审计和流程控制。
 *
 * 业务背景：
 *   文档处理任务可以由两种方式触发：
 *   - 系统自动：例如文档上传后自动触发解析、定时任务触发索引重建等。
 *   - 用户手动：例如用户在界面上点击"重新构建索引"按钮。
 *
 * 使用示例：
 *   DocumentTriggerSourceEnum source = DocumentTriggerSourceEnum.getRc(2);  // 返回 USER
 */
public enum DocumentTriggerSourceEnum {

    /** 系统自动触发（如上传后自动解析、定时任务等） */
    SYSTEM(1, "系统自动"),

    /** 用户手动触发（如点击"重新构建索引"按钮） */
    USER(2, "用户手动");

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
    DocumentTriggerSourceEnum(Integer code, String msg) {
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
    public static DocumentTriggerSourceEnum getRc(Integer code) {
        for (DocumentTriggerSourceEnum item : DocumentTriggerSourceEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
