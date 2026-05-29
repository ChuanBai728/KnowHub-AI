package ai.knowhub.exception;

/**
 * 【基础异常类】
 *
 * 作用：项目中所有自定义异常的基类，统一异常体系的根节点。
 *
 * 继承关系：BaseException extends RuntimeException
 *   - 选择 RuntimeException（非受检异常）而非 Exception（受检异常），
 *     是因为业务异常通常无法在调用方"恢复处理"，强制声明 throws 会增加代码冗余。
 *   - 所有业务异常（如 KnowHubFrameException、ArgumentException）都继承此类。
 *
 * 设计模式：「模板方法」模式的基础 —— 子类可以扩展 code、message 等字段，
 *           而基类提供统一的异常传播机制。
 *
 * 使用场景：
 *   - 直接抛出：throw new BaseException("未知错误");
 *   - 子类化：public class KnowHubFrameException extends BaseException { ... }
 */
public class BaseException extends RuntimeException{

    /**
     * 无参构造方法
     */
	public BaseException() {

	}

    /**
     * 带错误消息的构造方法
     *
     * @param message 错误描述信息
     */
	public BaseException(String message) {
		super(message);
	}

    /**
     * 带原始异常的构造方法（异常链）
     * 用于捕获一个异常后包装为业务异常重新抛出，保留原始异常信息。
     *
     * @param cause 原始异常
     */
	public BaseException(Throwable cause) {
		super(cause);
	}

    /**
     * 带错误消息和原始异常的构造方法
     *
     * @param message 错误描述信息
     * @param cause   原始异常
     */
	public BaseException(String message, Throwable cause) {
		super(message, cause);
	}

    /**
     * 带错误码、错误消息和原始异常的构造方法
     * 注意：code 参数未被使用，子类 KnowHubFrameException 扩展了 code 字段。
     *
     * @param code    错误码（此基类中未存储，由子类处理）
     * @param message 错误描述信息
     * @param cause   原始异常
     */
	public BaseException(Integer code, String message, Throwable cause) {
		super(message, cause);
	}
}
