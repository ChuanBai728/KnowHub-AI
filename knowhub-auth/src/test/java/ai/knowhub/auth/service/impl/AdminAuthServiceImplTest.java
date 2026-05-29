package ai.knowhub.auth.service.impl;

import ai.knowhub.auth.config.AdminAuthProperties;
import ai.knowhub.auth.dto.AdminLoginDto;
import ai.knowhub.auth.support.AdminJwtTokenService;
import ai.knowhub.auth.vo.AdminLoginVo;
import ai.knowhub.exception.KnowHubFrameException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceImplTest {

    @Test
    void loginTrimsConfiguredAdminAndReturnsToken() {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setUsername("admin");
        properties.setPassword("admin123456");
        properties.setTokenExpireMinutes(30L);
        AdminJwtTokenService tokenService = mock(AdminJwtTokenService.class);
        when(tokenService.generateToken("admin")).thenReturn("jwt-token");
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(properties, tokenService);

        AdminLoginDto dto = new AdminLoginDto();
        dto.setUsername(" admin ");
        dto.setPassword(" admin123456 ");

        AdminLoginVo result = service.login(dto);

        assertThat(result.getUsername()).isEqualTo("admin");
        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getExpireMinutes()).isEqualTo(30L);
        verify(tokenService).generateToken("admin");
    }

    @Test
    void loginRejectsInvalidPassword() {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setUsername("admin");
        properties.setPassword("admin123456");
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(properties, mock(AdminJwtTokenService.class));

        AdminLoginDto dto = new AdminLoginDto();
        dto.setUsername("admin");
        dto.setPassword("wrong");

        assertThatThrownBy(() -> service.login(dto))
            .isInstanceOfSatisfying(KnowHubFrameException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(401));
    }
}
