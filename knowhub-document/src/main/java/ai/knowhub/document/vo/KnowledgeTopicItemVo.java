package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识主题项返回值对象（Knowledge Topic Item Vo）
 *
 * 【类的作用】
 * 用于展示知识主题的信息。知识主题是比知识范围更细粒度的分类，
 * 用于进一步精确路由和检索。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于知识主题管理页面的数据展示。
 *
 * 【关键概念】
 * - 知识主题（Topic）：知识范围下的细分主题
 * - 答案形态（Answer Shape）：该主题期望的答案格式
 * - 执行偏好（Execution Preference）：该主题的执行策略偏好
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeTopicItemVo {

    /** 记录ID */
    private String id;

    /**
     * 主题编码（Topic Code）
     * 知识主题的唯一编码标识
     */
    private String topicCode;

    /**
     * 主题名称（Topic Name）
     * 知识主题的显示名称
     */
    private String topicName;

    /**
     * 所属范围编码（Scope Code）
     * 该主题所属的知识范围编码
     */
    private String scopeCode;

    /**
     * 主题描述（Description）
     * 知识主题的详细描述
     */
    private String description;

    /**
     * 别名（Aliases）
     * 主题的同义词，多个别名用逗号分隔
     */
    private String aliases;

    /**
     * 示例问题（Examples）
     * 属于该主题的典型问题示例
     */
    private String examples;

    /**
     * 答案形态（Answer Shape）
     * 该主题期望的答案格式（如列表、段落、表格等）
     */
    private String answerShape;

    /**
     * 执行偏好（Execution Preference）
     * 该主题的执行策略偏好
     */
    private String executionPreference;

    /**
     * 排序序号（Sort Order）
     * 主题在列表中的显示顺序
     */
    private String sortOrder;
}
