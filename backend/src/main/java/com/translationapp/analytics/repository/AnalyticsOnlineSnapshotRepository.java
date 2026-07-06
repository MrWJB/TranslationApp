package com.translationapp.analytics.repository;

import com.translationapp.analytics.entity.AnalyticsOnlineSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 在线快照数据访问接口。
 */
public interface AnalyticsOnlineSnapshotRepository extends JpaRepository<AnalyticsOnlineSnapshot, Long> {

    Optional<AnalyticsOnlineSnapshot> findBySnapshotTime(LocalDateTime snapshotTime);

    List<AnalyticsOnlineSnapshot> findBySnapshotTimeBetweenOrderBySnapshotTimeAsc(
            LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(MAX(s.onlineCount), 0) FROM AnalyticsOnlineSnapshot s WHERE s.snapshotTime >= :start")
    int findPeakOnlineSince(@Param("start") LocalDateTime start);

    @Query("SELECT COALESCE(AVG(s.onlineCount), 0) FROM AnalyticsOnlineSnapshot s WHERE s.snapshotTime >= :start")
    double findAvgOnlineSince(@Param("start") LocalDateTime start);

    long countBySnapshotTimeGreaterThanEqual(LocalDateTime start);
}
