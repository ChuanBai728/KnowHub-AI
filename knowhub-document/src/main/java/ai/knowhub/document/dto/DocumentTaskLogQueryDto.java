package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档任务日志查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询某个文档处理任务的日志列表"时所需的参数，
 * 支持分页查询。在RAG系统中，文档的索引构建等操作会被封装为异步任务（Task），
 * 每个任务执行过程中会产生多条日志记录，记录每一步的执行情况。
 *
 * 使用场景：管理端查看某个文档处理任务的执行日志时使用，可以了解任务的
 * 每一步执行状态、耗时、是否有错误等详细信息。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentTaskLogQueryDto {

    /**
     * 任务ID（必填）
     *
     * 要查询日志的文档处理任务的唯一标识。
     * @NotNull 校验：不能为null。
     */
    @NotNull(message = "任务id不能为空")
    private Long taskId;

    /**
     * 页码（可选）
     *
     * 分页查询的页码，从1开始。
     */
    private Integer pageNo;

    /**
     * 每页条数（可选）
     *
     * 分页查询时每页返回的日志记录数。
     */
    private Integer pageSize;
}
