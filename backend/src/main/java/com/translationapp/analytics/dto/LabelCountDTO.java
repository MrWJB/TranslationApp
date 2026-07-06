package com.translationapp.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 带标签的计数统计 DTO，用于分布图展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelCountDTO {
    private String label;
    private long count;
    private double percent;
}
