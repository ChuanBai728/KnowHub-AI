package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 知识域删除DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"删除知识域"请求时所需的参数。
 * 知识域（Knowledge Scope）是RAG知识库的顶层分类结构，
 * 例如"技术文档"、"产品手册"、"人力资源"等都属于不同的知识域。
 *
 * 使用场景：管理端在知识域管理页面删除某个知识域时使用。
 *
 * 注意：删除知识域是一个敏感操作，可能影响该域下所有文档和主题的检索，
 * 后端通常需要做级联处理或校验是否存在关联数据。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class KnowledgeScopeDeleteDto {

    /**
     * 知识域编码
     *
     * 要删除的知识域的唯一编码标识。使用编码而非数据库ID作为标识，
     * 说明知识域编码在系统中具有唯一性和业务含义。
     */
    private String scopeCode;

    /**
     * 操作人ID
     *
     * 执行删除操作的用户标识，用于记录操作日志和审计追踪。
     */
    private String operatorId;
}
