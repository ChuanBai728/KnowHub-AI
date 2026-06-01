package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocumentParentBlock;

/**
 * 文档父块（Parent Block）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document_parent_block表的数据库操作能力。
 * 在RAG的文档处理流水线中，文档首先被切分为较大的"父块"（Parent Block），
 * 然后父块再被进一步切分为较小的"子块"（Child Block/Chunk）。
 *
 * 两级切分架构说明：
 * 
 *   父块（Parent Block）：较大的文本段落，通常保留完整的上下文语义
 *   子块（Chunk）：较小的文本片段，用于向量化和精确检索
 *   检索时先命中子块，再回溯到父块获取更完整的上下文
 * 
 * 这种两级切分策略可以在保证检索精度的同时，提供更丰富的上下文信息。
 *
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubDocumentParentBlock
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubDocumentParentBlockMapper extends BaseMapper<KnowHubDocumentParentBlock> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
