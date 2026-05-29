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
 * 知识主题节点实体类。
 *
 * 对应数据库表 knowhub_knowledge_topic_node，记录知识体系中的主题（Topic）节点。
 *
 * 「知识主题」是知识范围下的细分领域，类似于图书馆的「小类」。
 * 每个主题归属于一个知识范围，代表该范围下的一个具体知识领域。
 *
 * 示例：
 * 
 *   「财务」范围下：「报销流程」「税务政策」「预算编制」「财务报表」等主题。
 *   「技术」范围下：「Java 开发规范」「数据库设计」「微服务架构」等主题。
 * 主题是 RAG 检索的关键路由节点：
 * 
 *   用户提问时，系统通过知识路由索引匹配最相关的主题。
 *   确定主题后，系统只在该主题关联的文档中进行语义检索。
 *   这样可以大幅缩小检索范围，提高检索精确率和响应速度。
 * 主题的高级属性：
 * 
 *   answerShape：期望的答案形态，如「步骤列表」「对比表格」「解释说明」等，
 *       用于指导 LLM 生成更符合期望的回答格式。
 *   executionPreference：执行偏好，如「优先查文档」「需要计算」「需要代码示例」等，
 *       用于指导 RAG 系统的执行策略。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_knowledge_topic_node")
@EqualsAndHashCode(callSuper = true)
public class KnowHubKnowledgeTopicNode extends BaseTableData {

    /**
     * 主题节点主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 主题编码。
     * 主题的唯一标识符，如 "finance.reimbursement"、"tech.java-spec" 等。
     * 使用层级编码风格，便于识别所属范围。
     */
    private String topicCode;

    /**
     * 主题名称。
     * 主题的显示名称，如 "报销流程"、"Java 开发规范" 等。
     */
    private String topicName;

    /**
     * 所属范围编码。
     * 关联到 KnowHubKnowledgeScopeNode 表的 scopeCode 字段。
     * 标识此主题归属于哪个知识范围。
     */
    private String scopeCode;

    /**
     * 主题描述。
     * 对此主题的详细说明，帮助理解主题的边界和包含内容。
     * 描述文本会被索引到 Elasticsearch，用于语义匹配。
     */
    private String description;

    /**
     * 主题别名。
     * 逗号分隔的别名列表，如 "报销流程" 的别名可以是 "报销,差旅报销,费用报销"。
     * 别名用于扩大路由匹配范围。
     */
    private String aliases;

    /**
     * 典型问题示例。
     * 逗号分隔的示例问题列表，如 "差旅费怎么报销,报销需要什么材料"。
     * 示例问题被索引到 Elasticsearch，用于语义匹配用户的真实问题。
     */
    private String examples;

    /**
     * 期望的答案形态。
     * 描述此主题的问题应该以什么形式回答，如 "步骤列表"、"对比表格"、"代码示例" 等。
     * 此信息会传递给 LLM，指导其生成更符合期望的回答格式。
     */
    private String answerShape;

    /**
     * 执行偏好。
     * 描述处理此主题问题时的执行策略偏好，如 "优先查文档"、"需要计算"、"需要代码示例" 等。
     * 此信息用于指导 RAG 系统选择合适的执行策略。
     */
    private String executionPreference;

    /**
     * 排序序号。
     * 控制主题在列表中的显示顺序，值越小越靠前。
     */
    private Integer sortOrder;
}
