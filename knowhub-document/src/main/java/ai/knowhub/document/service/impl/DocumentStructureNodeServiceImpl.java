package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.mapper.KnowHubDocumentStructureNodeMapper;
import ai.knowhub.document.service.DocumentStructureNodeService;
import ai.knowhub.document.support.DocumentStructureNodeCandidate;
import ai.knowhub.enums.BusinessStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【文档结构节点服务实现】
 *
 * 这个类负责管理文档的结构节点（StructureNode），即文档解析后提取出的
 * 标题、章节、列表项、步骤等结构化元素。
 *
 * 结构节点的作用：
 *   - 驱动结构切块：根据标题/章节边界切分文档
 *   - 支撑图查询：投影到 Neo4j 后，可通过图遍历找到父子、兄弟关系
 *   - 精确定位：检索命中某个 chunk 时，可以通过 structureNodeId 定位到具体章节
 *   - 导航索引：同步到 Elasticsearch 支持章节级的导航搜索
 *
 * 节点类型（DocumentStructureNodeTypeEnum）：
 *   - SECTION：章节/标题节点，有层级深度（depth）
 *   - STEP：步骤节点，属于某个章节
 *   - LIST_ITEM：列表项节点，属于某个章节
 *
 * 节点之间的关系通过以下字段维护：
 *   - parentNodeId：父节点ID（用于构建树形结构）
 *   - prevSiblingNodeId / nextSiblingNodeId：前后兄弟节点ID（用于构建链表结构）
 *
 * 注意：replaceDocumentNodes 方法采用"先删后插"的全量替换策略，
 * 每次解析都会重建该文档的所有结构节点。
 */
@AllArgsConstructor
@Service
public class DocumentStructureNodeServiceImpl implements DocumentStructureNodeService {

    /** 结构节点 Mapper */
    private final KnowHubDocumentStructureNodeMapper structureNodeMapper;

    /** UID 生成器，用于生成全局唯一 ID */
    private final UidGenerator uidGenerator;

    /**
     * 全量替换文档的结构节点。
     *
     * 处理逻辑（两遍遍历）：
     *   第一遍：为所有候选节点生成唯一 ID，建立 nodeNo -> ID 的映射
     *   第二遍：根据候选节点信息创建实体，设置父子、兄弟关系（通过映射表转换 nodeNo 为 ID）
     *
     * @param documentId   文档ID
     * @param parseTaskId  解析任务ID
     * @param candidates   解析器提取的候选节点列表
     * @return 最终创建的结构节点实体列表
     */
    @Override
    public List<KnowHubDocumentStructureNode> replaceDocumentNodes(Long documentId,
                                                                      Long parseTaskId,
                                                                      List<DocumentStructureNodeCandidate> candidates) {
        // 先删除该文档的所有旧结构节点
        deleteByDocumentId(documentId);
        if (documentId == null || parseTaskId == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // 第一遍：为所有候选节点分配唯一 ID，建立 nodeNo -> ID 的映射表
        Map<Integer, Long> nodeIdMap = new LinkedHashMap<>();
        List<KnowHubDocumentStructureNode> entities = new ArrayList<>();
        for (DocumentStructureNodeCandidate candidate : candidates) {
            if (candidate == null || candidate.getNodeNo() == null) {
                continue;
            }
            long id = uidGenerator.getUid();
            nodeIdMap.put(candidate.getNodeNo(), id);
        }

        // 第二遍：创建实体并设置关系（parentNodeNo -> parentNodeId 等）
        for (DocumentStructureNodeCandidate candidate : candidates) {
            if (candidate == null || candidate.getNodeNo() == null) {
                continue;
            }
            KnowHubDocumentStructureNode entity = new KnowHubDocumentStructureNode();
            entity.setId(nodeIdMap.get(candidate.getNodeNo()));
            entity.setDocumentId(documentId);
            entity.setParseTaskId(parseTaskId);
            entity.setNodeNo(candidate.getNodeNo());
            entity.setNodeType(candidate.getNodeType());
            // 将 nodeNo 引用转换为实际的数据库 ID
            entity.setParentNodeId(candidate.getParentNodeNo() == null ? null : nodeIdMap.get(candidate.getParentNodeNo()));
            entity.setPrevSiblingNodeId(candidate.getPrevSiblingNodeNo() == null ? null : nodeIdMap.get(candidate.getPrevSiblingNodeNo()));
            entity.setNextSiblingNodeId(candidate.getNextSiblingNodeNo() == null ? null : nodeIdMap.get(candidate.getNextSiblingNodeNo()));
            entity.setDepth(candidate.getDepth());
            entity.setNodeCode(candidate.getNodeCode());
            entity.setTitle(candidate.getTitle());
            entity.setAnchorText(candidate.getAnchorText());
            entity.setCanonicalPath(candidate.getCanonicalPath());
            entity.setSectionPath(candidate.getSectionPath());
            entity.setContentText(candidate.getContentText());
            entity.setItemIndex(candidate.getItemIndex());
            entity.setStatus(BusinessStatus.YES.getCode());
            structureNodeMapper.insert(entity);
            entities.add(entity);
        }
        return entities;
    }

    /**
     * 查询文档的所有结构节点（按 nodeNo 排序）
     *
     * @param documentId  文档ID
     * @param parseTaskId 解析任务ID（可选，为 null 时不过滤）
     * @return 结构节点列表
     */
    @Override
    public List<KnowHubDocumentStructureNode> listDocumentNodes(Long documentId, Long parseTaskId) {
        if (documentId == null) {
            return List.of();
        }
        LambdaQueryWrapper<KnowHubDocumentStructureNode> wrapper = new LambdaQueryWrapper<KnowHubDocumentStructureNode>()
            .eq(KnowHubDocumentStructureNode::getDocumentId, documentId)
            .eq(KnowHubDocumentStructureNode::getStatus, BusinessStatus.YES.getCode())
            .orderByAsc(KnowHubDocumentStructureNode::getNodeNo);
        if (parseTaskId != null) {
            wrapper.eq(KnowHubDocumentStructureNode::getParseTaskId, parseTaskId);
        }
        return structureNodeMapper.selectList(wrapper);
    }

    /**
     * 获取文档结构节点的 Map（nodeId -> node），方便通过 ID 快速查找
     *
     * @param documentId  文档ID
     * @param parseTaskId 解析任务ID（可选）
     * @return 以节点ID为 key 的 Map
     */
    @Override
    public Map<Long, KnowHubDocumentStructureNode> nodeMap(Long documentId, Long parseTaskId) {
        Map<Long, KnowHubDocumentStructureNode> result = new LinkedHashMap<>();
        for (KnowHubDocumentStructureNode node : listDocumentNodes(documentId, parseTaskId)) {
            result.put(node.getId(), node);
        }
        return result;
    }

    /**
     * 删除文档的所有结构节点（物理删除）
     *
     * @param documentId 文档ID
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        structureNodeMapper.delete(new LambdaQueryWrapper<KnowHubDocumentStructureNode>()
            .eq(KnowHubDocumentStructureNode::getDocumentId, documentId));
    }
}
