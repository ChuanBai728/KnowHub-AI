package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识路由追踪分页返回值对象（Knowledge Route Trace Page Vo）
 *
 * 【类的作用】
 * 用于知识路由追踪记录的分页查询响应，包含分页信息和追踪记录列表。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于知识路由追踪页面的分页响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRouteTracePageVo {

    /**
     * 页码（Page No）
     * 当前页码
     */
    private String pageNo;

    /**
     * 每页大小（Page Size）
     * 每页显示的记录数
     */
    private String pageSize;

    /**
     * 总记录数（Total Size）
     * 符合条件的记录总数
     */
    private String totalSize;

    /**
     * 总页数（Total Pages）
     * 总页数
     */
    private String totalPages;

    /**
     * 追踪记录列表（Records）
     * 当前页的追踪记录
     */
    private List<KnowledgeRouteTraceItemVo> records;
}
