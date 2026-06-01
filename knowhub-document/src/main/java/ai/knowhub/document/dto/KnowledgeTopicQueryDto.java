package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 知识主题查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询知识主题列表"时所需的参数。
 * 知识主题（Knowledge Topic）隶属于某个知识域（Knowledge Scope），
 * 本DTO通过scopeCode指定要查询哪个知识域下的主题列表。
 *
 * 使用场景：管理端在知识主题管理页面，选择某个知识域后，
 * 加载该域下的所有主题列表时使用。
 *
 * 知识层级结构：知识域(Scope) -> 知识主题(Topic) -> 文档(Document)
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class KnowledgeTopicQueryDto {

    /**
     * 知识域编码
     *
     * 指定要查询哪个知识域下的主题。通过scopeCode关联到知识域，
     * 查询该域下所有知识主题。
     */
    private String scopeCode;
}
