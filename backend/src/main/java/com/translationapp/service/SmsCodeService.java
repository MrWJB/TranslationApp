package com.translationapp.service;

import com.translationapp.config.SmsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SMS verification for phone registration.
 * Dev: uses configurable mock code ({@code app.sms.mock-code}, default 123456).
 * Production: replace {@link #sendCode(String)} body with Aliyun/Tencent SMS API call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private final SmsProperties smsProperties;
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public void sendCode(String phone) {
        String code;
        if (smsProperties.isMockEnabled()) {
            code = smsProperties.getMockCode();
            log.info("[SMS mock] code for {} is {} (integrate real SMS provider in production)", phone, code);
        } else {
            code = String.format("%06d", random.nextInt(1_000_000));
            // TODO: integrate Aliyun / Tencent Cloud SMS here
            log.info("[SMS] sent verification code to {}", phone);
        }
        long expiresAt = Instant.now().getEpochSecond()
                + smsProperties.getCodeTtlMinutes() * 60L;
        codes.put(phone, new CodeEntry(code, expiresAt));
    }

    public boolean verifyCode(String phone, String code) {
        CodeEntry entry = codes.get(phone);
        if (entry == null) {
            return false;
        }
        if (Instant.now().getEpochSecond() > entry.expiresAt) {
            codes.remove(phone);
            return false;
        }
        if (!entry.code.equals(code)) {
            return false;
        }
        codes.remove(phone);
        return true;
    }

    private record CodeEntry(String code, long expiresAt) {}
}
