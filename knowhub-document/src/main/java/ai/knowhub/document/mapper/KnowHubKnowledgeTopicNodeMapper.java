package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubKnowledgeTopicNode;

/**
 * 知识主题节点（Topic Node）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_knowledge_topic_node表的数据库操作能力。
 * 知识主题节点是知识主题（Knowledge Topic）在图结构中的表示形式，
 * 用于支持基于图的知识检索（Graph RAG）。
 *
 * 知识主题是知识域下的细分分类，层级关系为：
 * 知识域(Scope) -> 知识主题(Topic) -> 文档(Document)
 * 
 *   技术文档（知识域）
 *     
 *       Spring Boot（主题节点）-> 关联多个Spring Boot相关文档
 *       MyBatis（主题节点）-> 关联多个MyBatis相关文档
 *       Redis（主题节点）-> 关联多个Redis相关文档
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubKnowledgeTopicNode
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubKnowledgeTopicNodeMapper extends BaseMapper<KnowHubKnowledgeTopicNode> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
