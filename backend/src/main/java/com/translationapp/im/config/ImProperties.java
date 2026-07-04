package com.translationapp.im.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "im")
public class ImProperties {
    private Message message = new Message();
    private Attachment attachment = new Attachment();
    private Presence presence = new Presence();
    private RedisRelay redisRelay = new RedisRelay();

    @Data
    public static class Message {
        private int maxLength = 10000;
        private int historyPageSize = 30;
    }

    @Data
    public static class Attachment {
        private long maxSizeBytes = 52428800L;
        private String allowedMimeTypes = "image/*,application/pdf";
    }

    @Data
    public static class Presence {
        private long heartbeatIntervalMs = 30000;
        private long ttlSeconds = 90;
    }

    @Data
    public static class RedisRelay {
        private boolean enabled = true;
        private String channel = "im:user-events";
    }
}
