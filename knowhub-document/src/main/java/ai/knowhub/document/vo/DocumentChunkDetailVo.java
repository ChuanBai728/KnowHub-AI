package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档切片详情返回值对象（Document Chunk Detail Vo）
 *
 * 【类的作用】
 * 用于展示单个文档切片的详细信息，包括切片本身、其所属的父级文本块、
 * 以及同一父块下的其他兄弟切片。提供切片的完整上下文信息。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层（VO = Value Object），用于向前端返回数据。
 * 对应文档切片详情页面的数据展示需求。
 *
 * 【设计模式】
 * 返回值对象模式（Value Object）：将后端数据组装为前端所需的格式。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkDetailVo {

    /**
     * 文档ID（Document ID）
     * 切片所属文档的唯一标识
     */
    private Long documentId;

    /**
     * 任务ID（Task ID）
     * 生成该切片的处理任务ID
     */
    private Long taskId;

    /**
     * 计划ID（Plan ID）
     * 生成该切片的策略计划ID
     */
    private Long planId;

    /**
     * 当前切片信息（Chunk）
     * 被查看的切片的详细信息
     */
    private DocumentChunkItemVo chunk;

    /**
     * 父级文本块信息（Parent Block）
     * 当前切片所属的父级文本块
     */
    private DocumentParentBlockItemVo parentBlock;

    /**
     * 兄弟切片列表（Sibling Chunks）
     * 同一父块下的其他切片，用于展示上下文
     */
    private List<DocumentChunkItemVo> siblingChunks;
}
