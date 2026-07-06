package com.translationapp.im.service;

import com.translationapp.im.config.ImProperties;
import com.translationapp.im.domain.ImEventType;
import com.translationapp.im.domain.PresenceStatus;
import com.translationapp.im.dto.ImEvent;
import com.translationapp.im.repository.FriendshipRepository;
import com.translationapp.im.domain.FriendshipStatus;
import com.translationapp.im.websocket.ImEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * IM 用户在线状态服务，基于 Redis 维护 presence 信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PRESENCE_KEY_PREFIX = "im:presence:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ImProperties imProperties;
    private final FriendshipRepository friendshipRepository;
    private final ImEventPublisher eventPublisher;

    public void setOnline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        stringRedisTemplate.opsForHash().put(key, "status", PresenceStatus.ONLINE.name());
        stringRedisTemplate.opsForHash().put(key, "lastSeen", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.expire(key, Duration.ofSeconds(imProperties.getPresence().getTtlSeconds()));
        broadcastPresence(userId, PresenceStatus.ONLINE);
    }

    public void heartbeat(Long userId) {
        setOnline(userId);
    }

    public void setOffline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        stringRedisTemplate.opsForHash().put(key, "status", PresenceStatus.OFFLINE.name());
        stringRedisTemplate.opsForHash().put(key, "lastSeen", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.expire(key, Duration.ofHours(24));
        broadcastPresence(userId, PresenceStatus.OFFLINE);
    }

    public PresenceStatus getStatus(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        Object status = stringRedisTemplate.opsForHash().get(key, "status");
        if (status == null) {
            return PresenceStatus.OFFLINE;
        }
        try {
            return PresenceStatus.valueOf(status.toString());
        } catch (Exception e) {
            log.warn("Invalid presence status for user {}: {}", userId, status);
            return PresenceStatus.OFFLINE;
        }
    }

    public Set<Long> getOnlineUserIds() {
        Set<Long> online = new HashSet<>();
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(PRESENCE_KEY_PREFIX + "*")
                    .count(200)
                    .build();
            try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    Object status = stringRedisTemplate.opsForHash().get(key, "status");
                    if (PresenceStatus.ONLINE.name().equals(String.valueOf(status))) {
                        String idPart = key.substring(PRESENCE_KEY_PREFIX.length());
                        try {
                            online.add(Long.parseLong(idPart));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid presence key suffix: {}", idPart);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read online users from Redis: {}", e.getMessage());
        }
        return online;
    }

    public int countOnlineUsers() {
        return getOnlineUserIds().size();
    }

    private void broadcastPresence(Long userId, PresenceStatus status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("status", status);

        friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED)
                .forEach(f -> eventPublisher.publishToUser(
                        f.getFriendUserId(),
                        "/queue/presence",
                        ImEvent.of(ImEventType.PRESENCE, payload)));
    }
}
