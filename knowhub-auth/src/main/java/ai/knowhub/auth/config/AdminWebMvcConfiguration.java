package ai.knowhub.auth.config;

import ai.knowhub.auth.support.AdminAuthInterceptor;
import ai.knowhub.auth.support.PreviewModeInterceptor;
import ai.knowhub.web.ApiVersion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 后台管理登录与预览模式的 MVC 配置类。
 *
 * 该类实现了 WebMvcConfigurer 接口，用于向 Spring MVC 的请求处理链中
 * 注册自定义拦截器（Interceptor）。拦截器类似于 Servlet 的 Filter，但工作在
 * Spring MVC 层面，可以更方便地访问 Spring 上下文中的 Bean。
 *
 * 注册的拦截器
 * 
 *   <b>AdminAuthInterceptor</b>（后台登录鉴权）：拦截 /manage/** 和
 *       /admin/auth/me 路径，验证 JWT Token 的有效性，确保只有已登录的
 *       管理员才能访问管理接口。
 *   <b>PreviewModeInterceptor</b>（只读预览模式）：拦截所有路径（/**），
 *       当系统处于"只读展示模式"时，阻止写操作（如聊天、文档上传等）。
 * 拦截器执行顺序
 * 按照 addInterceptors 方法中的注册顺序依次执行。
 * 本配置中 AdminAuthInterceptor 先执行（先验证身份），PreviewModeInterceptor 后执行（再检查权限）。
 *
 * 涉及的注解
 * 
 *   @Configuration —— 标记为 Spring 配置类，Spring 容器启动时会自动加载
 *   @EnableConfigurationProperties —— 启用指定的配置属性类，使其成为可注入的 Bean
 * 
 */
@Configuration
@EnableConfigurationProperties({AdminAuthProperties.class, PreviewModeProperties.class})
public class AdminWebMvcConfiguration implements WebMvcConfigurer {

    /** 后台登录鉴权拦截器，负责验证 JWT Token */
    private final AdminAuthInterceptor adminAuthInterceptor;

    /** 只读预览模式拦截器，负责在展示模式下拦截写操作 */
    private final PreviewModeInterceptor previewModeInterceptor;

    /**
     * 构造函数，通过 Spring 的构造器注入方式获取拦截器实例。
     *
     * @param adminAuthInterceptor   后台登录鉴权拦截器
     * @param previewModeInterceptor 只读预览模式拦截器
     */
    public AdminWebMvcConfiguration(AdminAuthInterceptor adminAuthInterceptor,
                                    PreviewModeInterceptor previewModeInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.previewModeInterceptor = previewModeInterceptor;
    }

    /**
     * 注册拦截器到 Spring MVC 的拦截器链中。
     *
     * Spring MVC 在处理每个 HTTP 请求时，会按照注册顺序依次调用拦截器的
     * preHandle → Controller → postHandle → afterCompletion 方法。
     *
     * @param registry 拦截器注册表，用于添加拦截器并配置拦截路径
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 后台登录鉴权：只拦截管理后台相关的接口
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns(ApiVersion.V1_MANAGE + "/**", ApiVersion.V1_ADMIN_AUTH + "/me");

        // 只读预览模式：拦截所有接口，检查是否有写操作被禁止
        registry.addInterceptor(previewModeInterceptor)
            .addPathPatterns("/**");
    }
}
