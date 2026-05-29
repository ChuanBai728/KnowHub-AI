package ai.knowhub.enums;

/**
 * 【文档策略执行状态枚举】
 *
 * 作用：跟踪单个切块策略步骤的执行状态。
 *
 * 业务背景：
 *   一个文档处理方案（Plan）包含多个策略步骤（如结构化切块、递归切块等），
 *   每个步骤独立执行，有自己的状态。步骤之间可能存在依赖关系（如必须先完成解析才能切块）。
 *
 * 状态流转：
 *   WAIT_EXECUTE（待执行）--> EXECUTING（执行中）--> EXECUTE_SUCCESS（执行成功）
 *                                                 --> EXECUTE_FAILED（执行失败）
 *   WAIT_EXECUTE（待执行）--> SKIPPED（已跳过，条件不满足时跳过）
 *
 * 使用示例：
 *   DocumentStrategyExecuteStatusEnum status = DocumentStrategyExecuteStatusEnum.getRc(3);
 *   // 返回 EXECUTE_SUCCESS
 */
public enum DocumentStrategyExecuteStatusEnum {

    /** 待执行：策略步骤尚未开始 */
    WAIT_EXECUTE(1, "待执行"),

    /** 执行中：策略步骤正在处理 */
    EXECUTING(2, "执行中"),

    /** 执行成功：策略步骤已成功完成 */
    EXECUTE_SUCCESS(3, "执行成功"),

    /** 执行失败：策略步骤执行出错 */
    EXECUTE_FAILED(4, "执行失败"),

    /** 已跳过：因前置条件不满足或策略判断，该步骤被跳过 */
    SKIPPED(5, "已跳过");

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
    DocumentStrategyExecuteStatusEnum(Integer code, String msg) {
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
    public static DocumentStrategyExecuteStatusEnum getRc(Integer code) {
        for (DocumentStrategyExecuteStatusEnum item : DocumentStrategyExecuteStatusEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
