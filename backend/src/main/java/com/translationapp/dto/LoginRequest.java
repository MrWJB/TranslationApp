package com.translationapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Captcha id is required")
    private String captchaId;

    @NotBlank(message = "Captcha code is required")
    private String captchaCode;

    /** When true, issue a longer-lived JWT (see jwt.remember-me-expiration). */
    private boolean rememberMe;
}
