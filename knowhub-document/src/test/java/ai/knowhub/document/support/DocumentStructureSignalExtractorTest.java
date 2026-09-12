package ai.knowhub.document.support;

import ai.knowhub.document.config.DocumentManageProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStructureSignalExtractorTest {

    @Test
    void fencedCodeCannotCreateFakeHeadingsOrListItems() {
        DocumentStructureSignalExtractor extractor = new DocumentStructureSignalExtractor(
            new DocumentManageProperties(),
            new DocumentLineClassifier()
        );
        String markdown = """
            # 安装步骤

            ```bash
            # 这是一条 shell 注释
            4. kubectl apply -f demo.yaml
            ```

            ## 验证结果
            """;

        DocumentStructureSignalBatch batch = extractor.extract("部署文档", markdown);
        List<DocumentStructureSignal> codeSignals = batch.signals().stream()
            .filter(signal -> signal.getReasons().contains("fenced-code-content"))
            .toList();

        assertThat(codeSignals).hasSize(2);
        assertThat(codeSignals).allMatch(signal -> signal.getKind() == DocumentStructureSignalKind.BODY);
        assertThat(batch.signals().stream()
            .filter(DocumentStructureSignal::isHeadingLike)
            .map(DocumentStructureSignal::getTitle))
            .containsExactly("安装步骤", "验证结果");
        assertThat(batch.signals().stream()
            .filter(signal -> signal.getReasons().contains("fenced-code-boundary")))
            .hasSize(2);
    }

    @Test
    void htmlCommentsFrontMatterAndHugoShortcodesCannotCreateFakeNodes() {
        DocumentStructureSignalExtractor extractor = new DocumentStructureSignalExtractor(
            new DocumentManageProperties(),
            new DocumentLineClassifier()
        );
        String markdown = """
            # Kubernetes ConfigMap

            > 来源说明

            ---
            title: ConfigMap
            api_metadata:
            - apiVersion: v1
            ---

            {{< note >}}
            <!--
            ## Motivation
            - English-only commented item
            -->
            ## 动机 {#motivation}
            中文正文。
            {{< /note >}}
            ## {{% heading "whatsnext" %}}
            * {{< glossary_tooltip text="监视" term_id="watch" >}}上线的状态，直到完成。
            """;

        DocumentStructureSignalBatch batch = extractor.extract("Kubernetes ConfigMap", markdown);

        assertThat(batch.signals().stream()
            .filter(DocumentStructureSignal::isHeadingLike)
            .map(DocumentStructureSignal::getTitle))
            .containsExactly("动机");
        assertThat(batch.signals().stream()
            .filter(signal -> signal.getKind() == DocumentStructureSignalKind.LIST_ITEM))
            .extracting(DocumentStructureSignal::getTitle)
            .containsExactly("上线的状态，直到完成。");
        assertThat(batch.signals().stream()
            .filter(signal -> signal.getKind() != DocumentStructureSignalKind.NOISE)
            .map(DocumentStructureSignal::getTitle)
            .noneMatch(title -> title != null && title.contains("{{")))
            .isTrue();
        assertThat(batch.signals().stream()
            .flatMap(signal -> signal.getReasons().stream()))
            .contains("yaml-front-matter", "html-comment", "hugo-shortcode");
    }
}
