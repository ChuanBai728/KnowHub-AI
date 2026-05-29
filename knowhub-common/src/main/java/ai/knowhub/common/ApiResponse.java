package ai.knowhub.common;

import lombok.Data;
import ai.knowhub.enums.BaseCode;

import java.io.Serializable;

/**
 * 【统一 API 响应包装类】
 *
 * 作用：本项目中所有 REST 接口的返回值都必须用此类包装，保证前端收到的 JSON 结构一致。
 *       前端拿到响应后，先判断 code 是否为 0（成功），再决定读取 data 还是展示 message。
 *
 * 设计模式：采用了「静态工厂方法」模式 —— 通过 ApiResponse.ok() / ApiResponse.error() 创建实例，
 *           比直接 new 更语义化，调用方一眼就能看出是成功还是失败。
 *
 * 泛型 <T>：让不同接口可以返回不同类型的业务数据，例如：
 *           ApiResponse<List<User>>   —— 返回用户列表
 *           ApiResponse<String>       —— 返回简单文本
 *
 * @param <T> 业务数据的类型
 */
@Data
public class ApiResponse<T> implements Serializable {

    /**
     * 业务状态码
     * 约定：code = 0 表示成功，非 0 表示失败。
     * 常见错误码可在 BaseCode 枚举中查看。
     */
    private Integer code;

    /**
     * 提示信息
     * 给前端或调用方看的可读消息，例如"参数错误""系统异常"。
     * 成功时通常为空或 "OK"。
     */
    private String message;

    /**
     * 业务数据
     * 真正的返回内容。泛型 T 让不同接口可以返回不同类型的数据。
     * 例如用户查询接口返回 User 对象，列表接口返回 List<User>。
     */
    private T data;

    /**
     * 私有构造方法 —— 外部不能直接 new ApiResponse()
     * 必须通过静态工厂方法 ok() / error() 来创建，保证结构统一。
     */
    private ApiResponse() {}

    // ==================== 失败响应的工厂方法 ====================

    /**
     * 创建失败响应（指定错误码和提示信息）
     *
     * @param code    错误码，例如 -100、10054
     * @param message 错误提示信息
     * @param <T>     业务数据类型
     * @return 包装好的失败响应对象
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        // 静态工厂方法比直接 new 更清晰：调用方一眼能看出这是失败响应。
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = code;
        apiResponse.message = message;
        return apiResponse;
    }

    /**
     * 创建失败响应（使用默认错误码 -100，只指定提示信息）
     *
     * @param message 错误提示信息
     * @param <T>     业务数据类型
     * @return 包装好的失败响应对象
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.message = message;
        return apiResponse;
    }

    /**
     * 创建失败响应（使用默认错误码 -100，附带业务数据）
     *
     * @param code 错误码（注意：此重载中未使用该参数，实际固定为 -100，可能是历史遗留）
     * @param data 附带的业务数据
     * @param <T>  业务数据类型
     * @return 包装好的失败响应对象
     */
    public static <T> ApiResponse<T> error(Integer code, T data) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.data = data;
        return apiResponse;
    }

    /**
     * 创建失败响应（使用 BaseCode 枚举中预定义的错误码和提示信息）
     *
     * @param baseCode 错误码枚举，例如 BaseCode.SYSTEM_ERROR
     * @param <T>      业务数据类型
     * @return 包装好的失败响应对象
     */
    public static <T> ApiResponse<T> error(BaseCode baseCode) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = baseCode.getCode();
        apiResponse.message = baseCode.getMsg();
        return apiResponse;
    }

    /**
     * 创建失败响应（使用 BaseCode 枚举，同时附带业务数据）
     *
     * @param baseCode 错误码枚举
     * @param data     附带的业务数据
     * @param <T>      业务数据类型
     * @return 包装好的失败响应对象
     */
    public static <T> ApiResponse<T> error(BaseCode baseCode, T data) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = baseCode.getCode();
        apiResponse.message = baseCode.getMsg();
        apiResponse.data = data;
        return apiResponse;
    }

    /**
     * 创建默认失败响应（错误码 -100，提示"系统错误，请稍后重试!"）
     * 用于兜底场景，不想暴露具体错误细节时使用。
     *
     * @param <T> 业务数据类型
     * @return 包装好的失败响应对象
     */
    public static <T> ApiResponse<T> error() {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.message = "系统错误，请稍后重试!";
        return apiResponse;
    }

    // ==================== 成功响应的工厂方法 ====================

    /**
     * 创建成功响应（无业务数据）
     * 适用于只需要确认操作成功、不需要返回数据的场景，例如删除操作。
     *
     * @param <T> 业务数据类型
     * @return code=0 的成功响应对象
     */
    public static <T> ApiResponse<T> ok() {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = 0;
        return apiResponse;
    }

    /**
     * 创建成功响应（附带业务数据）
     * Controller 里通常直接写：return ApiResponse.ok(result);
     *
     * @param t   业务数据，例如查询到的用户对象
     * @param <T> 业务数据类型
     * @return code=0、data=t 的成功响应对象
     */
    public static <T> ApiResponse<T> ok(T t) {
        // 成功时把业务结果放进 data，Controller 里通常直接 return ApiResponse.ok(result)。
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = 0;
        apiResponse.setData(t);
        return apiResponse;
    }
}
