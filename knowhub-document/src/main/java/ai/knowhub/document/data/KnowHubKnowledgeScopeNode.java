package ai.knowhub.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

/**
 * 知识范围节点实体类。
 *
 * 对应数据库表 knowhub_knowledge_scope_node，记录知识体系中的范围（Scope）节点。
 *
 * 「知识范围」是知识管理体系的顶层分类，类似于图书馆的「大类」。
 * 每个范围代表一个独立的知识领域，如「财务」「技术」「人力资源」「产品」等。
 *
 * 知识范围的作用：
 * 
 *   <b>知识分类</b>：将企业知识按领域进行分类管理。
 *   <b>权限控制</b>：不同范围的知识可以设置不同的访问权限。
 *   <b>检索优化</b>：用户提问时，系统先确定问题属于哪个范围，
 *       然后只在该范围的文档中检索，大幅提高检索精确率。
 *   <b>知识路由</b>：范围信息被索引到 Elasticsearch 知识路由索引中，
 *       用于问题的自动路由。
 * 范围支持层级结构：
 * 
 *   通过 parentScopeCode 字段建立父子关系。
 *   例如：「财务」下可以有「会计」「税务」「审计」等子范围。
 * 范围还支持别名和示例：
 * 
 *   aliases：范围的其他称呼，用于扩大路由匹配范围。
 *   examples：该范围下的典型问题示例，用于优化路由准确性。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_knowledge_scope_node")
@EqualsAndHashCode(callSuper = true)
public class KnowHubKnowledgeScopeNode extends BaseTableData {

    /**
     * 范围节点主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 范围编码。
     * 范围的唯一标识符，如 "finance"、"technology"、"hr" 等。
     * 使用英文编码而非中文名称，避免编码中出现特殊字符。
     */
    private String scopeCode;

    /**
     * 范围名称。
     * 范围的显示名称，如 "财务"、"技术"、"人力资源" 等。
     */
    private String scopeName;

    /**
     * 父范围编码。
     * 关联到本表的另一个记录，表示此范围的父级范围。
     * 顶级范围的 parentScopeCode 为 null。
     */
    private String parentScopeCode;

    /**
     * 范围描述。
     * 对此范围的详细说明，帮助理解范围的边界和包含内容。
     * 描述文本会被索引到 Elasticsearch，用于语义匹配。
     */
    private String description;

    /**
     * 范围别名。
     * 逗号分隔的别名列表，如 "finance" 范围的别名可以是 "财务,会计,账务"。
     * 别名用于扩大路由匹配范围——用户可能用不同的称呼来指代同一个范围。
     */
    private String aliases;

    /**
     * 典型问题示例。
     * 逗号分隔的示例问题列表，如 "如何报销差旅费,年度预算怎么编"。
     * 示例问题被索引到 Elasticsearch，用于语义匹配用户的真实问题。
     */
    private String examples;

    /**
     * 排序序号。
     * 控制范围在列表中的显示顺序，值越小越靠前。
     */
    private Integer sortOrder;
}
