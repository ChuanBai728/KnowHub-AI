package ai.knowhub.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 后台管理登录的配置属性类。
 *
 * 该类使用 @ConfigurationProperties 将 application.yml 中以
 * app.admin-auth 为前缀的配置项自动绑定到 Java 对象的字段上。
 *
 * 配置示例（application.yml）
 * 
 * app:
 *   admin-auth:
 *     username: admin
 *     password: mySecretPassword
 *     token-secret: my-jwt-secret-key
 *     token-expire-minutes: 720
 * 设计说明
 * 这里使用了简单的用户名/密码配置来管理后台登录，适合内部管理系统场景。
 * 在生产环境中，建议将密码和 token-secret 通过环境变量注入，避免硬编码在配置文件中。
 *
 * 涉及的注解
 * 
 *   @Data —— Lombok 注解，自动生成 getter/setter/toString/equals/hashCode 方法
 *   @ConfigurationProperties —— Spring Boot 注解，实现类型安全的配置绑定
 * 
 */
@Data
@ConfigurationProperties(prefix = "app.admin-auth")
public class AdminAuthProperties {

    /**
     * 后台登录用户名。
     * 默认值 "admin"，可通过配置文件覆盖。
     */
    private String username = "admin";

    /**
     * 后台登录密码。
     * 默认值 "admin123456"，生产环境务必修改。
     */
    private String password = "admin123456";

    /**
     * JWT 签名密钥。
     * 用于对 JWT Token 进行 HMAC-SHA256 签名和验证。
     * 密钥长度建议至少 256 位（32 字节），以确保安全性。
     */
    private String tokenSecret = "knowhub-admin-token-secret-change-me";

    /**
     * Token 有效期，单位：分钟。
     * 默认 720 分钟（12 小时）。超过有效期后，用户需要重新登录。
     */
    private Long tokenExpireMinutes = 720L;
}
