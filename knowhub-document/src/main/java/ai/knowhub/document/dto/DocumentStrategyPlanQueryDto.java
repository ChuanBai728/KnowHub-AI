package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档处理策略方案查询DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"查询某个文档的处理策略方案"时所需的参数。
 * 在RAG系统中，文档上传后系统会自动规划一个处理策略方案（StrategyPlan），
 * 包括文档如何切分、使用什么处理流水线等。管理端可以查看该方案的详情。
 *
 * 使用场景：文档上传后，管理端查看系统为该文档生成的处理策略方案时使用。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentStrategyPlanQueryDto {

    /**
     * 文档ID（必填）
     *
     * 要查询处理策略方案的文档的唯一标识。
     * @NotNull 校验：不能为null。
     */
    @NotNull(message = "文档id不能为空")
    private Long documentId;
}
