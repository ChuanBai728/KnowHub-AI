package ai.knowhub.auth.vo;

/**
 * 后台登录成功的返回值对象（VO）。
 *
 * VO（Value Object / Value Object）是用于返回给前端的数据对象。
 * 当管理员登录成功后，该对象会被序列化为 JSON 返回给前端。
 *
 * JSON 响应示例
 * 
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": {
 *     "username": "admin",
 *     "token": "eyJhbGciOiJIUzI1NiJ9...",
 *     "expireMinutes": 720
 *   }
 * }
 * 前端使用说明
 * 
 *   前端收到响应后，应将 token 保存在 localStorage 或 Cookie 中
 *   后续请求在 Header 中添加 Authorization: Bearer <token>
 *   expireMinutes 可用于前端定时提醒用户 Token 即将过期
 * 
 */
public class AdminLoginVo {

    /** 登录的用户名 */
    private String username;

    /**
     * JWT Token 字符串。
     * 前端需要在后续请求的 Header 中携带此 Token 进行身份验证。
     */
    private String token;

    /**
     * Token 有效期（分钟）。
     * 前端可以据此计算 Token 的过期时间，提前提示用户重新登录。
     */
    private Long expireMinutes;

    /**
     * 全参构造函数。
     *
     * @param username      用户名
     * @param token         JWT Token
     * @param expireMinutes Token 有效期（分钟）
     */
    public AdminLoginVo(String username, String token, Long expireMinutes) {
        this.username = username;
        this.token = token;
        this.expireMinutes = expireMinutes;
    }

    /**
     * 获取用户名。
     *
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名。
     *
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取 JWT Token。
     *
     * @return JWT Token 字符串
     */
    public String getToken() {
        return token;
    }

    /**
     * 设置 JWT Token。
     *
     * @param token JWT Token 字符串
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * 获取 Token 有效期。
     *
     * @return 有效期（分钟）
     */
    public Long getExpireMinutes() {
        return expireMinutes;
    }

    /**
     * 设置 Token 有效期。
     *
     * @param expireMinutes 有效期（分钟）
     */
    public void setExpireMinutes(Long expireMinutes) {
        this.expireMinutes = expireMinutes;
    }
}
