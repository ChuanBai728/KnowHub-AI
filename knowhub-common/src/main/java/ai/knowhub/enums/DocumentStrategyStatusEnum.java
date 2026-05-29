package ai.knowhub.enums;

/**
 * 【文档策略状态枚举】
 *
 * 作用：跟踪切块策略本身的生命周期状态。
 *
 * 业务背景：
 *   系统生成的切块策略需要经过推荐、确认等流程才能被执行。
 *   策略也有有效期，过期后状态变为"已失效"。
 *
 * 状态流转：
 *   WAIT_RECOMMEND（待推荐）--> RECOMMENDED（已推荐）--> CONFIRMED（已确认）
 *   RECOMMENDED（已推荐）--> EXPIRED（已失效）
 *   CONFIRMED（已确认）--> EXPIRED（已失效）
 *
 * 使用示例：
 *   DocumentStrategyStatusEnum status = DocumentStrategyStatusEnum.getRc(3);  // 返回 CONFIRMED
 */
public enum DocumentStrategyStatusEnum {

    /** 待推荐：策略已生成，尚未推荐给用户 */
    WAIT_RECOMMEND(1, "待推荐"),

    /** 已推荐：策略已推荐给用户，等待用户确认 */
    RECOMMENDED(2, "已推荐"),

    /** 已确认：用户已确认该策略，可以执行 */
    CONFIRMED(3, "已确认"),

    /** 已失效：策略已过期或被替换，不再有效 */
    EXPIRED(4, "已失效");

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
    DocumentStrategyStatusEnum(Integer code, String msg) {
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
    public static DocumentStrategyStatusEnum getRc(Integer code) {
        for (DocumentStrategyStatusEnum item : DocumentStrategyStatusEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
