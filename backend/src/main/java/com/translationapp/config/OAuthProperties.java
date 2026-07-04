package com.translationapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    private String frontendCallbackUrl = "http://localhost:5173/oauth/callback";
    private boolean demoMode = false;

    private ProviderConfig github = new ProviderConfig();
    private ProviderConfig wechat = new ProviderConfig();
    private ProviderConfig qq = new ProviderConfig();
    private ProviderConfig wecom = new ProviderConfig();

    @Data
    public static class ProviderConfig {
        private boolean enabled = false;
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";
        /** WeCom agent id */
        private String agentId = "";
    }

    public ProviderConfig getProviderConfig(String provider) {
        return switch (provider.toLowerCase()) {
            case "github" -> github;
            case "wechat" -> wechat;
            case "qq" -> qq;
            case "wecom" -> wecom;
            default -> null;
        };
    }

    public boolean isConfigured(String provider) {
        ProviderConfig config = getProviderConfig(provider);
        if (config == null) {
            return false;
        }
        if (!config.isEnabled()) {
            return false;
        }
        return config.getClientId() != null && !config.getClientId().isBlank()
                && config.getClientSecret() != null && !config.getClientSecret().isBlank()
                && config.getRedirectUri() != null && !config.getRedirectUri().isBlank();
    }
}
