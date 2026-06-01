package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocumentTaskLog;

/**
 * 文档任务日志（Task Log）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document_task_log表的数据库操作能力。
 * 文档任务日志记录了文档处理任务执行过程中的每一步操作详情，
 * 包括执行状态、耗时、错误信息等，是任务监控和问题排查的重要数据来源。
 *
 * 与 KnowHubDocumentTaskMapper 的关系：
 * 一个Task（任务）可以产生多条TaskLog（日志），日志按时间顺序记录
 * 任务的执行过程。例如一次索引构建任务的日志可能包括：
 * "开始切分文档" -> "切分完成，共100个chunk" -> "开始向量化" -> "向量化完成"。
 *
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubDocumentTaskLog
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubDocumentTaskLogMapper extends BaseMapper<KnowHubDocumentTaskLog> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
