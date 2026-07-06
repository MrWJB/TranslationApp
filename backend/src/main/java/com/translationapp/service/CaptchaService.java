package com.translationapp.service;

import com.translationapp.config.CaptchaProperties;
import com.translationapp.dto.CaptchaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码生成与校验服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String REDIS_KEY_PREFIX = "captcha:";

    private final CaptchaProperties captchaProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, CacheEntry> fallbackCache = new ConcurrentHashMap<>();

    /**
     * 生成新的算术验证码。
     *
     * @return 验证码 ID 与 Base64 图片
     */
    public CaptchaResponse generate() {
        int leftOperand = random.nextInt(10) + 1;
        int rightOperand = random.nextInt(10) + 1;
        String answer = String.valueOf(leftOperand + rightOperand);
        String expression = leftOperand + " + " + rightOperand + " = ?";
        String captchaId = UUID.randomUUID().toString();
        storeAnswer(captchaId, answer);
        String imageBase64 = renderImage(expression);
        return new CaptchaResponse(captchaId, imageBase64);
    }

    /**
     * 校验用户提交的验证码。
     *
     * @param captchaId   验证码 ID
     * @param captchaCode 用户输入的验证码
     */
    public void validate(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new IllegalArgumentException("验证码无效，请刷新后重试");
        }
        if (captchaCode == null || captchaCode.isBlank()) {
            throw new IllegalArgumentException("请输入验证码");
        }

        String expected = consumeAnswer(captchaId);
        if (expected == null) {
            throw new IllegalArgumentException("验证码已过期，请刷新后重试");
        }
        if (!expected.equalsIgnoreCase(captchaCode.trim())) {
            throw new IllegalArgumentException("验证码错误");
        }
    }

    private void storeAnswer(String captchaId, String answer) {
        Duration ttl = Duration.ofSeconds(captchaProperties.getTtlSeconds());
        try {
            stringRedisTemplate.opsForValue().set(REDIS_KEY_PREFIX + captchaId, answer, ttl);
        } catch (Exception ex) {
            log.warn("Redis unavailable for captcha, using in-memory fallback: {}", ex.getMessage());
            long expiresAt = System.currentTimeMillis() + ttl.toMillis();
            fallbackCache.put(captchaId, new CacheEntry(answer, expiresAt));
        }
    }

    private String consumeAnswer(String captchaId) {
        try {
            String key = REDIS_KEY_PREFIX + captchaId;
            String answer = stringRedisTemplate.opsForValue().get(key);
            if (answer != null) {
                stringRedisTemplate.delete(key);
            }
            return answer;
        } catch (Exception ex) {
            log.warn("Redis unavailable for captcha verify, using in-memory fallback: {}", ex.getMessage());
            CacheEntry entry = fallbackCache.remove(captchaId);
            if (entry == null || System.currentTimeMillis() > entry.expiresAt) {
                return null;
            }
            return entry.answer;
        }
    }

    private String renderImage(String text) {
        int width = captchaProperties.getWidth();
        int height = captchaProperties.getHeight();
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(245, 247, 250));
                g.fillRect(0, 0, width, height);

                for (int i = 0; i < 6; i++) {
                    g.setColor(new Color(random.nextInt(180), random.nextInt(180), random.nextInt(180)));
                    g.setStroke(new BasicStroke(1.2f));
                    int x1 = random.nextInt(width);
                    int y1 = random.nextInt(height);
                    int x2 = random.nextInt(width);
                    int y2 = random.nextInt(height);
                    g.drawLine(x1, y1, x2, y2);
                }

                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                g.setColor(new Color(30, 80, 140));
                int textWidth = g.getFontMetrics().stringWidth(text);
                g.drawString(text, (width - textWidth) / 2, height / 2 + 8);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            } finally {
                g.dispose();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate captcha image (ensure -Djava.awt.headless=true)", ex);
        }
    }

    private record CacheEntry(String answer, long expiresAt) {}
}
