package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 请求对已上传文档重新执行解析与策略推荐。 */
@Data
public class DocumentReparseDto {

    @NotNull(message = "文档id不能为空")
    private Long documentId;

    /** 可选操作人，用于任务审计。 */
    private Long operatorId;
}
