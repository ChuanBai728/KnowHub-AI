package ai.knowhub.exception;

import lombok.Data;

import java.util.List;

/**
 * 【参数验证异常类】
 *
 * 作用：当接口参数校验失败时抛出此异常，携带所有验证错误的详细信息。
 *
 * 继承关系：ArgumentException extends BaseException extends RuntimeException
 *   - RuntimeException 是非受检异常，不需要在方法签名中声明 throws。
 *   - 通过继承链，此异常可以被 DefaultExceptionHandler 统一捕获。
 *
 * 使用场景：
 *   在 Service 层手动校验参数时，如果发现参数不合法，可以抛出此异常：
 *   throw new ArgumentException(BaseCode.PARAMETER_ERROR.getCode(), argumentErrorList);
 *
 * 设计模式：使用 Lombok @Data 自动生成 getter/setter 方法。
 */
@Data
public class ArgumentException extends BaseException {

    /**
     * 错误码
     * 通常使用 BaseCode.PARAMETER_ERROR 的 code 值（10054）
     */
    private Integer code;

    /**
     * 参数验证错误列表
     * 包含所有校验失败的字段信息，前端可以据此高亮显示错误字段
     */
    private List<ArgumentError> argumentErrorList;

    /**
     * 构造方法：传入错误码和错误详情列表
     *
     * @param code              错误码
     * @param argumentErrorList 参数验证错误列表
     */
    public ArgumentException(Integer code, List<ArgumentError> argumentErrorList) {
        this.code = code;
        this.argumentErrorList = argumentErrorList;
    }

    /**
     * 构造方法：仅传入错误消息
     *
     * @param message 错误消息
     */
    public ArgumentException(String message) {
        super(message);
    }

    /**
     * 构造方法：传入错误码和错误消息
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public ArgumentException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造方法：传入原始异常（用于异常链）
     *
     * @param cause 原始异常
     */
    public ArgumentException(Throwable cause) {
        super(cause);
    }

    /**
     * 构造方法：传入错误消息和原始异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public ArgumentException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造方法：传入错误码、错误消息和原始异常
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常
     */
    public ArgumentException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
