package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 已有文档重新解析任务的初始状态。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReparseVo {

    private Long documentId;
    private Long taskId;
    private String documentName;
    private Integer parseStatus;
    private Integer strategyStatus;
    private Integer indexStatus;
}
