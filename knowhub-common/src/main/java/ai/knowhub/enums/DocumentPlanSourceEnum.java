package ai.knowhub.enums;

/**
 * 【文档处理方案来源枚举】
 *
 * 作用：标识文档处理方案（Plan）是由谁制定的，用于区分系统推荐和用户手动调整的方案。
 *
 * 业务背景：
 *   RAG 系统会根据文档特征（如结构化程度、内容质量等）自动推荐切块策略。
 *   用户可以接受系统推荐，也可以手动调整方案。此枚举用于标记方案的来源。
 *
 * 使用示例：
 *   DocumentPlanSourceEnum source = DocumentPlanSourceEnum.getRc(1);  // 返回 SYSTEM_RECOMMEND
 */
public enum DocumentPlanSourceEnum {

    /** 系统推荐：由 AI 分析文档特征后自动生成的方案 */
    SYSTEM_RECOMMEND(1, "系统推荐"),

    /** 用户调整：用户在系统推荐基础上手动修改的方案 */
    USER_ADJUST(2, "用户调整");

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
    DocumentPlanSourceEnum(Integer code, String msg) {
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
    public static DocumentPlanSourceEnum getRc(Integer code) {
        for (DocumentPlanSourceEnum item : DocumentPlanSourceEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
