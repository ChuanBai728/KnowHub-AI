package ai.knowhub.chat.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ai.knowhub.chat.data.GraphCheckpoint;
import ai.knowhub.chat.data.GraphThread;
import ai.knowhub.chat.mapper.GraphCheckpointMapper;
import ai.knowhub.chat.mapper.GraphThreadMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 【聊天检查点管理器】
 *
 * 管理 Spring AI Alibaba Graph 框架中的"检查点"（Checkpoint）数据。
 *
 * 背景知识 - 检查点机制：
 * Spring AI Alibaba 的 ReactAgent 基于"有状态图"（Stateful Graph）运行。
 * 每次 Agent 执行工具调用、思考推理时，会把当前状态保存为一个"检查点"，
 * 类似于游戏中的"存档点"。这样做的好处是：
 *   1. 支持断点续传：如果执行中断，可以从最近的检查点恢复
 *   2. 支持调试回放：可以查看 Agent 在每一步的状态
 *   3. 支持会话重置：清除检查点等于清除 Agent 的记忆
 *
 * 数据库结构：
 * - graph_thread 表：存储线程信息（threadId = conversationId）
 * - graph_checkpoint 表：存储检查点数据（关联 threadId）
 *
 * 设计模式：外观模式（Facade）。封装了 MysqlSaver、GraphCheckpointMapper、
 * GraphThreadMapper 三个组件的操作，提供统一的检查点管理接口。
 *
 * 使用场景：
 * - BusinessChatService 通过 buildSessionConfig() 创建 RunnableConfig 时，
 *   ReactAgent 会自动通过 MysqlSaver 读写检查点
 * - resetConversation() 时调用 clearThread() 清除所有检查点
 * - getSession() 时调用 get() / list() 查看检查点状态
 */
@Component
public class ChatCheckpointManager {

    /** Spring AI Alibaba 提供的 MySQL 检查点存储器，负责检查点的自动读写 */
    private final MysqlSaver checkpointSaver;

    /** 检查点表的 MyBatis-Plus Mapper，用于手动查询和删除 */
    private final GraphCheckpointMapper graphCheckpointMapper;

    /** 线程表的 MyBatis-Plus Mapper，用于手动查询和删除 */
    private final GraphThreadMapper graphThreadMapper;

    /**
     * 构造函数，通过 Spring 依赖注入三个组件。
     *
     * @param checkpointSaver      MySQL 检查点存储器
     * @param graphCheckpointMapper 检查点 Mapper
     * @param graphThreadMapper     线程 Mapper
     */
    public ChatCheckpointManager(MysqlSaver checkpointSaver,
                                 GraphCheckpointMapper graphCheckpointMapper,
                                 GraphThreadMapper graphThreadMapper) {
        this.checkpointSaver = checkpointSaver;
        this.graphCheckpointMapper = graphCheckpointMapper;
        this.graphThreadMapper = graphThreadMapper;
    }

    /**
     * 获取指定运行配置对应的最新检查点。
     *
     * @param runnableConfig 运行配置，包含 threadId（即 conversationId）
     * @return 最新检查点的 Optional 包装，如果不存在则为 empty
     */
    public Optional<Checkpoint> get(RunnableConfig runnableConfig) {

        return checkpointSaver.get(runnableConfig);
    }

    /**
     * 列出指定运行配置对应的所有检查点。
     *
     * @param runnableConfig 运行配置，包含 threadId（即 conversationId）
     * @return 所有检查点的集合，按时间顺序排列
     */
    public Collection<Checkpoint> list(RunnableConfig runnableConfig) {

        return checkpointSaver.list(runnableConfig);
    }

    /**
     * 清除指定会话线程的所有检查点和线程记录。
     *
     * 执行逻辑：
     * 1. 根据 threadId（conversationId）查询 graph_thread 表，找到所有关联的线程记录
     * 2. 根据线程记录的 ID，删除 graph_checkpoint 表中的所有关联检查点
     * 3. 删除 graph_thread 表中的线程记录
     *
     * 使用 @Transactional 注解保证操作的原子性：
     * 如果删除过程中出现异常，所有已执行的删除操作都会回滚。
     *
     * @param threadId 会话线程 ID（即 conversationId）
     * @return 被删除的检查点数量
     */
    @Transactional
    public int clearThread(String threadId) {
        // 第一步：根据 threadName（即 conversationId）查询所有线程记录
        List<GraphThread> threads = graphThreadMapper.selectList(
            new LambdaQueryWrapper<GraphThread>()
                .eq(GraphThread::getThreadName, threadId)
        );
        if (threads == null || threads.isEmpty()) {
            return 0;
        }

        // 第二步：提取所有线程的内部 ID
        List<String> graphThreadIds = threads.stream()
            .map(GraphThread::getThreadId)
            .toList();

        // 第三步：统计并删除关联的检查点记录
        int checkpointCount = toInt(graphCheckpointMapper.selectCount(
            new LambdaQueryWrapper<GraphCheckpoint>()
                .in(GraphCheckpoint::getThreadId, graphThreadIds)
        ));

        if (checkpointCount > 0) {

            graphCheckpointMapper.delete(
                new LambdaQueryWrapper<GraphCheckpoint>()
                    .in(GraphCheckpoint::getThreadId, graphThreadIds)
            );
        }
        // 第四步：删除线程记录本身
        graphThreadMapper.delete(
            new LambdaQueryWrapper<GraphThread>()
                .eq(GraphThread::getThreadName, threadId)
        );
        return checkpointCount;
    }

    /**
     * 安全地将 Long 转为 int，null 时返回 0。
     *
     * @param count MyBatis-Plus selectCount 返回的 Long 值（可能为 null）
     * @return int 值
     */
    private int toInt(Long count) {

        return count == null ? 0 : count.intValue();
    }
}
