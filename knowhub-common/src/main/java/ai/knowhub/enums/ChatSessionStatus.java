package ai.knowhub.enums;

/**
 * 【聊天会话状态枚举】
 *
 * 作用：表示一个聊天会话（Session）的当前状态。
 *       用于控制并发：当一个会话正在处理请求时（RUNNING），新的请求应被拒绝或排队。
 *
 * 业务背景：
 *   AI Agent 的响应可能需要较长时间（涉及多轮工具调用、RAG 检索等），
 *   因此需要一个状态来标记会话是否空闲，防止用户重复提交导致资源浪费。
 *
 * 状态流转：
 *   IDLE（空闲）--> 用户发送消息 --> RUNNING（进行中）--> 响应完成 --> IDLE
 *
 * 使用示例：
 *   ChatSessionStatus status = ChatSessionStatus.fromCode(2);  // 返回 RUNNING
 *   boolean busy = ChatSessionStatus.isRunning(sessionStatusCode);  // 判断是否忙碌
 */
public enum ChatSessionStatus {

    /** 空闲状态：会话可以接受新的用户消息 */
    IDLE(1, "空闲"),

    /** 进行中状态：会话正在处理请求，此时应拒绝新的用户消息 */
    RUNNING(2, "进行中");

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
    ChatSessionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取数字状态码
     * @return 1（空闲）或 2（进行中）
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取中文状态描述
     * @return "空闲" 或 "进行中"
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 根据数字状态码查找对应的枚举实例
     *
     * @param code 数字状态码，如果为 null 则默认返回 IDLE（空闲）
     * @return 对应的 ChatSessionStatus 枚举实例
     * @throws IllegalArgumentException 如果 code 不是有效的状态码
     */
    public static ChatSessionStatus fromCode(Integer code) {
        if (code == null) {
            return IDLE;
        }
        for (ChatSessionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的会话状态 code: " + code);
    }

    /**
     * 判断给定的状态码是否表示"进行中"
     *
     * @param code 数字状态码，如果为 null 则视为 IDLE
     * @return true 表示会话正在运行中（忙碌），false 表示空闲
     */
    public static boolean isRunning(Integer code) {
        return RUNNING.code == (code == null ? IDLE.code : code);
    }
}
