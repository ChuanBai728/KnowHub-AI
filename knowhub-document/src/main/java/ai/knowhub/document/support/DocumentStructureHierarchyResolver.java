package ai.knowhub.document.support;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文档结构层级解析器（Document Structure Hierarchy Resolver）
 *
 * 【类的作用】
 * 将扁平的结构信号列表（DocumentStructureSignal）转换为具有父子关系的
 * 树状结构节点草案列表（DocumentStructureNodeDraft）。这是文档结构分析的
 * 核心步骤，决定了文档的层级组织方式。
 *
 * 【在架构中的角色】
 * 属于文档结构解析流水线的第三层——层级构建层。
 * 流程：信号提取 → 歧义消解 → **层级构建（本类）** → 树验证
 *
 * 【处理逻辑】
 * 1. 创建根节点（文档节点）
 * 2. 遍历所有信号，根据信号类型分别处理：
 *    - HEADING/HEADING_CANDIDATE → 创建章节节点，确定深度和父节点
 *    - LIST_ITEM/STEP_ITEM → 创建列表项节点，使用缩进栈管理嵌套
 *    - TABLE_ROW/QUOTE/BODY → 追加到当前章节或列表项的正文
 *    - BLANK → 重置列表上下文
 *    - NOISE → 忽略
 *
 * 【设计模式】
 * - 使用栈（Deque）管理列表项的嵌套关系（缩进感知）
 * - 使用 Map 记录最新的标题节点，用于确定父子关系
 */
@Component
public class DocumentStructureHierarchyResolver {

    /**
     * 解析结构信号列表，构建文档结构树
     *
     * @param documentTitle 文档标题
     * @param signals       经过歧义消解的结构信号列表
     * @return 结构节点草案列表，已按 nodeNo 排序
     */
    public List<DocumentStructureNodeDraft> resolve(String documentTitle,
                                                    List<DocumentStructureSignal> signals) {
        List<DocumentStructureNodeDraft> drafts = new ArrayList<>();

        // 创建根节点（文档节点，nodeNo=1，深度=0）
        DocumentStructureNodeDraft root = new DocumentStructureNodeDraft();
        root.setNodeNo(1);
        root.setLineNo(0);
        root.setNodeType(DocumentStructureNodeTypeEnum.DOCUMENT.getCode());
        root.setParentNodeNo(null);
        root.setDepth(0);
        root.setNodeCode("");
        root.setTitle(StrUtil.blankToDefault(documentTitle, "文档"));
        root.setAnchorText(StrUtil.blankToDefault(documentTitle, "文档"));
        root.setCanonicalPath("/document");
        root.setSectionPath("");
        root.setSourceFamily("document");
        root.setConfidence(1.0D);
        drafts.add(root);

        int nextNodeNo = 2; // 下一个可用的节点编号
        DocumentStructureNodeDraft currentSection = root;    // 当前章节节点
        DocumentStructureNodeDraft currentListItem = null;   // 当前列表项节点
        Deque<ListContext> listStack = new ArrayDeque<>();    // 列表嵌套栈（缩进感知）
        Map<Integer, Integer> latestHeadingByDepth = new LinkedHashMap<>();      // 深度 → 最新标题节点编号
        Map<String, Integer> latestHeadingByNumericPath = new LinkedHashMap<>(); // 数字路径 → 标题节点编号

        // 遍历所有信号，构建节点
        for (DocumentStructureSignal signal : signals) {
            if (signal == null || signal.getLineNo() == 0) {
                continue;
            }
            switch (signal.getKind()) {
                case BLANK -> {
                    // 空行重置列表上下文
                    currentListItem = null;
                    listStack.clear();
                }
                case NOISE -> {
                    // 噪音行直接忽略
                }
                case TABLE_ROW, QUOTE, BODY -> {
                    // 正文类内容：追加到当前章节或列表项
                    appendBody(signal, currentSection, currentListItem, root, drafts);
                }
                case STEP_ITEM, LIST_ITEM -> {
                    // 列表项/步骤项：确定父节点，创建列表节点
                    DocumentStructureNodeDraft listParent = resolveListParent(signal, currentSection == null ? root : currentSection, listStack, root);
                    DocumentStructureNodeDraft listNode = buildListNode(signal, nextNodeNo++, listParent);
                    drafts.add(listNode);
                    currentListItem = listNode;
                    registerListContext(signal, listNode, listStack);
                    // 同时将列表项文本追加到当前章节
                    if (currentSection != null) {
                        currentSection.appendLine(signal.getNormalizedText());
                    }
                }
                case HEADING, HEADING_CANDIDATE -> {
                    // 标题类：创建章节节点
                    DocumentStructureNodeDraft headingNode = buildHeadingNode(
                        signal,
                        nextNodeNo++,
                        drafts,
                        latestHeadingByDepth,
                        latestHeadingByNumericPath
                    );
                    drafts.add(headingNode);
                    currentSection = headingNode;
                    currentListItem = null; // 新章节重置列表上下文
                    listStack.clear();
                }
                default -> appendBody(signal, currentSection, currentListItem, root, drafts);
            }
        }

        // 按节点编号排序后返回
        drafts.sort(Comparator.comparing(DocumentStructureNodeDraft::getNodeNo));
        return drafts;
    }

