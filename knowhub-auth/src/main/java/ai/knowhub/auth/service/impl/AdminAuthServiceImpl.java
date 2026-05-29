package ai.knowhub.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import ai.knowhub.auth.config.AdminAuthProperties;
import ai.knowhub.auth.dto.AdminLoginDto;
import ai.knowhub.auth.service.AdminAuthService;
import ai.knowhub.auth.support.AdminJwtTokenService;
import ai.knowhub.auth.support.AdminRequestContext;
import ai.knowhub.auth.vo.AdminLoginVo;
import ai.knowhub.auth.vo.AdminProfileVo;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.stereotype.Service;

/**
 * 后台登录认证服务的实现类。
 *
 * 该类实现了 AdminAuthService 接口，提供具体的登录验证和用户信息查询逻辑。
 *
 * 登录流程
 * 
 *   接收前端传入的用户名和密码
 *   使用 Hutool 的 StrUtil.trim() 去除前后空格
 *   与配置文件中预设的用户名/密码进行比对
 *   验证通过后，调用 JWT 服务生成 Token
 *   封装登录结果（用户名、Token、过期时间）返回给前端
 * 涉及的注解
 * 
 *   @Service —— Spring 的服务层注解，标记为业务逻辑组件，会被自动扫描注册为 Bean
 * 依赖说明
 * 
 *   AdminAuthProperties —— 提供配置的用户名、密码和 JWT 密钥
 *   AdminJwtTokenService —— 负责 JWT Token 的生成和解析
 * 
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    /** 后台登录配置属性，包含用户名、密码、JWT 密钥等 */
    private final AdminAuthProperties adminAuthProperties;

    /** JWT Token 服务，负责生成和解析 Token */
    private final AdminJwtTokenService adminJwtTokenService;

    /**
     * 构造函数，通过依赖注入获取所需的 Bean。
     *
     * @param adminAuthProperties 后台登录配置属性
     * @param adminJwtTokenService JWT Token 服务
     */
    public AdminAuthServiceImpl(AdminAuthProperties adminAuthProperties,
                                AdminJwtTokenService adminJwtTokenService) {
        this.adminAuthProperties = adminAuthProperties;
        this.adminJwtTokenService = adminJwtTokenService;
    }

    /**
     * 处理管理员登录请求。
     *
     * 将前端传入的用户名/密码与配置文件中的值进行比对。
     * 验证通过后生成 JWT Token 返回。
     *
     * @param request 登录请求对象，包含 username 和 password
     * @return 登录成功后的响应，包含用户名、Token 和过期时间
     * @throws KnowHubFrameException 当用户名或密码不匹配时抛出 401 异常
     */
    @Override
    public AdminLoginVo login(AdminLoginDto request) {
        // 去除前后空格，防止用户误输入空格
        String username = StrUtil.trim(request.getUsername());
        String password = StrUtil.trim(request.getPassword());

        // 与配置文件中的用户名和密码进行比对
        if (!StrUtil.equals(username, adminAuthProperties.getUsername())
            || !StrUtil.equals(password, adminAuthProperties.getPassword())) {
            throw new KnowHubFrameException(401, "账号或密码不正确");
        }

        // 登录成功，生成 JWT Token
        String token = adminJwtTokenService.generateToken(username);

        // 封装登录结果返回
        return new AdminLoginVo(username, token, adminAuthProperties.getTokenExpireMinutes());
    }

    /**
     * 获取当前登录管理员的个人信息。
     *
     * 从请求上下文中获取由拦截器存入的用户名（拦截器已验证过 Token 的有效性）。
     *
     * @param request HTTP 请求对象
     * @return 当前管理员的个人信息（目前只包含用户名）
     */
    @Override
    public AdminProfileVo currentProfile(HttpServletRequest request) {
        // 从请求属性中获取拦截器存入的用户名
        String username = AdminRequestContext.resolveUsername(request);
        return new AdminProfileVo(username);
    }
}
