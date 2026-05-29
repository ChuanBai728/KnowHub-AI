package ai.knowhub.constant;

/**
 * 【全局常量定义类】
 *
 * 作用：集中存放整个项目中需要共享的常量值，避免硬编码（magic string/number）散落在各处。
 *
 * 设计思路：
 *   - PREFIX_DISTINCTION_NAME：配置文件中的属性 key，用于区分不同部署环境或租户的前缀。
 *   - DEFAULT_PREFIX_DISTINCTION_NAME：当配置文件中没有设置该属性时，使用的默认值。
 *   - SPRING_INJECT_PREFIX_DISTINCTION_NAME：Spring 的属性注入占位符格式，
 *     语法为 ${key:defaultValue}，Spring 容器启动时会自动解析并注入实际值。
 *
 * 使用场景：
 *   在 Bean 中通过 @Value("${prefix.distinction.name:knowhub}") 注解注入该前缀，
 *   用于 Redis key 前缀、Kafka topic 前缀等需要区分环境的场景。
 */
public class Constant {

    /**
     * 配置文件中的属性 key
     * 对应 application.yml 中的 prefix.distinction.name 配置项。
     * 用于在多租户或多环境下区分不同实例的资源前缀。
     */
    public static final String PREFIX_DISTINCTION_NAME = "prefix.distinction.name";

    /**
     * 默认的区分名称前缀
     * 当配置文件中未设置 prefix.distinction.name 时，使用此默认值 "knowhub"。
     */
    public static final String DEFAULT_PREFIX_DISTINCTION_NAME = "knowhub";

    /**
     * Spring 属性注入占位符
     * 格式：${属性key:默认值}
     * Spring 在初始化 Bean 时，会将此占位符替换为配置文件中的实际值。
     * 可直接用于 @Value 注解的 value 参数中。
     */
    public static final String SPRING_INJECT_PREFIX_DISTINCTION_NAME = "${"+PREFIX_DISTINCTION_NAME+":"+DEFAULT_PREFIX_DISTINCTION_NAME+"}";

}
