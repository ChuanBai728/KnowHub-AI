package ai.knowhub.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 【Swagger/Knife4j API 文档配置类】—— 配置接口文档的基本信息
 *
 * 核心作用：
 * 配置项目的 API 文档元信息，包括标题、版本号、描述、联系方式等。
 * 这些信息会显示在 Knife4j/Swagger UI 的文档页面上，帮助前端开发者了解接口。
 *
 * 什么是 Swagger / Knife4j？
 * 
 *   Swagger（现称 OpenAPI）：一种 API 文档规范，通过注解自动生成接口文档
 *   Knife4j：对 Swagger UI 的增强版，提供更美观的界面和更多功能
 *       （如接口搜索、离线文档、全局参数等）
 * 使用效果：
 * 启动项目后，访问 Knife4j 文档地址（通常为 /doc.html），
 * 可以看到所有 Controller 中定义的接口文档，并支持在线调试。
 *
 * 注解说明：
 * 
 *   @Configuration：标记此类为 Spring 配置类，Spring 会自动扫描并加载其中的 Bean
 *   @Bean：将方法返回的对象注册为 Spring 容器中的一个 Bean
 * OpenAPI 3.0 vs Swagger 2：
 * 本项目使用的是 OpenAPI 3.0 规范（对应 Swagger 3.x），
 * 比旧版 Swagger 2.x 功能更强大。注解也从 @ApiModel 变为 @Schema 等。
 */
@Configuration
public class SwaggerConfiguration {

    /**
     * 自定义 OpenAPI 文档信息
     *
     * 创建并返回 OpenAPI 对象，包含 API 文档的基本信息。
     * 这些信息会显示在 Knife4j 文档页面的顶部。
     *
     * 信息说明：
     * 
     *   title（标题）："前端使用" —— 表示此文档面向前端开发者
     *   version（版本）："1.0" —— API 版本号
     *   description（描述）："项目学习" —— 项目用途说明
     *   contact（联系人）：作者名称
     * @return 配置好文档信息的 OpenAPI 对象
     */
    @Bean
    public OpenAPI customOpenApi() {

        return new OpenAPI()
                .info(new Info()
                        .title("KnowHub AI API")
                        .version("v1")
                        .description("KnowHub AI 知识库问答平台接口文档")
                        .contact(new Contact()
                                .name("ChuanBai728")
                        ));
    }
}
