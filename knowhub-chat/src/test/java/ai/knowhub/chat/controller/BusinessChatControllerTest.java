package ai.knowhub.chat.controller;

import ai.knowhub.chat.dto.ConversationSessionListQueryDto;
import ai.knowhub.chat.service.BusinessChatService;
import ai.knowhub.chat.vo.ConversationSessionListVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BusinessChatControllerTest {

    @Mock
    private BusinessChatService businessChatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new BusinessChatController(businessChatService))
            .build();
    }

    @Test
    void sessionListUsesApiV1ChatRoute() throws Exception {
        when(businessChatService.listSessions(any(ConversationSessionListQueryDto.class)))
            .thenReturn(new ConversationSessionListVo(1, 10, 0, 0, List.of()));

        mockMvc.perform(post("/api/v1/chat/session/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageNo\":\"1\",\"pageSize\":\"10\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.pageNo").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(10));
    }
}
