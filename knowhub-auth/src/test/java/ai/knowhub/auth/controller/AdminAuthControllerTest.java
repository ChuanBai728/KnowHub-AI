package ai.knowhub.auth.controller;

import ai.knowhub.auth.dto.AdminLoginDto;
import ai.knowhub.auth.service.AdminAuthService;
import ai.knowhub.auth.vo.AdminLoginVo;
import ai.knowhub.auth.vo.AdminProfileVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    @Mock
    private AdminAuthService adminAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminAuthController(adminAuthService))
            .build();
    }

    @Test
    void loginUsesApiV1Route() throws Exception {
        when(adminAuthService.login(any(AdminLoginDto.class)))
            .thenReturn(new AdminLoginVo("admin", "token-value", 720L));

        mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.token").value("token-value"));
    }

    @Test
    void meUsesApiV1Route() throws Exception {
        when(adminAuthService.currentProfile(any()))
            .thenReturn(new AdminProfileVo("admin"));

        mockMvc.perform(get("/api/v1/admin/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.username").value("admin"));
    }
}
