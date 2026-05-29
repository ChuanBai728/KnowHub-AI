package ai.knowhub.enums;

/**
 * 【文档任务状态枚举】
 *
 * 作用：跟踪文档处理任务的整体执行状态。
 *
 * 业务背景：
 *   每个文档的处理过程被封装为一个任务（Task），任务有明确的生命周期：
 *   - 新建：任务刚创建，尚未开始执行。
 *   - 进行中：任务正在执行某个阶段。
 *   - 成功：所有阶段执行完毕，文档已可检索。
 *   - 失败：某个阶段出错，任务终止。
 *   - 已取消：用户或管理员主动取消了任务。
 *
 * 状态流转：
 *   NEW（新建）--> RUNNING（进行中）--> SUCCESS（成功）
 *                                    --> FAILED（失败）
 *   NEW（新建）--> CANCELED（已取消）
 *   RUNNING（进行中）--> CANCELED（已取消）
 *
 * 使用示例：
 *   DocumentTaskStatusEnum status = DocumentTaskStatusEnum.getRc(2);  // 返回 RUNNING
 */
public enum DocumentTaskStatusEnum {

    /** 新建：任务刚创建，等待执行 */
    NEW(1, "新建"),

    /** 进行中：任务正在执行 */
    RUNNING(2, "进行中"),

    /** 成功：任务执行完毕，文档已可检索 */
    SUCCESS(3, "成功"),

    /** 失败：任务执行出错 */
    FAILED(4, "失败"),

    /** 已取消：任务被用户或管理员主动取消 */
    CANCELED(5, "已取消");

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
    DocumentTaskStatusEnum(Integer code, String msg) {
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
    public static DocumentTaskStatusEnum getRc(Integer code) {
        for (DocumentTaskStatusEnum item : DocumentTaskStatusEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
