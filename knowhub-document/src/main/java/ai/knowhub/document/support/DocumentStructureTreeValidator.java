package ai.knowhub.document.support;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文档结构树验证器（Document Structure Tree Validator）
 *
 * 【类的作用】
 * 文档结构解析流水线的最后一步——树验证和修复。对层级解析器生成的节点草案
 * 进行验证和修复，确保结构树的完整性和一致性，然后转换为最终的候选节点列表。
 *
 * 【在架构中的角色】
 * 属于文档结构解析流水线的第四层（最后一层）。
 * 流程：信号提取 → 歧义消解 → 层级构建 → **树验证（本类）**
 *
 * 【验证和修复步骤】
 * 1. collapseSyntheticTitleSection：合并与文档标题重复的章节节点
 * 2. repairNumberedHierarchy：修复数字路径驱动的父子关系
 * 3. repairInvalidParents：修复无效的父节点引用（如章节的父节点是列表项）
 * 4. recomputeDepths：重新计算所有节点的深度
 * 5. rebuildPaths：重建规范路径和章节路径
 * 6. rebuildSiblingLinks：重建兄弟节点链接
 *
 * 【设计模式】
 * 使用多步修复策略，每步独立处理一类问题，按顺序执行。
 */
@Component
public class DocumentStructureTreeValidator {

    /**
     * 验证并构建最终的节点候选列表
     *
     * @param documentTitle 文档标题
     * @param drafts        节点草案列表
     * @return 验证和修复后的节点候选列表
     */
    public List<DocumentStructureNodeCandidate> validateAndBuild(String documentTitle,
                                                                 List<DocumentStructureNodeDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        // 构建节点编号 → 节点的映射
        Map<Integer, DocumentStructureNodeDraft> draftMap = new LinkedHashMap<>();
        for (DocumentStructureNodeDraft draft : drafts) {
            if (draft != null && draft.getNodeNo() != null) {
                draftMap.put(draft.getNodeNo(), draft);
            }
        }

        // 步骤1：合并与文档标题重复的章节节点
        collapseSyntheticTitleSection(documentTitle, draftMap);
        // 步骤2：修复数字路径驱动的父子关系
        repairNumberedHierarchy(draftMap);
        // 步骤3：修复无效的父节点引用
        repairInvalidParents(draftMap);
        // 步骤4：重新计算所有节点的深度
        recomputeDepths(draftMap);
        // 步骤5：重建规范路径和章节路径
        rebuildPaths(documentTitle, draftMap);
        // 步骤6：重建兄弟节点链接
        rebuildSiblingLinks(draftMap);

        // 转换为最终的候选节点列表
        return draftMap.values().stream()
            .sorted(Comparator.comparingInt(DocumentStructureNodeDraft::getNodeNo))
            .map(this::toCandidate)
            .toList();
    }

    /**
     * 合并与文档标题重复的章节节点
     * 如果某个章节节点的标题与文档标题相同，将其子节点提升为根节点的直接子节点，
     * 然后删除该重复节点。
     */
    private void collapseSyntheticTitleSection(String documentTitle,
                                               Map<Integer, DocumentStructureNodeDraft> draftMap) {
        String normalizedTitle = normalizeComparableTitle(documentTitle);
        if (normalizedTitle.isBlank()) {
            return;
        }
        // 查找与文档标题重复的章节节点（排除根节点）
        Integer duplicateNodeNo = null;
        for (DocumentStructureNodeDraft draft : draftMap.values()) {
            if (draft == null
                || draft.getNodeNo() == null
                || draft.getNodeNo() == 1
                || !draft.isSection()
                || !Objects.equals(draft.getParentNodeNo(), 1)
                || StrUtil.isNotBlank(draft.getNodeCode())) {
                continue;
            }
            if (normalizedTitle.equals(normalizeComparableTitle(draft.getTitle()))) {
                duplicateNodeNo = draft.getNodeNo();
                break;
            }
        }
        if (duplicateNodeNo == null) {
            return;
        }
        // 将重复节点的子节点提升为根节点的直接子节点
        for (DocumentStructureNodeDraft draft : draftMap.values()) {
            if (draft != null && Objects.equals(draft.getParentNodeNo(), duplicateNodeNo)) {
                draft.setParentNodeNo(1);
            }
        }
        // 删除重复节点
        draftMap.remove(duplicateNodeNo);
    }

