package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.AnswerHistoryContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerHistoryContextAssemblerTest {

    @Test
    void recognizesDemonstrativeFollowUpWithRecentContext() {
        AnswerHistoryContextAssembler assembler = new AnswerHistoryContextAssembler(new ChatRagProperties());

        AnswerHistoryContext context = assembler.assemble(
            "不想自己做这种编码转换时该用哪个字段？",
            "【最近相关对话】\n用户：Secret 的 data 字段值需要采用什么编码？"
        );

        assertThat(context.isFollowUpQuestion()).isTrue();
        assertThat(context.isEmpty()).isFalse();
        assertThat(context.getRenderedText()).contains("Secret 的 data 字段值");
    }
}
