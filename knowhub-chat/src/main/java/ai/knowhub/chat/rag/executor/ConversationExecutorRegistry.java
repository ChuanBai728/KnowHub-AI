package ai.knowhub.chat.rag.executor;

import ai.knowhub.chat.rag.model.ExecutionMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 会话执行器注册表 —— 策略模式的分发中心。
 *
 * 设计模式：策略模式的 Context 角色
 * 在策略模式中，除了策略接口（ConversationExecutor）和具体策略（各 Executor 实现类），
 * 还需要一个"上下文"角色来根据条件选择具体策略并调用。本类就是这个上下文角色。
 *
 * 工作原理
 * Spring 容器启动时，会把所有标注了 @Component 的 ConversationExecutor
 * 实现类自动注入到本类的构造函数中。构造函数遍历所有实现类，以它们的 ConversationExecutor#mode()
 * 返回值作为 key，执行器本身作为 value，存入 EnumMap。
 *
 * 当路由编排器决定好本次对话应该使用哪种执行模式后，只需调用 #get(ExecutionMode)
 * 就能拿到对应的执行器，然后调用其 execute() 方法。整个分发过程是 O(1) 的 Map 查找。
 *
 * 为什么用 EnumMap
 * EnumMap 是 Java 专门为枚举 key 优化的 Map 实现，内部用数组存储，
 * 比 HashMap 更紧凑、更快。因为 ExecutionMode 是枚举类型，用 EnumMap 最合适。
 *
 * @see ConversationExecutor 执行器接口
 * @see ExecutionMode 执行模式枚举
 */
@Component
public class ConversationExecutorRegistry {

    /**
     * 执行模式到执行器的映射表。
     *
     * key 是 ExecutionMode 枚举值（如 RETRIEVAL、GRAPH_ONLY 等），
     * value 是对应的 ConversationExecutor 实现类实例。
     *
     * 使用 EnumMap 而非 HashMap，因为 EnumMap 内部用数组实现，
     * 对枚举 key 的查找效率更高，内存占用更小。
     */
    private final Map<ExecutionMode, ConversationExecutor> executorMap = new EnumMap<>(ExecutionMode.class);

    /**
     * 构造函数 —— 利用 Spring 的自动注入收集所有执行器实现。
     *
     * Spring 会自动找到所有实现了 ConversationExecutor 接口并标注了
     * @Component 的 Bean，以 List 形式注入到这里。
     *
     * 构造时遍历所有执行器，以各自的 ConversationExecutor#mode() 返回值为 key
     * 存入 #executorMap，形成一个"执行模式 -> 执行器"的查找表。
     *
     * @param executors Spring 自动注入的所有 ConversationExecutor 实现类实例
     */
    public ConversationExecutorRegistry(List<ConversationExecutor> executors) {
        // Spring 会把所有 ConversationExecutor 实现注入进来，这里按执行模式做成查找表。
        for (ConversationExecutor executor : executors) {
            executorMap.put(executor.mode(), executor);
        }
    }

    /**
     * 根据执行模式获取对应的执行器。
     *
     * 路由编排器产出 ExecutionMode 后，调用此方法拿到执行器，
     * 再调用执行器的 ConversationExecutor#execute(TaskInfo) 方法执行对话。
     *
     * 如果找不到对应的执行器（理论上不应该发生，除非新增了 ExecutionMode 枚举值
     * 但忘记实现对应的执行器），会抛出 IllegalStateException。
     *
     * @param mode 执行模式枚举值，由路由编排器决定
     * @return 对应的执行器实例
     * @throws IllegalStateException 如果没有找到对应模式的执行器
     */
    public ConversationExecutor get(ExecutionMode mode) {
        // 编排器只产出 ExecutionMode，真正的执行类由注册表统一分发。
        ConversationExecutor executor = executorMap.get(mode);
        if (executor == null) {
            throw new IllegalStateException("未找到执行模式对应的执行器: " + mode);
        }
        return executor;
    }
}
