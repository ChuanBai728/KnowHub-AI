package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档处理策略步骤项DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：表示文档处理策略流水线中的一个步骤。在 DocumentStrategyConfirmDto 中，
 * parentSteps和childSteps列表的每个元素都是本DTO的实例。
 *
 * 使用场景：作为策略确认DTO的嵌套对象使用，不单独作为接口入参。
 * 前端通过 @Valid 注解触发对本DTO内部字段的级联校验。
 *
 * 示例：一个文档的父块流水线可能包含以下步骤：
 * 
 *   {stepNo: 1, strategyType: 1} -> 第1步：按段落切分
 *   {stepNo: 2, strategyType: 3} -> 第2步：生成摘要
 * 
 * strategyType的具体枚举值由后端定义，不同值代表不同的处理策略。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentStrategyStepItemDto {

    /**
     * 步骤编号（可选）
     *
     * 流水线中的步骤序号，从1开始递增。表示该步骤在处理流水线中的执行顺序。
     * 例如stepNo=1表示第一步，stepNo=2表示第二步。
     */
    private Integer stepNo;

    /**
     * 策略类型（必填）
     *
     * 该步骤使用的处理策略类型编码。不同的整数值代表不同的处理方式，
     * 例如：1=按段落切分、2=按句子切分、3=生成摘要、4=向量化等。
     * 具体的枚举值映射由后端Service层定义。
     *
     * @NotNull 校验：不能为null，每个步骤必须指定策略类型。
     */
    @NotNull(message = "策略类型不能为空")
    private Integer strategyType;
}
