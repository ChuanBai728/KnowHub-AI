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

/**
 * 聊天会话（Dialogue / Session）的数据实体。
 *
 * 对应数据库表：knowhub_chat_dialogue
 *
 * 什么是会话（Dialogue）？
 * 一个会话代表用户与 AI 的一次完整对话过程。
 * 
 *   一个会话包含多轮"交换"（Exchange），每轮交换是用户问一个问题、AI 回答一次
 *   会话有生命周期：进行中 → 已完成 / 已重置
 *   会话可以关联一个知识文档，用于文档问答场景
 * 字段映射说明
 * Java 属性名和数据库字段名做了不同的命名：
 * 
 *   conversationId → dialogue_code：会话唯一编码
 *   sessionStatus → dialogue_stage：会话状态阶段
 *   chatMode → chat_mode：聊天模式
 * 继承关系
 * 继承自 BaseTableData，自动拥有创建时间、更新时间等公共字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_chat_dialogue")
@EqualsAndHashCode(callSuper = true)
public class KnowHubChatDialogue extends BaseTableData {

    /**
     * 主键 ID。
     * 由应用程序手动设置。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 对话编码（会话 ID）。
     * 会话的唯一标识符，由系统生成。
     * 前端和后端都通过此 ID 来标识一个会话。
     */
    @TableField("dialogue_code")
    private String conversationId;

    /**
     * 会话状态（阶段）。
     * 使用整数编码表示会话的当前状态，例如：
     * 
     *   0 —— 进行中
     *   1 —— 已完成
     *   2 —— 已重置
     * 
     * 具体的状态枚举值请参考项目中的 SessionStatusEnum。
     */
    @TableField("dialogue_stage")
    private Integer sessionStatus;

    /**
     * 聊天模式。
     * 标识当前会话使用的聊天模式，例如：
 * 
 *   0 —— 普通对话模式（直接与 AI 聊天）
 *   1 —— 文档问答模式（基于选定文档进行问答）
 * 
     */
    @TableField("chat_mode")
    private Integer chatMode;

    /**
     * 选中的文档 ID。
     * 在文档问答模式下，记录用户选择的知识文档的 ID。
     * 普通对话模式下可能为 null。
     */
    @TableField("selected_document_id")
    private Long selectedDocumentId;

    /**
     * 选中的文档名称。
     * 冗余存储文档名称，避免每次都关联查询文档表，提高查询效率。
     * 这是一种常见的"空间换时间"的数据库设计优化。
     */
    @TableField("selected_document_name")
    private String selectedDocumentName;
}
