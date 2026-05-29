package ai.knowhub.document.support;

import java.util.List;

/**
 * 文档结构信号批次（Document Structure Signal Batch）
 *
 * 【类的作用】
 * 封装了文档结构信号提取的结果，包含两个部分：
 * 1. 上下文行列表（contextLines）：文档的所有逻辑行文本，用于后续的歧义消解
 * 2. 信号列表（signals）：每行文本对应的结构信号
 *
 * 【在架构中的角色】
 * 是 DocumentStructureSignalExtractor 的输出，同时作为
 * DocumentStructureAmbiguityResolver 的输入。
 *
 * 【设计模式】
 * 使用 Java record 定义的不可变数据对象。
 *
 * @param contextLines 文档的所有逻辑行文本（标准化后）
 * @param signals      对应的结构信号列表
 */
public record DocumentStructureSignalBatch(
    List<String> contextLines,
    List<DocumentStructureSignal> signals
) {
}
