package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocumentTask;

/**
 * 文档处理任务（Task）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document_task表的数据库操作能力。
 * 文档处理任务是文档索引构建等异步操作的执行记录，每次触发文档处理
 * （如构建索引、重新生成画像等）都会创建一个Task记录。
 *
 * 任务管理机制说明：
 * 
 *   文档处理是耗时操作，通常采用异步方式执行
 *   Task记录任务的整体状态（待执行、执行中、成功、失败等）
 *   TaskLog记录任务执行过程中的详细步骤日志
 *   管理端可以通过查询Task和TaskLog来监控处理进度
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubDocumentTask
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubDocumentTaskMapper extends BaseMapper<KnowHubDocumentTask> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
