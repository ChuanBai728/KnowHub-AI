package ai.knowhub.document.dto;

import lombok.Data;

import java.util.List;

/**
 * 文档画像批量重新生成DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"批量重新生成文档画像"请求时所需的参数。
 * 文档画像（Document Profile）是系统对文档内容的摘要描述，通常由AI自动生成。
 * 当画像质量不佳或文档内容更新后，可以批量重新生成多个文档的画像。
 *
 * 使用场景：管理端在文档列表中选择多个文档，点击"批量重新生成画像"按钮时使用。
 *
 * 与 DocumentProfileRegenerateDto 的区别：本DTO支持批量操作（多个文档），
 * 而DocumentProfileRegenerateDto只支持单个文档。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentProfileBatchRegenerateDto {

    /**
     * 文档ID列表
     *
     * 要重新生成画像的文档ID集合。前端选择多个文档后，
     * 将这些文档的ID放入列表中一起传给后端进行批量处理。
     */
    private List<String> documentIds;

    /**
     * 操作人ID
     *
     * 执行批量重新生成操作的用户标识，用于记录操作日志。
     */
    private String operatorId;
}
