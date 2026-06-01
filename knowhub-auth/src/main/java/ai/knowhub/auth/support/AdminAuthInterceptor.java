package ai.knowhub.auth.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import ai.knowhub.common.ApiResponse;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台管理接口的鉴权拦截器。
 *
 * 该拦截器实现了 HandlerInterceptor 接口，在请求到达 Controller 之前
 * 进行 JWT Token 验证。只有携带有效 Token 的请求才能访问后台管理接口。
 *
 * 拦截流程
 * 
 *   从请求 Header 中提取 Authorization 字段
 *   解析出 JWT Token（支持 "Bearer xxx" 和直接传 Token 两种格式）
 *   调用 JWT 服务验证 Token 的签名和有效期
 *   从 Token 中提取用户名，存入请求上下文供后续使用
 *   验证失败则返回 401 未授权响应
 * 设计模式：拦截器模式（Interceptor Pattern）
 * 拦截器是 AOP（面向切面编程）的一种实现方式，可以在不修改业务代码的情况下
 * 统一处理横切关注点（如认证、日志、权限等）。
 *
 * 涉及的注解
 * 
 *   @Component —— 标记为 Spring 组件，会被自动扫描注册为 Bean
 * 
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    /** JWT Token 服务，用于解析和验证 Token */
    private final AdminJwtTokenService adminJwtTokenService;

    /** JSON 序列化工具，用于将错误响应写入 HTTP 输出流 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入依赖。
     *
     * @param adminJwtTokenService JWT Token 服务
     * @param objectMapper         Jackson 的 JSON 序列化器
     */
    public AdminAuthInterceptor(AdminJwtTokenService adminJwtTokenService,
                                ObjectMapper objectMapper) {
        this.adminJwtTokenService = adminJwtTokenService;
        this.objectMapper = objectMapper;
    }

    /**
     * 请求预处理方法 —— 在 Controller 方法执行之前调用。
     *
     * 该方法负责验证 JWT Token 的有效性。如果验证通过，返回 true 让请求继续；
     * 如果验证失败，返回 false 阻止请求到达 Controller。
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param handler  要执行的处理器（通常是 Controller 方法）
     * @return true 表示验证通过，false 表示验证失败
     * @throws Exception 可能抛出的异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求（CORS 跨域请求会先发送 OPTIONS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从 Authorization 头中提取 Token
        String authorization = request.getHeader("Authorization");
        String token = resolveToken(authorization);

        // Token 为空，返回未授权
        if (StrUtil.isBlank(token)) {
            writeUnauthorized(response, "请先登录后台管理台");
            return false;
        }

        try {
            // 解析 Token，验证签名和有效期
            Claims claims = adminJwtTokenService.parseToken(token);
            String username = claims.getSubject();

            // Token 中没有用户名，视为无效
            if (StrUtil.isBlank(username)) {
                writeUnauthorized(response, "后台登录无效，请重新登录");
                return false;
            }

            // 将用户名存入请求上下文，供 Controller 和 Service 使用
            AdminRequestContext.storeUsername(request, username);
            return true;
        } catch (KnowHubFrameException exception) {
            // Token 解析失败（过期或无效），返回未授权
            writeUnauthorized(response, exception.getMessage());
            return false;
        }
    }

    /**
     * 从 Authorization 头中解析 JWT Token。
     *
     * 支持两种格式：
     * 
     *   "Bearer eyJhbGciOiJIUzI1NiJ9..." —— 标准的 OAuth2 格式
     *   "eyJhbGciOiJIUzI1NiJ9..." —— 直接传 Token
     * @param authorization Authorization 头的值
     * @return 解析出的 Token 字符串，如果为空则返回 null
     */
    private String resolveToken(String authorization) {
        if (StrUtil.isBlank(authorization)) {
            return null;
        }
        // 如果以 "Bearer " 开头，去掉这个前缀
        if (StrUtil.startWithIgnoreCase(authorization, "Bearer ")) {
            return StrUtil.trim(authorization.substring(7));
        }
        return StrUtil.trim(authorization);
    }

    /**
     * 向 HTTP 响应中写入 401 未授权的 JSON 错误信息。
     *
     * @param response HTTP 响应对象
     * @param message  错误提示信息
     * @throws Exception 写入响应时可能抛出的异常
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 设置 401 状态码
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());  // 设置字符编码
        response.setContentType("application/json;charset=UTF-8");  // 设置响应类型为 JSON
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, message)));
    }
}
