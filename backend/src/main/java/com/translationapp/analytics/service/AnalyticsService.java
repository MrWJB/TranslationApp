package com.translationapp.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.translationapp.analytics.domain.AnalyticsRange;
import com.translationapp.analytics.dto.*;
import com.translationapp.analytics.entity.AnalyticsOnlineSnapshot;
import com.translationapp.analytics.entity.UserLoginLog;
import com.translationapp.analytics.repository.AnalyticsOnlineSnapshotRepository;
import com.translationapp.analytics.repository.UserLoginLogRepository;
import com.translationapp.entity.User;
import com.translationapp.im.service.PresenceService;
import com.translationapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户在线与登录数据分析服务，负责快照采集与大屏看板聚合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> AGE_BUCKETS = List.of(
            "18岁以下", "18-24岁", "25-34岁", "35-44岁", "45-54岁", "55岁及以上", "未知"
    );

    private static final Map<String, String> GENDER_LABELS = Map.of(
            "MALE", "男",
            "FEMALE", "女",
            "OTHER", "其他",
            "UNKNOWN", "未知"
    );

    private final AnalyticsOnlineSnapshotRepository snapshotRepository;
    private final UserLoginLogRepository loginLogRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final ObjectMapper objectMapper;

    /**
     * 记录当前在线用户快照，并按性别、年龄、地区维度汇总。
     */
    @Transactional
    public void recordSnapshot() {
        Set<Long> onlineIds = presenceService.getOnlineUserIds();
        List<User> onlineUsers = onlineIds.isEmpty()
                ? List.of()
                : userRepository.findAllById(onlineIds);
        long totalUsers = userRepository.count();

        AnalyticsOnlineSnapshot snapshot = new AnalyticsOnlineSnapshot();
        snapshot.setSnapshotTime(LocalDateTime.now().withSecond(0).withNano(0));
        snapshot.setOnlineCount(onlineUsers.size());
        snapshot.setTotalUsers((int) totalUsers);

        Map<String, Integer> regionStats = new HashMap<>();
        for (User user : onlineUsers) {
            countGender(snapshot, user.getGender());
            countAge(snapshot, user.getBirthDate());
            String province = blankToDefault(user.getProvince(), "未知");
            regionStats.merge(province, 1, Integer::sum);
        }

        try {
            snapshot.setRegionStatsJson(objectMapper.writeValueAsString(regionStats));
        } catch (Exception e) {
            log.warn("Failed to serialize region stats: {}", e.getMessage());
        }

        snapshotRepository.findBySnapshotTime(snapshot.getSnapshotTime())
                .ifPresentOrElse(existing -> {
                    existing.setOnlineCount(snapshot.getOnlineCount());
                    existing.setTotalUsers(snapshot.getTotalUsers());
                    copyCounts(snapshot, existing);
                    existing.setRegionStatsJson(snapshot.getRegionStatsJson());
                    snapshotRepository.save(existing);
                }, () -> snapshotRepository.save(snapshot));
    }

    /**
     * 记录用户登录日志，并更新用户最近登录时间。
     *
     * @param userId    登录用户 ID
     * @param ipAddress 登录 IP，可为 null
     */
    @Transactional
    public void recordLogin(Long userId, String ipAddress) {
        User user = userRepository.findById(userId).orElse(null);
        UserLoginLog logEntry = new UserLoginLog();
        logEntry.setUserId(userId);
        logEntry.setLoginTime(LocalDateTime.now());
        logEntry.setIpAddress(ipAddress);
        if (user != null) {
            logEntry.setProvince(user.getProvince());
            logEntry.setCity(user.getCity());
            user.setLastLoginTime(LocalDateTime.now());
            userRepository.save(user);
        }
        loginLogRepository.save(logEntry);
    }

    /**
     * 构建大屏看板聚合数据。
     *
     * @param range 统计时间范围
     * @return 大屏看板 DTO
     */
    public BigScreenDashboardDTO getDashboard(AnalyticsRange range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = resolvePeriodStart(range, now);
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

        Set<Long> onlineIds = presenceService.getOnlineUserIds();
        List<User> allUsers = userRepository.findAll();
        List<User> onlineUsers = allUsers.stream()
                .filter(user -> onlineIds.contains(user.getId()))
                .toList();
        int realtimeOnline = onlineUsers.size();

        List<AnalyticsOnlineSnapshot> snapshots = snapshotRepository
                .findBySnapshotTimeBetweenOrderBySnapshotTimeAsc(periodStart, now);

        return assembleDashboard(range, now, periodStart, todayStart, allUsers, onlineUsers, snapshots, realtimeOnline);
    }

    private BigScreenDashboardDTO assembleDashboard(
            AnalyticsRange range,
            LocalDateTime now,
            LocalDateTime periodStart,
            LocalDateTime todayStart,
            List<User> allUsers,
            List<User> onlineUsers,
            List<AnalyticsOnlineSnapshot> snapshots,
            int realtimeOnline) {

        int todayPeak = computeTodayPeakOnline(snapshots, todayStart, realtimeOnline);
        int periodPeak = computePeriodPeakOnline(snapshots, realtimeOnline);
        double periodAvg = computePeriodAvgOnline(snapshots, realtimeOnline);

        long totalUsers = allUsers.size();
        long enabledUsers = allUsers.stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsEnabled()))
                .count();
        long newUsers = allUsers.stream()
                .filter(user -> user.getCreatedAt() != null && !user.getCreatedAt().isBefore(periodStart))
                .count();

        long activeUsers = loginLogRepository.countDistinctUsersSince(periodStart);
        long loginCount = loginLogRepository.countByLoginTimeGreaterThanEqual(periodStart);
        double onlineRate = totalUsers == 0 ? 0 : (realtimeOnline * 100.0 / totalUsers);

        return buildDashboardDto(
                range, now, periodStart, todayStart, allUsers, onlineUsers, snapshots, realtimeOnline,
                todayPeak, periodPeak, periodAvg, totalUsers, enabledUsers, newUsers,
                activeUsers, loginCount, onlineRate);
    }

    private BigScreenDashboardDTO buildDashboardDto(
            AnalyticsRange range,
            LocalDateTime now,
            LocalDateTime periodStart,
            LocalDateTime todayStart,
            List<User> allUsers,
            List<User> onlineUsers,
            List<AnalyticsOnlineSnapshot> snapshots,
            int realtimeOnline,
            int todayPeak,
            int periodPeak,
            double periodAvg,
            long totalUsers,
            long enabledUsers,
            long newUsers,
            long activeUsers,
            long loginCount,
            double onlineRate) {

        return BigScreenDashboardDTO.builder()
                .range(range)
                .rangeLabel(resolveRangeLabel(range))
                .generatedAt(now.format(DATETIME_FMT))
                .realtimeOnline(realtimeOnline)
                .todayPeakOnline(todayPeak)
                .periodPeakOnline(periodPeak)
                .periodAvgOnline(Math.round(periodAvg * 10) / 10.0)
                .totalUsers(totalUsers)
                .enabledUsers(enabledUsers)
                .newUsersInPeriod(newUsers)
                .activeUsersInPeriod(activeUsers)
                .loginCountInPeriod(loginCount)
                .onlineRate(Math.round(onlineRate * 10) / 10.0)
                .onlineTrend(buildOnlineTrend(range, snapshots, periodStart, now, realtimeOnline))
                .hourlyOnlineToday(buildHourlyToday(snapshots, todayStart, now, realtimeOnline))
                .genderDistributionAll(buildGenderDistribution(allUsers))
                .genderDistributionOnline(buildGenderDistribution(onlineUsers))
                .ageDistributionAll(buildAgeDistribution(allUsers))
                .ageDistributionOnline(buildAgeDistribution(onlineUsers))
                .regionDistributionAll(buildRegionDistribution(allUsers))
                .regionDistributionOnline(buildRegionDistribution(onlineUsers))
                .cityDistributionTop10(buildCityDistribution(allUsers, 10))
                .loginTrend(buildLoginTrend(periodStart))
                .realtimeOnlineUsers(buildRealtimeOnlineUsers(onlineUsers))
                .build();
    }

    private int computeTodayPeakOnline(
            List<AnalyticsOnlineSnapshot> snapshots,
            LocalDateTime todayStart,
            int realtimeOnline) {
        int peak = snapshots.stream()
                .filter(snapshot -> !snapshot.getSnapshotTime().isBefore(todayStart))
                .mapToInt(AnalyticsOnlineSnapshot::getOnlineCount)
                .max()
                .orElse(realtimeOnline);
        return Math.max(peak, realtimeOnline);
    }

    private int computePeriodPeakOnline(List<AnalyticsOnlineSnapshot> snapshots, int realtimeOnline) {
        int peak = snapshots.stream()
                .mapToInt(AnalyticsOnlineSnapshot::getOnlineCount)
                .max()
                .orElse(realtimeOnline);
        return Math.max(peak, realtimeOnline);
    }

    private double computePeriodAvgOnline(List<AnalyticsOnlineSnapshot> snapshots, int realtimeOnline) {
        if (snapshots.isEmpty()) {
            return realtimeOnline;
        }
        return snapshots.stream()
                .mapToInt(AnalyticsOnlineSnapshot::getOnlineCount)
                .average()
                .orElse(realtimeOnline);
    }

    private List<RealtimeOnlineUserDTO> buildRealtimeOnlineUsers(List<User> onlineUsers) {
        return onlineUsers.stream()
                .sorted(Comparator.comparing(User::getUsername))
                .limit(50)
                .map(u -> RealtimeOnlineUserDTO.builder()
                        .userId(u.getId())
                        .username(u.getUsername())
                        .realName(u.getRealName())
                        .gender(GENDER_LABELS.getOrDefault(safeGender(u.getGender()), "未知"))
                        .age(calculateAge(u.getBirthDate()))
                        .province(blankToDefault(u.getProvince(), "未知"))
                        .city(blankToDefault(u.getCity(), "未知"))
                        .lastSeen(LocalDateTime.now().format(TIME_FMT))
                        .build())
                .toList();
    }

    private List<OnlineTrendPointDTO> buildOnlineTrend(
            AnalyticsRange range,
            List<AnalyticsOnlineSnapshot> snapshots,
            LocalDateTime periodStart,
            LocalDateTime now,
            int currentOnline) {

        if (snapshots.isEmpty()) {
            return List.of(OnlineTrendPointDTO.builder()
                    .time(now.format(TIME_FMT))
                    .onlineCount(currentOnline)
                    .peakInBucket(currentOnline)
                    .build());
        }

        Duration bucket = switch (range) {
            case TODAY -> Duration.ofMinutes(15);
            case WEEK -> Duration.ofHours(4);
            case MONTH -> Duration.ofDays(1);
            case QUARTER, YEAR -> Duration.ofDays(7);
        };

        Map<String, List<AnalyticsOnlineSnapshot>> grouped = groupSnapshotsByTimeBucket(snapshots, periodStart, bucket);
        List<OnlineTrendPointDTO> result = buildTrendPointsFromGroupedSnapshots(range, grouped);

        if (result.isEmpty()) {
            result.add(OnlineTrendPointDTO.builder()
                    .time(now.format(TIME_FMT))
                    .onlineCount(currentOnline)
                    .peakInBucket(currentOnline)
                    .build());
        }
        return result;
    }

    private Map<String, List<AnalyticsOnlineSnapshot>> groupSnapshotsByTimeBucket(
            List<AnalyticsOnlineSnapshot> snapshots,
            LocalDateTime periodStart,
            Duration bucket) {
        Map<String, List<AnalyticsOnlineSnapshot>> grouped = new LinkedHashMap<>();
        for (AnalyticsOnlineSnapshot snapshot : snapshots) {
            long bucketIndex = Duration.between(periodStart, snapshot.getSnapshotTime()).toMinutes() / bucket.toMinutes();
            String key = String.valueOf(bucketIndex);
            grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(snapshot);
        }
        return grouped;
    }

    private List<OnlineTrendPointDTO> buildTrendPointsFromGroupedSnapshots(
            AnalyticsRange range,
            Map<String, List<AnalyticsOnlineSnapshot>> grouped) {
        List<OnlineTrendPointDTO> result = new ArrayList<>();
        for (List<AnalyticsOnlineSnapshot> bucketSnapshots : grouped.values()) {
            AnalyticsOnlineSnapshot last = bucketSnapshots.get(bucketSnapshots.size() - 1);
            int avg = (int) Math.round(bucketSnapshots.stream()
                    .mapToInt(AnalyticsOnlineSnapshot::getOnlineCount)
                    .average()
                    .orElse(0));
            int peak = bucketSnapshots.stream()
                    .mapToInt(AnalyticsOnlineSnapshot::getOnlineCount)
                    .max()
                    .orElse(0);
            String label = formatTrendLabel(range, last.getSnapshotTime());
            result.add(OnlineTrendPointDTO.builder()
                    .time(label)
                    .onlineCount(avg)
                    .peakInBucket(peak)
                    .build());
        }
        return result;
    }

    private List<OnlineTrendPointDTO> buildHourlyToday(
            List<AnalyticsOnlineSnapshot> snapshots,
            LocalDateTime todayStart,
            LocalDateTime now,
            int currentOnline) {

        Map<Integer, List<Integer>> hourly = new TreeMap<>();
        for (int h = 0; h < 24; h++) {
            hourly.put(h, new ArrayList<>());
        }

        for (AnalyticsOnlineSnapshot snapshot : snapshots) {
            if (snapshot.getSnapshotTime().isBefore(todayStart)) {
                continue;
            }
            int hour = snapshot.getSnapshotTime().getHour();
            hourly.get(hour).add(snapshot.getOnlineCount());
        }

        int currentHour = now.getHour();
        hourly.get(currentHour).add(currentOnline);

        List<OnlineTrendPointDTO> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : hourly.entrySet()) {
            List<Integer> values = entry.getValue();
            int avg = values.isEmpty() ? 0 : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
            int peak = values.isEmpty() ? 0 : values.stream().mapToInt(Integer::intValue).max().orElse(0);
            result.add(OnlineTrendPointDTO.builder()
                    .time(String.format("%02d:00", entry.getKey()))
                    .onlineCount(avg)
                    .peakInBucket(peak)
                    .build());
        }
        return result;
    }

    private List<LabelCountDTO> buildGenderDistribution(List<User> users) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String code : List.of("MALE", "FEMALE", "OTHER", "UNKNOWN")) {
            counts.put(code, 0L);
        }
        for (User user : users) {
            String gender = safeGender(user.getGender());
            counts.merge(gender, 1L, Long::sum);
        }
        return toLabelCounts(counts, GENDER_LABELS);
    }

    private List<LabelCountDTO> buildAgeDistribution(List<User> users) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String bucket : AGE_BUCKETS) {
            counts.put(bucket, 0L);
        }
        for (User user : users) {
            counts.merge(resolveAgeBucket(user.getBirthDate()), 1L, Long::sum);
        }
        return toLabelCounts(counts, null);
    }

    private List<LabelCountDTO> buildRegionDistribution(List<User> users) {
        Map<String, Long> counts = users.stream()
                .collect(Collectors.groupingBy(
                        u -> blankToDefault(u.getProvince(), "未知"),
                        Collectors.counting()));
        return toLabelCounts(sortByCount(counts), null);
    }

    private List<LabelCountDTO> buildCityDistribution(List<User> users, int limit) {
        Map<String, Long> counts = users.stream()
                .filter(u -> u.getCity() != null && !u.getCity().isBlank())
                .collect(Collectors.groupingBy(
                        u -> u.getProvince() + "·" + u.getCity(),
                        Collectors.counting()));
        Map<String, Long> sorted = sortByCount(counts);
        return toLabelCounts(sorted.entrySet().stream()
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new)),
                null);
    }

    private List<LabelCountDTO> buildLoginTrend(LocalDateTime periodStart) {
        List<Object[]> rows = loginLogRepository.countDailyActiveUsers(periodStart);
        List<LabelCountDTO> result = new ArrayList<>();
        long total = 0;
        for (Object[] row : rows) {
            total += ((Number) row[1]).longValue();
        }
        for (Object[] row : rows) {
            long count = ((Number) row[1]).longValue();
            String dateLabel = String.valueOf(row[0]);
            if (dateLabel.length() >= 10) {
                dateLabel = dateLabel.substring(5);
            }
            double percent = total == 0 ? 0 : count * 100.0 / total;
            result.add(LabelCountDTO.builder()
                    .label(dateLabel)
                    .count(count)
                    .percent(Math.round(percent * 10) / 10.0)
                    .build());
        }
        return result;
    }

    private List<LabelCountDTO> toLabelCounts(Map<String, Long> counts, Map<String, String> labelMap) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<LabelCountDTO> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getValue() == 0 && total > 0) {
                continue;
            }
            String label = labelMap != null
                    ? labelMap.getOrDefault(entry.getKey(), entry.getKey())
                    : entry.getKey();
            double percent = total == 0 ? 0 : entry.getValue() * 100.0 / total;
            result.add(LabelCountDTO.builder()
                    .label(label)
                    .count(entry.getValue())
                    .percent(Math.round(percent * 10) / 10.0)
                    .build());
        }
        return result;
    }

    private Map<String, Long> sortByCount(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new));
    }

    private void countGender(AnalyticsOnlineSnapshot snapshot, String gender) {
        switch (safeGender(gender)) {
            case "MALE" -> snapshot.setMaleOnline(snapshot.getMaleOnline() + 1);
            case "FEMALE" -> snapshot.setFemaleOnline(snapshot.getFemaleOnline() + 1);
            case "OTHER" -> snapshot.setOtherOnline(snapshot.getOtherOnline() + 1);
            default -> snapshot.setUnknownOnline(snapshot.getUnknownOnline() + 1);
        }
    }

    private void countAge(AnalyticsOnlineSnapshot snapshot, LocalDate birthDate) {
        Integer age = calculateAge(birthDate);
        if (age == null) {
            return;
        }
        if (age < 18) snapshot.setAgeUnder18(snapshot.getAgeUnder18() + 1);
        else if (age <= 24) snapshot.setAge1824(snapshot.getAge1824() + 1);
        else if (age <= 34) snapshot.setAge2534(snapshot.getAge2534() + 1);
        else if (age <= 44) snapshot.setAge3544(snapshot.getAge3544() + 1);
        else if (age <= 54) snapshot.setAge4554(snapshot.getAge4554() + 1);
        else snapshot.setAge55Plus(snapshot.getAge55Plus() + 1);
    }

    private void copyCounts(AnalyticsOnlineSnapshot from, AnalyticsOnlineSnapshot to) {
        to.setMaleOnline(from.getMaleOnline());
        to.setFemaleOnline(from.getFemaleOnline());
        to.setOtherOnline(from.getOtherOnline());
        to.setUnknownOnline(from.getUnknownOnline());
        to.setAgeUnder18(from.getAgeUnder18());
        to.setAge1824(from.getAge1824());
        to.setAge2534(from.getAge2534());
        to.setAge3544(from.getAge3544());
        to.setAge4554(from.getAge4554());
        to.setAge55Plus(from.getAge55Plus());
    }

    private String safeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return "UNKNOWN";
        }
        return gender.toUpperCase(Locale.ROOT);
    }

    private Integer calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private String resolveAgeBucket(LocalDate birthDate) {
        Integer age = calculateAge(birthDate);
        if (age == null) {
            return "未知";
        }
        if (age < 18) return "18岁以下";
        if (age <= 24) return "18-24岁";
        if (age <= 34) return "25-34岁";
        if (age <= 44) return "35-44岁";
        if (age <= 54) return "45-54岁";
        return "55岁及以上";
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private LocalDateTime resolvePeriodStart(AnalyticsRange range, LocalDateTime now) {
        return switch (range) {
            case TODAY -> now.toLocalDate().atStartOfDay();
            case WEEK -> now.minusDays(7);
            case MONTH -> now.minusDays(30);
            case QUARTER -> now.minusDays(90);
            case YEAR -> now.minusDays(365);
        };
    }

    private String resolveRangeLabel(AnalyticsRange range) {
        return switch (range) {
            case TODAY -> "近当天";
            case WEEK -> "近一周";
            case MONTH -> "近一个月";
            case QUARTER -> "近一个季度";
            case YEAR -> "近一年";
        };
    }

    private String formatTrendLabel(AnalyticsRange range, LocalDateTime time) {
        return switch (range) {
            case TODAY -> time.format(TIME_FMT);
            case WEEK, MONTH -> time.format(DATE_FMT);
            case QUARTER, YEAR -> time.format(DateTimeFormatter.ofPattern("MM-dd"));
        };
    }

    /**
     * 解析快照中存储的地区统计 JSON。
     *
     * @param json 地区统计 JSON 字符串
     * @return 地区名称到在线人数的映射，解析失败时返回空 Map
     */
    public Map<String, Integer> parseRegionStats(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse region stats json: {}", e.getMessage());
            return Map.of();
        }
    }
}
