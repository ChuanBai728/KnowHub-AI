package ai.knowhub.document.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档结构信号（Document Structure Signal）
 *
 * 【类的作用】
 * 表示文档中一行文本经过结构分析后产生的"结构信号"。
 * 每个信号包含了该行的分类结果（标题/列表项/正文等）、
 * 置信度、提取原因等信息。
 *
 * 【在架构中的角色】
 * 是文档结构解析流程中的核心数据对象。
 * DocumentStructureSignalExtractor 将每行文本转换为一个信号，
 * 然后 DocumentStructureAmbiguityResolver 对模糊信号进行消解，
 * 最终 DocumentStructureHierarchyResolver 将信号转换为结构节点。
 *
 * 【设计模式】
 * 使用 Builder 模式构建对象，支持灵活的字段设置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStructureSignal {

    /**
     * 行号（Line No）
     * 信号对应的文本行在文档中的行号（从 1 开始，0 表示文档标题）
     */
    private int lineNo;

    /**
     * 原始文本（Raw Text）
     * 未经处理的原始文本行
     */
    private String rawText;

    /**
     * 标准化文本（Normalized Text）
     * 经过空白标准化处理的文本
     */
    private String normalizedText;

    /**
     * 信号类型（Kind）
     * 标识该行的结构类型，对应 DocumentStructureSignalKind 枚举
     */
    private DocumentStructureSignalKind kind;

    /**
     * 节点编码（Node Code）
     * 从文本中提取的编号标识，如 "1.1"、"第一章"、"附录A" 等
     */
    private String nodeCode;

    /**
     * 标题文本（Title）
     * 从文本中提取的标题内容
     */
    private String title;

    /**
     * 层级提示（Level Hint）
     * 标题的层级提示值，如 Markdown 的 # 号数量
     */
    private Integer levelHint;

    /**
     * 缩进层级（Indent Level）
     * 文本行的缩进空格数（tab 算 4 个空格）
     */
    private Integer indentLevel;

    /**
     * 项目索引（Item Index）
     * 列表项在列表中的序号
     */
    private Integer itemIndex;

    /**
     * 数字路径（Numeric Path）
     * 用于十进制标题的层级推断，如 [1, 2, 3] 对应 "1.2.3"
     */
    @Builder.Default
    private List<Integer> numericPath = new ArrayList<>();

    /**
     * 提取原因列表（Reasons）
     * 记录信号被分类的原因，如 "markdown-heading"、"bullet-list" 等
     * 用于调试和后续的层级推断
     */
    @Builder.Default
    private List<String> reasons = new ArrayList<>();

    /**
     * 置信度（Confidence）
     * 分类结果的置信度，范围 0.0 ~ 1.0
     * 值越高表示分类越确定
     */
    private double confidence;

    /**
     * 判断是否为标题类信号（标题或标题候选）
     *
     * @return true 表示是标题类信号
     */
    public boolean isHeadingLike() {
        return kind == DocumentStructureSignalKind.HEADING
            || kind == DocumentStructureSignalKind.HEADING_CANDIDATE;
    }

    /**
     * 判断是否为列表类信号（步骤项或列表项）
     *
     * @return true 表示是列表类信号
     */
    public boolean isListLike() {
        return kind == DocumentStructureSignalKind.STEP_ITEM
            || kind == DocumentStructureSignalKind.LIST_ITEM;
    }

    /**
     * 判断是否为歧义信号（标题候选）
     * 歧义信号需要通过 LLM 进一步消解
     *
     * @return true 表示是歧义信号
     */
    public boolean isAmbiguous() {
        return kind == DocumentStructureSignalKind.HEADING_CANDIDATE;
    }
}
