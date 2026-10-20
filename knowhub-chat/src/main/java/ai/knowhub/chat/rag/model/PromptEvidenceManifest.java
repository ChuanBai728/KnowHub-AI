package ai.knowhub.chat.rag.model;

import ai.knowhub.chat.model.SearchReference;
import org.springframework.beans.BeanUtils;

import java.util.List;

/**
 * 本轮实际写入 Prompt 的证据快照。检索候选和历史引用不能代替本清单。
 * 构建和读取时都复制引用，避免后续修改检索对象改变引用资格或归档内容。
 */
public final class PromptEvidenceManifest {
    private final List<SearchReference> references;

    public PromptEvidenceManifest(List<SearchReference> references) {
        this.references = references.stream().map(PromptEvidenceManifest::copy).toList();
    }

    public List<SearchReference> references() {
        return references.stream().map(PromptEvidenceManifest::copy).toList();
    }

    private static SearchReference copy(SearchReference reference) {
        SearchReference snapshot = new SearchReference();
        BeanUtils.copyProperties(reference, snapshot);
        return snapshot;
    }
}
