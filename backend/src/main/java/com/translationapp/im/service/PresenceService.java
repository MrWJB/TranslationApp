package com.translationapp.im.service;

import com.translationapp.im.config.ImProperties;
import com.translationapp.im.domain.ImEventType;
import com.translationapp.im.domain.PresenceStatus;
import com.translationapp.im.dto.ImEvent;
import com.translationapp.im.repository.FriendshipRepository;
import com.translationapp.im.domain.FriendshipStatus;
import com.translationapp.im.websocket.ImEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
            return PresenceStatus.OFFLINE;
        }
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
