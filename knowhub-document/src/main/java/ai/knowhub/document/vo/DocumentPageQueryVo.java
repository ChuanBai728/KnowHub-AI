package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档分页查询返回值对象（Document Page Query Vo）
 *
 * 【类的作用】
 * 用于文档列表的分页查询响应，包含分页信息和文档列表数据。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于文档列表页面的分页响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPageQueryVo {

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
     * 符合条件的文档总数
     */
    private Long total;

    /**
     * 文档记录列表（Records）
     * 当前页的文档列表
     */
    private List<DocumentListItemVo> records;
}
