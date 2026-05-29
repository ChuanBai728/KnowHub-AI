package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 文档分页查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"分页查询文档列表"时所需的参数。
 * 管理端的文档列表页面支持分页浏览和关键词搜索，本DTO封装了这些查询条件。
 *
 * 使用场景：管理端打开文档管理页面时，前端传入页码、每页条数和可选的关键词，
 * 后端返回匹配的文档列表。
 *
 * 设计说明：本DTO没有使用@NotNull等校验注解，说明这些参数都是可选的，
 * 后端Service层会提供默认值（如pageNo默认1，pageSize默认10等）。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentPageQueryDto {

    /**
     * 页码（可选）
     *
     * 分页查询的页码，从1开始。如果前端不传，后端通常默认为第1页。
     */
    private Integer pageNo;

    /**
     * 每页条数（可选）
     *
     * 每页返回的文档记录数。如果前端不传，后端通常有默认值（如10或20条）。
     */
    private Integer pageSize;

    /**
     * 搜索关键词（可选）
     *
     * 用于模糊搜索文档。后端通常会根据此关键词在文档名称、标签等字段中进行模糊匹配。
     * 如果不传或为空，则返回所有文档。
     */
    private String keyword;
}
