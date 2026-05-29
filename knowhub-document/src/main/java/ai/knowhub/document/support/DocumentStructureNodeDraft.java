package ai.knowhub.document.support;

import lombok.Data;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档结构节点草案（Document Structure Node Draft）
 *
 * 【类的作用】
 * 文档结构树构建过程中的中间产物。在层级解析阶段，每个信号被转换为一个 NodeDraft，
 * 包含了节点的完整构建信息。最终会被 DocumentStructureTreeValidator 转换为
 * DocumentStructureNodeCandidate。
 *
 * 【在架构中的角色】
 * 属于文档结构解析流水线的中间层。从信号提取到最终的候选节点之间，
 * NodeDraft 承载了构建过程中的所有中间状态。
 *
 * 【关键特性】
 * - 使用 ContentHolder 内部类高效地拼接文本内容
 * - 支持数字路径（numericPath）用于十进制标题的层级推断
 * - 支持来源家族（sourceFamily）标识标题格式来源
 */
@Data
public class DocumentStructureNodeDraft {

    /**
     * 节点编号（Node No）
     * 节点的唯一标识，根节点为 1，后续节点递增
     */
    private Integer nodeNo;

    /**
     * 源行号（Line No）
     * 节点对应的原始文本在文档中的行号
     */
    private Integer lineNo;

    /**
     * 节点类型（Node Type）
     * 对应 DocumentStructureNodeTypeEnum 的编码值
     */
    private Integer nodeType;

    /**
     * 父节点编号（Parent Node No）
     * 指向父节点的编号
     */
    private Integer parentNodeNo;

    /**
     * 前一个兄弟节点编号（Previous Sibling Node No）
     */
    private Integer prevSiblingNodeNo;

    /**
     * 后一个兄弟节点编号（Next Sibling Node No）
     */
    private Integer nextSiblingNodeNo;

    /**
     * 节点深度（Depth）
     * 节点在树中的深度，根节点为 0
     */
    private Integer depth;

    /**
     * 节点编码（Node Code）
     * 节点的编号标识，如 "1.1"、"第一章" 等
     */
    private String nodeCode;

    /**
     * 节点标题（Title）
     */
    private String title;

    /**
     * 锚文本（Anchor Text）
     */
    private String anchorText;

    /**
     * 规范路径（Canonical Path）
     */
    private String canonicalPath;

    /**
     * 章节路径（Section Path）
     */
    private String sectionPath;

    /**
     * 项目索引（Item Index）
     * 列表项在列表中的序号
     */
    private Integer itemIndex;

    /**
     * 内容持有者（Content Holder）
     * 使用 StringBuilder 高效拼接文本内容，避免频繁创建字符串对象
     */
    @Data
    private static final class ContentHolder {
        private final StringBuilder builder = new StringBuilder();
    }

    /**
     * 内容实例
     */
    private final ContentHolder content = new ContentHolder();

    /**
     * 数字路径（Numeric Path）
     * 用于十进制标题的层级推断，如 [1, 2, 3] 对应 "1.2.3"
     */
    private List<Integer> numericPath = new ArrayList<>();

    /**
     * 来源家族（Source Family）
     * 标识标题的格式来源：document/markdown/chapter/appendix/decimal/plain/step/list
     */
    private String sourceFamily;

    /**
     * 置信度（Confidence）
     * 节点分类的置信度，范围 0.0 ~ 1.0
     */
    private double confidence;

    /**
     * 追加一行文本到节点内容
     * 如果内容不为空，会自动添加换行符
     *
     * @param line 要追加的文本行
     */
    public void appendLine(String line) {
        String normalized = line == null ? "" : line.trim();
        if (normalized.isBlank()) {
            return;
        }
        if (!content.builder.isEmpty()) {
            content.builder.append('\n');
        }
        content.builder.append(normalized);
    }

    /**
     * 获取节点的完整文本内容
     *
     * @return 拼接后的文本内容（去除首尾空白）
     */
    public String contentText() {
        return content.builder.toString().trim();
    }

    /**
     * 判断是否为章节节点
     *
     * @return true 表示是章节节点
     */
    public boolean isSection() {
        return DocumentStructureNodeTypeEnum.SECTION.getCode().equals(nodeType);
    }

    /**
     * 判断是否为列表类节点（步骤项或列表项）
     *
     * @return true 表示是列表类节点
     */
    public boolean isListLike() {
        return DocumentStructureNodeTypeEnum.STEP.getCode().equals(nodeType)
            || DocumentStructureNodeTypeEnum.LIST_ITEM.getCode().equals(nodeType);
    }
}
