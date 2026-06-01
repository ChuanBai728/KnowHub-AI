package ai.knowhub.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import ai.knowhub.auth.dto.AdminLoginDto;
import ai.knowhub.auth.vo.AdminLoginVo;
import ai.knowhub.auth.vo.AdminProfileVo;

/**
 * 后台登录认证的服务接口。
 *
 * 定义了后台管理系统的认证业务方法。遵循"面向接口编程"的原则，
 * 具体实现由 ai.knowhub.auth.service.impl.AdminAuthServiceImpl 提供。
 *
 * 设计模式：接口与实现分离
 * 将接口和实现分开有以下好处：
 * 
 *   解耦：Controller 只依赖接口，不关心具体实现
 *   可替换：可以轻松替换实现类（如从简单登录切换到 LDAP 认证）
 *   可测试：单元测试时可以使用 Mock 实现
 *   AOP 代理：Spring 可以基于接口创建 JDK 动态代理
 * 
 */
public interface AdminAuthService {

    /**
     * 管理员登录。
     *
     * 验证用户名和密码，成功后生成 JWT Token 返回。
     *
     * @param request 登录请求，包含用户名和密码
     * @return 登录成功的响应对象，包含用户名、Token 和过期时间
     * @throws ai.knowhub.exception.KnowHubFrameException 当用户名或密码不正确时抛出 401 异常
     */
    AdminLoginVo login(AdminLoginDto request);

    /**
     * 获取当前登录管理员的个人信息。
     *
     * 从请求上下文中提取已通过拦截器验证的用户名，
     * 并返回对应的用户信息。
     *
     * @param request HTTP 请求对象，其中包含拦截器存入的用户名信息
     * @return 当前管理员的个人信息
     */
    AdminProfileVo currentProfile(HttpServletRequest request);
}
