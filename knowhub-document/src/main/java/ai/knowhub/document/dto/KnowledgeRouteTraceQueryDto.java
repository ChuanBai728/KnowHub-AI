package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 知识路由追踪查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询知识路由追踪记录"时所需的参数，支持分页查询。
 * 知识路由（Knowledge Route）是RAG系统中将用户查询路由到合适知识域/主题的过程。
 * 追踪记录（Trace）记录了每次路由的详细信息，便于分析和调试路由策略的效果。
 *
 * 使用场景：管理端查看知识路由的历史追踪记录时使用，可以按会话ID、路由模式、
 * 路由状态等条件筛选，用于分析路由策略的准确性和效果。
 *
 * 注意：本DTO中分页参数使用String类型而非Integer，可能是前端表单提交的原始格式。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class KnowledgeRouteTraceQueryDto {

    /**
     * 会话ID（可选）
     *
     * 按会话ID筛选路由追踪记录。同一个会话中可能有多次路由操作，
     * 传入conversationId可以查看某个特定会话的所有路由记录。
     */
    private String conversationId;

    /**
     * 路由模式（可选）
     *
     * 按路由模式筛选。不同的路由模式代表不同的路由策略，
     * 例如"关键词匹配"、"语义匹配"、"混合模式"等。
     */
    private String mode;

    /**
     * 路由状态（可选）
     *
     * 按路由状态筛选。例如"成功"、"失败"、"降级"等状态，
     * 用于快速定位路由异常的记录。
     */
    private String routeStatus;

    /**
     * 页码（可选）
     *
     * 分页查询的页码。注意使用String类型，后端需要转换为Integer。
     */
    private String pageNo;

    /**
     * 每页条数（可选）
     *
     * 分页查询时每页返回的记录数。
     */
    private String pageSize;
}