    /**
     * 将正文类信号追加到合适的节点
     * 优先追加到当前列表项，其次追加到当前章节
     *
     * @param signal         结构信号
     * @param currentSection 当前章节节点
     * @param currentListItem 当前列表项节点
     * @param root           根节点
     * @param drafts         所有节点草案列表
     */
    private void appendBody(DocumentStructureSignal signal,
                            DocumentStructureNodeDraft currentSection,
                            DocumentStructureNodeDraft currentListItem,
                            DocumentStructureNodeDraft root,
                            List<DocumentStructureNodeDraft> drafts) {
        String line = signal == null ? "" : signal.getNormalizedText();
        if (StrUtil.isBlank(line)) {
            return;
        }
        // 确定追加目标：列表项 > 章节 > 根
        DocumentStructureNodeDraft target = currentListItem != null ? currentListItem : (currentSection == null ? root : currentSection);
        target.appendLine(line);
        // 如果列表项和章节不是同一个节点，也追加到章节
        if (currentListItem != null && currentSection != null && !Objects.equals(currentSection.getNodeNo(), currentListItem.getNodeNo())) {
            currentSection.appendLine(line);
        }
        // 兜底：如果没有章节，也追加到根节点
        if (currentSection == null && target != root) {
            root.appendLine(line);
        }
    }

    /**
     * 构建列表项节点
     *
     * @param signal  结构信号
     * @param nodeNo  节点编号
     * @param parent  父节点
     * @return 新建的列表项节点草案
     */
    private DocumentStructureNodeDraft buildListNode(DocumentStructureSignal signal,
                                                     int nodeNo,
                                                     DocumentStructureNodeDraft parent) {
        DocumentStructureNodeDraft draft = new DocumentStructureNodeDraft();
        draft.setNodeNo(nodeNo);
        draft.setLineNo(signal.getLineNo());
        // 根据信号类型确定节点类型：步骤项 or 列表项
        draft.setNodeType(signal.getKind() == DocumentStructureSignalKind.STEP_ITEM
            ? DocumentStructureNodeTypeEnum.STEP.getCode()
            : DocumentStructureNodeTypeEnum.LIST_ITEM.getCode());
        draft.setParentNodeNo(parent == null ? 1 : parent.getNodeNo());
        draft.setDepth((parent == null ? 0 : parent.getDepth()) + 1);
        draft.setNodeCode(StrUtil.blankToDefault(signal.getNodeCode(), signal.getItemIndex() == null ? "" : String.valueOf(signal.getItemIndex())));
        draft.setTitle(signal.getTitle());
        draft.setAnchorText(StrUtil.blankToDefault(signal.getNormalizedText(), signal.getTitle()));
        draft.setItemIndex(signal.getItemIndex());
        draft.setSourceFamily(signal.getKind() == DocumentStructureSignalKind.STEP_ITEM ? "step" : "list");
        draft.setConfidence(signal.getConfidence());
        draft.appendLine(signal.getNormalizedText());
        return draft;
    }

