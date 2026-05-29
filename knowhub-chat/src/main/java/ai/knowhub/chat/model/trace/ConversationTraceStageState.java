package ai.knowhub.chat.model.trace;

/**
 * 【会话追踪阶段状态枚举】
 *
 * 作用：定义对话处理流程中每个阶段的执行状态。
 * 用于追踪每个处理阶段的生命周期：从开始执行到完成或失败。
 *
 * 所属架构位置：属于会话追踪系统（Conversation Trace System），
 * 与 ConversationTraceStageCode（阶段编码）配合使用，
 * 共同描述一个处理阶段的「是什么」和「执行到哪了」。
 *
 * 设计模式说明：「枚举模式（Enum Pattern）」，
 * 每个枚举值包含状态码（code）和中文标签（label）。
 *
 * 状态流转说明：
 *   RUNNING(1) --> COMPLETED(2)  [正常完成]
 *   RUNNING(1) --> FAILED(3)     [执行失败]
 *   某阶段 --> SKIPPED(4)        [被跳过，如条件不满足时]
 *
 * @author knowhub
 */
public enum ConversationTraceStageState {

    /** 进行中（code=1）—— 阶段正在执行 */
    RUNNING(1, "进行中"),

    /** 已完成（code=2）—— 阶段执行成功 */
    COMPLETED(2, "已完成"),

    /** 失败（code=3）—— 阶段执行出错 */
    FAILED(3, "失败"),

    /** 跳过（code=4）—— 阶段因条件不满足等原因被跳过 */
    SKIPPED(4, "跳过");

    /** 状态码，用于数据库存储 */
    private final int code;

    /** 中文标签，用于前端展示 */
    private final String label;

    ConversationTraceStageState(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 根据状态码反向查找枚举值
     *
     * 工厂方法模式：通过整数状态码查找对应的枚举常量。
     * 常用于从数据库读取状态码后转换为枚举类型。
     *
     * @param code 状态码
     * @return 对应的枚举值，如果 code 为 null 则默认返回 RUNNING
     * @throws IllegalArgumentException 如果状态码不匹配任何已知值
     */
    public static ConversationTraceStageState fromCode(Integer code) {
        if (code == null) {
            return RUNNING;
        }
        for (ConversationTraceStageState value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知的阶段状态 code: " + code);
    }
}
