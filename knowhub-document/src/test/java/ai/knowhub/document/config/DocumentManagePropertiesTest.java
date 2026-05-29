package ai.knowhub.document.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentManagePropertiesTest {

    @Test
    void bindsRetainedVectorAndGraphOptions() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.manage.milvus.enabled", "true")
            .withProperty("app.manage.milvus.collection-name", "knowhub_document_embedding")
            .withProperty("app.manage.pgvector.database", "knowhub_pgvector")
            .withProperty("app.manage.elasticsearch.index-name", "knowhub-document-keyword")
            .withProperty("app.manage.neo4j.enabled", "true")
            .withProperty("app.manage.neo4j.uri", "bolt://127.0.0.1:7687");

        DocumentManageProperties properties = Binder.get(environment)
            .bind("app.manage", Bindable.of(DocumentManageProperties.class))
            .orElseThrow(IllegalStateException::new);

        assertThat(properties.getMilvus().getEnabled()).isTrue();
        assertThat(properties.getMilvus().getCollectionName()).isEqualTo("knowhub_document_embedding");
        assertThat(properties.getPgVector().getDatabase()).isEqualTo("knowhub_pgvector");
        assertThat(properties.getElasticsearch().getIndexName()).isEqualTo("knowhub-document-keyword");
        assertThat(properties.getNeo4j().getEnabled()).isTrue();
        assertThat(properties.getNeo4j().getUri()).isEqualTo("bolt://127.0.0.1:7687");
    }
}
