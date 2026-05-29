package ai.knowhub.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.chat.model.ConversationSessionVo;

import java.util.List;

/**
 * 会话列表查询结果返回值对象（VO）。
 *
 * 该类封装了会话列表查询的分页结果，包含分页信息和会话数据列表。
 * 当用户在前端查看历史会话列表时，后端会返回该对象作为查询结果。
 *
 * 在架构中的角色
 * 属于 VO（Value Object）层，是分页查询结果的标准响应格式。
 * 包含完整的分页元数据（页码、每页大小、总记录数、总页数），
 * 使前端能够正确渲染分页组件。
 *
 * Lombok 注解说明
 * 
 *   @Data - 自动生成 getter/setter/toString/equals/hashCode 方法
 *   @NoArgsConstructor - 自动生成无参构造方法
 *   @AllArgsConstructor - 自动生成包含所有字段的构造方法
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionListVo {

    /**
     * 当前页码。
     *
     * 从 1 开始计数，表示当前展示的是第几页数据。
     */
    private long pageNo;

    /**
     * 每页显示条数。
     *
     * 表示每页包含的会话记录数量，由前端在查询时指定。
     */
    private long pageSize;

    /**
     * 总记录数。
     *
     * 满足查询条件的会话记录总数，前端据此计算总页数。
     */
    private long totalSize;

    /**
     * 总页数。
     *
     * 根据总记录数和每页大小计算得出的总页数，
     * 前端据此渲染分页导航组件（如"上一页/下一页"按钮）。
     */
    private long totalPages;

    /**
     * 会话展示列表。
     *
     * 当前页的会话数据列表，每条数据是一个 ConversationSessionVo 对象，
     * 包含会话的摘要信息（如会话标题、最后更新时间、消息数量等），
     * 用于前端列表页面的展示。
     */
    private List<ConversationSessionVo> sessions;
}
