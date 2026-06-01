package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 文档画像详情查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询某个文档的画像详情"时所需的参数。
 * 文档画像（Document Profile）是系统利用AI对文档内容进行分析后生成的摘要描述，
 * 包括文档的主题、关键信息等。管理端可以查看某个文档的画像详情。
 *
 * 使用场景：管理端在文档详情页中查看该文档的AI生成画像时使用。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentProfileDetailQueryDto {

    /**
     * 文档ID
     *
     * 要查询画像的文档的唯一标识。注意这里使用String类型，
     * 可能是因为前端传入的是字符串格式的ID。
     */
    private String documentId;
}
