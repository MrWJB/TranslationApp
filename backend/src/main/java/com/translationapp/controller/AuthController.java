package com.translationapp.controller;

import com.translationapp.dto.CaptchaResponse;
import com.translationapp.dto.LoginRequest;
import com.translationapp.dto.LoginResponse;
import com.translationapp.dto.OAuthProviderDTO;
import com.translationapp.dto.PhoneRegisterRequest;
import com.translationapp.dto.PhoneSendCodeRequest;
import com.translationapp.dto.RegisterRequest;
import com.translationapp.service.AuthService;
import com.translationapp.service.CaptchaService;
import com.translationapp.service.OAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthService oauthService;
    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    public ResponseEntity<CaptchaResponse> captcha() {
        return ResponseEntity.ok(captchaService.generate());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/register/phone/send-code")
    public ResponseEntity<Map<String, String>> sendPhoneCode(@Valid @RequestBody PhoneSendCodeRequest request) {
        authService.sendPhoneCode(request.getPhone());
        return ResponseEntity.ok(Map.of("message", "验证码已发送"));
    }

    @PostMapping("/register/phone")
    public ResponseEntity<LoginResponse> registerByPhone(@Valid @RequestBody PhoneRegisterRequest request) {
        return ResponseEntity.ok(authService.registerByPhone(request));
    }

    @GetMapping("/oauth/providers")
    public ResponseEntity<List<OAuthProviderDTO>> oauthProviders() {
        return ResponseEntity.ok(oauthService.listProviders());
    }

    @GetMapping("/oauth/{provider}/authorize")
    public void oauthAuthorize(@PathVariable String provider, HttpServletResponse response) throws IOException {
        try {
            String state = UUID.randomUUID().toString();
            String url = oauthService.buildAuthorizeUrl(provider, state);
            response.sendRedirect(url);
        } catch (IllegalArgumentException ex) {
            response.sendRedirect(oauthService.buildFrontendErrorRedirect(ex.getMessage()));
        }
    }

    @GetMapping("/oauth/{provider}/callback")
    public void oauthCallback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            HttpServletResponse response
    ) throws IOException {
        if (error != null && !error.isBlank()) {
            String msg = error_description != null ? error_description : error;
            response.sendRedirect(oauthService.buildFrontendErrorRedirect(msg));
            return;
        }
        if (code == null || code.isBlank()) {
            response.sendRedirect(oauthService.buildFrontendErrorRedirect("OAuth 授权失败：缺少 code"));
            return;
        }
        try {
            LoginResponse loginResponse = oauthService.handleCallback(provider, code, state);
            response.sendRedirect(oauthService.buildFrontendRedirect(loginResponse));
        } catch (Exception ex) {
            response.sendRedirect(oauthService.buildFrontendErrorRedirect(ex.getMessage()));
        }
    }
}
