package ai.knowhub.document.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档检索请求（Document Retrieve Request）
 *
 * 【类的作用】
 * 封装一次完整的文档检索请求所需的所有参数，包括用户原始问题、
 * 检索查询语句、文档/任务范围限制、返回数量、过滤条件等。
 *
 * 【在架构中的角色】
 * 位于 RAG（检索增强生成）流程的入口，由上层业务（如对话控制器）
 * 构造此请求对象，然后传递给检索引擎执行检索。
 *
 * 【字段说明】
 * - question：用户的原始问题
 * - retrievalQuery：经过查询改写后的检索查询（可能与 question 不同，经过 HyDE 等优化）
 * - documentId / documentIds：限定在某个/某些文档范围内检索
 * - taskId / taskIds：限定在某个/某些解析任务范围内检索
 * - topK：返回最相关的前 K 个结果
 * - filters：多维度过滤条件（见 DocumentRetrieveFilters）
 * - queryContextHints：查询上下文提示，用于辅助检索（如用户的历史对话摘要）
 *
 * 【设计模式】
 * - 封装模式：将检索参数封装为一个对象，避免方法参数列表过长
 */
@Data
@NoArgsConstructor
public class DocumentRetrieveDto {

    /**
     * 用户的原始问题
     * 直接来自用户的自然语言输入，未经过任何处理。
     */
    private String question;

    /**
     * 检索查询语句
     * 经过查询改写（Query Rewriting）后的检索文本，
     * 可能经过 HyDE（假设性文档嵌入）等技术优化，
     * 用于提高向量检索的召回率和精准度。
     */
    private String retrievalQuery;

    /**
     * 单个文档 ID
     * 当只检索某个特定文档时使用，与 documentIds 互为备选。
     */
    private Long documentId;

    /**
     * 单个任务 ID
     * 当只检索某个特定解析任务的结果时使用，与 taskIds 互为备选。
     */
    private Long taskId;

    /**
     * 多个文档 ID 列表
     * 当需要在多个文档范围内检索时使用，
     * 优先级高于 documentId（见 resolvedDocumentIds 方法）。
     */
    private List<Long> documentIds;

    /**
     * 多个任务 ID 列表
     * 当需要在多个解析任务范围内检索时使用，
     * 优先级高于 taskId（见 resolvedTaskIds 方法）。
     */
    private List<Long> taskIds;

    /**
     * 返回结果数量上限
     * 检索引擎返回最相关的前 K 个文档片段。
     */
    private int topK;

    /**
     * 多维度过滤条件
     * 包含文档名称、业务类别、标签、章节路径等多种过滤提示。
     */
    private DocumentRetrieveFilters filters;

    /**
     * 查询上下文提示列表
     * 来自对话历史或用户画像的辅助信息，
     * 帮助检索引擎更好地理解用户的意图，提高检索质量。
     */
    private List<String> queryContextHints;

    /**
     * 全参构造器
     *
     * 【注意】在构造时会自动将单个 documentId/taskId 转换为列表形式，
     * 确保 documentIds 和 taskIds 字段始终有值，避免下游空指针。
     *
     * @param question           用户原始问题
     * @param retrievalQuery     检索查询语句
     * @param documentId         单个文档 ID
     * @param taskId             单个任务 ID
     * @param topK               返回结果数量上限
     * @param filters            多维度过滤条件
     * @param queryContextHints  查询上下文提示
     */
    public DocumentRetrieveDto(String question,
                               String retrievalQuery,
                               Long documentId,
                               Long taskId,
                               int topK,
                               DocumentRetrieveFilters filters,
                               List<String> queryContextHints) {
        this.question = question;
        this.retrievalQuery = retrievalQuery;
        this.documentId = documentId;
        this.taskId = taskId;
        // 如果传入了单个 documentId，则自动包装为单元素列表
        this.documentIds = documentId == null ? List.of() : List.of(documentId);
        // 如果传入了单个 taskId，则自动包装为单元素列表
        this.taskIds = taskId == null ? List.of() : List.of(taskId);
        this.topK = topK;
        this.filters = filters;
        this.queryContextHints = queryContextHints;
    }

    /**
     * 解析并返回有效的文档 ID 列表
     *
     * 【解析逻辑（优先级从高到低）】
     * 1. 如果 documentIds 不为空，直接返回 documentIds
     * 2. 如果 documentIds 为空但 documentId 有值，返回单元素列表 [documentId]
     * 3. 如果都没有值，返回空列表
     *
     * @return 有效的文档 ID 列表，不会返回 null
     */
    public List<Long> resolvedDocumentIds() {
        return documentIds != null && !documentIds.isEmpty()
            ? documentIds
            : (documentId == null ? List.of() : List.of(documentId));
    }

    /**
     * 解析并返回有效的任务 ID 列表
     *
     * 【解析逻辑（优先级从高到低）】
     * 1. 如果 taskIds 不为空，直接返回 taskIds
     * 2. 如果 taskIds 为空但 taskId 有值，返回单元素列表 [taskId]
     * 3. 如果都没有值，返回空列表
     *
     * @return 有效的任务 ID 列表，不会返回 null
     */
    public List<Long> resolvedTaskIds() {
        return taskIds != null && !taskIds.isEmpty()
            ? taskIds
            : (taskId == null ? List.of() : List.of(taskId));
    }
}