    /**
     * 解析列表项的父节点（基于缩进）
     * 使用缩进栈来确定嵌套关系：缩进更深的项是前一项的子节点
     *
     * @param signal        结构信号
     * @param currentSection 当前章节节点
     * @param listStack     列表嵌套栈
     * @param root          根节点
     * @return 列表项的父节点
     */
    private DocumentStructureNodeDraft resolveListParent(DocumentStructureSignal signal,
                                                         DocumentStructureNodeDraft currentSection,
                                                         Deque<ListContext> listStack,
                                                         DocumentStructureNodeDraft root) {
        int indentLevel = safeIndentLevel(signal);
        // 弹出栈中缩进 >= 当前缩进的项（同级或更深的项不再作为父节点）
        while (!listStack.isEmpty() && listStack.peekLast().indentLevel() >= indentLevel) {
            listStack.removeLast();
        }
        // 如果栈非空且当前缩进更深，栈顶就是父节点
        if (!listStack.isEmpty() && indentLevel > listStack.peekLast().indentLevel()) {
            return listStack.peekLast().node();
        }
        // 否则父节点是当前章节或根节点
        return currentSection == null ? root : currentSection;
    }

    /**
     * 注册列表项到缩进栈
     *
     * @param signal    结构信号
     * @param listNode  列表项节点
     * @param listStack 列表嵌套栈
     */
    private void registerListContext(DocumentStructureSignal signal,
                                     DocumentStructureNodeDraft listNode,
                                     Deque<ListContext> listStack) {
        int indentLevel = safeIndentLevel(signal);
        // 弹出栈中缩进 >= 当前缩进的项
        while (!listStack.isEmpty() && listStack.peekLast().indentLevel() >= indentLevel) {
            listStack.removeLast();
        }
        // 将当前项压入栈
        listStack.addLast(new ListContext(listNode, indentLevel));
    }

    /**
     * 构建标题节点
     *
     * @param signal                   结构信号
     * @param nodeNo                   节点编号
     * @param drafts                   所有节点草案列表
     * @param latestHeadingByDepth     深度 → 最新标题节点编号映射
     * @param latestHeadingByNumericPath 数字路径 → 标题节点编号映射
     * @return 新建的标题节点草案
     */
    private DocumentStructureNodeDraft buildHeadingNode(DocumentStructureSignal signal,
                                                        int nodeNo,
                                                        List<DocumentStructureNodeDraft> drafts,
                                                        Map<Integer, Integer> latestHeadingByDepth,
                                                        Map<String, Integer> latestHeadingByNumericPath) {
        // 解析标题深度和父节点
        int depth = resolveHeadingDepth(signal, drafts, latestHeadingByDepth, latestHeadingByNumericPath);
        Integer parentNodeNo = resolveHeadingParentNodeNo(signal, depth, drafts, latestHeadingByDepth, latestHeadingByNumericPath);

        DocumentStructureNodeDraft draft = new DocumentStructureNodeDraft();
        draft.setNodeNo(nodeNo);
        draft.setLineNo(signal.getLineNo());
        draft.setNodeType(DocumentStructureNodeTypeEnum.SECTION.getCode());
        draft.setParentNodeNo(parentNodeNo);
        draft.setDepth(depth);
        draft.setNodeCode(StrUtil.blankToDefault(signal.getNodeCode(), ""));
        draft.setTitle(signal.getTitle());
        draft.setAnchorText(buildHeadingAnchorText(signal));
        draft.setNumericPath(signal.getNumericPath() == null ? List.of() : new ArrayList<>(signal.getNumericPath()));
        draft.setSourceFamily(resolveHeadingFamily(signal));
        draft.setConfidence(signal.getConfidence());
        draft.appendLine(signal.getNormalizedText());

        // 更新深度映射：移除所有 >= 当前深度的旧映射，添加新映射
        latestHeadingByDepth.entrySet().removeIf(entry -> entry.getKey() >= depth);
        latestHeadingByDepth.put(depth, nodeNo);
        // 更新数字路径映射
        String numericKey = numericKey(draft.getNumericPath());
        if (StrUtil.isNotBlank(numericKey)) {
            latestHeadingByNumericPath.put(numericKey, nodeNo);
        }
        return draft;
    }