    /**
     * 修复数字路径驱动的父子关系
     * 根据节点的数字路径（如 [1, 2, 3]）重新确定父子关系，
     * 确保 "1.2.3" 的父节点是 "1.2"
     */
    private void repairNumberedHierarchy(Map<Integer, DocumentStructureNodeDraft> draftMap) {
        // 构建数字路径 → 节点编号的映射
        Map<String, Integer> numericPathMap = new LinkedHashMap<>();
        for (DocumentStructureNodeDraft draft : draftMap.values()) {
            if (draft == null || !draft.isSection()) {
                continue;
            }
            String key = numericKey(draft.getNumericPath());
            if (StrUtil.isNotBlank(key)) {
                numericPathMap.putIfAbsent(key, draft.getNodeNo());
            }
        }

        // 根据数字路径修复父子关系
        for (DocumentStructureNodeDraft draft : draftMap.values()) {
            if (draft == null || !draft.isSection()) {
                continue;
            }
            List<Integer> numericPath = draft.getNumericPath();
            if (numericPath == null || numericPath.isEmpty()) {
                continue;
            }
            // 单级数字路径：父节点为根节点
            if (numericPath.size() == 1) {
                draft.setParentNodeNo(1);
                continue;
            }
            // 多级数字路径：查找直接父节点
            String directParentKey = numericKey(numericPath.subList(0, numericPath.size() - 1));
            Integer directParent = numericPathMap.get(directParentKey);
            if (directParent != null) {
                draft.setParentNodeNo(directParent);
                continue;
            }
            // 回退到章节级父节点
            String chapterParentKey = numericKey(List.of(numericPath.get(0)));
            Integer chapterParent = numericPathMap.get(chapterParentKey);
            if (chapterParent != null) {
                draft.setParentNodeNo(chapterParent);
            }
        }
    }

    /**
     * 修复无效的父节点引用
     * 例如：章节节点的父节点不应该是列表项节点
     */
    private void repairInvalidParents(Map<Integer, DocumentStructureNodeDraft> draftMap) {
        for (DocumentStructureNodeDraft draft : draftMap.values()) {
            if (draft == null || draft.getNodeNo() == 1) {
                continue;
            }
            DocumentStructureNodeDraft parent = draft.getParentNodeNo() == null ? null : draftMap.get(draft.getParentNodeNo());
            // 父节点不存在：回退到根节点
            if (parent == null) {
                draft.setParentNodeNo(1);
                continue;
            }
            // 章节节点的父节点不能是列表类节点：提升到列表节点的父节点
            if (draft.isSection() && parent.isListLike()) {
                draft.setParentNodeNo(parent.getParentNodeNo() == null ? 1 : parent.getParentNodeNo());
            }
        }
    }

    /**
     * 重新计算所有节点的深度
     * 基于父子关系递归计算，确保深度值的准确性
     */
    private void recomputeDepths(Map<Integer, DocumentStructureNodeDraft> draftMap) {
        DocumentStructureNodeDraft root = draftMap.get(1);
        if (root == null) {
            return;
        }
        root.setDepth(0);
        List<DocumentStructureNodeDraft> ordered = draftMap.values().stream()
            .sorted(Comparator.comparingInt(DocumentStructureNodeDraft::getNodeNo))
            .toList();
        for (DocumentStructureNodeDraft draft : ordered) {
            if (draft == null || draft.getNodeNo() == 1) {
                continue;
            }
            DocumentStructureNodeDraft parent = draftMap.get(draft.getParentNodeNo());
            draft.setDepth(parent == null ? 1 : parent.getDepth() + 1);
        }
    }

    /**
     * 重建所有节点的规范路径和章节路径
     * 规范路径：/document/chapter-1/section-2/item-3
     * 章节路径：第一章 > 第一节 > 背景
     */
    private void rebuildPaths(String documentTitle,
                              Map<Integer, DocumentStructureNodeDraft> draftMap) {
        for (DocumentStructureNodeDraft draft : draftMap.values()) {
            if (draft == null) {
                continue;
            }
            // 根节点的路径固定
            if (draft.getNodeNo() == 1) {
                draft.setCanonicalPath("/document");
                draft.setSectionPath("");
                continue;
            }
            DocumentStructureNodeDraft parent = draftMap.get(draft.getParentNodeNo());
            String parentCanonicalPath = parent == null ? "/document" : StrUtil.blankToDefault(parent.getCanonicalPath(), "/document");
            String parentSectionPath = parent == null ? "" : StrUtil.blankToDefault(parent.getSectionPath(), "");
            String segment = buildPathSegment(draft);
            draft.setCanonicalPath(parentCanonicalPath + "/" + segment);
            // 章节节点追加到章节路径，其他节点继承父节点的章节路径
            if (draft.isSection()) {
                draft.setSectionPath(joinSectionPath(parentSectionPath, displayTitle(draft)));
            }
            else {
                draft.setSectionPath(parentSectionPath);
            }
        }
    }

