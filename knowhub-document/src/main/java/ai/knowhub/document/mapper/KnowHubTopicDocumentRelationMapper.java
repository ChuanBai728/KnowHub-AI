package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubTopicDocumentRelation;

/**
 * 主题-文档关联关系（Topic-Document Relation）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_topic_document_relation表的数据库操作能力。
 * 该表存储知识主题（Topic）和文档（Document）之间的多对多关联关系。
 *
 * 关联关系说明：
 * 
 *   一个知识主题可以关联多个文档（一对多）
 *   一个文档也可以属于多个主题（多对一）
 *   因此整体是多对多关系，通过本关联表实现
 * 
 * 关联时还可以记录关联分数（relationScore）、关联来源（relationSource）
 * 和关联原因（reason）等附加信息。
 *
 * 在RAG检索中的作用：
 * 当用户查询被路由到某个主题后，系统通过本关联表找到该主题下的所有文档，
 * 然后在这些文档中进行精确检索，从而提高检索的准确性和相关性。
 *
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubTopicDocumentRelation
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubTopicDocumentRelationMapper extends BaseMapper<KnowHubTopicDocumentRelation> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
