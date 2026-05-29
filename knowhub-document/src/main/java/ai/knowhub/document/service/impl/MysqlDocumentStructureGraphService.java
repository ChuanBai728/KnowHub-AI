package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import cn.hutool.core.util.StrUtil;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.model.graph.GraphItem;
import ai.knowhub.document.model.graph.GraphSection;
import ai.knowhub.document.service.DocumentStructureGraphService;
import ai.knowhub.document.service.DocumentStructureNodeService;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 【文档结构图服务 - MySQL 实现】
 *
 * 这个类是 DocumentStructureGraphService 接口的 MySQL 实现，
 * 作为 KnowHub AI 的结构图查询实现。
 *
 * 实现原理：
 *   - 从 MySQL 的 document_structure_node 表读取所有结构节点
 *   - 在内存中通过 parentNodeId、prevSiblingNodeId、nextSiblingNodeId 等字段
 *     模拟图结构的父子、兄弟遍历
 *   - 本质上是将关系型数据"伪装"成图查询
 *
 * 与 MySQL 实现的区别：
 *   - MySQL 方案：每次查询都加载所有节点到内存，通过 Java 代码过滤和排序
 *   - MySQL 方案：直接用 Cypher 查询语言在数据库层遍历图结构，性能更优
 *
 * 使用场景：
 *   - 当 MySQL 未配置或该文档没有图数据时，CompositeDocumentStructureGraphService 会回退到本类
 *   - 适用于文档结构节点较少的场景（几百个节点以内）
 *
 * 所有方法都做了 null 安全处理，参数为空时返回 null 或空列表，不会抛异常。
 */
@AllArgsConstructor
@Service("mysqlDocumentStructureGraphService")
public class MysqlDocumentStructureGraphService implements DocumentStructureGraphService {

    /** 结构节点服务，用于从 MySQL 加载节点数据 */
    private final DocumentStructureNodeService documentStructureNodeService;

    /**
     * 根据节点ID查找章节节点
     * @param documentId 文档ID
     * @param sectionNodeId 章节节点ID
     * @return 章节信息
     */
    @Override
    public GraphSection findSectionById(Long documentId, Long sectionNodeId) {
        if (documentId == null || sectionNodeId == null) {
            return null;
        }
        return loadSectionMap(documentId).get(sectionNodeId);
    }