    /**
     * 解析标题的深度
     * 根据标题的来源家族（markdown/chapter/appendix/decimal）使用不同的深度计算策略
     *
     * @param signal                   结构信号
     * @param drafts                   已有的节点草案
     * @param latestHeadingByDepth     深度映射
     * @param latestHeadingByNumericPath 数字路径映射
     * @return 标题深度（从 1 开始）
     */
    private int resolveHeadingDepth(DocumentStructureSignal signal,
                                    List<DocumentStructureNodeDraft> drafts,
                                    Map<Integer, Integer> latestHeadingByDepth,
                                    Map<String, Integer> latestHeadingByNumericPath) {
        String family = resolveHeadingFamily(signal);
        List<Integer> numericPath = signal.getNumericPath() == null ? List.of() : signal.getNumericPath();
        // Markdown 标题：直接使用 # 号数量作为深度
        if ("markdown".equals(family)) {
            return Math.max(1, safeLevel(signal.getLevelHint(), 1));
        }
        // 中文章节和附录：固定为一级
        if ("chapter".equals(family) || "appendix".equals(family)) {
            return 1;
        }
        // 十进制数字标题：根据数字路径确定深度
        if ("decimal".equals(family)) {
            if (numericPath.size() <= 1) {
                return 1;
            }
            // 尝试通过直接父路径找到父节点
            Integer parentNodeNo = latestHeadingByNumericPath.get(numericKey(numericPath.subList(0, numericPath.size() - 1)));
            if (parentNodeNo != null) {
                DocumentStructureNodeDraft parent = findByNodeNo(drafts, parentNodeNo);
                if (parent != null) {
                    return parent.getDepth() + 1;
                }
            }
            // 回退到章节级父节点
            Integer chapterParent = latestHeadingByNumericPath.get(numericKey(List.of(numericPath.get(0))));
            if (chapterParent != null) {
                DocumentStructureNodeDraft parent = findByNodeNo(drafts, chapterParent);
                if (parent != null) {
                    return parent.getDepth() + 1;
                }
            }
            return numericPath.size();
        }
        // 默认：使用层级提示
        return Math.max(1, safeLevel(signal.getLevelHint(), 1));
    }

    /**
     * 解析标题的父节点编号
     *
     * @param signal                   结构信号
     * @param depth                    标题深度
     * @param drafts                   已有的节点草案
     * @param latestHeadingByDepth     深度映射
     * @param latestHeadingByNumericPath 数字路径映射
     * @return 父节点编号
     */
    private Integer resolveHeadingParentNodeNo(DocumentStructureSignal signal,
                                               int depth,
                                               List<DocumentStructureNodeDraft> drafts,
                                               Map<Integer, Integer> latestHeadingByDepth,
                                               Map<String, Integer> latestHeadingByNumericPath) {
        String family = resolveHeadingFamily(signal);
        List<Integer> numericPath = signal.getNumericPath() == null ? List.of() : signal.getNumericPath();
        // 中文章节和附录：父节点固定为根节点
        if ("chapter".equals(family) || "appendix".equals(family)) {
            return 1;
        }
        // 十进制数字标题：通过数字路径查找父节点
        if ("decimal".equals(family) && numericPath.size() > 1) {
            Integer exactParent = latestHeadingByNumericPath.get(numericKey(numericPath.subList(0, numericPath.size() - 1)));
            if (exactParent != null) {
                return exactParent;
            }
            Integer chapterParent = latestHeadingByNumericPath.get(numericKey(List.of(numericPath.get(0))));
            if (chapterParent != null) {
                return chapterParent;
            }
        }
        // 默认：通过深度查找最近的父节点
        return findNearestParentByDepth(depth, latestHeadingByDepth);
    }

