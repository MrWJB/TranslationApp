package com.translationapp.analytics.repository;

import com.translationapp.analytics.entity.UserLoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户登录日志数据访问接口。
 */
public interface UserLoginLogRepository extends JpaRepository<UserLoginLog, Long> {

    @Query("SELECT COUNT(DISTINCT l.userId) FROM UserLoginLog l WHERE l.loginTime >= :start")
    long countDistinctUsersSince(@Param("start") LocalDateTime start);

    @Query("SELECT FUNCTION('DATE', l.loginTime), COUNT(DISTINCT l.userId) FROM UserLoginLog l " +
            "WHERE l.loginTime >= :start GROUP BY FUNCTION('DATE', l.loginTime) ORDER BY FUNCTION('DATE', l.loginTime)")
    List<Object[]> countDailyActiveUsers(@Param("start") LocalDateTime start);

    long countByLoginTimeGreaterThanEqual(LocalDateTime start);
}
