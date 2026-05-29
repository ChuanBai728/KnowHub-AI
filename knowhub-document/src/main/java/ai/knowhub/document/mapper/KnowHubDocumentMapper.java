package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocument;

/**
 * 文档（Document）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document表的数据库操作能力。
 * 文档是RAG知识库的核心数据载体，管理端上传的每一份文档（PDF、Word、Markdown等）
 * 都会在该表中创建一条记录，记录文档的基本信息（名称、状态、所属知识域等）。
 *
 * 在RAG架构中的位置：文档是知识库的入口，文档上传后经过策略规划、
 * 索引构建等流程，最终被切分成chunk存储以供检索。
 *
 * 设计模式：Mapper层（数据访问对象模式，DAO）。
 * 继承MyBatis-Plus的BaseMapper，自动获得CRUD通用方法。
 *
 * 对应的数据实体类：KnowHubDocument
 */
@Mapper  // MyBatis注解：标记这是一个Mapper接口，Spring容器会自动扫描并注册
public interface KnowHubDocumentMapper extends BaseMapper<KnowHubDocument> {
    // 继承BaseMapper已提供了完整的CRUD方法，无需额外定义
}
