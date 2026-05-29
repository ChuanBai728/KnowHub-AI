package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档分块详情查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询某个文档的某个分块详情"时所需的参数。
 * 在RAG（检索增强生成）流程中，文档会被切分成多个chunk（分块/片段），
 * 每个chunk都有唯一的ID。本DTO用于根据文档ID和chunkID查询单个分块的详细信息。
 *
 * 使用场景：管理端查询某个文档中某个特定分块的详细内容时使用。
 *
 * 涉及的设计模式：DTO模式——将前端请求参数封装为对象，避免Controller方法参数列表过长，
 * 同时便于参数校验（通过Jakarta Validation注解）。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法，减少样板代码
public class DocumentChunkDetailQueryDto {

    /**
     * 文档ID（必填）
     *
     * 标识要查询的是哪个文档。对应数据库中knowhub_document表的主键。
     * @NotNull 表示该字段不能为null，如果前端传入null，框架会抛出校验异常，
     * 异常信息为"文档id不能为空"。
     */
    @NotNull(message = "文档id不能为空")
    private Long documentId;

    /**
     * 分块ID（必填）
     *
     * 标识要查询的是文档中的哪个chunk（分块）。
     * 文档经过切分后，每个chunk都会生成一个唯一ID。
     * @NotNull 表示该字段不能为null。
     */
    @NotNull(message = "chunkId不能为空")
    private Long chunkId;

    /**
     * 任务ID（可选）
     *
     * 标识本次查询关联的文档处理任务。同一个文档可能经过多次处理（如重新构建索引），
     * 每次处理会生成不同的taskId。传入taskId可以精确查询某次任务下的分块数据。
     */
    private Long taskId;
}
