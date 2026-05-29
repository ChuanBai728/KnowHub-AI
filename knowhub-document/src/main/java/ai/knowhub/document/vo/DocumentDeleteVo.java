package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档删除返回值对象（Document Delete Vo）
 *
 * 【类的作用】
 * 用于文档删除操作的请求参数，包含要删除的文档ID和名称。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于删除文档的 API 请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDeleteVo {

    /**
     * 文档ID（Document ID）
     * 要删除的文档的唯一标识
     */
    private Long documentId;

    /**
     * 文档名称（Document Name）
     * 要删除的文档名称，用于确认操作
     */
    private String documentName;
}
