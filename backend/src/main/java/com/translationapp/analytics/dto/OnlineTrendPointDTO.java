package com.translationapp.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线人数趋势数据点 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineTrendPointDTO {
    private String time;
    private int onlineCount;
    private int peakInBucket;
}
