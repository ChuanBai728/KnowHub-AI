package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 知识域保存DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"新增或修改知识域"请求时所需的参数。
 * 知识域（Knowledge Scope）是RAG知识库的顶层分类结构，用于组织和管理文档。
 * 本DTO既支持新增（id为空）也支持修改（id不为空），是典型的"保存"（Save）语义DTO。
 *
 * 使用场景：管理端在知识域管理页面新增知识域或编辑已有知识域信息时使用。
 *
 * 知识域层级结构说明：
 * 知识域支持父子层级关系（通过parentScopeCode字段），
 * 形成树状结构。例如：
 * 
 *   技术文档 (scopeCode: TECH)
 *     - Java技术 (scopeCode: TECH_JAVA, parentScopeCode: TECH)
 *     - Python技术 (scopeCode: TECH_PYTHON, parentScopeCode: TECH)
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class KnowledgeScopeSaveDto {

    /**
     * 知识域ID（可选）
     *
     * 知识域的数据库主键ID。
     * 如果为空，表示新增操作；如果不为空，表示修改已有知识域。
     * 这是"新增/修改合一"设计模式的常见做法。
     */
    private String id;

    /**
     * 知识域编码
     *
     * 知识域的唯一业务编码，用于程序中引用和关联。
     * 编码通常使用大写字母和下划线，如"TECH_DOC"、"PRODUCT_MANUAL"等。
     */
    private String scopeCode;

    /**
     * 知识域名称
     *
     * 知识域的显示名称，用于前端页面展示。例如"技术文档"、"产品手册"等。
     */
    private String scopeName;

    /**
     * 父级知识域编码（可选）
     *
     * 如果本知识域是某个父域的子域，则填写父域的scopeCode。
     * 如果为空，表示本知识域是顶级域。
     */
    private String parentScopeCode;

    /**
     * 知识域描述
     *
     * 对知识域内容范围的文字描述，帮助用户和AI理解该知识域包含哪些内容。
     * 例如"包含所有Java、Python等编程语言的技术文档和教程"。
     */
    private String description;

    /**
     * 别名列表
     *
     * 知识域的别名，多个别名之间用分隔符连接。
     * 别名用于辅助路由匹配，当用户使用不同的称呼时也能正确路由到该知识域。
     * 例如知识域"技术文档"的别名可能是"技术资料,开发文档,技术手册"。
     */
    private String aliases;

    /**
     * 示例列表
     *
     * 属于该知识域的典型问题或查询示例，用于辅助AI进行路由判断。
     * 例如"如何配置Spring Boot？"、"Java多线程怎么实现？"等。
     */
    private String examples;

    /**
     * 排序序号
     *
     * 知识域在列表中的显示顺序。值越小越靠前显示。
     * 使用String类型，后端可能需要转换为数值进行排序。
     */
    private String sortOrder;

    /**
     * 操作人ID
     *
     * 执行保存操作的用户标识，用于记录操作日志。
     */
    private String operatorId;
}
