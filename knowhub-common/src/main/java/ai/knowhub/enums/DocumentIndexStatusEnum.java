package ai.knowhub.enums;

/**
 * 【文档索引构建状态枚举】
 *
 * 作用：跟踪文档向量索引的构建进度，用于异步任务的状态管理。
 *
 * 业务背景：
 *   文档上传后需要经过解析、切块、向量化等步骤才能构建出可检索的索引。
 *   整个过程是异步的（通过 Kafka 消息队列驱动），因此需要状态来跟踪进度。
 *
 * 状态流转：
 *   WAIT_BUILD（待构建）--> BUILDING（构建中）--> BUILD_SUCCESS（构建成功）
 *                                              --> BUILD_FAILED（构建失败）
 *
 * 使用示例：
 *   DocumentIndexStatusEnum status = DocumentIndexStatusEnum.getRc(3);  // 返回 BUILD_SUCCESS
 */
public enum DocumentIndexStatusEnum {

    /** 待构建：文档已上传，等待开始构建索引 */
    WAIT_BUILD(1, "待构建"),

    /** 构建中：正在执行解析、切块、向量化等步骤 */
    BUILDING(2, "构建中"),

    /** 构建成功：索引已就绪，可以进行检索 */
    BUILD_SUCCESS(3, "构建成功"),

    /** 构建失败：某个步骤出错，需要排查原因 */
    BUILD_FAILED(4, "构建失败");

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
    DocumentIndexStatusEnum(Integer code, String msg) {
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
    public static DocumentIndexStatusEnum getRc(Integer code) {
        for (DocumentIndexStatusEnum item : DocumentIndexStatusEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
