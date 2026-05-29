package ai.knowhub.core;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import static ai.knowhub.constant.Constant.DEFAULT_PREFIX_DISTINCTION_NAME;
import static ai.knowhub.constant.Constant.PREFIX_DISTINCTION_NAME;

/**
 * 【Spring 容器工具类】
 *
 * 作用：在非 Spring 管理的类中（例如工具类、拦截器等），通过此类手动获取 Spring 容器中的 Bean。
 *       相当于一个"万能入口"，让普通 Java 类也能访问 Spring 容器。
 *
 * 实现原理：
 *   实现了 ApplicationContextInitializer 接口，Spring 容器启动初期会自动调用 initialize() 方法，
 *   此时将 ApplicationContext 保存到静态变量中。之后任何地方都可以通过静态方法 getBean() 获取 Bean。
 *
 * 注意事项：
 *   - 需要在 spring.factories 或 application.yml 中注册此类，Spring 才会发现并调用它。
 *   - 因为使用了静态变量，在单元测试中需要注意容器状态。
 *
 * 使用示例：
 *   UserService userService = SpringUtil.getBean(UserService.class);
 */
public class SpringUtil implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * 静态持有的 Spring 应用上下文引用
     * 在 Spring 容器初始化阶段被赋值，之后全局可用。
     */
    private static ConfigurableApplicationContext configurableApplicationContext;

    /**
     * 获取配置文件中的"区分名称前缀"
     * 先从配置文件中读取 prefix.distinction.name 属性，如果没有配置则返回默认值 "knowhub"。
     *
     * @return 前缀名称字符串，例如 "knowhub"
     */
    public static String getPrefixDistinctionName(){
        return configurableApplicationContext.getEnvironment().getProperty(PREFIX_DISTINCTION_NAME,
                DEFAULT_PREFIX_DISTINCTION_NAME);
    }

    /**
     * Spring 容器初始化回调方法
     * Spring 在启动过程中会自动调用此方法，将 ApplicationContext 传入并保存到静态变量中。
     * 这是 ApplicationContextInitializer 接口要求实现的方法。
     *
     * @param applicationContext Spring 应用上下文对象
     */
    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        configurableApplicationContext = applicationContext;
    }

    /**
     * 根据类型从容器中获取 Bean
     * 适用于容器中该类型只有一个 Bean 的情况。
     *
     * @param requiredType 要获取的 Bean 的类型（接口或类）
     * @param <T>          Bean 的类型
     * @return 容器中对应类型的 Bean 实例
     */
    public static <T> T getBean(Class<T> requiredType){
        return configurableApplicationContext.getBean(requiredType);
    }

    /**
     * 根据名称和类型从容器中获取 Bean
     * 适用于容器中同一类型有多个 Bean、需要按名称区分的情况。
     *
     * @param name         Bean 的名称（通常在 @Component/@Service 等注解中定义）
     * @param requiredType 要获取的 Bean 的类型
     * @param <T>          Bean 的类型
     * @return 容器中对应名称和类型的 Bean 实例
     */
    public static <T> T getBean(String name, Class<T> requiredType){
        return configurableApplicationContext.getBean(name,requiredType);
    }
}
