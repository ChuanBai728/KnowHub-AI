package ai.knowhub.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 测试DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：这是一个用于开发测试的简单DTO，仅包含一个ID字段。
 * 开发人员在开发新功能或调试时，可能使用本DTO快速测试接口的请求和响应。
 *
 * 使用场景：开发和测试阶段使用，生产环境中通常不会使用此类。
 * 建议在正式发布前清理此类测试代码。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class TestDto {

    /**
     * ID（必填）
     *
     * 测试用的唯一标识。
     * @NotNull 校验：不能为null。
     */
    @NotNull
    private Long id;
}
