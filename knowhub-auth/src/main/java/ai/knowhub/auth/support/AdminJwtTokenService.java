package ai.knowhub.auth.support;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import ai.knowhub.auth.config.AdminAuthProperties;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.stereotype.Component;

/**
 * 后台登录的 JWT（JSON Web Token）服务。
 *
 * JWT 是一种开放标准（RFC 7519），用于在网络应用环境间安全地传递声明信息。
 * 一个 JWT Token 由三部分组成，用 "." 分隔：
 * 
 *   <b>Header</b>（头部）：声明令牌类型和签名算法（如 HS256）
 *   <b>Payload</b>（载荷）：包含声明信息（如用户名、过期时间等）
 *   <b>Signature</b>（签名）：对前两部分的签名，防止篡改
 * JWT 的优势
 * 
 *   无状态：服务端不需要保存会话信息，适合分布式系统
 *   自包含：Token 中包含了所有必要信息
 *   跨域友好：可以轻松在不同服务间传递
 * 安全注意事项
 * 
 *   签名密钥必须保密，且长度足够（至少 256 位）
 *   Token 有效期不宜过长
 *   敏感信息不应放在 Payload 中（JWT 只是编码，不是加密）
 * 使用的库
 * 本类使用 JJWT 库来生成和解析 JWT Token。
 */
@Component
public class AdminJwtTokenService {

    /** 后台登录配置，提供 JWT 签名密钥和 Token 有效期 */
    private final AdminAuthProperties adminAuthProperties;

    /**
     * 构造函数，注入配置属性。
     *
     * @param adminAuthProperties 后台登录配置
     */
    public AdminJwtTokenService(AdminAuthProperties adminAuthProperties) {
        this.adminAuthProperties = adminAuthProperties;
    }

    /**
     * 生成 JWT Token。
     *
     * 创建一个包含用户名和过期时间的 JWT Token，并使用 HMAC-SHA256 算法签名。
     *
     * Token 的 Payload 包含：
     * 
     *   sub（Subject）：用户名
     *   iat（Issued At）：签发时间
     *   exp（Expiration）：过期时间
     * @param username 要写入 Token 的用户名
     * @return 签名后的 JWT Token 字符串
     */
    public String generateToken(String username) {
        Instant now = Instant.now();
        // 计算过期时间：当前时间 + 配置的有效期（分钟转秒）
        Instant expireAt = now.plusSeconds(adminAuthProperties.getTokenExpireMinutes() * 60);

        return Jwts.builder()
            .setSubject(username)                              // 设置主题（用户名）
            .setIssuedAt(Date.from(now))                       // 设置签发时间
            .setExpiration(Date.from(expireAt))                // 设置过期时间
            .signWith(
                SignatureAlgorithm.HS256,                       // 使用 HMAC-SHA256 签名算法
                adminAuthProperties.getTokenSecret().getBytes(StandardCharsets.UTF_8)  // 签名密钥
            )
            .compact();  // 压缩生成最终的 Token 字符串
    }

    /**
     * 解析并验证 JWT Token。
     *
     * 验证 Token 的签名是否正确、是否已过期，并返回其中包含的声明信息。
     *
     * @param token 要解析的 JWT Token 字符串
     * @return Token 中包含的声明信息（Claims），可以通过 claims.getSubject() 获取用户名
     * @throws KnowHubFrameException 当 Token 已过期时抛出 401 异常（提示"已过期"）
     * @throws KnowHubFrameException 当 Token 无效（签名错误、格式错误等）时抛出 401 异常
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                .setSigningKey(adminAuthProperties.getTokenSecret().getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)   // 解析并验证签名
                .getBody();              // 获取 Payload 中的声明信息
        } catch (ExpiredJwtException exception) {
            // Token 已过期
            throw new KnowHubFrameException(401, "后台登录已过期，请重新登录", exception);
        } catch (JwtException | IllegalArgumentException exception) {
            // Token 无效（签名错误、格式错误、为空等）
            throw new KnowHubFrameException(401, "后台登录无效，请重新登录", exception);
        }
    }
}
