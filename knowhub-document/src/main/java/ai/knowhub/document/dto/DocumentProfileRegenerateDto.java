package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 文档画像重新生成DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"重新生成单个文档画像"请求时所需的参数。
 * 文档画像（Document Profile）由AI根据文档内容自动生成，当需要更新画像时使用本DTO。
 *
 * 使用场景：管理端在某个文档的详情页中点击"重新生成画像"按钮时使用。
 *
 * 与 DocumentProfileBatchRegenerateDto 的区别：本DTO只处理单个文档，
 * 而BatchRegenerate版本支持批量处理多个文档。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentProfileRegenerateDto {

    /**
     * 文档ID
     *
     * 要重新生成画像的文档的唯一标识。
     */
    private String documentId;

    /**
     * 操作人ID
     *
     * 执行重新生成操作的用户标识，用于记录操作日志和审计追踪。
     */
    private String operatorId;
}
