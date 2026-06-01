package ai.knowhub.config;

import java.util.List;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 【公共模块自动配置类】—— 配置 Jackson 全局规则和 MVC 消息转换器
 *
 * 核心作用：
 * 这是 knowhub-common 模块的核心配置类。它做了两件重要的事情：
 * 
 *   注册 JacksonCustom 作为全局 Jackson 自定义器（配置日期格式等）
 *   自定义 Spring MVC 的 JSON 消息转换器（处理 null 值序列化 + 数字转字符串）
 * 为什么需要"两层"Jackson 配置？
 * 
 *   第一层（JacksonCustom）：配置全局的日期序列化/反序列化规则，影响所有 ObjectMapper
 *   第二层（webMvcJacksonConfigurer）：专门为 MVC 的 HTTP 消息转换器定制，
 *       添加了 null 值处理（JsonCustomSerializer）和数字转字符串功能
 * 关于条件注解：
 * 
 *   @ConditionalOnWebApplication(SERVLET)：仅在 Servlet Web 应用中生效（排除 WebFlux）
 *   @ConditionalOnClass(WebMvcConfigurer.class)：仅在 classpath 中有 Spring MVC 时生效
 *   @ConditionalOnBean(ObjectMapper.class)：仅在容器中已有 ObjectMapper Bean 时生效
 * 注意：
 * 本类没有标注 @Configuration，而是通过 SPI 机制（spring.factories 或
 * AutoConfiguration.imports）自动加载，这是 Spring Boot 自动配置的标准做法。
 *
 * @see JacksonCustom         Jackson 全局自定义配置
 * @see JsonCustomSerializer  null 值序列化修饰器
 */
public class KnowHubCommonAutoConfig {

    /**
     * 注册 Jackson 全局自定义器 Bean
     *
     * 返回 JacksonCustom 实例，Spring Boot 会自动用它来定制 ObjectMapper。
     * 这个 Bean 负责配置日期格式、JSON 读取特性、忽略未知属性等全局规则。
     *
     * @return JacksonCustom 实例（实现了 Jackson2ObjectMapperBuilderCustomizer 接口）
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustom() {
        return new JacksonCustom();
    }

    /**
     * 注册自定义的 Spring MVC WebMvcConfigurer Bean
     *
     * 这个 Bean 的核心目的是：替换 Spring MVC 默认的 JSON 消息转换器，
     * 使用一个经过特殊定制的 ObjectMapper，该 ObjectMapper 具备以下能力：
     * 
     *   null 值处理：通过 JsonCustomSerializer，将 null String 转为 ""，
     *       null Number 转为 ""，null Boolean 转为 false，null 集合转为 []
     *   数字转字符串：所有 Number 类型输出为字符串格式（如 123 → "123"），
     *       避免 JavaScript 大数字精度丢失问题
     * 条件注解说明：
     * 
     *   @ConditionalOnWebApplication(SERVLET)：仅在传统 Servlet Web 环境生效
     *   @ConditionalOnClass(WebMvcConfigurer.class)：需要 Spring MVC 存在
     *   @ConditionalOnBean(ObjectMapper.class)：需要已存在 ObjectMapper Bean
     * @param objectMapper Spring 自动配置的 ObjectMapper（由 Spring Boot 自动注入）
     * @return 自定义的 WebMvcConfigurer 实例
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer.class)
    @ConditionalOnBean(ObjectMapper.class)
    public WebMvcConfigurer webMvcJacksonConfigurer(ObjectMapper objectMapper) {
        return new WebMvcConfigurer() {
            /**
             * 扩展（替换）HTTP 消息转换器列表
             *
             * Spring MVC 在处理 HTTP 请求/响应时，会使用消息转换器来完成
             * Java 对象与 JSON 字符串之间的转换。默认使用的是
             * MappingJackson2HttpMessageConverter。
             *
             * 此方法的逻辑：
             * 
             *   创建一个使用定制 ObjectMapper 的新转换器
             *   遍历现有转换器列表，找到默认的 Jackson 转换器
             *   用新转换器替换默认转换器（保持支持的媒体类型不变）
             *   如果没找到默认 Jackson 转换器，则将新转换器添加到列表首位
             * @param converters 当前的 HTTP 消息转换器列表
             */
            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {

                // 使用定制后的 ObjectMapper 创建新的消息转换器
                MappingJackson2HttpMessageConverter mvcJacksonConverter =
                    new MappingJackson2HttpMessageConverter(createMvcObjectMapper(objectMapper));

                // 遍历现有转换器，找到并替换默认的 Jackson 转换器
                for (int i = 0; i < converters.size(); i++) {
                    if (converters.get(i) instanceof MappingJackson2HttpMessageConverter existingConverter) {
                        // 将原转换器支持的媒体类型（Content-Type）复制到新转换器
                        mvcJacksonConverter.setSupportedMediaTypes(existingConverter.getSupportedMediaTypes());
                        // 替换：用新转换器覆盖原转换器
                        converters.set(i, mvcJacksonConverter);
                        return;
                    }
                }

                // 如果没找到默认的 Jackson 转换器（不太常见），将新转换器添加到列表首位
                converters.add(0, mvcJacksonConverter);
            }
        };
    }

    /**
     * 创建用于 MVC 的定制 ObjectMapper
     *
     * 基于 Spring 自动配置的 ObjectMapper 创建一个副本，并添加两项定制：
     * 
     *   注册 JsonCustomSerializer 作为序列化修饰器，处理 null 值的默认输出
     *   启用 WRITE_NUMBERS_AS_STRINGS 特性，将所有数字输出为字符串格式
     * 为什么要用 copy()？
     * 不能直接修改 Spring 自动注入的 ObjectMapper，因为它可能被其他地方使用。
     * 创建副本后独立修改，互不影响。
     *
     * 为什么数字要转字符串？
     * JavaScript 的 Number 类型最大安全整数是 2^53（约 9007 万亿），
     * 超过此范围的 Long 类型数字会丢失精度。输出为字符串可以避免此问题。
     *
     * @param objectMapper Spring 自动配置的原始 ObjectMapper
     * @return 定制后的 ObjectMapper 副本
     */
    private ObjectMapper createMvcObjectMapper(ObjectMapper objectMapper) {
        // 创建副本，避免影响原始的 ObjectMapper
        ObjectMapper mvcObjectMapper = objectMapper.copy();
        // 注册 null 值序列化修饰器：控制各类型 null 值的 JSON 输出
        mvcObjectMapper.setSerializerFactory(
            mvcObjectMapper.getSerializerFactory().withSerializerModifier(new JsonCustomSerializer())
        );
        // 启用"数字作为字符串输出"特性：避免 JavaScript 大数字精度丢失
        mvcObjectMapper.getFactory().configure(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS.mappedFeature(), true);
        return mvcObjectMapper;
    }
}
