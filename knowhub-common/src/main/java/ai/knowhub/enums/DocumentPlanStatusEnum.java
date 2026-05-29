package ai.knowhub.enums;

/**
 * 【文档处理方案状态枚举】
 *
 * 作用：跟踪文档处理方案（Plan）的生命周期状态。
 *
 * 业务背景：
 *   系统为文档推荐切块方案后，方案需要经过用户确认才能执行。
 *   整个生命周期为：待确认 --> 已确认 --> 已执行，或者直接废弃。
 *
 * 状态流转：
 *   WAIT_CONFIRM（待确认）--> CONFIRMED（已确认）--> EXECUTED（已执行）
 *   WAIT_CONFIRM（待确认）--> DISCARDED（已废弃）
 *   CONFIRMED（已确认）--> DISCARDED（已废弃）
 *
 * 使用示例：
 *   DocumentPlanStatusEnum status = DocumentPlanStatusEnum.getRc(2);  // 返回 CONFIRMED
 */
public enum DocumentPlanStatusEnum {

    /** 待确认：系统已推荐方案，等待用户确认或调整 */
    WAIT_CONFIRM(1, "待确认"),

    /** 已确认：用户已确认方案，等待执行 */
    CONFIRMED(2, "已确认"),

    /** 已执行：方案已执行完毕 */
    EXECUTED(3, "已执行"),

    /** 已废弃：方案被用户废弃，不再执行 */
    DISCARDED(4, "已废弃");

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
    DocumentPlanStatusEnum(Integer code, String msg) {
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
    public static DocumentPlanStatusEnum getRc(Integer code) {
        for (DocumentPlanStatusEnum item : DocumentPlanStatusEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
