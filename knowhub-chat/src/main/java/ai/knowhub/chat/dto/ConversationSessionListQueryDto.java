package ai.knowhub.chat.dto;

import lombok.Data;

/**
 * 会话列表查询 DTO。
 *
 * 该类用于封装查询会话列表（分页）时的请求参数，支持多条件筛选。
 * 当用户在前端查看历史会话列表时，可以通过关键词搜索、聊天模式筛选、
 * 会话状态过滤等方式缩小查询范围。
 *
 * 在架构中的角色
 * 作为会话列表查询接口的入参 DTO，传递给 Service 层进行数据库查询。
 * 支持分页和多维度筛选，满足前端会话管理页面的展示需求。
 *
 * Lombok 注解说明
 * 
 *   @Data - 自动生成 getter/setter/toString/equals/hashCode 方法
 * 
 */
@Data
public class ConversationSessionListQueryDto {

    /**
     * 搜索关键词，用于模糊匹配会话标题或内容。
     *
     * 当用户在会话列表页面使用搜索功能时，该字段携带搜索关键词，
     * 后端会在数据库中进行模糊查询（LIKE）。
     */
    private String keyword;

    /**
     * 聊天模式筛选条件。
     *
     * 按聊天模式过滤会话列表，例如只显示 "React Agent" 模式或
     * "Graph Only" 模式的会话。对应 ChatRequestDto#chatMode 字段。
     */
    private String chatMode;

    /**
     * 会话状态筛选条件。
     *
     * 按当前轮次状态过滤会话，例如只显示"进行中"或"已完成"的会话。
     * 对应会话（Conversation）的轮次状态枚举值。
     */
    private String turnStatus;

    /**
     * 当前页码（字符串类型，由前端传入）。
     *
     * 分页查询的起始页码，从 1 开始。使用字符串类型是为了兼容
     * 前端直接传递 URL 查询参数的场景，后端会将其转换为数值。
     */
    private String pageNo;

    /**
     * 每页显示条数（字符串类型，由前端传入）。
     *
     * 分页查询的每页记录数。同样使用字符串类型以兼容前端参数传递。
     */
    private String pageSize;
}
