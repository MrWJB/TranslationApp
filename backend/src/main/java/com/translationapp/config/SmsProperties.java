package com.translationapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    /** Dev mode: accept fixed mock code instead of real SMS */
    private boolean mockEnabled = true;
    private String mockCode = "123456";
    private int codeTtlMinutes = 5;
}
