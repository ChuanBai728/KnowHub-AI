package ai.knowhub.exception;

import ai.knowhub.common.ApiResponse;
import ai.knowhub.enums.BaseCode;
import lombok.Data;

/**
 * 【业务框架异常类】
 *
 * 作用：项目中最常用的业务异常类，当业务逻辑出错时抛出此异常，
 *       由全局异常处理器 DefaultExceptionHandler 捕获并转换为 ApiResponse 返回。
 *
 * 继承关系：KnowHubFrameException extends BaseException extends RuntimeException
 *
 * 使用场景：
 *   1. Service 层业务校验失败：throw new KnowHubFrameException("用户不存在");
 *   2. 使用预定义错误码：throw new KnowHubFrameException(BaseCode.SYSTEM_ERROR);
 *   3. 使用文档管理错误码：throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND);
 *   4. 包装已有响应：throw new KnowHubFrameException(apiResponse);
 *
 * 设计模式：
 *   - 「异常链」模式：支持传入原始异常 cause，保留完整的异常调用栈。
 *   - 「多态构造」模式：提供多种构造方法，适应不同的创建场景。
 *
 * 使用 Lombok @Data 自动生成 getter/setter 方法。
 */
@Data
public class KnowHubFrameException extends BaseException {

    /**
     * 业务错误码
     * 对应 BaseCode 或各模块错误码枚举中的 code 值
     */
	private Integer code;

    /**
     * 错误提示信息
     * 面向用户的可读描述
     */
	private String message;

    /**
     * 无参构造方法
     */
	public KnowHubFrameException() {
		super();
	}

    /**
     * 带错误消息的构造方法
     *
     * @param message 错误提示信息
     */
	public KnowHubFrameException(String message) {
		super(message);
	}

    /**
     * 带字符串错误码和消息的构造方法
     * 适用于错误码以字符串形式传递的场景（如从配置文件读取）。
     *
     * @param code    字符串形式的错误码（会被解析为 Integer）
     * @param message 错误提示信息
     */
	public KnowHubFrameException(String code, String message) {
		super(message);
		this.code = Integer.parseInt(code);
		this.message = message;
	}

    /**
     * 带数字错误码和消息的构造方法（最常用）
     *
     * @param code    数字错误码
     * @param message 错误提示信息
     */
	public KnowHubFrameException(Integer code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}

    /**
     * 使用 BaseCode 枚举创建异常
     * 直接从预定义的错误码枚举中获取 code 和 message。
     *
     * @param baseCode 错误码枚举实例，如 BaseCode.SYSTEM_ERROR
     */
	public KnowHubFrameException(BaseCode baseCode) {
		super(baseCode.getMsg());
		this.code = baseCode.getCode();
		this.message = baseCode.getMsg();
	}

    /**
     * 使用 ApiResponse 创建异常
     * 适用于需要将已有的错误响应包装为异常的场景。
     *
     * @param apiResponse API 响应对象
     */
	public KnowHubFrameException(ApiResponse apiResponse) {
		super(apiResponse.getMessage());
		this.code = apiResponse.getCode();
		this.message = apiResponse.getMessage();
	}

    /**
     * 带原始异常的构造方法（异常链）
     *
     * @param cause 原始异常
     */
	public KnowHubFrameException(Throwable cause) {
		super(cause);
	}

    /**
     * 带错误消息和原始异常的构造方法
     *
     * @param message 错误提示信息
     * @param cause   原始异常
     */
	public KnowHubFrameException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}

    /**
     * 带错误码、消息和原始异常的构造方法
     *
     * @param code    数字错误码
     * @param message 错误提示信息
     * @param cause   原始异常
     */
	public KnowHubFrameException(Integer code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.message = message;
	}
}
