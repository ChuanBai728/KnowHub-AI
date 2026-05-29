package ai.knowhub.chat.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 【聊天运行时注册表】
 *
 * 维护当前正在执行中的会话任务的注册表，基于 ConcurrentHashMap 实现。
 *
 * 作用：在任意时刻，一个会话（conversationId）最多只能有一个正在执行的任务。
 * ChatRuntimeRegistry 通过注册表机制确保这个约束：
 *   - register(): 尝试注册一个新任务，如果该会话已有任务在执行则返回 false
 *   - get(): 查询指定会话当前是否有任务在执行
 *   - remove(): 任务完成后从注册表中移除
 *
 * 与 Redis 租约的关系：
 * - Redis 租约（RedisLeaseManager）是跨服务实例的分布式锁，防止多台服务器同时处理同一会话
 * - ChatRuntimeRegistry 是单 JVM 内存级别的注册表，防止同一线程池中的并发冲突
 * - 两者配合使用，形成双重保护
 *
 * 设计模式：注册表模式（Registry Pattern）。
 * 使用 ConcurrentHashMap 保证线程安全，putIfAbsent 保证原子性注册。
 *
 * 使用场景：
 * - BusinessChatService.bootstrapConversation() 中调用 register() 注册新任务
 * - BusinessChatService.stopConversation() 中调用 get() 查找正在执行的任务
 * - BusinessChatService.cleanup() 中调用 remove() 移除已完成的任务
 */
@Component
public class ChatRuntimeRegistry {

    /**
     * 运行时任务注册表。
     * Key: conversationId（会话 ID）
     * Value: TaskInfo（任务信息，包含 sink、执行计划、追踪记录器等）
     *
     * 使用 ConcurrentHashMap 保证多线程环境下的线程安全。
     */
    private final ConcurrentMap<String, TaskInfo> taskMap = new ConcurrentHashMap<>();

    /**
     * 注册一个新的会话任务。
     *
     * 使用 putIfAbsent 保证原子性：只有当该会话没有正在执行的任务时才注册成功。
     *
     * @param taskInfo 要注册的任务信息
     * @return true 表示注册成功（该会话之前没有任务在执行），
     *         false 表示注册失败（该会话已有任务在执行）
     */
    public boolean register(TaskInfo taskInfo) {
        return taskMap.putIfAbsent(taskInfo.conversationId(), taskInfo) == null;
    }

    /**
     * 查询指定会话当前正在执行的任务。
     *
     * @param conversationId 会话 ID
     * @return 任务信息的 Optional 包装，如果该会话没有任务在执行则为 empty
     */
    public Optional<TaskInfo> get(String conversationId) {
        return Optional.ofNullable(taskMap.get(conversationId));
    }

    /**
     * 从注册表中移除指定会话的任务（无条件移除）。
     *
     * 注意：此方法会无条件移除，不检查当前注册的任务是否是预期的那个。
     * 如果需要安全移除（只移除自己注册的任务），请使用带 expectedTaskInfo 参数的重载方法。
     *
     * @param conversationId 会话 ID
     */
    public void remove(String conversationId) {

        taskMap.remove(conversationId);
    }

    /**
     * 从注册表中安全移除指定会话的任务（仅当当前注册的任务是预期的那个时才移除）。
     *
     * 这是一个"条件移除"操作，只有当注册表中当前存储的 TaskInfo 与 expectedTaskInfo
     * 是同一个对象（引用相等）时才执行移除。这可以防止以下竞态条件：
     *   1. 任务 A 正在执行，注册表中存储了任务 A
     *   2. 用户发起停止请求，准备移除任务 A
     *   3. 同时任务 A 已完成，任务 B 注册到注册表中
     *   4. 如果无条件移除，会误删任务 B
     *
     * @param conversationId  会话 ID
     * @param expectedTaskInfo 预期的任务信息（只有当前注册的是这个任务时才移除）
     */
    public void remove(String conversationId, TaskInfo expectedTaskInfo) {
        if (conversationId == null || expectedTaskInfo == null) {
            return;
        }
        taskMap.remove(conversationId, expectedTaskInfo);
    }
}
