package ai.knowhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 企业 AI Agent 平台 —— 业务对话模块的启动类。
 *
 * 本类是整个 knowhub-app 应用的入口。
 * 通过 @SpringBootApplication 注解，Spring Boot 会自动完成以下工作：
 * 
 *   组件扫描：自动发现并注册当前包及其子包下带有 @Component、@Service、@Controller 等注解的类
 *   自动配置：根据 classpath 中的依赖（如 Spring AI、MyBatis-Plus 等）自动配置 Bean
 *   配置属性加载：读取 application.yml / application.properties 中的配置
 * 启动后，该应用会暴露 REST 接口（如 SSE 流式聊天、会话管理等），
 * 底层集成 Spring AI Alibaba 的 ReactAgent 进行 AI 推理，
 * 并连接 MySQL、Elasticsearch、Neo4j 等中间件实现 RAG 检索增强生成。
 *
 * 技术栈概览
 * 
 *   Java 17 + Spring Boot 3.5.6
 *   Spring AI 1.1.0 + Spring AI Alibaba 1.1.2（通义千问大模型）
 *   MyBatis-Plus（数据库 ORM）
 *   响应式编程（WebFlux / Flux）实现 SSE 流式推送
 * 
 */
@SpringBootApplication
public class KnowHubApplication {

    /**
     * 应用主入口方法。
     *
     * JVM 从这里开始执行。SpringApplication.run() 会：
     * 
     *   创建 Spring 应用上下文（ApplicationContext）
     *   自动配置所有 Bean
     *   启动内嵌的 Tomcat / Netty 服务器
     *   加载配置文件中的属性
     * @param args 命令行参数，可通过 --key=value 的形式覆盖配置文件中的属性
     */
    public static void main(String[] args) {
        SpringApplication.run(KnowHubApplication.class, args);
    }

}
