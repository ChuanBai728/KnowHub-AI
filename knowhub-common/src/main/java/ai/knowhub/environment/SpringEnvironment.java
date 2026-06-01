package ai.knowhub.environment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 【Spring 环境后处理器】—— 在 Spring 容器初始化之前修改应用配置
 *
 * 核心作用：
 * 允许 Bean 定义覆盖。即当多个模块中存在相同名称的 Bean 时，
 * 后注册的 Bean 可以覆盖先注册的 Bean，而不会抛出异常。
 *
 * 为什么需要这个？
 * 在多模块项目中，不同模块可能注册了同名的 Bean（例如相同的配置类）。
 * Spring Boot 默认不允许 Bean 定义覆盖（会抛出 BeanDefinitionOverrideException）。
 * 通过设置 setAllowBeanDefinitionOverriding(true)，可以放宽这个限制，
 * 让后加载的 Bean 覆盖先加载的同名 Bean。
 *
 * EnvironmentPostProcessor 是什么？
 * 这是 Spring Boot 提供的扩展点，允许在应用上下文（ApplicationContext）创建之前，
 * 对环境配置（Environment）进行修改。它比普通的 @Configuration 更早执行。
 *
 * 加载机制：
 * 此类通过 SPI（Service Provider Interface）机制自动加载。
 * 在 META-INF/spring.factories 或 META-INF/spring/org.springframework.boot.env.AutoConfiguration.imports
 * 文件中注册，Spring Boot 启动时会自动发现并调用。
 *
 * 执行时机：
 * SpringApplication.run() → Environment 准备好之后 → 调用所有 EnvironmentPostProcessor → 创建 ApplicationContext
 *
 * @see org.springframework.boot.env.EnvironmentPostProcessor Spring Boot 官方接口文档
 */
public class SpringEnvironment implements EnvironmentPostProcessor {

    /**
     * 环境后处理方法
     *
     * 在 Spring 应用的 Environment 准备好之后、ApplicationContext 创建之前调用。
     * 这里设置了允许 Bean 定义覆盖。
     *
     * @param environment 可配置的环境对象，包含所有配置属性（application.yml 等）
     * @param application SpringApplication 实例，代表整个 Spring 应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 允许 Bean 定义覆盖
        // 当存在同名 Bean 时，后注册的会覆盖先注册的，而不是抛出异常
        application.setAllowBeanDefinitionOverriding(true);
    }
}
