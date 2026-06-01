package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;

/**
 * 文档结构节点（Structure Node）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document_structure_node表的数据库操作能力。
 * 文档结构节点用于表示文档的结构化层级信息，例如标题、章节、段落等
 * 构成的树状结构。
 *
 * 在RAG系统中的作用：
 * 
 *   记录文档的逻辑结构（目录树），帮助理解文档的组织方式
 *   辅助基于结构的检索（如"在某个章节下查找相关内容"）
 *   为图结构查询（Graph RAG）提供节点数据
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubDocumentStructureNode
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubDocumentStructureNodeMapper extends BaseMapper<KnowHubDocumentStructureNode> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
