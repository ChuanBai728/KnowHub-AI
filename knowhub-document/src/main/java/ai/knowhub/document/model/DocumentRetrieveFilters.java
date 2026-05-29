package ai.knowhub.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档检索过滤器（Document Retrieve Filters）
 *
 * 【类的作用】
 * 封装 RAG（检索增强生成）流程中对文档检索结果的多维度过滤条件。
 * 在检索引擎返回候选文档片段后，可以用这些过滤条件进一步缩小范围，
 * 确保返回给大模型的上下文与用户问题最相关。
 *
 * 【在架构中的角色】
 * 属于文档检索请求模型层，被 DocumentRetrieveDto 引用。
 * 检索引擎（如 Elasticsearch、向量数据库）根据这些 hints（提示）构建过滤查询。
 *
 * 【设计模式】
 * - 建造者模式（Builder Pattern）：通过 @Builder 注解支持链式构建对象
 * - 每个字段都用 @Builder.Default 设置了空列表作为默认值，避免空指针
 *
 * 【Lombok 注解说明】
 * - @Data：自动生成 getter/setter/toString/equals/hashCode
 * - @Builder：生成建造者模式代码
 * - @NoArgsConstructor：生成无参构造器
 * - @AllArgsConstructor：生成全参构造器
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRetrieveFilters {

    /**
     * 文档名称提示列表
     * 用于按文档名称进行模糊匹配过滤，例如用户提到"员工手册"时，
     * 可以将"员工手册"作为 hint 传入，优先检索该文档的内容。
     */
    @Builder.Default
    private List<String> documentNameHints = new ArrayList<>();

    /**
     * 业务类别提示列表
     * 按业务分类过滤文档，例如"人力资源"、"财务管理"等，
     * 帮助检索引擎定位到特定业务领域的文档。
     */
    @Builder.Default
    private List<String> businessCategoryHints = new ArrayList<>();

    /**
     * 文档标签提示列表
     * 按文档标签过滤，标签是对文档的关键词标记，
     * 例如"政策"、"流程"、"FAQ"等。
     */
    @Builder.Default
    private List<String> documentTagHints = new ArrayList<>();

    /**
     * 章节路径提示列表
     * 按文档的章节路径过滤，章节路径表示文档内部的层级结构，
     * 例如"第一章/第二节/1.2.3"，用于精准定位文档中的特定位置。
     */
    @Builder.Default
    private List<String> sectionPathHints = new ArrayList<>();

    /**
     * 规范路径提示列表
     * 规范路径（canonical path）是文档节点的标准化唯一路径标识，
     * 与 sectionPath 不同，它不依赖于文档的目录编号格式，
     * 而是使用系统内部统一的路径表示方式。
     */
    @Builder.Default
    private List<String> canonicalPathHints = new ArrayList<>();

    /**
     * 结构节点 ID 提示列表
     * 文档在解析后会形成树形结构（如章节、段落），每个节点有唯一 ID。
     * 通过节点 ID 可以精确过滤到文档结构树中的特定节点。
     */
    @Builder.Default
    private List<Long> structureNodeIdHints = new ArrayList<>();

    /**
     * 条目索引提示列表
     * 文档中每个内容块（chunk）在原文中的顺序索引，
     * 用于按位置过滤，例如只检索前 N 个块或特定位置的块。
     */
    @Builder.Default
    private List<Integer> itemIndexHints = new ArrayList<>();

    /**
     * 年份提示列表
     * 按文档关联的年份过滤，适用于时效性强的文档场景，
     * 例如用户问"2024年的政策"时，可以限定只检索2024年的文档。
     */
    @Builder.Default
    private List<String> yearHints = new ArrayList<>();

    /**
     * 判断所有过滤条件是否都为空
     *
     * 【使用场景】
     * 在构建检索查询前调用此方法，如果所有条件都为空，
     * 则表示不需要额外过滤，直接返回全量检索结果即可。
     *
     * @return true 表示没有任何过滤条件，false 表示至少有一个过滤条件被设置了
     */
    public boolean isEmpty() {
        return documentNameHints.isEmpty()
            && businessCategoryHints.isEmpty()
            && documentTagHints.isEmpty()
            && sectionPathHints.isEmpty()
            && canonicalPathHints.isEmpty()
            && structureNodeIdHints.isEmpty()
            && itemIndexHints.isEmpty()
            && yearHints.isEmpty();
    }
}
