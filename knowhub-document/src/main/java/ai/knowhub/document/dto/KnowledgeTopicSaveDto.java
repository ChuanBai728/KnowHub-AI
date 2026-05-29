package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 知识主题保存DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"新增或修改知识主题"请求时所需的参数。
 * 知识主题（Knowledge Topic）是知识域下的细分分类，本DTO既支持新增也支持修改。
 *
 * 使用场景：管理端在知识主题管理页面新增主题或编辑已有主题信息时使用。
 *
 * 知识层级结构：知识域(Scope) -> 知识主题(Topic) -> 文档(Document)
 * 每个主题归属于某个知识域（通过scopeCode关联），主题下可以关联多个文档。
 *
 * 主题的answerShape和executionPreference字段用于指导AI在该主题下的回答风格和执行偏好，
 * 这是RAG系统中"主题感知"检索的重要配置。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class KnowledgeTopicSaveDto {

    /**
     * 知识主题ID（可选）
     *
     * 主题的数据库主键ID。为空表示新增，不为空表示修改已有主题。
     */
    private String id;

    /**
     * 知识主题编码
     *
     * 主题的唯一业务编码，用于程序中引用和关联。
     * 例如"SPRING_BOOT"、"MYBATIS"、"REDIS"等。
     */
    private String topicCode;

    /**
     * 知识主题名称
     *
     * 主题的显示名称，用于前端页面展示。例如"Spring Boot"、"MyBatis"等。
     */
    private String topicName;

    /**
     * 所属知识域编码
     *
     * 该主题归属于哪个知识域。通过scopeCode关联到知识域。
     */
    private String scopeCode;

    /**
     * 主题描述
     *
     * 对主题内容范围的文字描述，帮助AI理解该主题包含哪些知识。
     * 例如"Spring Boot框架的配置、使用和最佳实践"。
     */
    private String description;

    /**
     * 别名列表
     *
     * 主题的别名，用于辅助路由匹配。
     * 例如主题"Spring Boot"的别名可能是"springboot,spring-boot,sb框架"。
     */
    private String aliases;

    /**
     * 示例列表
     *
     * 属于该主题的典型问题或查询示例，用于辅助AI路由判断。
     * 例如"Spring Boot怎么配置数据源？"、"如何打包Spring Boot应用？"等。
     */
    private String examples;

    /**
     * 回答形态（Answer Shape）
     *
     * 指导AI在该主题下生成回答时应采用的形态或格式。
     * 例如"步骤式回答"、"对比表格"、"代码示例为主"等。
     * 这是RAG系统中个性化的高级配置。
     */
    private String answerShape;

    /**
     * 执行偏好
     *
     * 指导AI在处理该主题相关查询时的执行策略偏好。
     * 例如"优先使用向量检索"、"优先使用关键词检索"、"混合检索"等。
     */
    private String executionPreference;

    /**
     * 排序序号
     *
     * 主题在列表中的显示顺序。值越小越靠前。
     */
    private String sortOrder;

    /**
     * 操作人ID
     *
     * 执行保存操作的用户标识，用于记录操作日志。
     */
    private String operatorId;
}
