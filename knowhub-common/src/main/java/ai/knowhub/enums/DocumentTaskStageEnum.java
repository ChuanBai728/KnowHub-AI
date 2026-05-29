package ai.knowhub.enums;

/**
 * 【文档任务阶段枚举】
 *
 * 作用：定义文档从上传到可检索的完整处理流水线中的各个阶段。
 *       用于跟踪文档处理进度、展示进度条、以及在失败时定位具体阶段。
 *
 * 业务背景：
 *   一个文档从上传到可以被检索，需要经历以下阶段：
 *   1. 文件上传：将原始文件存储到 MinIO
 *   2. 内容解析：提取纯文本内容
 *   3. 策略路由：根据文档特征选择切块策略
 *   4. 策略确认：等待用户确认策略方案
 *   5. 切块执行：按策略将文档切分成小块
 *   6. 切块后处理：对切块进行增强（如补充上下文）
 *   7. 向量化：将文本块转换为向量
 *   8. 入库完成：向量存入数据库，索引就绪
 *
 * 使用示例：
 *   DocumentTaskStageEnum stage = DocumentTaskStageEnum.getRc(7);  // 返回 VECTORIZE
 */
public enum DocumentTaskStageEnum {

    /** 文件上传：将原始文件存储到 MinIO 对象存储 */
    FILE_UPLOAD(1, "文件上传"),

    /** 内容解析：使用解析器提取文档中的纯文本内容 */
    CONTENT_PARSE(2, "内容解析"),

    /** 策略路由：根据文档特征（结构化程度、内容质量等）选择切块策略 */
    STRATEGY_ROUTE(3, "策略路由"),

    /** 策略确认：等待用户确认系统推荐的切块策略方案 */
    STRATEGY_CONFIRM(4, "策略确认"),

    /** 切块执行：按照确认的策略将文档切分成小块（Chunk） */
    CHUNK_EXECUTE(5, "切块执行"),

    /** 切块后处理：对切块进行增强处理（如补充上下文、合并碎片等） */
    CHUNK_POST_PROCESS(6, "切块后处理"),

    /** 向量化：使用 Embedding 模型将文本块转换为向量 */
    VECTORIZE(7, "向量化"),

    /** 入库完成：向量存入数据库（如 Milvus/PGVector），索引就绪可检索 */
    STORE_COMPLETE(8, "入库完成");

    /**
     * 数字编码，也代表阶段的顺序
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
    DocumentTaskStageEnum(Integer code, String msg) {
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
    public static DocumentTaskStageEnum getRc(Integer code) {
        for (DocumentTaskStageEnum item : DocumentTaskStageEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
