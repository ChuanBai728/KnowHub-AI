package ai.knowhub.enums;

/**
 * 【文档管理错误码枚举】
 *
 * 作用：定义文档管理模块专用的业务错误码，覆盖文档上传、解析、索引、检索等全流程的异常场景。
 *       配合 ApiResponse.error(DocumentManageCode) 使用，返回结构化的错误信息。
 *
 * 业务背景：
 *   文档管理是 RAG 系统的核心模块，涉及文件上传、格式解析、策略切块、向量化、索引构建等多个环节，
 *   每个环节都可能出错。通过统一的错误码枚举，前端可以根据不同的错误码展示不同的提示或引导用户操作。
 *
 * 错误码范围：20001 ~ 20013（文档管理模块专用，避免与其他模块冲突）
 *
 * 使用示例：
 *   throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND);
 *   // 返回 {"code":20001,"message":"文档不存在"}
 */
public enum DocumentManageCode {

    /** 文档不存在：根据 ID 查询不到文档记录 */
    DOCUMENT_NOT_FOUND(20001, "文档不存在"),

    /** 文件类型不支持：上传的文件格式不在系统支持范围内 */
    UNSUPPORTED_FILE_TYPE(20002, "当前文件类型暂不支持"),

    /** 文件内容为空：上传的文件没有可提取的文本内容 */
    EMPTY_FILE_CONTENT(20003, "文件内容不能为空"),

    /** 文档状态不允许操作：例如正在构建中的文档不能再次触发构建 */
    DOCUMENT_STATUS_INVALID(20004, "文档当前状态不允许执行该操作"),

    /** 策略方案不存在：引用的切块策略方案已被删除或不存在 */
    STRATEGY_PLAN_NOT_FOUND(20005, "策略方案不存在"),

    /** 策略步骤为空：当前策略方案中没有可执行的切块步骤 */
    STRATEGY_STEP_EMPTY(20006, "当前没有可执行的策略步骤"),

    /** 索引任务冲突：该文档已有一个正在执行的索引构建任务 */
    INDEX_TASK_RUNNING(20007, "当前文档已有索引任务正在执行"),

    /** Kafka 消息发送失败：异步任务无法投递到消息队列 */
    KAFKA_SEND_FAILED(20008, "异步任务投递失败"),

    /** 文件解析失败：文档解析器无法读取文件内容 */
    DOCUMENT_PARSE_FAILED(20009, "文件解析失败"),

    /** 文件存储失败：文件上传到 MinIO 等存储服务时失败 */
    DOCUMENT_STORAGE_FAILED(20010, "文件存储失败"),

    /** 向量化处理失败：文本向量化（Embedding）过程出错 */
    DOCUMENT_VECTOR_FAILED(20011, "向量化处理失败"),

    /** 无可用索引：文档没有已构建成功的索引，无法进行检索 */
    DOCUMENT_INDEX_UNAVAILABLE(20012, "文档当前没有可用索引"),

    /** 检索结果为空：在文档索引中未找到与查询相关的内容 */
    DOCUMENT_RETRIEVE_EMPTY(20013, "未检索到可用资料");

    /**
     * 数字错误码
     */
    private final Integer code;

    /**
     * 中文错误提示信息
     */
    private final String msg;

    /**
     * 枚举构造方法
     *
     * @param code 数字错误码
     * @param msg  中文错误提示信息
     */
    DocumentManageCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取数字错误码
     * @return 错误码
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取中文错误提示信息
     * @return 提示文本，如果 msg 为 null 则返回空字符串
     */
    public String getMsg() {
        return msg == null ? "" : msg;
    }

    /**
     * 根据数字错误码查找对应的提示信息
     *
     * @param code 数字错误码
     * @return 对应的提示文本，未找到则返回空字符串
     */
    public static String getMsg(Integer code) {
        for (DocumentManageCode item : DocumentManageCode.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item.msg;
            }
        }
        return "";
    }

    /**
     * 根据数字错误码查找对应的枚举实例
     *
     * @param code 数字错误码
     * @return 对应的枚举实例，未找到则返回 null
     */
    public static DocumentManageCode getRc(Integer code) {
        for (DocumentManageCode item : DocumentManageCode.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
