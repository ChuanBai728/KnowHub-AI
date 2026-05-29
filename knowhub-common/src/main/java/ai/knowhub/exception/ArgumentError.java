package ai.knowhub.exception;

import lombok.Data;

/**
 * 【参数验证错误详情类】
 *
 * 作用：封装单个参数验证失败的详细信息，包括出错的字段名和错误原因。
 *
 * 业务背景：
 *   Spring 的 @Valid 注解在参数校验失败时会抛出 MethodArgumentNotValidException，
 *   其中包含多个字段的验证错误。此类用于将每个字段的错误信息结构化，
 *   便于前端定位具体是哪个字段出了问题。
 *
 * 使用场景：
 *   在 DefaultExceptionHandler 中捕获参数验证异常后，将每个字段错误转换为 ArgumentError 对象，
 *   最终返回给前端的 JSON 格式如：
 *   {
 *     "code": 10054,
 *     "data": [
 *       {"argumentName": "username", "message": "用户名不能为空"},
 *       {"argumentName": "email", "message": "邮箱格式不正确"}
 *     ]
 *   }
 *
 * 设计模式：使用 Lombok @Data 自动生成 getter/setter/toString 等方法，减少样板代码。
 */
@Data
public class ArgumentError {

    /**
     * 出错的参数/字段名称
     * 对应表单字段名或 JSON 属性名，例如 "username"、"email"
     */
    private String argumentName;

    /**
     * 错误提示信息
     * 对应 @NotBlank(message="用户名不能为空") 中的 message 属性
     */
    private String message;
}
