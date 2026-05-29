package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档上传返回值对象（Document Upload Vo）
 *
 * 【类的作用】
 * 用于文档上传操作的响应，返回上传后的文档ID、关联的任务ID以及
 * 文档的初始状态信息。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于文档上传 API 的响应。
 * 上传成功后，系统会自动创建处理任务，用户可通过返回的 taskId 跟踪进度。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadVo {

    /**
     * 文档ID（Document ID）
     * 上传成功后生成的文档唯一标识
     */
    private Long documentId;

    /**
     * 任务ID（Task ID）
     * 系统自动创建的处理任务ID
     */
    private Long taskId;

    /**
     * 文档名称（Document Name）
     * 处理后的文档名称
     */
    private String documentName;

    /**
     * 解析状态编码（Parse Status）
     * 文档解析的初始状态
     */
    private Integer parseStatus;

    /**
     * 策略状态编码（Strategy Status）
     * 策略的初始状态
     */
    private Integer strategyStatus;

    /**
     * 索引状态编码（Index Status）
     * 索引构建的初始状态
     */
    private Integer indexStatus;
}
