package ai.knowhub.enums;

/**
 * 【基础错误码枚举】
 *
 * 作用：定义全系统通用的错误码和对应提示信息，统一管理错误码避免冲突。
 *       配合 ApiResponse.error(BaseCode) 使用，快速返回标准化的错误响应。
 *
 * 设计思路：
 *   - 每个枚举值包含一个 code（数字错误码）和一个 msg（中文提示信息）。
 *   - code = 0 表示成功，负数或正数表示不同类型的错误。
 *   - 不同模块可以定义自己的错误码枚举（如 DocumentManageCode），但基础错误码在此统一管理。
 *
 * 使用示例：
 *   return ApiResponse.error(BaseCode.SYSTEM_ERROR);  // 返回 {"code":-1,"message":"系统异常，请稍后重试"}
 */
public enum BaseCode {

    /** 操作成功 */
    SUCCESS(0, "OK"),

    /** 系统级异常，通常是未捕获的运行时错误 */
    SYSTEM_ERROR(-1,"系统异常，请稍后重试"),

    /** UID 生成器的 workerId 设置失败，影响分布式 ID 生成 */
    UID_WORK_ID_ERROR(500,"uid_work_id设置失败"),

    /** 请求参数校验不通过，例如缺少必填字段或格式不正确 */
    PARAMETER_ERROR(10054,"参数验证异常"),
    ;

    /**
     * 数字错误码
     * 用于程序逻辑判断（如 if (code == 0)），也返回给前端做条件分支。
     */
    private final Integer code;

    /**
     * 提示信息
     * 面向用户的可读描述，前端可以直接展示。
     */
    private String msg = "";

    /**
     * 枚举构造方法
     *
     * @param code 数字错误码
     * @param msg  中文提示信息
     */
    BaseCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取错误码
     * @return 数字错误码
     */
    public Integer getCode() {
        return this.code;
    }

    /**
     * 获取提示信息
     * @return 中文提示信息，如果 msg 为 null 则返回空字符串（防空指针）
     */
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }

    /**
     * 根据数字错误码查找对应的提示信息
     * 遍历所有枚举值，找到 code 匹配的项并返回其 msg。
     *
     * @param code 数字错误码
     * @return 对应的提示信息，未找到则返回空字符串
     */
    public static String getMsg(Integer code) {
        for (BaseCode re : BaseCode.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    /**
     * 根据数字错误码查找对应的枚举实例
     * 遍历所有枚举值，找到 code 匹配的项并返回整个枚举对象。
     *
     * @param code 数字错误码
     * @return 对应的 BaseCode 枚举实例，未找到则返回 null
     */
    public static BaseCode getRc(Integer code) {
        for (BaseCode re : BaseCode.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
