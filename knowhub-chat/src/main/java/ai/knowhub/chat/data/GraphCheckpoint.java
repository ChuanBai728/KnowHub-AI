package ai.knowhub.chat.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图执行引擎的检查点（Checkpoint）数据实体。
 *
 * 对应数据库表：GRAPH_CHECKPOINT
 *
 * 什么是检查点？
 * 在 ReactAgent 的 ReAct 循环中，Agent 可能需要执行多个步骤才能得出最终答案。
 * 检查点机制会在每个关键步骤保存当前的执行状态（就像游戏中的"存档点"）。
 *
 * 检查点的作用
 * 
 *   <b>断点续传</b>：服务重启后可以从上次的状态继续执行，而不是从头开始
 *   <b>状态回溯</b>：当执行出错时，可以回退到之前的某个检查点重试
 *   <b>调试追踪</b>：记录每一步的状态变化，方便排查问题
 * 涉及的注解
 * 
 *   @Data —— Lombok 注解，自动生成 getter/setter/toString/equals/hashCode
 *   @NoArgsConstructor —— 生成无参构造函数（MyBatis-Plus 反射需要）
 *   @AllArgsConstructor —— 生成全参构造函数
 *   @TableName("GRAPH_CHECKPOINT") —— MyBatis-Plus 注解，指定对应的数据库表名
 *   @TableId —— 标记主键字段及其生成策略
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("GRAPH_CHECKPOINT")
public class GraphCheckpoint {

    /**
     * 检查点的唯一标识 ID。
     * 使用 IdType.INPUT 策略，表示由应用程序手动设置 ID 值，
     * 而不是由数据库自动生成。
     */
    @TableId(value = "checkpoint_id", type = IdType.INPUT)
    private String checkpointId;

    /**
     * 线程 ID。
     * 标识这个检查点属于哪个执行线程。
     * 一个线程对应一次完整的 Agent 推理过程。
     */
    private String threadId;

    /**
     * 节点 ID。
     * 记录检查点保存时 Agent 执行到了图中的哪个节点。
     * 图（Graph）是 Agent 的执行流程，由多个节点组成。
     */
    private String nodeId;

    /**
     * 下一个要执行的节点 ID。
     * 记录 Agent 接下来要执行的节点，用于断点续传时恢复执行流程。
     */
    private String nextNodeId;

    /**
     * 状态数据（JSON 格式）。
     * 存储 Agent 在该检查点时的完整状态信息，包括：
     * 
     *   对话历史
     *   工具调用结果
     *   中间推理过程
     *   其他运行时数据
     * 
     * 使用 JSON 字符串存储，便于序列化和反序列化复杂的嵌套对象。
     */
    private String stateData;

    /**
     * 保存时间。
     * 记录该检查点被创建的时间，用于过期清理和排序。
     */
    private LocalDateTime savedAt;
}
