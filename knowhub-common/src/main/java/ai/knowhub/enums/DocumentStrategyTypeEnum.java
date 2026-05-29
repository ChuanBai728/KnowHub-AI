package ai.knowhub.enums;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 【文档切块策略类型枚举】
 *
 * 作用：定义系统支持的文档切块（Chunking）策略类型，是 RAG 流水线中最核心的枚举之一。
 *
 * 业务背景：
 *   将长文档切分成适合向量化的小块是 RAG 系统的关键步骤。不同策略适用于不同类型的文档：
 *   - 结构化切块：利用文档的标题、章节等结构信息进行切分，适合有清晰层次结构的文档。
 *   - 递归分块：按层级分隔符（如段落、句子、单词）递归切分，是通用的默认策略。
 *   - 语义分块：基于语义相似度进行切分，相似内容归为一块，适合内容连续性强的文档。
 *   - 大模型智能切块：使用 LLM 分析文档内容后智能切分，效果最好但成本最高。
 *
 * 策略复杂度从低到高：STRUCTURE < RECURSIVE < SEMANTIC < LLM
 *
 * 使用示例：
 *   DocumentStrategyTypeEnum type = DocumentStrategyTypeEnum.getRc(2);  // 返回 RECURSIVE
 *   List<DocumentStrategyTypeEnum> ordered = DocumentStrategyTypeEnum.orderedValues();  // 按编码排序
 */
public enum DocumentStrategyTypeEnum {

    /** 基于文档结构切块：利用标题、章节等结构信息切分 */
    STRUCTURE(1, "基于文档结构切块"),

    /** 递归分块：按分隔符层级递归切分（段落 -> 句子 -> 单词），通用默认策略 */
    RECURSIVE(2, "递归分块"),

    /** 语义分块：基于 Embedding 向量的语义相似度进行切分 */
    SEMANTIC(3, "语义分块"),

    /** 大模型智能切块：使用 LLM 分析文档内容后智能切分，效果最优但成本最高 */
    LLM(4, "大模型智能切块");

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
    DocumentStrategyTypeEnum(Integer code, String msg) {
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
    public static DocumentStrategyTypeEnum getRc(Integer code) {
        for (DocumentStrategyTypeEnum item : DocumentStrategyTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }

    /**
     * 获取按编码升序排列的所有枚举值
     * 使用 Java Stream API 进行排序，返回不可修改的列表。
     * 适用于需要按策略复杂度顺序展示或执行的场景。
     *
     * @return 按 code 升序排列的枚举值列表
     */
    public static List<DocumentStrategyTypeEnum> orderedValues() {
        return Arrays.stream(DocumentStrategyTypeEnum.values())
            .sorted(Comparator.comparingInt(DocumentStrategyTypeEnum::getCode))
            .toList();
    }
}
