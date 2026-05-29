package ai.knowhub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Admin login request parameters.
 */
@Data
public class AdminLoginDto {

    @NotBlank(message = "请输入账号")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}
