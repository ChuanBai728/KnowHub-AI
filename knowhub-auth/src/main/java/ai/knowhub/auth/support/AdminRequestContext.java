package ai.knowhub.auth.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前请求中的后台管理员上下文工具类。
 *
 * 该类用于在 HTTP 请求的生命周期内传递管理员信息。
 * 它利用 HttpServletRequest#setAttribute 方法，将数据存储在
 * 当前请求的作用域中，使得同一个请求的不同组件（拦截器、Controller、Service）
 * 都能访问到这些信息。
 *
 * 工作原理
 * 
 *   AdminAuthInterceptor 验证 Token 后，调用 #storeUsername 将用户名存入请求
 *   Controller 或 Service 通过 #resolveUsername 从请求中取出用户名
 *   请求结束后，请求对象被销毁，存储的数据也随之释放
 * 设计模式：ThreadLocal 的请求级替代方案
 * 虽然也可以使用 ThreadLocal 来存储请求上下文，但直接使用 HttpServletRequest
 * 的属性更加安全和直观，因为：
 * 
 *   生命周期与请求绑定，不需要手动清理
 *   在异步场景下不会出现数据混乱
 *   代码意图更清晰
 * 工具类设计
 * 该类是典型的工具类设计：
 * 
 *   构造函数私有化，防止实例化
 *   所有方法都是静态方法，直接通过类名调用
 *   使用常量定义属性键名，避免魔法字符串
 * 
 */
public final class AdminRequestContext {

    /**
     * 存储管理员用户名的请求属性键名。
     * 使用 "super.agent.admin.username" 作为键，避免与其他组件的属性名冲突。
     */
    public static final String ADMIN_USERNAME_ATTRIBUTE = "super.agent.admin.username";

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private AdminRequestContext() {
    }

    /**
     * 将管理员用户名存入当前请求的上下文中。
     *
     * 通常由 AdminAuthInterceptor 在验证 Token 成功后调用。
     *
     * @param request  HTTP 请求对象
     * @param username 要存储的用户名
     */
    public static void storeUsername(HttpServletRequest request, String username) {
        request.setAttribute(ADMIN_USERNAME_ATTRIBUTE, username);
    }

    /**
     * 从当前请求的上下文中获取管理员用户名。
     *
     * 如果请求中没有存储用户名（例如未经拦截器处理的请求），
     * 则返回空字符串。
     *
     * @param request HTTP 请求对象
     * @return 管理员用户名，如果没有则返回空字符串
     */
    public static String resolveUsername(HttpServletRequest request) {
        Object username = request.getAttribute(ADMIN_USERNAME_ATTRIBUTE);
        return username == null ? "" : String.valueOf(username);
    }
}
