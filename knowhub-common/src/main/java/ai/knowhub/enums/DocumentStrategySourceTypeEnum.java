package ai.knowhub.enums;

/**
 * 【文档策略来源类型枚举】
 *
 * 作用：标识切块策略的来源，区分是系统推荐、用户新增还是用户保留的策略。
 *
 * 业务背景：
 *   在文档处理方案中，策略可以有多种来源：
 *   - 系统推荐：AI 根据文档特征自动生成的策略。
 *   - 用户新增：用户手动添加的自定义策略。
 *   - 用户保留：用户从系统推荐中选择保留的策略。
 *
 * 使用示例：
 *   DocumentStrategySourceTypeEnum type = DocumentStrategySourceTypeEnum.getRc(1);
 *   // 返回 SYSTEM_RECOMMEND
 */
public enum DocumentStrategySourceTypeEnum {

    /** 系统推荐：由 AI 分析文档后自动生成的策略 */
    SYSTEM_RECOMMEND(1, "系统推荐"),

    /** 用户新增：用户手动添加的自定义策略 */
    USER_ADD(2, "用户新增"),

    /** 用户保留：用户从系统推荐中选择保留下来的策略 */
    USER_KEEP(3, "用户保留");

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
    DocumentStrategySourceTypeEnum(Integer code, String msg) {
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
    public static DocumentStrategySourceTypeEnum getRc(Integer code) {
        for (DocumentStrategySourceTypeEnum item : DocumentStrategySourceTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
