package ai.knowhub.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 文档处理策略确认DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"确认文档处理策略"请求时所需的参数。
 * 在RAG系统中，文档上传后需要经过策略规划（确定如何切分、使用什么处理方式），
 * 用户确认策略方案后，系统才会按照该方案执行文档处理和索引构建。
 *
 * 使用场景：系统为文档生成处理策略后，管理端查看并调整策略方案，
 * 然后点击"确认"按钮提交。本DTO包含了用户确认后的策略信息。
 *
 * 文档处理策略包括两个流水线：
 * 
 *   parentSteps（父块流水线）：对文档的父级块（较大的文本段落）的处理步骤
 *   childSteps（子块流水线）：对文档的子级块（较小的文本片段）的处理步骤
 * 
 * 例如：父块流水线可能是"分段 -> 摘要"，子块流水线可能是"分句 -> 向量化"。
 *
 * 涉及的校验注解说明：
 * 
 *   @NotNull：字段不能为null
 *   @NotEmpty：集合/数组不能为null且不能为空（size > 0）
 *   @Valid：级联校验——会继续校验List中每个元素内部的校验注解
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentStrategyConfirmDto {

    /**
     * 文档ID（必填）
     *
     * 要确认处理策略的文档的唯一标识。
     * @NotNull 校验：不能为null。
     */
    @NotNull(message = "文档id不能为空")
    private Long documentId;

    /**
     * 基础方案ID（必填）
     *
     * 用户基于哪个方案进行调整确认。系统会先生成一个基础方案，
     * 用户可以在此基础上微调，basePlanId记录了原始方案的ID。
     * @NotNull 校验：不能为null。
     */
    @NotNull(message = "基础方案id不能为空")
    private Long basePlanId;

    /**
     * 调整说明（可选）
     *
     * 用户对基础方案所做的调整说明文本。例如"将分块大小从500改为300"。
     * 用于记录用户对默认方案的修改内容，便于后续追溯。
     */
    private String adjustNote;

    /**
     * 操作人ID（可选）
     *
     * 执行确认操作的用户标识，用于记录操作日志。
     */
    private Long operatorId;

    /**
     * 父块处理流水线步骤列表（必填，至少1个步骤）
     *
     * 文档父级块（parent block）的处理步骤列表。父块是文档切分后的较大文本段落。
     * 每个步骤 DocumentStrategyStepItemDto 定义了步骤编号和策略类型。
     * 例如：[步骤1: 分段策略, 步骤2: 摘要策略]
     *
     * 校验说明：
     * 
     *   @Valid：级联校验，会触发List中每个DocumentStrategyStepItemDto内部的校验
     *   @NotEmpty：列表不能为null且不能为空（至少包含1个步骤）
     */
    @Valid
    @NotEmpty(message = "父块流水线不能为空")
    private List<DocumentStrategyStepItemDto> parentSteps;

    /**
     * 子块处理流水线步骤列表（必填，至少1个步骤）
     *
     * 文档子级块（child block）的处理步骤列表。子块是从父块中进一步切分出的较小文本片段。
     * 通常子块用于向量化存储，以便进行语义检索。
     *
     * 校验说明同parentSteps。
     */
    @Valid
    @NotEmpty(message = "子块流水线不能为空")
    private List<DocumentStrategyStepItemDto> childSteps;
}
