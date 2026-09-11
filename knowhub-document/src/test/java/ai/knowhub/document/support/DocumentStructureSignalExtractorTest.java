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
}
