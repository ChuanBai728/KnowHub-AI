package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档删除DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"删除文档"请求时所需的参数。
 * 管理端在文档管理页面选择要删除的文档，前端将文档ID封装到本DTO中发送给后端。
 *
 * 使用场景：管理端删除某个已上传的文档时使用。
 *
 * 注意：这里documentId使用String类型而非Long类型，
 * 可能是因为前端传入的是字符串格式的ID，或者支持批量删除时传入逗号分隔的多个ID。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentDeleteDto {

    /**
     * 文档ID（必填）
     *
     * 要删除的文档的唯一标识。
     * @NotBlank 校验：字符串不能为null、不能为""（空字符串）、不能全是空白字符。
     * 与@NotNull的区别是，@NotBlank专门用于String类型，能检测空字符串的情况。
     */
    @NotBlank(message = "文档id不能为空")
    private String documentId;
}
