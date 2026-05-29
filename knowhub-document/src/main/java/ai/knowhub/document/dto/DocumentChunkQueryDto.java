package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档分块列表查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询某个文档的所有分块列表"时所需的参数，
 * 支持分页查询。在RAG流程中，文档被切分成多个chunk后，管理端需要查看
 * 所有chunk的列表，本DTO封装了分页查询所需的参数。
 *
 * 使用场景：管理端查看某个文档被切分成了哪些chunk，以列表形式展示，
 * 支持按页码和每页条数进行分页。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentChunkQueryDto {

    /**
     * 文档ID（必填）
     *
     * 标识要查询哪个文档的分块列表。
     * @NotNull 校验：不能为null。
     */
    @NotNull(message = "文档id不能为空")
    private Long documentId;

    /**
     * 任务ID（可选）
     *
     * 指定查询某次处理任务下的分块数据。同一文档多次处理可能产生不同的分块结果。
     */
    private Long taskId;

    /**
     * 页码（可选）
     *
     * 分页查询的页码，从1开始。例如pageNo=1表示第一页。
     */
    private Integer pageNo;

    /**
     * 每页条数（可选）
     *
     * 分页查询时每页返回的记录数。例如pageSize=10表示每页返回10条记录。
     */
    private Integer pageSize;
}
