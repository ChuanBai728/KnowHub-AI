package ai.knowhub.enums;

import lombok.Getter;

/**
 * 【聊天提问模式枚举】
 *
 * 作用：定义用户在 AI Agent 对话中可以选择的提问方式。
 *       不同模式决定了系统如何处理用户的问题——是基于文档检索回答，还是直接让大模型自由回答。
 *
 * 业务背景：
 *   RAG（检索增强生成）系统中，用户可以选择不同提问模式：
 *   - 文档问答：只从指定文档中检索答案
 *   - 自动知识问答：系统自动判断是否需要检索文档
 *   - 开放式提问：直接用大模型的知识回答，不检索文档
 *
 * 使用场景：
 *   前端传递提问模式参数，后端根据模式决定 RAG 检索策略。
 *
 * 使用示例：
 *   ChatQueryMode mode = ChatQueryMode.fromCode(1);  // 返回 DOCUMENT
 */
@Getter
public enum ChatQueryMode {

    /**
     * 当前文档问答模式
     * 只从用户当前选定的文档中检索答案，适合精确查询特定文档内容的场景。
     */
    DOCUMENT(1, "当前文档问答"),

    /**
     * 自动知识问答模式
     * 系统自动判断用户问题是否需要从知识库文档中检索，智能选择回答策略。
     */
    AUTO_DOCUMENT(3, "自动知识问答"),

    /**
     * 开放式提问模式
     * 不做文档检索，直接由大模型根据自身知识回答，适合闲聊或通用知识问答。
     */
    OPEN_CHAT(2, "开放式提问");

    /**
     * 数字编码，用于前端传递和数据库存储
     */
    private final int code;

    /**
     * 中文标签，用于前端展示
     */
    private final String label;

    /**
     * 枚举构造方法
     *
     * @param code  数字编码
     * @param label 中文标签
     */
    ChatQueryMode(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据数字编码查找对应的枚举实例
     *
     * @param code 数字编码（不能为 null）
     * @return 对应的 ChatQueryMode 枚举实例
     * @throws IllegalArgumentException 如果 code 为 null 或者找不到匹配的枚举值
     */
    public static ChatQueryMode fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("提问模式 code 不能为空");
        }
        for (ChatQueryMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知的提问模式 code: " + code);
    }
}
