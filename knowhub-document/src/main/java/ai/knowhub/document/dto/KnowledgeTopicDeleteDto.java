package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 知识主题删除DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"删除知识主题"请求时所需的参数。
 * 知识主题（Knowledge Topic）是知识域下的细分分类，一个知识域可以包含多个主题。
 * 例如"技术文档"知识域下可能有"Spring Boot"、"MyBatis"、"Redis"等主题。
 *
 * 使用场景：管理端在知识主题管理页面删除某个主题时使用。
 *
 * 知识层级结构：知识域(Scope) -> 知识主题(Topic) -> 文档(Document)
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class KnowledgeTopicDeleteDto {

    /**
     * 知识主题编码
     *
     * 要删除的知识主题的唯一业务编码标识。
     */
    private String topicCode;

    /**
     * 操作人ID
     *
     * 执行删除操作的用户标识，用于记录操作日志和审计追踪。
     */
    private String operatorId;
}
