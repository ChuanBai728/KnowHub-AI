package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档详情查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询某个文档的详细信息"时所需的参数。
 * 管理端点击某个文档时，需要查看该文档的完整详情（如名称、上传时间、处理状态等）。
 *
 * 使用场景：管理端在文档列表中点击某个文档，跳转到文档详情页时使用。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentDetailQueryDto {

    /**
     * 文档ID（必填）
     *
     * 要查询详情的文档的唯一标识，对应数据库主键。
     * @NotNull 校验：不能为null。
     */
    @NotNull(message = "文档id不能为空")
    private Long documentId;
}
