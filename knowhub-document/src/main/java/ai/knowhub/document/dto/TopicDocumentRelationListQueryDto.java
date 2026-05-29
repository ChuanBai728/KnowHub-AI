package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 主题-文档关联列表查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询某个知识主题下关联的文档列表"时所需的参数。
 * 在RAG系统中，知识主题（Topic）和文档（Document）之间存在多对多的关联关系，
 * 一个主题可以关联多个文档，一个文档也可以属于多个主题。
 *
 * 使用场景：管理端查看某个知识主题下关联了哪些文档时使用。
 * 这有助于了解知识库的文档覆盖情况和主题的知识完整性。
 *
 * 涉及的设计模式：DTO模式，将查询参数封装为对象。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class TopicDocumentRelationListQueryDto {

    /**
     * 知识主题编码
     *
     * 指定要查询哪个主题下的文档关联列表。通过topicCode关联到知识主题。
     */
    private String topicCode;
}
