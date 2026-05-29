package ai.knowhub.enums;

/**
 * 【聊天轮次状态枚举】
 *
 * 作用：表示一次聊天对话中单个"轮次"（Turn）的处理状态。
 *       一个会话（Session）包含多个轮次，每轮对应用户的一次提问和 AI 的一次回答。
 *
 * 业务背景：
 *   AI Agent 处理用户问题时，可能涉及多次工具调用、RAG 检索等步骤，
 *   整个过程可能成功完成、失败或被用户中途停止，因此需要细分轮次状态。
 *
 * 状态流转：
 *   RUNNING（进行中）--> COMPLETED（已完成）
 *                     --> FAILED（失败）
 *                     --> STOPPED（用户主动停止）
 *
 * 使用示例：
 *   ChatTurnStatus status = ChatTurnStatus.fromCode(2);  // 返回 COMPLETED
 */
public enum ChatTurnStatus {

    /** 进行中：AI 正在处理用户的问题 */
    RUNNING(1, "进行中"),

    /** 已完成：AI 已成功生成回答 */
    COMPLETED(2, "已完成"),

    /** 失败：处理过程中发生错误 */
    FAILED(3, "失败"),

    /** 已停止：用户主动中断了本次回答 */
    STOPPED(4, "已停止");

    /**
     * 数字状态码
     */
    private final int code;

    /**
     * 中文状态描述
     */
    private final String desc;

    /**
     * 枚举构造方法
     *
     * @param code 数字状态码
     * @param desc 中文状态描述
     */
    ChatTurnStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取数字状态码
     * @return 状态码数字
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取中文状态描述
     * @return 中文描述文本
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 根据数字状态码查找对应的枚举实例
     *
     * @param code 数字状态码（不能为 null）
     * @return 对应的 ChatTurnStatus 枚举实例
     * @throws IllegalArgumentException 如果 code 为 null 或者找不到匹配的枚举值
     */
    public static ChatTurnStatus fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("轮次状态 code 不能为空");
        }
        for (ChatTurnStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的轮次状态 code: " + code);
    }
}
