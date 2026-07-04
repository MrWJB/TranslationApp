package com.translationapp.im.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "webrtc")
public class WebrtcProperties {
    private String stunUrls = "stun:localhost:3478";
    private String turnUrls = "turn:localhost:3478";
    private String turnUsername = "turnuser";
    private String turnCredential = "turnpass";
}
