package ai.knowhub.infra.uid;

import com.baidu.fsg.uid.config.IdGeneratorRedisConfig;
import com.baidu.fsg.uid.config.WorkerNodeConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * KnowHub AI wrapper for the retained Baidu UID implementation.
 */
@Configuration(proxyBeanMethods = false)
@Import({IdGeneratorRedisConfig.class, WorkerNodeConfig.class})
public class BaiduUidAutoConfiguration {
}