    /**
     * 根据节点编码查找章节
     */
    @Override
    public GraphSection findSectionByCode(Long documentId, String nodeCode) {
        if (documentId == null || StrUtil.isBlank(nodeCode)) {
            return null;
        }
        return listSections(documentId).stream()
            .filter(section -> nodeCode.trim().equals(section.getNodeCode()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 根据标题文本查找章节（支持标题、锚文本、路径的归一化匹配）
     */
    @Override
    public GraphSection findSectionByTitle(Long documentId, String title) {
        if (documentId == null || StrUtil.isBlank(title)) {
            return null;
        }
        String normalized = normalize(title);
        return listSections(documentId).stream()
            .filter(section -> normalized.equals(normalize(section.getTitle()))
                || normalized.equals(normalize(section.getAnchorText()))
                || normalized.equals(normalize(section.getSectionPath())))
            .findFirst()
            .orElse(null);
    }

    /**
     * 根据规范路径精确查找章节
     */
    @Override
    public GraphSection findSectionByCanonicalPath(Long documentId, String canonicalPath) {
        if (documentId == null || StrUtil.isBlank(canonicalPath)) {
            return null;
        }
        return listSections(documentId).stream()
            .filter(section -> canonicalPath.trim().equals(section.getCanonicalPath()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 根据主题和侧面关键词，通过评分机制找到最佳匹配的章节。
     *
     * 评分规则：
     *   - topic 出现在标题或路径中：+8 分
     *   - topic 出现在锚文本中：+6 分
     *   - topic 出现在内容中：+2 分
     *   - facet 出现在标题或路径中：+5 分
     *   - facet 出现在内容中：+1 分
     */
    @Override
    public GraphSection findBestSection(Long documentId, String topic, String facet) {
        String normalizedTopic = normalize(topic);
        String normalizedFacet = normalize(facet);
        if (documentId == null || (normalizedTopic.isBlank() && normalizedFacet.isBlank())) {
            return null;
        }
        GraphSection bestSection = null;
        int bestScore = 0;
        for (GraphSection section : listSections(documentId)) {
            int score = 0;
            String sectionPath = normalize(section.getSectionPath());
            String title = normalize(section.getTitle());
            String anchorText = normalize(section.getAnchorText());
            String content = normalize(section.getContentText());
            if (StrUtil.isNotBlank(normalizedTopic)) {
                if (title.contains(normalizedTopic) || sectionPath.contains(normalizedTopic)) {
                    score += 8;
                }
                else if (anchorText.contains(normalizedTopic)) {
                    score += 6;
                }
                else if (content.contains(normalizedTopic)) {
                    score += 2;
                }
            }
            if (StrUtil.isNotBlank(normalizedFacet)) {
                if (title.contains(normalizedFacet) || sectionPath.contains(normalizedFacet)) {
                    score += 5;
                }
                else if (content.contains(normalizedFacet)) {
                    score += 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestSection = section;
            }
        }
        return bestScore > 0 ? bestSection : null;
    }

    /**
     * 列出文档的所有章节节点（按 nodeNo 排序）
     */
    @Override
    public List<GraphSection> listSections(Long documentId) {
        return loadSections(documentId);
    }

    /**
     * 列出指定章节的直接子章节
     */
    @Override
    public List<GraphSection> listChildren(Long documentId, Long sectionNodeId) {
        if (documentId == null || sectionNodeId == null) {
            return List.of();
        }
        return listSections(documentId).stream()
            .filter(section -> Objects.equals(sectionNodeId, section.getParentNodeId()))
            .sorted(Comparator.comparing(GraphSection::getNodeNo, Comparator.nullsLast(Integer::compareTo)))
            .toList();
    }

    /**
     * 获取父章节
     */
    @Override
    public GraphSection parentSection(Long documentId, Long sectionNodeId) {
        GraphSection section = findSectionById(documentId, sectionNodeId);
        if (section == null || section.getParentNodeId() == null) {
            return null;
        }
        return findSectionById(documentId, section.getParentNodeId());
    }

    /**
     * 获取前一个兄弟章节
     */
    @Override
    public GraphSection previousSibling(Long documentId, Long sectionNodeId) {
        GraphSection section = findSectionById(documentId, sectionNodeId);
        if (section == null || section.getPrevSiblingNodeId() == null) {
            return null;
        }
        return findSectionById(documentId, section.getPrevSiblingNodeId());
    }

    /**
     * 获取后一个兄弟章节
     */
    @Override
    public GraphSection nextSibling(Long documentId, Long sectionNodeId) {
        GraphSection section = findSectionById(documentId, sectionNodeId);
        if (section == null || section.getNextSiblingNodeId() == null) {
            return null;
        }
        return findSectionById(documentId, section.getNextSiblingNodeId());
    }

    /**
     * 根据条目索引查找列表项/步骤
     */
    @Override
    public GraphItem findItemByIndex(Long documentId, Long sectionNodeId, Integer itemIndex) {
        if (documentId == null || sectionNodeId == null || itemIndex == null) {
            return null;
        }
        return listItems(documentId, sectionNodeId).stream()
            .filter(item -> Objects.equals(itemIndex, item.getItemIndex()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 列出指定章节下的所有条目
     */
    @Override
    public List<GraphItem> listItems(Long documentId, Long sectionNodeId) {
        if (documentId == null || sectionNodeId == null) {
            return List.of();
        }
        return loadItems(documentId).stream()
            .filter(item -> Objects.equals(sectionNodeId, item.getSectionNodeId()))
            .sorted(Comparator.comparing(GraphItem::getNodeNo, Comparator.nullsLast(Integer::compareTo)))
            .toList();
    }

    /**
     * 在指定章节下按关键词搜索条目
     */
    @Override
    public List<GraphItem> searchItemsInSection(Long documentId, Long sectionNodeId, String keyword) {
        List<GraphItem> items = listItems(documentId, sectionNodeId);
        if (items.isEmpty()) {
            return List.of();
        }
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isBlank()) {
            return items;
        }
        List<GraphItem> matched = new ArrayList<>();
        for (GraphItem item : items) {
            String haystack = normalize(String.join(" ",
                StrUtil.blankToDefault(item.getTitle(), ""),
                StrUtil.blankToDefault(item.getAnchorText(), ""),
                StrUtil.blankToDefault(item.getContentText(), "")
            ));
            if (haystack.contains(normalizedKeyword)) {
                matched.add(item);
            }
        }
        return matched;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 加载章节节点的 Map（nodeId -> section）
     */
    private Map<Long, GraphSection> loadSectionMap(Long documentId) {
        Map<Long, GraphSection> sectionMap = new LinkedHashMap<>();
        for (GraphSection section : loadSections(documentId)) {
            sectionMap.put(section.getNodeId(), section);
        }
        return sectionMap;
    }

    /**
     * 从 MySQL 加载文档的所有章节节点并转为 GraphSection
     */
    private List<GraphSection> loadSections(Long documentId) {
        if (documentId == null) {
            return List.of();
        }
        return documentStructureNodeService.listDocumentNodes(documentId, null).stream()
            .filter(node -> node != null && Objects.equals(DocumentStructureNodeTypeEnum.SECTION.getCode(), node.getNodeType()))
            .sorted(Comparator.comparing(KnowHubDocumentStructureNode::getNodeNo, Comparator.nullsLast(Integer::compareTo)))
            .map(this::toGraphSection)
            .toList();
    }

    /**
     * 从 MySQL 加载文档的所有条目节点（STEP 和 LIST_ITEM）并转为 GraphItem
     */
    private List<GraphItem> loadItems(Long documentId) {
        if (documentId == null) {
            return List.of();
        }
        return documentStructureNodeService.listDocumentNodes(documentId, null).stream()
            .filter(node -> node != null
                && (Objects.equals(DocumentStructureNodeTypeEnum.STEP.getCode(), node.getNodeType())
                || Objects.equals(DocumentStructureNodeTypeEnum.LIST_ITEM.getCode(), node.getNodeType())))
            .sorted(Comparator.comparing(KnowHubDocumentStructureNode::getNodeNo, Comparator.nullsLast(Integer::compareTo)))
            .map(this::toGraphItem)
            .toList();
    }

    /** 将数据库实体转为 GraphSection */
    private GraphSection toGraphSection(KnowHubDocumentStructureNode node) {
        return GraphSection.builder()
            .nodeId(node.getId())
            .documentId(node.getDocumentId())
            .parseTaskId(node.getParseTaskId())
            .nodeNo(node.getNodeNo())
            .depth(node.getDepth())
            .parentNodeId(node.getParentNodeId())
            .prevSiblingNodeId(node.getPrevSiblingNodeId())
            .nextSiblingNodeId(node.getNextSiblingNodeId())
            .nodeCode(safeText(node.getNodeCode()))
            .title(safeText(node.getTitle()))
            .anchorText(safeText(node.getAnchorText()))
            .sectionPath(safeText(node.getSectionPath()))
            .canonicalPath(safeText(node.getCanonicalPath()))
            .contentText(safeText(node.getContentText()))
            .build();
    }

    /** 将数据库实体转为 GraphItem */
    private GraphItem toGraphItem(KnowHubDocumentStructureNode node) {
        DocumentStructureNodeTypeEnum nodeTypeEnum = DocumentStructureNodeTypeEnum.getRc(node.getNodeType());
        return GraphItem.builder()
            .nodeId(node.getId())
            .documentId(node.getDocumentId())
            .parseTaskId(node.getParseTaskId())
            .nodeNo(node.getNodeNo())
            .nodeType(nodeTypeEnum == null ? "" : nodeTypeEnum.name())
            .sectionNodeId(node.getParentNodeId())
            .prevSiblingNodeId(node.getPrevSiblingNodeId())
            .nextSiblingNodeId(node.getNextSiblingNodeId())
            .title(safeText(node.getTitle()))
            .anchorText(safeText(node.getAnchorText()))
            .sectionPath(safeText(node.getSectionPath()))
            .canonicalPath(safeText(node.getCanonicalPath()))
            .contentText(safeText(node.getContentText()))
            .itemIndex(node.getItemIndex())
            .build();
    }

    /**
     * 文本归一化：去除空白、特殊字符，转小写
     * 用于模糊匹配时消除格式差异
     */
    private String normalize(String text) {
        return safeText(text)
            .replaceAll("[\\s>`*#_\\-]+", "")
            .toLowerCase(Locale.ROOT);
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
