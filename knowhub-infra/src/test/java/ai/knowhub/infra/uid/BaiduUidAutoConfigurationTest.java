package ai.knowhub.infra.uid;

import com.baidu.fsg.uid.UidGenerator;
import com.baidu.fsg.uid.impl.CachedUidGenerator;
import com.baidu.fsg.uid.worker.WorkerIdAssigner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class BaiduUidAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BaiduUidAutoConfiguration.class))
        .withUserConfiguration(TestWorkerIdConfiguration.class);

    @Test
    void registersOnlyBaiduUidGeneratorBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(UidGenerator.class);
            assertThat(context.getBean(UidGenerator.class)).isInstanceOf(CachedUidGenerator.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestWorkerIdConfiguration {

        @Bean
        WorkerIdAssigner disposableWorkerIdAssigner() {
            return () -> 1L;
        }
    }
}
