package ai.knowhub.document.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分析结果（Document Analysis Result）
 *
 * 【类的作用】
 * 存储文档解析后的分析结果，包括文本内容、统计信息（字符数、token数）、
 * 结构层级信息、内容质量评估以及提取出的结构节点候选列表。
 *
 * 【在架构中的角色】
 * 是文档处理流水线（Pipeline）的核心数据载体。文档上传后首先被解析为文本，
 * 然后通过分析器生成本对象，其中的结构节点候选列表会进一步用于构建文档结构树。
 *
 * 【关键概念】
 * - 结构层级（structureLevel）：反映文档的结构复杂度
 * - 内容质量等级（contentQualityLevel）：评估文档内容的可处理性
 * - 结构节点候选（structureNodes）：从文档中识别出的标题、列表等结构元素
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAnalysisResult {

    /**
     * 解析后的纯文本（Parsed Text）
     * 从原始文档（PDF、Word等）中提取出的纯文本内容
     */
    private String parsedText;

    /**
     * 字符数（Character Count）
     * 解析后文本的总字符数，用于统计和质量评估
     */
    private Integer charCount;

    /**
     * Token 数量（Token Count）
     * 文本经过分词后的 token 数量，用于估算 LLM 处理成本
     */
    private Integer tokenCount;

    /**
     * 结构层级（Structure Level）
     * 反映文档的结构复杂度等级，数值越高表示结构越复杂
     */
    private Integer structureLevel;

    /**
     * 内容质量等级（Content Quality Level）
     * 对文档内容质量的评估，影响后续处理策略的选择
     */
    private Integer contentQualityLevel;

    /**
     * 标题数量（Heading Count）
     * 文档中识别出的标题总数
     */
    private Integer headingCount;

    /**
     * 段落数量（Paragraph Count）
     * 文档中的段落总数
     */
    private Integer paragraphCount;

    /**
     * 最大段落长度（Max Paragraph Length）
     * 文档中最长段落的字符数，用于评估内容密度
     */
    private Integer maxParagraphLength;

    /**
     * 结构节点候选列表（Structure Nodes）
     * 从文档中提取出的所有结构节点候选，包括标题、列表项等
     * 这些候选节点会被进一步处理，最终构建为文档结构树
     */
    private List<DocumentStructureNodeCandidate> structureNodes = new ArrayList<>();
}
