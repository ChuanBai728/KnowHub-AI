package ai.knowhub.chat.support;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 【RestClient 工厂支持类】
 *
 * 作用：提供创建 Spring RestClient 实例的工厂方法。
 * RestClient 是 Spring 6.1+ 引入的现代 HTTP 客户端，
 * 用于替代 RestTemplate 进行 HTTP 调用。
 *
 * 所属架构位置：属于支持工具层（Support Layer），为各业务模块提供 HTTP 客户端创建能力。
 * 例如用于调用外部搜索 API（如 Tavily）、知识图谱 API 等。
 *
 * 设计模式说明：
 * 1. 「工厂方法模式（Factory Method）」—— 封装 RestClient 的创建过程。
 * 2. 「不可实例化」—— 私有构造函数，所有方法都是静态的。
 *
 * @author knowhub
 */
public final class RestClientFactorySupport {

    /**
     * 私有构造函数，防止实例化
     */
    private RestClientFactorySupport() {
    }

    /**
     * 创建 RestClient 实例
     *
     * @param baseUrl          基础 URL，如 "https://api.tavily.com"。如果为空则不设置。
     * @param connectTimeoutMs 连接超时时间（毫秒）。如果 <= 0 则使用默认值。
     * @param readTimeoutMs    读取超时时间（毫秒）。如果 <= 0 则使用默认值。
     * @return 配置好的 RestClient 实例
     */
    public static RestClient create(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        // 创建请求工厂，配置超时参数
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (connectTimeoutMs > 0) {
            requestFactory.setConnectTimeout(connectTimeoutMs);
        }
        if (readTimeoutMs > 0) {
            requestFactory.setReadTimeout(readTimeoutMs);
        }

        // 构建 RestClient
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (StringUtils.hasText(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
