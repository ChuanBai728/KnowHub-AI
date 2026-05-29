package ai.knowhub.enums;

/**
 * 【业务通用是/否状态枚举】
 *
 * 作用：表示业务逻辑中的"是"与"否"，用于数据库字段或接口参数中的布尔语义。
 *       相比 Java 原生的 boolean，使用 Integer (1/0) 更便于数据库存储和前端交互。
 *
 * 使用场景：
 *   - 数据库中 is_deleted、is_enabled 等字段
 *   - 接口参数中表示开关状态
 *
 * 使用示例：
 *   Integer isEnabled = BusinessStatus.YES.getCode();  // 返回 1
 *   BusinessStatus status = BusinessStatus.getRc(0);   // 返回 BusinessStatus.NO
 */
public enum BusinessStatus {

    /** 是 / 启用 / 开启 —— 对应整数值 1 */
    YES(1,"是"),

    /** 否 / 禁用 / 关闭 —— 对应整数值 0 */
    NO(0,"否")
    ;

    /**
     * 数字编码：1 表示"是"，0 表示"否"
     */
    private Integer code;

    /**
     * 中文描述："是" 或 "否"
     */
    private String msg;

    /**
     * 枚举构造方法
     *
     * @param code 数字编码
     * @param msg  中文描述
     */
    BusinessStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取数字编码
     * @return 1 或 0
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取中文描述
     * @return "是" 或 "否"，如果 msg 为 null 则返回空字符串
     */
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }

    /**
     * 根据数字编码查找对应的中文描述
     *
     * @param code 数字编码（1 或 0）
     * @return 对应的中文描述，未找到则返回空字符串
     */
    public static String getMsg(Integer code) {
        for (BusinessStatus re : BusinessStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    /**
     * 根据数字编码查找对应的枚举实例
     *
     * @param code 数字编码（1 或 0）
     * @return 对应的 BusinessStatus 枚举实例，未找到则返回 null
     */
    public static BusinessStatus getRc(Integer code) {
        for (BusinessStatus re : BusinessStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
