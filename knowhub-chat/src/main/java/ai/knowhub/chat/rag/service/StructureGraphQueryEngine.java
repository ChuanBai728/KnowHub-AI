package ai.knowhub.chat.rag.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.model.graph.GraphItem;
import ai.knowhub.document.model.graph.GraphItemWithContext;
import ai.knowhub.document.model.graph.GraphQueryResult;
import ai.knowhub.document.model.graph.GraphSection;
import ai.knowhub.document.model.graph.GraphSectionWithChildren;
import ai.knowhub.document.model.graph.GraphSectionWithSiblings;
import ai.knowhub.document.service.DocumentStructureGraphService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 【结构图查询引擎 — RAG 流水线的"图数据库查询层"】
 *
 * 这个类负责查询文档的结构图（存储在 Neo4j 图数据库中），
 * 提供章节的层级关系、相邻关系、编号项等查询能力。
 *
 * 文档结构图是什么？
 * 每份文档被解析后，会生成一棵章节树（类似目录结构）：
 * - 根节点：文档
 * - 一级子节点：章
 * - 二级子节点：节
 * - 三级子节点：小节
 * - 叶子节点：编号项（步骤、条款等）
 *
 * 这棵树存储在 Neo4j 图数据库中，支持高效的层级查询和关系遍历。
 *
 * 提供的查询能力：
 * 1. findSectionWithChildren：查找章节及其子章节（目录展开）
 * 2. findSectionWithSiblings：查找章节及其相邻章节（上一节/下一节）
 * 3. findItemInSection：查找章节内的编号项（第几步/第几条）
 * 4. searchItemsInSection：在章节内搜索编号项（关键词匹配）
 * 5. buildGraphResult：构建完整的图查询结果（汇总以上能力）
 *
 * 设计模式：门面模式（Facade Pattern），封装了 DocumentStructureGraphService 的复杂查询。
 *
 * 在 RAG 流水线中的位置：
 * DocumentQuestionRouter（路由决策）-> 【本类：图查询】-> GraphAnswerRenderer（渲染结果）
 */
@Service
@Slf4j
public class StructureGraphQueryEngine {

    /** 文档结构图服务（底层的 Neo4j 查询服务） */
    private final DocumentStructureGraphService graphService;

