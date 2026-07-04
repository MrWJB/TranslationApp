package com.translationapp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class HealthController {

    private final RestTemplate restTemplate;

    @Value("${crawler.node-service.url:http://localhost:3000}")
    private String crawlerUrl;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "translation-backend"));
    }

    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> services() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("backend", Map.of("status", "ok", "port", 8080));

        String crawlerStatus = "down";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> crawlerHealth = restTemplate.getForObject(crawlerUrl + "/health", Map.class);
            if (crawlerHealth != null && "ok".equals(crawlerHealth.get("status"))) {
                crawlerStatus = "ok";
            }
        } catch (Exception ignored) {
            // crawler unreachable
        }
        result.put("crawler", Map.of("status", crawlerStatus, "port", 3000, "url", crawlerUrl));
        return ResponseEntity.ok(result);
    }
}
