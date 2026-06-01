package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档索引构建DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"构建文档索引"请求时所需的参数。
 * 在RAG系统中，文档上传后需要经过处理（切分、向量化等）才能被检索到，
 * 这个过程称为"索引构建"（Index Build）。本DTO封装了触发索引构建所需的参数。
 *
 * 使用场景：管理端确认文档的处理策略后，点击"构建索引"按钮触发。
 * 系统会根据指定的方案（planId）对文档进行切分、向量化等处理。
 *
 * RAG索引构建流程大致如下：
 * 1. 文档上传 -> 2. 策略规划（确定如何切分） -> 3. 确认方案 -> 4. 构建索引（本步骤）
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentIndexBuildDto {

    /**
     * 文档ID（必填）
     *
     * 要构建索引的文档的唯一标识。
     * @NotNull 校验：不能为null。
     */
    @NotNull(message = "文档id不能为空")
    private Long documentId;

    /**
     * 方案ID（必填）
     *
     * 文档处理方案的唯一标识。在构建索引之前，系统会先规划一个处理方案（StrategyPlan），
     * 包括如何切分文档、使用什么策略等。planId指定了使用哪个方案来构建索引。
     * @NotNull 校验：不能为null。
     */
    @NotNull(message = "方案id不能为空")
    private Long planId;

    /**
     * 操作人ID（可选）
     *
     * 执行索引构建操作的用户标识，用于记录操作日志和审计追踪。
     */
    private Long operatorId;
}
