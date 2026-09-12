package ai.knowhub.document.service.impl;

import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteEmbeddingCacheTest {

    @Test
    void reloadsPersistedEmbeddingAfterJvmCacheIsRecreated() {
        Map<String, float[]> redisValues = new LinkedHashMap<>();
        ObjectProvider<RedissonClient> provider = redisProvider(redisValues);
        RouteEmbeddingCache firstInstance = new RouteEmbeddingCache(provider, "text-embedding-v3");
        float[] expected = new float[]{0.1F, 0.2F, 0.3F};

        firstInstance.put("Dubbo P2C Load Balance", expected);

        RouteEmbeddingCache restartedInstance = new RouteEmbeddingCache(provider, "text-embedding-v3");
        assertArrayEquals(expected, restartedInstance.get("Dubbo P2C Load Balance"));
    }

    @Test
    void separatesPersistentCachesWhenEmbeddingModelChanges() {
        ObjectProvider<RedissonClient> provider = mockProviderWithoutRedis();

        RouteEmbeddingCache oldModel = new RouteEmbeddingCache(provider, "text-embedding-v3");
        RouteEmbeddingCache newModel = new RouteEmbeddingCache(provider, "text-embedding-v4");

        assertNotEquals(oldModel.redisMapName(), newModel.redisMapName());
    }

    @Test
    void keepsWorkingWhenRedisIsUnavailable() {
        RouteEmbeddingCache cache = new RouteEmbeddingCache(mockProviderWithoutRedis(), "text-embedding-v3");
        float[] expected = new float[]{0.5F, 0.6F};

        cache.put("Kubernetes Secret", expected);

        assertArrayEquals(expected, cache.get("Kubernetes Secret"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<RedissonClient> redisProvider(Map<String, float[]> redisValues) {
        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMap<String, float[]> redisMap = mock(RMap.class);
        when(provider.getIfAvailable()).thenReturn(redissonClient);
        when(redissonClient.<String, float[]>getMap(org.mockito.ArgumentMatchers.anyString())).thenReturn(redisMap);
        when(redisMap.getAll(anySet())).thenAnswer(invocation -> {
            Set<String> keys = invocation.getArgument(0);
            Map<String, float[]> result = new LinkedHashMap<>();
            keys.forEach(key -> {
                if (redisValues.containsKey(key)) {
                    result.put(key, redisValues.get(key));
                }
            });
            return result;
        });
        doAnswer(invocation -> {
            Map<String, float[]> values = invocation.getArgument(0);
            redisValues.putAll(values);
            return null;
        }).when(redisMap).putAll(anyMap());
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<RedissonClient> mockProviderWithoutRedis() {
        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
