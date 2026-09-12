package ai.knowhub.document.service.impl;

import ai.knowhub.document.support.DocumentKnowledgeMetadataKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentKnowledgeServiceImplTest {

    @Test
    void recognizesExplicitChannelCountFromRrfFusion() {
        Document hybrid = document("hybrid", 2);

        assertThat(DocumentKnowledgeServiceImpl.hasMultiChannelSupport(List.of(hybrid))).isTrue();
    }

    @Test
    void remainsCompatibleWithLegacyHybridMarker() {
        Document legacyHybrid = document("hybrid", null);

        assertThat(DocumentKnowledgeServiceImpl.hasMultiChannelSupport(List.of(legacyHybrid))).isTrue();
    }

    @Test
    void recognizesDifferentChannelsAcrossSiblingHits() {
        assertThat(DocumentKnowledgeServiceImpl.hasMultiChannelSupport(List.of(
            document("vector", 1),
            document("keyword", 1)
        ))).isTrue();
    }

    @Test
    void doesNotRewardRepeatedHitsFromOnlyOneChannel() {
        assertThat(DocumentKnowledgeServiceImpl.hasMultiChannelSupport(List.of(
            document("keyword", 1),
            document("keyword", 1),
            document("keyword", 1)
        ))).isFalse();
    }

    private Document document(String channel, Integer channelCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL, channel);
        if (channelCount != null) {
            metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL_COUNT, channelCount);
        }
        return Document.builder()
            .id(channel + "-" + metadata.hashCode())
            .text("证据")
            .metadata(metadata)
            .build();
    }
}
