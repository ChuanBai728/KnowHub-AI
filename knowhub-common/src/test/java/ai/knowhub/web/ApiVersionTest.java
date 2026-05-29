package ai.knowhub.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionTest {

    @Test
    void exposesVersionedRoutePrefixes() {
        assertThat(ApiVersion.V1_PREFIX).isEqualTo("/api/v1");
        assertThat(ApiVersion.V1_CHAT).isEqualTo("/api/v1/chat");
        assertThat(ApiVersion.V1_ADMIN_AUTH).isEqualTo("/api/v1/admin/auth");
        assertThat(ApiVersion.V1_MANAGE_DOCUMENT).isEqualTo("/api/v1/manage/document");
        assertThat(ApiVersion.V1_MANAGE_KNOWLEDGE).isEqualTo("/api/v1/manage/knowledge");
    }
}
