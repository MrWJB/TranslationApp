package com.translationapp.analytics.controller;

import com.translationapp.analytics.domain.AnalyticsRange;
import com.translationapp.analytics.dto.BigScreenDashboardDTO;
import com.translationapp.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端数据分析 API，提供大屏看板与快照触发接口。
 */
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * 获取指定时间范围的大屏看板数据。
     *
     * @param range 统计时间范围，默认 TODAY
     * @return 大屏看板 DTO
     */
    @GetMapping("/dashboard")
    public ResponseEntity<BigScreenDashboardDTO> dashboard(
            @RequestParam(defaultValue = "TODAY") AnalyticsRange range) {
        return ResponseEntity.ok(analyticsService.getDashboard(range));
    }

    /**
     * 手动触发一次在线快照采集。
     *
     * @return 空响应体
     */
    @PostMapping("/snapshot")
    public ResponseEntity<Void> triggerSnapshot() {
        analyticsService.recordSnapshot();
        return ResponseEntity.ok().build();
    }
}
