package ai.knowhub.chat.rag.support;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.document.support.DocumentKnowledgeMetadataKeys;
import org.springframework.ai.document.Document;

import java.util.Map;

/**
 * 【搜索引用映射器 — 将 Spring AI Document 转换为 SearchReference】
 *
 * 这个类负责将 Spring AI 框架的 {@link Document} 对象转换为
 * 业务层的 {@link SearchReference} 对象。
 *
 * 为什么要转换？
 * Spring AI 的 Document 是通用的文档片段对象，包含文本和元数据。
 * 但 RAG Prompt 组装需要更丰富的引用信息（如引用编号、来源类型、
 * 文档名称、章节路径等），这些信息需要从 Document 的 metadata 中提取。
 *
 * 支持两种来源类型：
 * 1. DOCUMENT：本地文档检索结果
 *    - 包含文档ID、文档名称、章节路径、chunk 信息等
 * 2. WEB：网络搜索结果（如 Tavily 搜索）
 *    - 包含标题、URL、工具名称等
 *
 * 设计模式：映射器模式（Mapper Pattern），静态工具方法。
 *
 * 在 RAG 流水线中的位置：
 * RagRetrievalEngine（检索引擎）-> 【本类：Document -> SearchReference】
 * -> RagPromptAssemblyService（Prompt 组装）
 */
public final class SearchReferenceMapper {

    /** 私有构造函数，防止实例化 */
    private SearchReferenceMapper() {
    }

    /**
     * 将 Spring AI Document 转换为 SearchReference。
     *
     * 转换流程：
     * 1. 从 metadata 中提取来源类型（DOCUMENT 或 WEB）
     * 2. 设置通用字段（引用编号、文本片段、子问题信息等）
     * 3. 根据来源类型设置不同的字段
     *
     * @param document        Spring AI 文档对象
     * @param subQuestionIndex 子问题编号
     * @param subQuestion      子问题文本
     * @param referenceNumber  引用编号
     * @return SearchReference 业务层的搜索引用对象
     */
    public static SearchReference fromDocument(Document document,
                                               int subQuestionIndex,
                                               String subQuestion,
                                               int referenceNumber) {
        Map<String, Object> metadata = document.getMetadata();
        // 提取来源类型，默认为 DOCUMENT
        String sourceType = asText(metadata.get(DocumentKnowledgeMetadataKeys.SOURCE_TYPE), "DOCUMENT");
        SearchReference reference = new SearchReference();
        // 设置通用字段
        reference.setReferenceId(String.valueOf(referenceNumber));
        reference.setSourceType(sourceType);
        reference.setSnippet(document.getText());
        reference.setSubQuestionIndex(subQuestionIndex);
        reference.setSubQuestion(subQuestion);
        reference.setChannel(asText(metadata.get(DocumentKnowledgeMetadataKeys.CHANNEL), "vector"));
        reference.setScore(asDouble(metadata.get(DocumentKnowledgeMetadataKeys.SCORE)));

        // WEB 类型的特殊处理
        if ("WEB".equalsIgnoreCase(sourceType)) {
            reference.setTitle(asText(metadata.get(DocumentKnowledgeMetadataKeys.TITLE), "网页来源"));
            reference.setUrl(asText(metadata.get(DocumentKnowledgeMetadataKeys.URL), ""));
            reference.setToolName(asText(metadata.get(DocumentKnowledgeMetadataKeys.TOOL_NAME), "tavily_search"));
            return reference;
        }

        // DOCUMENT 类型的字段设置
        reference.setTitle(asText(metadata.get(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME), "文档片段"));
        reference.setDocumentId(asLong(metadata.get(DocumentKnowledgeMetadataKeys.DOCUMENT_ID)));
        reference.setDocumentName(asText(metadata.get(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME), ""));
        reference.setParentBlockId(asLong(metadata.get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID)));
        reference.setParentBlockNo(asInteger(metadata.get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO)));
        reference.setChunkId(asLong(metadata.get(DocumentKnowledgeMetadataKeys.CHUNK_ID)));
        reference.setChunkNo(asInteger(metadata.get(DocumentKnowledgeMetadataKeys.CHUNK_NO)));
        reference.setSectionPath(asText(metadata.get(DocumentKnowledgeMetadataKeys.SECTION_PATH), ""));
        reference.setStructureNodeId(asLong(metadata.get(DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID)));
        reference.setStructureNodeType(asInteger(metadata.get(DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_TYPE)));
        reference.setCanonicalPath(asText(metadata.get(DocumentKnowledgeMetadataKeys.CANONICAL_PATH), ""));
        reference.setItemIndex(asInteger(metadata.get(DocumentKnowledgeMetadataKeys.ITEM_INDEX)));
        reference.setKnowledgeScopeCode(asText(metadata.get(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_CODE), ""));
        reference.setKnowledgeScopeName(asText(metadata.get(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_NAME), ""));
        return reference;
    }

    /**
     * 安全的文本转换：null 返回默认值。
     */
    private static String asText(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    /**
     * 安全的 Long 转换：非 Number 类型返回 null。
     */
    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 安全的 Integer 转换：非 Number 类型返回 null。
     */
    private static Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /**
     * 安全的 Double 转换：非 Number 类型返回 null。
     */
    private static Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
