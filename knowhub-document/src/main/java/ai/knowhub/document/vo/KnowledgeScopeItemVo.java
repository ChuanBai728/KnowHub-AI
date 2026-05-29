package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识范围项返回值对象（Knowledge Scope Item Vo）
 *
 * 【类的作用】
 * 用于展示知识范围的信息。知识范围是对文档的高级分类，用于知识路由时
 * 将用户问题快速定位到相关的文档集合。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于知识范围管理页面的数据展示。
 *
 * 【关键概念】
 * - 知识范围（Scope）：文档的高级分类维度，如"产品文档"、"技术规范"等
 * - 父范围（Parent Scope）：支持范围的层级组织
 * - 别名（Aliases）：范围的同义词，用于路由匹配
 * - 示例（Examples）：属于该范围的典型问题示例
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeScopeItemVo {

    /** 记录ID */
    private String id;

    /**
     * 范围编码（Scope Code）
     * 知识范围的唯一编码标识
     */
    private String scopeCode;

    /**
     * 范围名称（Scope Name）
     * 知识范围的显示名称
     */
    private String scopeName;

    /**
     * 父范围编码（Parent Scope Code）
     * 父级范围的编码，用于层级组织
     */
    private String parentScopeCode;

    /**
     * 范围描述（Description）
     * 知识范围的详细描述
     */
    private String description;

    /**
     * 别名（Aliases）
     * 范围的同义词，多个别名用逗号分隔
     */
    private String aliases;

    /**
     * 示例问题（Examples）
     * 属于该范围的典型问题示例
     */
    private String examples;

    /**
     * 排序序号（Sort Order）
     * 范围在列表中的显示顺序
     */
    private String sortOrder;
}
