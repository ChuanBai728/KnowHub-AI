package ai.knowhub.document.support;

/**
 * 文档结构信号类型枚举（Document Structure Signal Kind）
 *
 * 【类的作用】
 * 定义了文档结构分析中所有可能的信号类型。
 * 每个信号类型对应文档中一行文本的结构分类结果。
 *
 * 【在架构中的角色】
 * 是文档结构解析流程的核心枚举，被 DocumentStructureSignal 使用，
 * 决定了后续处理逻辑（如层级构建时如何处理每种类型的信号）。
 */
public enum DocumentStructureSignalKind {

    /**
     * 文档标题（Document Title）
     * 文档本身的标题，通常在信号列表中作为第一个元素（行号为 0）
     */
    DOCUMENT_TITLE,

    /**
     * 确定的标题（Heading）
     * 通过规则确定识别为标题的行，如 Markdown 标题、中文章节、十进制数字标题等
     */
    HEADING,

    /**
     * 标题候选（Heading Candidate）
     * 可能是标题但不确定的行，需要通过 LLM 进一步消解
     * 例如：单级数字行 "1、概述" 可能是标题也可能是列表项
     */
    HEADING_CANDIDATE,

    /**
     * 步骤项（Step Item）
     * 明确的步骤标记，如 "第1步"、"步骤三" 等
     */
    STEP_ITEM,

    /**
     * 列表项（List Item）
     * 列表中的项目，包括无序列表（- * +）、有序列表、checkbox 列表等
     */
    LIST_ITEM,

    /**
     * 表格行（Table Row）
     * 表格中的一行，通常以 | 分隔或包含 tab 字符
     */
    TABLE_ROW,

    /**
     * 引用行（Quote）
     * 以 > 开头的引用文本
     */
    QUOTE,

    /**
     * 正文（Body）
     * 普通的正文文本，不属于任何特殊结构
     */
    BODY,

    /**
     * 空行（Blank）
     * 空白行，用于分隔段落或重置列表上下文
     */
    BLANK,

    /**
     * 噪音行（Noise）
     * 应该被忽略的噪音内容，如页码、版权信息、重复的页眉页脚等
     */
    NOISE
}
