package com.translationapp.analytics;

import com.translationapp.analytics.domain.AnalyticsRange;
import com.translationapp.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AnalyticsDashboardIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Test
    void getDashboardTodayDoesNotThrow() {
        analyticsService.getDashboard(AnalyticsRange.TODAY);
    }
}
