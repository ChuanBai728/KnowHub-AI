package ai.knowhub.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthPropertiesTest {

    @Test
    void bindsKnowHubAdminAuthProperties() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.admin-auth.username", "root")
            .withProperty("app.admin-auth.password", "secret")
            .withProperty("app.admin-auth.token-secret", "knowhub-secret")
            .withProperty("app.admin-auth.token-expire-minutes", "60");

        AdminAuthProperties properties = Binder.get(environment)
            .bind("app.admin-auth", Bindable.of(AdminAuthProperties.class))
            .orElseThrow(IllegalStateException::new);

        assertThat(properties.getUsername()).isEqualTo("root");
        assertThat(properties.getPassword()).isEqualTo("secret");
        assertThat(properties.getTokenSecret()).isEqualTo("knowhub-secret");
        assertThat(properties.getTokenExpireMinutes()).isEqualTo(60L);
    }
}
