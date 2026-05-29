package ai.knowhub.document.controller;

import ai.knowhub.document.dto.DocumentPageQueryDto;
import ai.knowhub.document.service.DocumentManageService;
import ai.knowhub.document.vo.DocumentPageQueryVo;
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
class DocumentManageControllerTest {

    @Mock
    private DocumentManageService documentManageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new DocumentManageController(documentManageService))
            .build();
    }

    @Test
    void pageQueryUsesApiV1ManageDocumentRoute() throws Exception {
        when(documentManageService.queryDocumentPage(any(DocumentPageQueryDto.class)))
            .thenReturn(new DocumentPageQueryVo(1, 10, 0L, List.of()));

        mockMvc.perform(post("/api/v1/manage/document/page/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageNo\":1,\"pageSize\":10}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.pageNo").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(10));
    }
}
