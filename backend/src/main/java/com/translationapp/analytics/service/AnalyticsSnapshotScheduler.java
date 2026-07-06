package com.translationapp.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时采集在线用户快照的调度器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsSnapshotScheduler {

    private final AnalyticsService analyticsService;

    /**
     * 每 5 分钟记录一次在线用户快照。
     */
    @Scheduled(fixedRate = 300_000)
    public void recordOnlineSnapshot() {
        try {
            analyticsService.recordSnapshot();
        } catch (Exception e) {
            log.warn("Failed to record analytics snapshot: {}", e.getMessage());
        }
    }
}
