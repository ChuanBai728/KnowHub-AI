package ai.knowhub.chat.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

import java.util.Date;

/**
 * 聊天记忆摘要（Memory Summary）的数据实体。
 *
 * 对应数据库表：knowhub_chat_memory_summary
 *
 * 什么是记忆摘要？
 * 当对话历史很长时，将所有历史消息直接发送给大模型会导致：
 * 
 *   Token 消耗过大，成本增加
 *   超出模型的上下文窗口限制
 *   模型注意力分散，回答质量下降
 * 
 * 记忆摘要机制将较长的对话历史压缩为一段简洁的摘要文本，
 * 既保留了关键信息，又大幅减少了 Token 消耗。
 *
 * 摘要的工作流程
 * 
 *   系统定期检查对话历史的长度
 *   当历史超过阈值时，调用大模型将旧消息压缩为摘要
 *   后续对话中，使用"摘要 + 最近几轮消息"作为上下文
 *   随着新对话的进行，摘要会持续更新
 * 继承关系
 * 继承自 BaseTableData，自动拥有创建时间、更新时间等公共字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_chat_memory_summary")
@EqualsAndHashCode(callSuper = true)
public class KnowHubChatMemorySummary extends BaseTableData {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 对话编码（会话 ID）。
     * 标识这个摘要属于哪个会话。
     */
    @TableField("dialogue_code")
    private String conversationId;

    /**
     * 已覆盖的最大交换 ID。
     * 记录这个摘要覆盖到了哪一次交换（Exchange）。
     * 例如，如果 coveredExchangeId = 10，说明第 1-10 次交换的内容已被压缩为摘要。
     */
    @TableField("covered_exchange_id")
    private Long coveredExchangeId;

    /**
     * 已覆盖的交换数量。
     * 这个摘要总共涵盖了多少次交换的内容。
     */
    @TableField("covered_exchange_count")
    private Integer coveredExchangeCount;

    /**
     * 压缩次数。
     * 记录这个摘要经历了多少次压缩操作。
     * 每次压缩都会将新产生的对话历史合并到已有的摘要中。
     */
    @TableField("compression_count")
    private Integer compressionCount;

    /**
     * 摘要版本号。
     * 每次更新摘要时版本号递增，用于乐观锁和版本管理。
     */
    @TableField("summary_version")
    private Integer summaryVersion;

    /**
     * 摘要文本。
     * 大模型生成的对话历史摘要，是一段自然语言文本。
     * 后续对话时，这段摘要会作为上下文发送给模型。
     */
    @TableField("summary_text")
    private String summaryText;

    /**
     * 摘要的结构化数据（JSON 格式）。
     * 除了自然语言摘要外，还可能存储结构化的信息，
     * 如关键实体、话题列表等，便于后续查询和分析。
     */
    @TableField("summary_json")
    private String summaryJson;

    /**
     * 最后一次源数据编辑时间。
     * 记录原始对话历史最后一次被修改的时间，
     * 用于判断摘要是否需要更新。
     */
    @TableField("last_source_edit_time")
    private Date lastSourceEditTime;
}
