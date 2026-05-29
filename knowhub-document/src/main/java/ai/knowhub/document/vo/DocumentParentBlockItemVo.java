package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档父级文本块列表项返回值对象（Document Parent Block Item Vo）
 *
 * 【类的作用】
 * 用于展示文档中父级文本块的信息，包括文本块的位置、来源类型、
 * 文本统计和包含的子切片范围。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于父块列表页面的数据展示。
 * 父块是切片的上一级组织单位，一个父块包含多个子切片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParentBlockItemVo {

    /**
     * 父块ID（Parent Block ID）
     * 父级文本块的唯一标识
     */
    private Long parentBlockId;

    /**
     * 父块编号（Parent Block No）
     * 父块在文档中的顺序编号
     */
    private Integer parentBlockNo;

    /**
     * 章节路径（Section Path）
     * 父块在文档结构树中的路径
     */
    private String sectionPath;

    /**
     * 来源类型编码（Source Type）
     * 父块来源的编码值
     */
    private Integer sourceType;

    /**
     * 来源类型名称（Source Type Name）
     * 父块来源的可读名称
     */
    private String sourceTypeName;

    /**
     * 字符数（Character Count）
     * 父块文本的字符数
     */
    private Integer charCount;

    /**
     * Token 数量（Token Count）
     * 父块文本的 token 数量
     */
    private Integer tokenCount;

    /**
     * 子切片数量（Child Count）
     * 该父块包含的子切片数量
     */
    private Integer childCount;

    /**
     * 起始切片编号（Start Chunk No）
     * 该父块中第一个子切片的编号
     */
    private Integer startChunkNo;

    /**
     * 结束切片编号（End Chunk No）
     * 该父块中最后一个子切片的编号
     */
    private Integer endChunkNo;

    /**
     * 父块文本内容（Parent Text）
     * 父块的完整文本内容
     */
    private String parentText;
}
