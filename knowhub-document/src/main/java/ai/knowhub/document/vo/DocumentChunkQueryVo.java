package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档切片查询返回值对象（Document Chunk Query Vo）
 *
 * 【类的作用】
 * 用于文档切片的分页查询，包含查询条件和分页结果。
 * 支持按文档ID、任务ID、计划ID筛选切片。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于切片列表的分页查询请求和响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkQueryVo {

    /**
     * 文档ID（Document ID）
     * 按文档筛选切片
     */
    private Long documentId;

    /**
     * 任务ID（Task ID）
     * 按处理任务筛选切片
     */
    private Long taskId;

    /**
     * 计划ID（Plan ID）
     * 按策略计划筛选切片
     */
    private Long planId;

    /**
     * 页码（Page No）
     * 当前页码（从 1 开始）
     */
    private Integer pageNo;

    /**
     * 每页大小（Page Size）
     * 每页显示的记录数
     */
    private Integer pageSize;

    /**
     * 总记录数（Total）
     * 符合条件的切片总数
     */
    private Long total;

    /**
     * 切片记录列表（Records）
     * 当前页的切片列表
     */
    private List<DocumentChunkItemVo> records;
}
