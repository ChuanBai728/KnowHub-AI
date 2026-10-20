package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.rag.model.PromptEvidenceManifest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只将答案中的合法 ASCII [n] 绑定到同轮 Prompt 证据。
 * 未展示、未知、编号冲突的来源均不绑定；不根据相似度或历史来源补引。
 */
@Service
public class ExplicitCitationBindingService {
    private static final Pattern TOKEN = Pattern.compile("\\[([1-9][0-9]*)]");

    public BindingResult bind(String answer, PromptEvidenceManifest manifest) {
        Map<String, SearchReference> eligible = new LinkedHashMap<>();
        Set<String> ambiguousIds = new HashSet<>();
        if (manifest != null) {
            for (SearchReference reference : manifest.references()) {
                String id = reference.getReferenceId();
                if (id == null || !id.matches("[1-9][0-9]*")) {
                    continue;
                }
                SearchReference previous = eligible.putIfAbsent(id, reference);
                if (previous != null && !previous.uniqueKey().equals(reference.uniqueKey())) {
                    ambiguousIds.add(id);
                }
            }
        }
        Map<String, SearchReference> bound = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            String id = matcher.group(1);
            SearchReference reference = eligible.get(id);
            if (reference == null || ambiguousIds.contains(id)) {
                rejected.add(matcher.group());
            } else {
                // 按合法 token 首次出现顺序归档，同一证据多次引用只保留一次。
                bound.putIfAbsent(reference.uniqueKey(), reference);
            }
        }
        return new BindingResult(List.copyOf(bound.values()), List.copyOf(rejected));
    }

    public record BindingResult(List<SearchReference> references, List<String> rejectedTokens) { }
}
