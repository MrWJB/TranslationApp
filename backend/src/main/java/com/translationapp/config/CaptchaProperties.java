package com.translationapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.captcha")
public class CaptchaProperties {

    private int ttlSeconds = 300;
    private int width = 130;
    private int height = 44;
}
