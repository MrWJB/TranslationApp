package com.translationapp.analytics.dto;

import com.translationapp.analytics.domain.AnalyticsRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 大屏看板聚合数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BigScreenDashboardDTO {
    private AnalyticsRange range;
    private String rangeLabel;
    private String generatedAt;

    private int realtimeOnline;
    private int todayPeakOnline;
    private int periodPeakOnline;
    private double periodAvgOnline;
    private long totalUsers;
    private long enabledUsers;
    private long newUsersInPeriod;
    private long activeUsersInPeriod;
    private long loginCountInPeriod;
    private double onlineRate;

    private List<OnlineTrendPointDTO> onlineTrend;
    private List<OnlineTrendPointDTO> hourlyOnlineToday;
    private List<LabelCountDTO> genderDistributionAll;
    private List<LabelCountDTO> genderDistributionOnline;
    private List<LabelCountDTO> ageDistributionAll;
    private List<LabelCountDTO> ageDistributionOnline;
    private List<LabelCountDTO> regionDistributionAll;
    private List<LabelCountDTO> regionDistributionOnline;
    private List<LabelCountDTO> cityDistributionTop10;
    private List<LabelCountDTO> loginTrend;
    private List<RealtimeOnlineUserDTO> realtimeOnlineUsers;
}
