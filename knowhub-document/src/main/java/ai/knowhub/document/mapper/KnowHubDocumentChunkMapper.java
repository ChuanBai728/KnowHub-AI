package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocumentChunk;

/**
 * 文档分块（Chunk）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document_chunk表的数据库操作能力。
 * 文档分块（Chunk）是RAG系统中对文档进行切分后的最小检索单元。
 * 文档经过处理后会被切分成多个chunk，每个chunk会被向量化存储到向量数据库中，
 * 以便后续进行语义检索。
 *
 * 在RAG架构中的位置：文档(Document) -> 处理任务(Task) -> 分块列表(Chunk List)
 * 每个chunk包含原始文本内容和对应的向量表示。
 *
 * 设计模式：Mapper层（数据访问对象模式，DAO）。
 * 继承MyBatis-Plus的BaseMapper，自动获得CRUD（增删改查）通用方法，
 * 无需手写SQL即可完成基本的数据库操作。
 *
 * BaseMapper提供的常用方法包括：
 * 
 *   insert(T entity) —— 新增一条记录
 *   deleteById(Serializable id) —— 根据主键删除
 *   updateById(T entity) —— 根据主键更新
 *   selectById(Serializable id) —— 根据主键查询
 *   selectList(Wrapper<T> queryWrapper) —— 条件查询列表
 *   selectPage(Page<T> page, Wrapper<T> queryWrapper) —— 分页查询
 * 对应的数据实体类：KnowHubDocumentChunk
 */
@Mapper  // MyBatis注解：标记这是一个Mapper接口，Spring会自动将其注册为Bean并生成代理实现
public interface KnowHubDocumentChunkMapper extends BaseMapper<KnowHubDocumentChunk> {
    // 继承BaseMapper已提供了完整的CRUD方法，无需额外定义
    // 如需自定义SQL查询，可在此接口中定义方法，并在XML映射文件中编写SQL
}
