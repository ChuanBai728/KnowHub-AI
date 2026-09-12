package ai.knowhub.document.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 知识路由候选向量的两级缓存。
 *
 * <p>JVM 缓存负责单实例热路径，Redis 缓存负责跨服务重启复用。缓存键由
 * embedding 模型和路由文本内容共同决定：模型切换或文档路由材料变化时会自然生成新键，
 * 不会读取旧向量。Redis 故障时仅退回 JVM 缓存，不中断知识路由。</p>
 */
@Slf4j
@Component
public class RouteEmbeddingCache {

    static final int LOCAL_CACHE_MAX_SIZE = 20_000;
    private static final String CACHE_SCHEMA_VERSION = "v1";
    private static final String REDIS_MAP_PREFIX = "knowhub:route-embedding:";

    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final String redisMapName;
    private final ConcurrentMap<String, float[]> localCache = new ConcurrentHashMap<>();

    public RouteEmbeddingCache(ObjectProvider<RedissonClient> redissonClientProvider,
                               @Value("${spring.ai.openai.embedding.options.model:default}") String embeddingModelName) {
        this.redissonClientProvider = redissonClientProvider;
        String resolvedModelName = StrUtil.blankToDefault(embeddingModelName, "default").trim();
        this.redisMapName = REDIS_MAP_PREFIX + CACHE_SCHEMA_VERSION + ":" + sha256(resolvedModelName);
    }

    /** 批量读取向量，返回值以原始路由文本为键。 */
    Map<String, float[]> getAll(List<String> routeTexts) {
        if (routeTexts == null || routeTexts.isEmpty()) {
            return Map.of();
        }
        shrinkLocalCacheIfNecessary();
        Map<String, String> textByCacheKey = new LinkedHashMap<>();
        Map<String, float[]> result = new LinkedHashMap<>();
        for (String routeText : routeTexts) {
            String normalizedText = normalizeText(routeText);
            String cacheKey = sha256(normalizedText);
            float[] localEmbedding = localCache.get(cacheKey);
            if (localEmbedding != null) {
                result.put(normalizedText, localEmbedding);
            }
            else {
                textByCacheKey.putIfAbsent(cacheKey, normalizedText);
            }
        }
        if (textByCacheKey.isEmpty()) {
            return result;
        }
        try {
            RMap<String, float[]> redisMap = redisMap();
            if (redisMap == null) {
                return result;
            }
            Map<String, float[]> persisted = redisMap.getAll(new LinkedHashSet<>(textByCacheKey.keySet()));
            for (Map.Entry<String, float[]> entry : persisted.entrySet()) {
                String routeText = textByCacheKey.get(entry.getKey());
                float[] embedding = entry.getValue();
                if (routeText != null && embedding != null && embedding.length > 0) {
                    localCache.putIfAbsent(entry.getKey(), embedding);
                    result.put(routeText, embedding);
                }
            }
        }
        catch (RuntimeException exception) {
            log.warn("读取知识路由持久化向量缓存失败，退回 JVM 缓存: {}", exception.getMessage());
        }
        return result;
    }

    /** 批量写入 JVM 和 Redis；Redis 写入失败不会影响当前请求。 */
    void putAll(Map<String, float[]> embeddingsByRouteText) {
        if (embeddingsByRouteText == null || embeddingsByRouteText.isEmpty()) {
            return;
        }
        shrinkLocalCacheIfNecessary();
        Map<String, float[]> valuesByCacheKey = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : embeddingsByRouteText.entrySet()) {
            float[] embedding = entry.getValue();
            if (embedding == null || embedding.length == 0) {
                continue;
            }
            String cacheKey = sha256(normalizeText(entry.getKey()));
            localCache.putIfAbsent(cacheKey, embedding);
            valuesByCacheKey.put(cacheKey, embedding);
        }
        if (valuesByCacheKey.isEmpty()) {
            return;
        }
        try {
            RMap<String, float[]> redisMap = redisMap();
            if (redisMap != null) {
                redisMap.putAll(valuesByCacheKey);
            }
        }
        catch (RuntimeException exception) {
            log.warn("写入知识路由持久化向量缓存失败，仅保留 JVM 缓存: {}", exception.getMessage());
        }
    }

    float[] get(String routeText) {
        return getAll(new ArrayList<>(List.of(normalizeText(routeText)))).get(normalizeText(routeText));
    }

    void put(String routeText, float[] embedding) {
        putAll(Map.of(normalizeText(routeText), embedding));
    }

    String redisMapName() {
        return redisMapName;
    }

    private RMap<String, float[]> redisMap() {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        return redissonClient == null ? null : redissonClient.getMap(redisMapName);
    }

    private void shrinkLocalCacheIfNecessary() {
        if (localCache.size() > LOCAL_CACHE_MAX_SIZE) {
            localCache.clear();
        }
    }

    private static String normalizeText(String text) {
        return StrUtil.blankToDefault(text, "").trim();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(StrUtil.blankToDefault(value, "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 支持", exception);
        }
    }
}
