package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubKnowledgeScopeNode;

/**
 * 知识域节点（Scope Node）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_knowledge_scope_node表的数据库操作能力。
 * 知识域节点是知识域（Knowledge Scope）在图结构中的表示形式，
 * 用于支持基于图的知识检索（Graph RAG）。
 *
 * 知识域层级体系说明：
 * 知识域是RAG知识库的顶层分类，形成树状或图状结构。例如：
 * 
 *   技术文档（顶级域）
 *     
 *       Java技术（子域）
 *       Python技术（子域）
 *   产品手册（顶级域）
 * 
 * 本Mapper操作的知识域节点表，存储了这些域的结构化信息。
 *
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubKnowledgeScopeNode
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubKnowledgeScopeNodeMapper extends BaseMapper<KnowHubKnowledgeScopeNode> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
