package com.translationapp.analytics.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线用户快照实体，记录某一时刻的在线人数及维度分布。
 */
@Data
@Entity
@Table(name = "analytics_online_snapshot")
public class AnalyticsOnlineSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_time", nullable = false, unique = true)
    private LocalDateTime snapshotTime;

    @Column(name = "online_count", nullable = false)
    private Integer onlineCount = 0;

    @Column(name = "total_users", nullable = false)
    private Integer totalUsers = 0;

    @Column(name = "male_online", nullable = false)
    private Integer maleOnline = 0;

    @Column(name = "female_online", nullable = false)
    private Integer femaleOnline = 0;

    @Column(name = "other_online", nullable = false)
    private Integer otherOnline = 0;

    @Column(name = "unknown_online", nullable = false)
    private Integer unknownOnline = 0;

    @Column(name = "age_under_18", nullable = false)
    private Integer ageUnder18 = 0;

    @Column(name = "age_18_24", nullable = false)
    private Integer age1824 = 0;

    @Column(name = "age_25_34", nullable = false)
    private Integer age2534 = 0;

    @Column(name = "age_35_44", nullable = false)
    private Integer age3544 = 0;

    @Column(name = "age_45_54", nullable = false)
    private Integer age4554 = 0;

    @Column(name = "age_55_plus", nullable = false)
    private Integer age55Plus = 0;

    @Column(name = "region_stats_json", columnDefinition = "TEXT")
    private String regionStatsJson;
}
