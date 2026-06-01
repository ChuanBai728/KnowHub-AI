package ai.knowhub.auth.vo;

/**
 * 当前后台管理员信息的返回值对象（VO）。
 *
 * 当已登录的管理员调用 /admin/auth/me 接口时，返回该对象。
 * 前端通常用它来显示当前登录用户的信息（如用户名显示在页面右上角）。
 *
 * JSON 响应示例
 * 
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": {
 *     "username": "admin"
 *   }
 * }
 * 扩展说明
 * 目前只返回用户名，后续可以扩展更多字段，如：
 * 
 *   角色（role）：超级管理员、普通管理员等
 *   头像（avatar）：用户头像 URL
 *   权限列表（permissions）：细粒度的权限控制
 * 
 */
public class AdminProfileVo {

    /** 管理员用户名 */
    private String username;

    /**
     * 构造函数。
     *
     * @param username 管理员用户名
     */
    public AdminProfileVo(String username) {
        this.username = username;
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
}
