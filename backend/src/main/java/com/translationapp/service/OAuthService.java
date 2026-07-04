package com.translationapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.translationapp.config.OAuthProperties;
import com.translationapp.dto.LoginResponse;
import com.translationapp.dto.OAuthProviderDTO;
import com.translationapp.entity.Role;
import com.translationapp.entity.User;
import com.translationapp.entity.UserOauthBinding;
import com.translationapp.repository.RoleRepository;
import com.translationapp.repository.UserOauthBindingRepository;
import com.translationapp.repository.UserRepository;
import com.translationapp.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthProperties oauthProperties;
    private final UserRepository userRepository;
    private final UserOauthBindingRepository oauthBindingRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WebClient.Builder webClientBuilder;

    public List<OAuthProviderDTO> listProviders() {
        return List.of(
                provider("github", "GitHub"),
                provider("wechat", "微信"),
                provider("qq", "QQ"),
                provider("wecom", "企业微信")
        );
    }

    private OAuthProviderDTO provider(String id, String name) {
        return new OAuthProviderDTO(id, name, oauthProperties.isConfigured(id) || oauthProperties.isDemoMode());
    }

    public String buildAuthorizeUrl(String provider, String state) {
        if (!oauthProperties.isConfigured(provider) && !oauthProperties.isDemoMode()) {
            throw new IllegalArgumentException("该登录方式尚未配置，请联系管理员");
        }
        OAuthProperties.ProviderConfig config = oauthProperties.getProviderConfig(provider);
        if (config == null) {
            throw new IllegalArgumentException("Unsupported OAuth provider: " + provider);
        }

        return switch (provider.toLowerCase()) {
            case "github" -> UriComponentsBuilder
                    .fromUriString("https://github.com/login/oauth/authorize")
                    .queryParam("client_id", config.getClientId())
                    .queryParam("redirect_uri", config.getRedirectUri())
                    .queryParam("scope", "read:user user:email")
                    .queryParam("state", state)
                    .build(true)
                    .toUriString();
            case "wechat" -> "https://open.weixin.qq.com/connect/qrconnect"
                    + "?appid=" + encode(config.getClientId())
                    + "&redirect_uri=" + encode(config.getRedirectUri())
                    + "&response_type=code&scope=snsapi_login&state=" + encode(state)
                    + "#wechat_redirect";
            case "qq" -> "https://graph.qq.com/oauth2.0/authorize"
                    + "?response_type=code"
                    + "&client_id=" + encode(config.getClientId())
                    + "&redirect_uri=" + encode(config.getRedirectUri())
                    + "&state=" + encode(state);
            case "wecom" -> "https://open.weixin.qq.com/connect/oauth2/authorize"
                    + "?appid=" + encode(config.getClientId())
                    + "&redirect_uri=" + encode(config.getRedirectUri())
                    + "&response_type=code"
                    + "&scope=snsapi_privateinfo"
                    + "&agentid=" + encode(config.getAgentId())
                    + "&state=" + encode(state)
                    + "#wechat_redirect";
            default -> throw new IllegalArgumentException("Unsupported OAuth provider: " + provider);
        };
    }

    @Transactional
    public LoginResponse handleCallback(String provider, String code, String state) {
        if (oauthProperties.isDemoMode() && !oauthProperties.isConfigured(provider)) {
            return handleDemoCallback(provider);
        }
        if (!oauthProperties.isConfigured(provider)) {
            throw new IllegalArgumentException("该登录方式尚未配置");
        }

        OAuthProfile profile = switch (provider.toLowerCase()) {
            case "github" -> fetchGitHubProfile(code);
            case "wechat" -> fetchWeChatProfile(code, provider);
            case "qq" -> fetchQqProfile(code, provider);
            case "wecom" -> fetchWeComProfile(code, provider);
            default -> throw new IllegalArgumentException("Unsupported OAuth provider: " + provider);
        };

        return loginOrRegisterOAuthUser(provider, profile);
    }

    private LoginResponse handleDemoCallback(String provider) {
        String openId = "demo_" + provider + "_" + UUID.randomUUID().toString().substring(0, 8);
        OAuthProfile profile = new OAuthProfile(openId, null, provider + "_demo", null);
        return loginOrRegisterOAuthUser(provider, profile);
    }

    private OAuthProfile fetchGitHubProfile(String code) {
        OAuthProperties.ProviderConfig config = oauthProperties.getGithub();
        WebClient client = webClientBuilder.build();

        JsonNode tokenResponse = client.post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(java.util.Map.of(
                        "client_id", config.getClientId(),
                        "client_secret", config.getClientSecret(),
                        "code", code,
                        "redirect_uri", config.getRedirectUri()
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (tokenResponse == null || !tokenResponse.has("access_token")) {
            throw new IllegalArgumentException("GitHub OAuth token exchange failed");
        }
        String accessToken = tokenResponse.get("access_token").asText();

        JsonNode user = client.get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (user == null || !user.has("id")) {
            throw new IllegalArgumentException("Failed to fetch GitHub user profile");
        }

        String openId = user.get("id").asText();
        String nickname = user.has("login") ? user.get("login").asText() : "github_user";
        String avatar = user.has("avatar_url") ? user.get("avatar_url").asText() : null;
        return new OAuthProfile(openId, null, nickname, avatar);
    }

    /** Placeholder: wire WeChat token + userinfo APIs when credentials are available */
    private OAuthProfile fetchWeChatProfile(String code, String provider) {
        OAuthProperties.ProviderConfig config = oauthProperties.getWechat();
        log.info("WeChat OAuth callback received (code present). Configure app.oauth.wechat and implement token exchange.");
        throw new IllegalArgumentException("微信 OAuth 已收到回调，请在后端 OAuthService.fetchWeChatProfile 中完成 access_token 与用户信息换取");
    }

    private OAuthProfile fetchQqProfile(String code, String provider) {
        log.info("QQ OAuth callback received. Configure app.oauth.qq and implement token exchange.");
        throw new IllegalArgumentException("QQ OAuth 已收到回调，请在后端 OAuthService.fetchQqProfile 中完成 access_token 与 openid 换取");
    }

    private OAuthProfile fetchWeComProfile(String code, String provider) {
        log.info("WeCom OAuth callback received. Configure app.oauth.wecom and implement token exchange.");
        throw new IllegalArgumentException("企业微信 OAuth 已收到回调，请在后端 OAuthService.fetchWeComProfile 中完成 access_token 与 userid 换取");
    }

    @Transactional
    protected LoginResponse loginOrRegisterOAuthUser(String provider, OAuthProfile profile) {
        UserOauthBinding binding = oauthBindingRepository
                .findByProviderAndOpenId(provider, profile.openId())
                .orElse(null);

        User user;
        if (binding != null) {
            user = userRepository.findById(binding.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("OAuth binding references missing user"));
        } else {
            String baseUsername = sanitizeUsername(profile.nickname() != null ? profile.nickname() : provider + "_" + profile.openId());
            String username = uniqueUsername(baseUsername);
            user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRealName(profile.nickname());
            user.setAvatar(profile.avatarUrl());
            user.setIsEnabled(true);
            user.setIsLocked(false);
            user.setRoles(defaultUserRoles());
            user = userRepository.save(user);

            binding = new UserOauthBinding();
            binding.setUserId(user.getId());
            binding.setProvider(provider);
            binding.setOpenId(profile.openId());
            binding.setUnionId(profile.unionId());
            binding.setNickname(profile.nickname());
            binding.setAvatarUrl(profile.avatarUrl());
            oauthBindingRepository.save(binding);
        }

        return buildLoginResponse(user);
    }

    private Set<Role> defaultUserRoles() {
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalArgumentException("Default USER role not found"));
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        return roles;
    }

    private String uniqueUsername(String base) {
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int i = 1; i < 1000; i++) {
            String candidate = base + "_" + i;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        return base + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String sanitizeUsername(String raw) {
        String cleaned = raw.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
        if (cleaned.length() < 3) {
            cleaned = cleaned + "_oauth";
        }
        return cleaned.substring(0, Math.min(cleaned.length(), 28));
    }

    private LoginResponse buildLoginResponse(User user) {
        String roleName = user.getRoles() != null && !user.getRoles().isEmpty()
                ? user.getRoles().iterator().next().getName()
                : "USER";
        String token = jwtUtil.generateToken(user.getUsername(), roleName, user.getId());
        return new LoginResponse(token, user.getUsername(), roleName, user.getId());
    }

    public String buildFrontendRedirect(LoginResponse response) {
        return UriComponentsBuilder.fromUriString(oauthProperties.getFrontendCallbackUrl())
                .queryParam("token", response.getToken())
                .queryParam("username", response.getUsername())
                .queryParam("userId", response.getUserId())
                .queryParam("role", response.getRole())
                .build(true)
                .toUriString();
    }

    public String buildFrontendErrorRedirect(String message) {
        return UriComponentsBuilder.fromUriString(oauthProperties.getFrontendCallbackUrl())
                .queryParam("error", message)
                .build(true)
                .toUriString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record OAuthProfile(String openId, String unionId, String nickname, String avatarUrl) {}
}