    /**
     * 通过深度查找最近的父节点
     * 从 depth-1 开始向下查找，找到第一个存在的节点即为父节点
     *
     * @param depth                当前深度
     * @param latestHeadingByDepth 深度映射
     * @return 父节点编号，如果找不到则返回 1（根节点）
     */
    private Integer findNearestParentByDepth(int depth,
                                             Map<Integer, Integer> latestHeadingByDepth) {
        for (int candidateDepth = depth - 1; candidateDepth >= 1; candidateDepth--) {
            Integer parentNodeNo = latestHeadingByDepth.get(candidateDepth);
            if (parentNodeNo != null) {
                return parentNodeNo;
            }
        }
        return 1; // 默认回退到根节点
    }

    /**
     * 解析标题的来源家族
     * 根据信号的 reasons 列表判断标题的格式来源
     *
     * @param signal 结构信号
     * @return 来源家族字符串：markdown/chapter/appendix/decimal/plain
     */
    private String resolveHeadingFamily(DocumentStructureSignal signal) {
        if (signal == null || signal.getReasons() == null) {
            return "plain";
        }
        if (signal.getReasons().contains("markdown-heading")) {
            return "markdown";
        }
        if (signal.getReasons().contains("chapter-heading")) {
            return "chapter";
        }
        if (signal.getReasons().contains("appendix-heading")) {
            return "appendix";
        }
        if (signal.getReasons().contains("decimal-heading")) {
            return "decimal";
        }
        if (signal.getReasons().contains("single-digit-ambiguous-heading")) {
            return "decimal";
        }
        return "plain";
    }

    /**
     * 构建标题的锚文本
     * 如果有节点编码（如 "1.1"），则将编码和标题拼接
     *
     * @param signal 结构信号
     * @return 锚文本字符串
     */
    private String buildHeadingAnchorText(DocumentStructureSignal signal) {
        String code = StrUtil.blankToDefault(signal.getNodeCode(), "").trim();
        String title = StrUtil.blankToDefault(signal.getTitle(), "").trim();
        if (StrUtil.isBlank(code)) {
            return title;
        }
        if (title.startsWith(code)) {
            return title;
        }
        return code + " " + title;
    }

    /**
     * 将数字路径转换为点分隔的字符串键
     * 例如 [1, 2, 3] → "1.2.3"
     *
     * @param numericPath 数字路径列表
     * @return 点分隔的字符串键
     */
    private String numericKey(List<Integer> numericPath) {
        if (numericPath == null || numericPath.isEmpty()) {
            return "";
        }
        return numericPath.stream().map(String::valueOf).reduce((left, right) -> left + "." + right).orElse("");
    }

    /**
     * 安全地获取层级值
     *
     * @param levelHint    层级提示值（可能为 null）
     * @param defaultValue 默认值
     * @return 有效的层级值
     */
    private int safeLevel(Integer levelHint, int defaultValue) {
        return levelHint == null || levelHint <= 0 ? defaultValue : levelHint;
    }

    /**
     * 安全地获取缩进层级
     *
     * @param signal 结构信号
     * @return 缩进层级（>= 0）
     */
    private int safeIndentLevel(DocumentStructureSignal signal) {
        if (signal == null || signal.getIndentLevel() == null || signal.getIndentLevel() < 0) {
            return 0;
        }
        return signal.getIndentLevel();
    }

    /**
     * 根据节点编号查找节点
     *
     * @param drafts 节点草案列表
     * @param nodeNo 要查找的节点编号
     * @return 找到的节点，未找到返回 null
     */
    private DocumentStructureNodeDraft findByNodeNo(List<DocumentStructureNodeDraft> drafts, Integer nodeNo) {
        if (nodeNo == null) {
            return null;
        }
        for (DocumentStructureNodeDraft draft : drafts) {
            if (draft != null && nodeNo.equals(draft.getNodeNo())) {
                return draft;
            }
        }
        return null;
    }

    /**
     * 列表上下文记录类
     * 用于管理列表项的嵌套关系
     *
     * @param node        列表项节点
     * @param indentLevel 缩进层级
     */
    private record ListContext(
        DocumentStructureNodeDraft node,
        int indentLevel
    ) {
    }
}
