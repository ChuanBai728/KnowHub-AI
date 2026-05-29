package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档切片列表项返回值对象（Document Chunk Item Vo）
 *
 * 【类的作用】
 * 用于展示文档切片列表中的单个切片信息，包括切片的基本信息、
 * 所属父块信息、文本统计和向量化状态。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于切片列表页面的数据展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkItemVo {

    /**
     * 切片ID（Chunk ID）
     * 切片的唯一标识
     */
    private Long chunkId;

    /**
     * 父块ID（Parent Block ID）
     * 切片所属父级文本块的ID
     */
    private Long parentBlockId;

    /**
     * 父块编号（Parent Block No）
     * 父级文本块在文档中的顺序编号
     */
    private Integer parentBlockNo;

    /**
     * 父块子切片总数（Parent Child Count）
     * 当前父块下包含的切片总数
     */
    private Integer parentChildCount;

    /**
     * 父块起始切片编号（Parent Start Chunk No）
     * 父块中第一个切片的编号
     */
    private Integer parentStartChunkNo;

    /**
     * 父块结束切片编号（Parent End Chunk No）
     * 父块中最后一个切片的编号
     */
    private Integer parentEndChunkNo;

    /**
     * 切片编号（Chunk No）
     * 切片在文档中的顺序编号
     */
    private Integer chunkNo;

    /**
     * 章节路径（Section Path）
     * 切片在文档结构树中的路径
     */
    private String sectionPath;

    /**
     * 来源类型编码（Source Type）
     * 切片来源的编码值
     */
    private Integer sourceType;

    /**
     * 来源类型名称（Source Type Name）
     * 切片来源的可读名称
     */
    private String sourceTypeName;

    /**
     * 字符数（Character Count）
     * 切片文本的字符数
     */
    private Integer charCount;

    /**
     * Token 数量（Token Count）
     * 切片文本的 token 数量
     */
    private Integer tokenCount;

    /**
     * 向量状态编码（Vector Status）
     * 切片向量化的状态编码
     */
    private Integer vectorStatus;

    /**
     * 向量状态名称（Vector Status Name）
     * 切片向量化状态的可读名称
     */
    private String vectorStatusName;

    /**
     * 切片文本内容（Chunk Text）
     * 切片的实际文本内容
     */
    private String chunkText;
}
