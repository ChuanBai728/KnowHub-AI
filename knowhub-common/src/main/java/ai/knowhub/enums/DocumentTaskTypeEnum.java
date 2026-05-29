package ai.knowhub.enums;

/**
 * 【文档任务类型枚举】
 *
 * 作用：定义文档处理任务的类型，区分不同的处理流程。
 *
 * 业务背景：
 *   文档处理主要有两种任务类型：
 *   - 解析路由：上传文档后的初始处理，包括解析内容、分析特征、推荐策略等。
 *   - 构建索引：用户确认策略后的后续处理，包括切块、向量化、入库等。
 *
 *   这两种任务通常分阶段执行，中间等待用户确认策略。
 *
 * 使用示例：
 *   DocumentTaskTypeEnum type = DocumentTaskTypeEnum.getRc(2);  // 返回 BUILD_INDEX
 */
public enum DocumentTaskTypeEnum {

    /** 解析路由任务：文档上传后的初始处理（解析内容、分析特征、推荐策略） */
    PARSE_ROUTE(1, "解析路由"),

    /** 构建索引任务：用户确认策略后的处理（切块、向量化、入库） */
    BUILD_INDEX(2, "构建索引");

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
    DocumentTaskTypeEnum(Integer code, String msg) {
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
    public static DocumentTaskTypeEnum getRc(Integer code) {
        for (DocumentTaskTypeEnum item : DocumentTaskTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
