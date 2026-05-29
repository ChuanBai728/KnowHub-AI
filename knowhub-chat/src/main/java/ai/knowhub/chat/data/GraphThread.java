package ai.knowhub.chat.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图执行引擎的线程（Thread）数据实体。
 *
 * 对应数据库表：GRAPH_THREAD
 *
 * 什么是线程（Thread）？
 * 在 Spring AI Alibaba 的图执行框架中，Thread 是一次完整的 Agent 推理过程。
 * 它类似于操作系统中的"线程"概念，但这里指的是一个逻辑执行单元：
 * 
 *   一个 Thread 对应用户的一次提问到获得最终回答的完整过程
 *   一个 Thread 中可能包含多个检查点（Checkpoint）
 *   Thread 可以被释放（released），释放后的检查点数据可以被清理
 * 与会话（Session）的关系
 * 一个会话可能包含多个 Thread。例如，用户在一个会话中提了 3 个问题，
 * 那么可能对应 3 个 Thread（每个问题一个 Thread）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("GRAPH_THREAD")
public class GraphThread {

    /**
     * 线程的唯一标识 ID。
     * 由应用程序生成并设置（IdType.INPUT）。
     */
    @TableId(value = "thread_id", type = IdType.INPUT)
    private String threadId;

    /**
     * 线程名称。
     * 人类可读的线程标识，方便在日志和调试中识别。
     * 通常包含会话 ID 和时间戳等信息。
     */
    private String threadName;

    /**
     * 是否已释放。
     * 当 Agent 完成推理并返回最终答案后，线程可以被标记为"已释放"。
     * 已释放的线程的检查点数据可以被定期清理，释放数据库空间。
     *
     * 注意：数据库字段名为 "is_released"，使用 @TableField 显式映射，
     * 因为 MyBatis-Plus 对布尔类型字段的自动映射可能存在兼容性问题。
     */
    @TableField("is_released")
    private Boolean released;
}
