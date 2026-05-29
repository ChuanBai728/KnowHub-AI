package ai.knowhub.document.support;

/**
 * 文档逻辑行（Document Structure Logical Line）
 *
 * 【类的作用】
 * 表示文档中的一个"逻辑行"。与物理行不同，一个物理行可能包含多个逻辑行
 * （例如一行中包含多个步骤时，会被拆分为多个逻辑行）。
 *
 * 【在架构中的角色】
 * 属于文档结构解析的第一步——行预处理。DocumentStructureSignalExtractor
 * 在分析文档时，首先将原始文本拆分为逻辑行列表，然后对每个逻辑行进行分类。
 *
 * 【设计模式】
 * 使用 Java record 定义的不可变数据对象，所有字段都是 final 的。
 *
 * @param lineNo         逻辑行编号（从 1 开始，全局唯一）
 * @param sourceLineNo   原始物理行编号（从 1 开始）
 * @param segmentIndex   在同一物理行中的段索引（从 1 开始，单段行为 1）
 * @param indentLevel    缩进层级（空格数，tab 算 4 个空格）
 * @param rawText        原始文本（未处理）
 * @param normalizedText 标准化后的文本（去除首尾空白）
 */
public record DocumentStructureLogicalLine(
    int lineNo,
    int sourceLineNo,
    int segmentIndex,
    int indentLevel,
    String rawText,
    String normalizedText
) {
}