    /**
     * 重建兄弟节点链接
     * 为每个节点设置前一个和后一个兄弟节点的编号
     */
    private void rebuildSiblingLinks(Map<Integer, DocumentStructureNodeDraft> draftMap) {
        // 按父节点分组
        Map<Integer, List<DocumentStructureNodeDraft>> childrenByParent = new LinkedHashMap<>();
        for (DocumentStructureNodeDraft draft : draftMap.values()) {
            if (draft == null || draft.getNodeNo() == 1) {
                continue;
            }
            childrenByParent.computeIfAbsent(draft.getParentNodeNo(), ignored -> new ArrayList<>()).add(draft);
        }
        // 对每组子节点按行号排序，设置兄弟链接
        for (List<DocumentStructureNodeDraft> siblings : childrenByParent.values()) {
            siblings.sort(Comparator.comparingInt(DocumentStructureNodeDraft::getLineNo));
            for (int index = 0; index < siblings.size(); index++) {
                DocumentStructureNodeDraft current = siblings.get(index);
                current.setPrevSiblingNodeNo(index == 0 ? 0 : siblings.get(index - 1).getNodeNo());
                current.setNextSiblingNodeNo(index == siblings.size() - 1 ? 0 : siblings.get(index + 1).getNodeNo());
            }
        }
    }

    /**
     * 将节点草案转换为节点候选
     */
    private DocumentStructureNodeCandidate toCandidate(DocumentStructureNodeDraft draft) {
        return new DocumentStructureNodeCandidate(
            draft.getNodeNo(),
            draft.getNodeType(),
            draft.getParentNodeNo(),
            normalizeSibling(draft.getPrevSiblingNodeNo()),
            normalizeSibling(draft.getNextSiblingNodeNo()),
            draft.getDepth(),
            draft.getNodeCode(),
            draft.getTitle(),
            draft.getAnchorText(),
            draft.getCanonicalPath(),
            draft.getSectionPath(),
            draft.contentText(),
            draft.getItemIndex()
        );
    }

    /**
     * 标准化兄弟节点编号（null → 0）
     */
    private Integer normalizeSibling(Integer siblingNodeNo) {
        return siblingNodeNo == null ? 0 : siblingNodeNo;
    }

    /**
     * 拼接章节路径
     */
    private String joinSectionPath(String parentSectionPath, String currentTitle) {
        if (StrUtil.isBlank(parentSectionPath)) {
            return StrUtil.blankToDefault(currentTitle, "");
        }
        if (StrUtil.isBlank(currentTitle)) {
            return parentSectionPath;
        }
        return parentSectionPath + " > " + currentTitle;
    }

    /**
     * 构建路径段
     * 列表类节点使用 item-{index}，其他节点使用 slug 化的编码或标题
     */
    private String buildPathSegment(DocumentStructureNodeDraft draft) {
        if (draft == null) {
            return "node";
        }
        if (draft.isListLike()) {
            if (draft.getItemIndex() != null && draft.getItemIndex() > 0) {
                return "item-" + draft.getItemIndex();
            }
            return slug(displayTitle(draft));
        }
        String code = StrUtil.blankToDefault(draft.getNodeCode(), "").trim();
        if (StrUtil.isNotBlank(code)) {
            return slug(code);
        }
        return slug(displayTitle(draft));
    }

    /**
     * 获取节点的显示标题（编码 + 标题）
     */
    private String displayTitle(DocumentStructureNodeDraft draft) {
        String code = StrUtil.blankToDefault(draft.getNodeCode(), "").trim();
        String title = StrUtil.blankToDefault(draft.getTitle(), "").trim();
        if (StrUtil.isBlank(code)) {
            return title;
        }
        if (title.startsWith(code)) {
            return title;
        }
        return code + " " + title;
    }

    /**
     * 将文本转换为 URL 友好的 slug
     * 保留中文、字母、数字、下划线、点、短横线
     */
    private String slug(String value) {
        String normalized = StrUtil.blankToDefault(value, "").trim();
        if (normalized.isBlank()) {
            return "node";
        }
        String slug = normalized
            .replaceAll("\\s+", "-")
            .replaceAll("[^\\p{IsHan}A-Za-z0-9_.-]", "");
        return slug.isBlank() ? "node" : slug;
    }

    /**
     * 将数字路径转换为点分隔的字符串键
     */
    private String numericKey(List<Integer> numericPath) {
        if (numericPath == null || numericPath.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < numericPath.size(); index++) {
            if (index > 0) {
                builder.append('.');
            }
            Integer segment = numericPath.get(index);
            if (segment != null) {
                builder.append(segment);
            }
        }
        return builder.toString();
    }

    /**
     * 标准化标题文本用于比较
     */
    private String normalizeComparableTitle(String text) {
        String normalized = StrUtil.blankToDefault(text, "").trim();
        if (normalized.isBlank()) {
            return "";
        }
        return normalized
            .replaceAll("^#+\\s*", "")
            .replaceAll("\\.[A-Za-z0-9]{1,6}$", "")
            .replaceAll("\\s+", "")
            .toLowerCase();
    }
}
