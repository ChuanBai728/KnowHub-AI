package ai.knowhub.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ai.knowhub.auth.dto.AdminLoginDto;
import ai.knowhub.auth.service.AdminAuthService;
import ai.knowhub.auth.vo.AdminLoginVo;
import ai.knowhub.auth.vo.AdminProfileVo;
import ai.knowhub.common.ApiResponse;
import ai.knowhub.web.ApiVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台登录认证的 REST 控制器。
 *
 * 该控制器提供后台管理系统的登录、登出和获取当前用户信息三个接口。
 * 所有接口都遵循统一的 ApiResponse 响应格式。
 *
 * 接口列表
 * 
 *   POST /admin/auth/login —— 用户名密码登录，返回 JWT Token
 *   POST /admin/auth/logout —— 登出（前端清除 Token 即可，后端无需处理）
 *   GET  /admin/auth/me —— 获取当前登录用户信息
 * 认证流程
 * 
 *   前端调用 /login 接口，传入用户名和密码
 *   后端验证通过后，返回 JWT Token
 *   前端将 Token 保存在 localStorage 或 Cookie 中
 *   后续请求在 Header 中携带 Authorization: Bearer <token>
 *   AdminAuthInterceptor 拦截请求，验证 Token 的有效性
 * 涉及的注解
 * 
 *   @RestController —— 组合了 @Controller 和 @ResponseBody，方法返回值自动序列化为 JSON
 *   @RequestMapping(ApiVersion.V1_ADMIN_AUTH) —— 定义该控制器下所有接口的统一路径前缀
 *   @Valid —— 触发 JSR-303 参数校验（如 @NotBlank 等）
 *   @RequestBody —— 将 HTTP 请求体中的 JSON 反序列化为 Java 对象
 * 
 */
@RestController
@RequestMapping(ApiVersion.V1_ADMIN_AUTH)
public class AdminAuthController {

    /** 后台认证服务，处理具体的登录和用户信息查询逻辑 */
    private final AdminAuthService adminAuthService;

    /**
     * 构造函数，注入认证服务。
     *
     * @param adminAuthService 后台认证服务接口
     */
    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /**
     * 用户名密码登录接口。
     *
     * 前端提交用户名和密码，后端验证成功后返回 JWT Token。
     * Token 中包含了用户名和过期时间信息。
     *
     * @param request 登录请求体，包含 username 和 password 字段
     * @return 登录成功的响应，包含用户名、Token 和过期时间
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginVo> login(@Valid @RequestBody AdminLoginDto request) {
        return ApiResponse.ok(adminAuthService.login(request));
    }

    /**
     * 登出接口。
     *
     * 由于 JWT 是无状态的（服务端不保存 Token 状态），
     * 登出操作实际上由前端负责清除本地存储的 Token。
     * 此接口仅返回成功响应，作为前端登出流程的配合。
     *
     * @return 空的成功响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    /**
     * 获取当前登录用户信息接口。
     *
     * 需要在请求 Header 中携带有效的 JWT Token。
     * AdminAuthInterceptor 会先验证 Token，并将用户名存入请求上下文。
     *
     * @param request HTTP 请求对象，其中包含了拦截器存入的用户名信息
     * @return 当前登录用户的详细信息
     */
    @GetMapping("/me")
    public ApiResponse<AdminProfileVo> me(HttpServletRequest request) {
        return ApiResponse.ok(adminAuthService.currentProfile(request));
    }
}