    /**
     * 构造函数。
     *
     * @param graphService 文档结构图服务
     */
    public StructureGraphQueryEngine(DocumentStructureGraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * 根据主题词查找章节及其子章节。
     *
     * 先通过主题词找到最佳匹配的章节，再查询其子章节列表。
     * 用于回答"XX 包含哪些小节？"这类问题。
     *
     * @param documentId 文档ID
     * @param topic      主题词（如"部署流程"）
     * @return 章节及其子章节
     */
    public GraphSectionWithChildren findSectionWithChildren(Long documentId, String topic) {
        GraphSection section = graphService.findBestSection(documentId, topic, "");
        return findSectionWithChildren(documentId, section == null ? null : section.getNodeId());
    }

    /**
     * 根据章节节点ID查找章节及其子章节。
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @return 章节及其子章节
     */
    public GraphSectionWithChildren findSectionWithChildren(Long documentId, Long sectionNodeId) {
        GraphSection section = graphService.findSectionById(documentId, sectionNodeId);
        List<GraphSection> children = section == null ? List.of() : graphService.listChildren(documentId, section.getNodeId());
        log.info("结构图查询子章节: documentId={}, sectionNodeId={}, targetSection='{}', childCount={}",
            documentId,
            sectionNodeId,
            section == null ? "" : section.displayTitle(),
            children.size());
        return GraphSectionWithChildren.builder()
            .section(section)
            .children(children)
            .build();
    }

    /**
     * 查找章节及其相邻章节（上一节/下一节/父章节）。
     *
     * 用于回答"XX 的上一节是什么？"这类问题。
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @return 章节及其相邻章节
     */
    public GraphSectionWithSiblings findSectionWithSiblings(Long documentId, Long sectionNodeId) {
        GraphSection section = graphService.findSectionById(documentId, sectionNodeId);
        GraphSection parent = section == null ? null : graphService.parentSection(documentId, section.getNodeId());
        GraphSection previousSibling = section == null ? null : graphService.previousSibling(documentId, section.getNodeId());
        GraphSection nextSibling = section == null ? null : graphService.nextSibling(documentId, section.getNodeId());
        log.info("结构图查询相邻章节: documentId={}, sectionNodeId={}, targetSection='{}', parent='{}', previous='{}', next='{}'",
            documentId,
            sectionNodeId,
            section == null ? "" : section.displayTitle(),
            parent == null ? "" : parent.displayTitle(),
            previousSibling == null ? "" : previousSibling.displayTitle(),
            nextSibling == null ? "" : nextSibling.displayTitle());
        return GraphSectionWithSiblings.builder()
            .section(section)
            .parent(parent)
            .previousSibling(previousSibling)
            .nextSibling(nextSibling)
            .build();
    }

    /**
     * 根据主题词和编号索引查找章节内的编号项。
     *
     * 先通过主题词定位章节，再在章节内查找指定编号的项。
     * 用于回答"部署流程的第三步是什么？"这类问题。
     *
     * @param documentId 文档ID
     * @param sectionTopic 章节主题词
     * @param itemIndex    编号项索引（如 3 表示第三步）
     * @return 编号项及其上下文（所属章节、同级编号项等）
     */
    public GraphItemWithContext findItemInSection(Long documentId, String sectionTopic, Integer itemIndex) {
        GraphSection section = graphService.findBestSection(documentId, sectionTopic, "");
        return findItemInSection(documentId, section == null ? null : section.getNodeId(), itemIndex);
    }

    /**
     * 根据章节节点ID和编号索引查找章节内的编号项。
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @param itemIndex     编号项索引
     * @return 编号项及其上下文
     */
    public GraphItemWithContext findItemInSection(Long documentId, Long sectionNodeId, Integer itemIndex) {
        GraphSection section = graphService.findSectionById(documentId, sectionNodeId);
        // 在章节树中递归查找编号项
        GraphItem item = findItemInSectionTree(documentId, sectionNodeId, itemIndex);
        // 获取同级编号项列表
        List<GraphItem> siblingItems = sectionNodeId == null ? List.of() : listItemsInSectionTree(documentId, sectionNodeId);
        log.info("结构图查询编号项: documentId={}, sectionNodeId={}, itemIndex={}, targetSection='{}', itemFound={}, siblingItemCount={}",
            documentId,
            sectionNodeId,
            itemIndex,
            section == null ? "" : section.displayTitle(),
            item != null,
            siblingItems.size());
        return GraphItemWithContext.builder()
            .section(section)
            .item(item)
            .siblingItems(siblingItems)
            .build();
    }

    /**
     * 在章节内搜索编号项（关键词匹配）。
     *
     * 用于回答"部署流程中有哪些步骤？"这类问题。
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @param keyword       搜索关键词
     * @return 匹配的编号项列表
     */
    public List<GraphItem> searchItemsInSection(Long documentId, Long sectionNodeId, String keyword) {
        List<GraphItem> items = searchItemsInSectionTree(documentId, sectionNodeId, StrUtil.blankToDefault(keyword, ""));
        log.info("结构图搜索章节内 item: documentId={}, sectionNodeId={}, keyword='{}', matchedCount={}",
            documentId,
            sectionNodeId,
            StrUtil.blankToDefault(keyword, ""),
            items.size());
        return items;
    }

    /**
     * 构建完整的图查询结果。
     *
     * 汇总以下信息：
     * - 目标章节
     * - 子章节列表
     * - 所有编号项
     * - 目标编号项（如果有）
     * - 匹配的编号项（如果有关键词搜索）
     * - 父章节、相邻章节
     *
     * @param documentId         文档ID
     * @param targetSectionNodeId 目标章节节点ID
     * @param targetItemIndex    目标编号项索引（可为 null）
     * @param itemKeyword        编号项搜索关键词（可为 null）
     * @return 完整的图查询结果
     */
    public GraphQueryResult buildGraphResult(Long documentId,
                                             Long targetSectionNodeId,
                                             Integer targetItemIndex,
                                             String itemKeyword) {
        GraphSection section = graphService.findSectionById(documentId, targetSectionNodeId);
        List<GraphSection> children = section == null ? List.of() : graphService.listChildren(documentId, section.getNodeId());
        List<GraphItem> allItems = section == null ? List.of() : listItemsInSectionTree(documentId, section.getNodeId());
        GraphItem targetItem = targetItemIndex == null || section == null
            ? null
            : findItemInSectionTree(documentId, section.getNodeId(), targetItemIndex);
        List<GraphItem> matchedItems = StrUtil.isNotBlank(itemKeyword) && section != null
            ? searchItemsInSection(documentId, section.getNodeId(), itemKeyword)
            : List.of();
        // 如果目标编号项属于子章节，更新 resolvedSection
        GraphSection resolvedSection = section;
        if (targetItem != null && targetItem.getSectionNodeId() != null) {
            GraphSection itemOwnerSection = graphService.findSectionById(documentId, targetItem.getSectionNodeId());
            if (itemOwnerSection != null) {
                resolvedSection = itemOwnerSection;
            }
        }
        else if (matchedItems.size() == 1 && matchedItems.get(0).getSectionNodeId() != null) {
            GraphSection itemOwnerSection = graphService.findSectionById(documentId, matchedItems.get(0).getSectionNodeId());
            if (itemOwnerSection != null) {
                resolvedSection = itemOwnerSection;
                targetItem = matchedItems.get(0);
            }
        }
        GraphQueryResult.GraphQueryResultBuilder builder = GraphQueryResult.builder()
            .targetSection(resolvedSection)
            .children(children)
            .allItems(allItems)
            .targetItem(targetItem)
            .matchedItems(matchedItems);
        if (resolvedSection != null) {
            builder.parentSection(graphService.parentSection(documentId, resolvedSection.getNodeId()))
                .previousSibling(graphService.previousSibling(documentId, resolvedSection.getNodeId()))
                .nextSibling(graphService.nextSibling(documentId, resolvedSection.getNodeId()));
        }
        GraphQueryResult result = builder.build();
        log.info("结构图结果汇总: documentId={}, targetSectionNodeId={}, targetItemIndex={}, itemKeyword='{}', targetSection='{}', targetItemFound={}, childCount={}, allItemCount={}, matchedItemCount={}",
            documentId,
            targetSectionNodeId,
            targetItemIndex,
            StrUtil.blankToDefault(itemKeyword, ""),
            result.getTargetSection() == null ? "" : result.getTargetSection().displayTitle(),
            result.getTargetItem() != null,
            result.getChildren() == null ? 0 : result.getChildren().size(),
            result.getAllItems() == null ? 0 : result.getAllItems().size(),
            result.getMatchedItems() == null ? 0 : result.getMatchedItems().size());
        return result;
    }

    /**
     * 在章节树中递归查找指定编号的编号项。
     *
     * 先在当前章节查找，找不到则递归到子章节中查找。
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @param itemIndex     编号项索引
     * @return 找到的编号项，未找到返回 null
     */
    private GraphItem findItemInSectionTree(Long documentId, Long sectionNodeId, Integer itemIndex) {
        if (documentId == null || sectionNodeId == null || itemIndex == null) {
            return null;
        }
        GraphItem item = graphService.findItemByIndex(documentId, sectionNodeId, itemIndex);
        if (item != null) {
            return item;
        }
        // 递归查找子章节
        for (GraphSection child : graphService.listChildren(documentId, sectionNodeId)) {
            GraphItem descendant = findItemInSectionTree(documentId, child.getNodeId(), itemIndex);
            if (descendant != null) {
                return descendant;
            }
        }
        return null;
    }

    /**
     * 递归列出章节树中的所有编号项。
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @return 按 nodeNo 排序的所有编号项
     */
    private List<GraphItem> listItemsInSectionTree(Long documentId, Long sectionNodeId) {
        if (documentId == null || sectionNodeId == null) {
            return List.of();
        }
        List<GraphItem> items = new ArrayList<>(graphService.listItems(documentId, sectionNodeId));
        // 递归收集子章节的编号项
        for (GraphSection child : graphService.listChildren(documentId, sectionNodeId)) {
            items.addAll(listItemsInSectionTree(documentId, child.getNodeId()));
        }
        return items.stream()
            .sorted(Comparator.comparing(GraphItem::getNodeNo, Comparator.nullsLast(Integer::compareTo)))
            .toList();
    }

    /**
     * 在章节树中递归搜索匹配关键词的编号项。
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @param keyword       搜索关键词
     * @return 去重后按 nodeNo 排序的匹配编号项
     */
    private List<GraphItem> searchItemsInSectionTree(Long documentId, Long sectionNodeId, String keyword) {
        if (documentId == null || sectionNodeId == null) {
            return List.of();
        }
        List<GraphItem> items = new ArrayList<>(graphService.searchItemsInSection(documentId, sectionNodeId, keyword));
        // 递归搜索子章节
        for (GraphSection child : graphService.listChildren(documentId, sectionNodeId)) {
            items.addAll(searchItemsInSectionTree(documentId, child.getNodeId(), keyword));
        }
        return items.stream()
            .distinct()
            .sorted(Comparator.comparing(GraphItem::getNodeNo, Comparator.nullsLast(Integer::compareTo)))
            .toList();
    }
}
