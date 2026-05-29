package ai.knowhub.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.common.ApiResponse;
import ai.knowhub.enums.BaseCode;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 【全局异常处理器】
 *
 * 作用：统一捕获 Controller 层抛出的所有异常，将其转换为标准的 ApiResponse 格式返回给前端。
 *       避免将异常堆栈信息直接暴露给用户，提高安全性和用户体验。
 *
 * 核心注解：
 *   - @RestControllerAdvice：Spring MVC 的全局异常处理注解，等价于 @ControllerAdvice + @ResponseBody。
 *     它会拦截所有 @RestController 中抛出的异常。
 *   - @ExceptionHandler：标记具体处理哪种异常的方法。Spring 会根据异常类型自动匹配处理方法。
 *
 * 处理的异常类型（按优先级从高到低）：
 *   1. KnowHubFrameException：业务异常，返回具体的错误码和消息。
 *   2. MethodArgumentNotValidException：参数校验异常，返回每个字段的验证错误。
 *   3. Throwable：兜底处理，捕获所有未预期的异常，返回通用错误提示。
 *
 * 设计模式：「责任链」模式 —— Spring 按异常类型匹配最具体的处理器。
 */
@Slf4j
@RestControllerAdvice
public class DefaultExceptionHandler {

    /**
     * 处理业务异常（KnowHubFrameException）
     * 当业务代码中主动抛出 KnowHubFrameException 时，会进入此方法。
     *
     * @param request                  HTTP 请求对象（用于记录请求信息）
     * @param superAgentFrameException 业务异常对象，包含 code 和 message
     * @return 标准化的错误响应
     */
    @ExceptionHandler(value = KnowHubFrameException.class)
    public ApiResponse<String> toolkitExceptionHandler(HttpServletRequest request, KnowHubFrameException superAgentFrameException) {
        // 记录详细的错误日志，包含请求方法、URL、查询参数，便于排查问题
        log.error("业务异常 错误信息 : {} method : {} url : {} query : {} ", superAgentFrameException.getMessage(), request.getMethod(), getRequestUrl(request), getRequestQuery(request), superAgentFrameException);
        return ApiResponse.error(superAgentFrameException.getCode(), superAgentFrameException.getMessage());
    }

    /**
     * 处理参数校验异常（MethodArgumentNotValidException）
     * 当使用 @Valid 注解校验参数失败时，Spring 会抛出此异常。
     *
     * @param request HTTP 请求对象
     * @param ex      参数校验异常，包含所有字段的验证错误信息
     * @return 包含每个字段错误详情的响应
     *
     * @SneakyThrows 是 Lombok 注解，自动将受检异常包装为非受检异常抛出，
     * 避免在方法签名中声明 throws。
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ApiResponse<List<ArgumentError>> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        log.error("参数验证异常 错误信息 : {} method : {} url : {} query : {} ", ex.getMessage(), request.getMethod(), getRequestUrl(request), getRequestQuery(request), ex);
        // 从异常中获取 BindingResult，它包含了所有字段的验证错误
        BindingResult bindingResult = ex.getBindingResult();
        // 使用 Java Stream API 将字段错误转换为 ArgumentError 列表
        List<ArgumentError> argumentErrorList =
                bindingResult.getFieldErrors()
                        .stream()
                        .map(fieldError -> {
                            ArgumentError argumentError = new ArgumentError();
                            argumentError.setArgumentName(fieldError.getField());      // 字段名
                            argumentError.setMessage(fieldError.getDefaultMessage());  // 错误消息
                            return argumentError;
                        }).collect(Collectors.toList());
        return ApiResponse.error(BaseCode.PARAMETER_ERROR.getCode(),argumentErrorList);
    }

    /**
     * 兜底异常处理器（Throwable）
     * 捕获所有未被上述处理器处理的异常，返回通用错误提示。
     * 避免将异常堆栈暴露给前端，提高安全性。
     *
     * @param request   HTTP 请求对象
     * @param throwable 异常对象
     * @return 通用错误响应（"系统错误，请稍后重试!"）
     */
    @ExceptionHandler(value = Throwable.class)
    public ApiResponse<String> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("全局异常 错误信息 : {} method : {} url : {} query : {} ", throwable.getMessage(), request.getMethod(), getRequestUrl(request), getRequestQuery(request), throwable);
        return ApiResponse.error();
    }

    /**
     * 辅助方法：获取请求的完整 URL
     *
     * @param request HTTP 请求对象
     * @return 请求 URL 字符串
     */
    private String getRequestUrl(HttpServletRequest request) {
        return request.getRequestURL().toString();
    }

    /**
     * 辅助方法：获取请求的查询参数
     *
     * @param request HTTP 请求对象
     * @return 查询参数字符串（如 "name=test&page=1"），无参数时返回 null
     */
    private String getRequestQuery(HttpServletRequest request){
        return request.getQueryString();
    }
}
