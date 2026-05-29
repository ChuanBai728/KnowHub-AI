package ai.knowhub.enums;

import lombok.Getter;

/**
 * 【检索通道类型枚举】
 *
 * 作用：定义 RAG 系统中不同的检索通道类型，支持多通道混合检索。
 *
 * 业务背景：
 *   RAG 系统采用"多通道检索 + 重排序"的策略来提高检索质量：
 *   - 关键词检索（KEYWORD）：传统的 BM25/全文检索，擅长精确匹配专业术语。
 *   - 向量检索（VECTOR）：基于 Embedding 的语义检索，擅长理解用户意图。
 *   - 重排序（RERANK）：对前两个通道的检索结果进行二次排序，融合两种检索的优势。
 *
 *   检索流程：关键词检索 + 向量检索（并行）--> 结果合并 --> 重排序 --> 返回 Top-K
 *
 * 使用示例：
 *   RetrievalChannelEnum channel = RetrievalChannelEnum.fromCode(2);  // 返回 VECTOR
 */
public enum RetrievalChannelEnum {

    /** 关键词检索通道：基于 BM25/全文检索，擅长精确匹配 */
    KEYWORD(1, "keyword","关键词检索"),

    /** 向量检索通道：基于 Embedding 语义检索，擅长理解意图 */
    VECTOR(2, "vector","向量检索"),

    /** 重排序通道：对多通道检索结果进行二次排序融合 */
    RERANK(3,"rerank","重排序");

    /**
     * 数字编码
     */
    @Getter
    private final int code;

    /**
     * 英文名称（用于配置文件和日志标识）
     */
    @Getter
    private final String name;

    /**
     * 中文描述（用于前端展示）
     */
    @Getter
    private final String desc;

    /**
     * 枚举构造方法
     *
     * @param code 数字编码
     * @param name 英文名称
     * @param desc 中文描述
     */
    RetrievalChannelEnum(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 根据数字编码查找对应的枚举实例
     *
     * @param code 数字编码（不能为 null）
     * @return 对应的 RetrievalChannelEnum 枚举实例
     * @throws IllegalArgumentException 如果 code 为 null 或者找不到匹配的枚举值
     */
    public static RetrievalChannelEnum fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("检索通道类型 code 不能为空");
        }
        for (RetrievalChannelEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的检索通道类型 code: " + code);
    }
}
