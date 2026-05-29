package ai.knowhub.enums;

/**
 * 【文档任务事件类型枚举】
 *
 * 作用：定义文档处理任务中产生的事件类型，用于事件驱动架构中的消息分类。
 *
 * 业务背景：
 *   文档处理任务（如解析、切块、向量化）是异步执行的，任务过程中会产生各种事件：
 *   - 开始/完成/失败：任务生命周期事件。
 *   - 推荐策略：系统生成了切块策略推荐。
 *   - 用户调整/确认：用户对策略的操作事件。
 *
 *   这些事件通过 Kafka 消息队列传递，下游消费者根据事件类型执行不同的处理逻辑。
 *
 * 使用示例：
 *   DocumentTaskEventTypeEnum type = DocumentTaskEventTypeEnum.getRc(1);  // 返回 START
 */
public enum DocumentTaskEventTypeEnum {

    /** 开始：任务开始执行 */
    START(1, "开始"),

    /** 完成：任务执行成功完成 */
    COMPLETE(2, "完成"),

    /** 失败：任务执行过程中出错 */
    FAILED(3, "失败"),

    /** 推荐策略：系统生成了切块策略推荐 */
    RECOMMEND_STRATEGY(4, "推荐策略"),

    /** 用户调整：用户对系统推荐的策略进行了修改 */
    USER_ADJUST(5, "用户调整"),

    /** 用户确认：用户确认了切块策略 */
    USER_CONFIRM(6, "用户确认");

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
    DocumentTaskEventTypeEnum(Integer code, String msg) {
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
    public static DocumentTaskEventTypeEnum getRc(Integer code) {
        for (DocumentTaskEventTypeEnum item : DocumentTaskEventTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
