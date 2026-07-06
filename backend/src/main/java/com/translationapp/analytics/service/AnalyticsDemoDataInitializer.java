package com.translationapp.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.translationapp.analytics.entity.AnalyticsOnlineSnapshot;
import com.translationapp.analytics.entity.UserLoginLog;
import com.translationapp.analytics.repository.AnalyticsOnlineSnapshotRepository;
import com.translationapp.analytics.repository.UserLoginLogRepository;
import com.translationapp.entity.Role;
import com.translationapp.entity.User;
import com.translationapp.repository.RoleRepository;
import com.translationapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 分析模块演示数据初始化器，用于填充用户画像、在线快照与登录日志。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class AnalyticsDemoDataInitializer implements CommandLineRunner {

    private static final String[] PROVINCES = {
            "北京", "上海", "广东", "浙江", "江苏", "四川", "湖北", "山东", "福建", "河南",
            "湖南", "安徽", "河北", "陕西", "辽宁", "重庆", "天津", "广西", "云南", "黑龙江"
    };

    private static final String[] GENDERS = {"MALE", "FEMALE", "OTHER"};
    private static final String[] CITIES = {
            "朝阳区", "浦东新区", "天河区", "西湖区", "鼓楼区", "武侯区", "洪山区", "历下区", "思明区", "金水区"
    };

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AnalyticsOnlineSnapshotRepository snapshotRepository;
    private final UserLoginLogRepository loginLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.analytics.seed-demo-data:true}")
    private boolean seedDemoData;

    /**
     * 启动时填充演示用的用户画像、在线快照与登录日志。
     *
     * @param args 命令行参数
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (!seedDemoData) {
            return;
        }
        enrichExistingUsers();
        seedDemoUsers();
        seedHistoricalSnapshots();
        seedLoginLogs();
    }

    private void enrichExistingUsers() {
        userRepository.findByUsername("admin").ifPresent(u -> applyProfile(u, "MALE", LocalDate.of(1988, 5, 12), "北京", "朝阳区"));
        userRepository.findByUsername("user").ifPresent(u -> applyProfile(u, "FEMALE", LocalDate.of(1995, 8, 20), "上海", "浦东新区"));

        Random random = new Random(123);
        for (User user : userRepository.findAll()) {
            if (!needsProfile(user)) {
                continue;
            }
            int pIdx = Math.abs(Objects.hashCode(user.getUsername())) % PROVINCES.length;
            applyProfile(
                    user,
                    GENDERS[Math.abs(Objects.hashCode(user.getUsername())) % GENDERS.length],
                    randomBirthDate(random),
                    PROVINCES[pIdx],
                    CITIES[pIdx % CITIES.length]
            );
        }
    }

    private boolean needsProfile(User user) {
        return user.getGender() == null
                || "UNKNOWN".equalsIgnoreCase(user.getGender())
                || user.getBirthDate() == null
                || user.getProvince() == null
                || user.getProvince().isBlank()
                || user.getCity() == null
                || user.getCity().isBlank();
    }

    private void seedDemoUsers() {
        Role userRole = roleRepository.findByName("USER").orElse(null);
        Random random = new Random(42);
        for (int i = 1; i <= 58; i++) {
            String username = "demo" + String.format("%03d", i);
            if (userRepository.existsByUsername(username)) {
                continue;
            }
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode("demo12345"));
            user.setRealName("演示用户" + i);
            user.setIsEnabled(true);
            user.setIsLocked(false);
            user.setPhoneVerified(false);
            if (userRole != null) {
                user.setRoles(new HashSet<>(Set.of(userRole)));
            }
            int pIdx = random.nextInt(PROVINCES.length);
            applyProfile(user, GENDERS[random.nextInt(GENDERS.length)],
                    randomBirthDate(random), PROVINCES[pIdx], CITIES[pIdx % CITIES.length]);
            userRepository.save(user);
        }
    }

    private void applyProfile(User user, String gender, LocalDate birthDate, String province, String city) {
        user.setGender(gender);
        user.setBirthDate(birthDate);
        user.setProvince(province);
        user.setCity(city);
        userRepository.save(user);
    }

    private LocalDate randomBirthDate(Random random) {
        int year = 1975 + random.nextInt(35);
        int month = 1 + random.nextInt(12);
        int day = 1 + random.nextInt(28);
        return LocalDate.of(year, month, day);
    }

    private void seedHistoricalSnapshots() {
        if (snapshotRepository.count() > 100) {
            return;
        }
        long totalUsers = userRepository.count();
        Random random = new Random(7);
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        for (int dayOffset = 365; dayOffset >= 0; dayOffset--) {
            LocalDate date = now.toLocalDate().minusDays(dayOffset);
            int snapshotsPerDay = dayOffset <= 7 ? 24 : (dayOffset <= 30 ? 4 : 1);
            for (int s = 0; s < snapshotsPerDay; s++) {
                int hour = snapshotsPerDay == 24 ? s : (s * 6);
                LocalDateTime snapshotTime = LocalDateTime.of(date, LocalTime.of(hour, 0));
                if (snapshotTime.isAfter(now)) {
                    continue;
                }
                if (snapshotRepository.findBySnapshotTime(snapshotTime).isPresent()) {
                    continue;
                }
                int base = hour >= 9 && hour <= 22 ? 18 : 6;
                int online = Math.max(1, base + random.nextInt(15) + (dayOffset == 0 ? 3 : 0));
                AnalyticsOnlineSnapshot snapshot = buildSnapshot(snapshotTime, online, (int) totalUsers, random);
                snapshotRepository.save(snapshot);
            }
        }
        log.info("Seeded analytics online snapshots");
    }

    private AnalyticsOnlineSnapshot buildSnapshot(LocalDateTime time, int online, int totalUsers, Random random) {
        AnalyticsOnlineSnapshot snapshot = new AnalyticsOnlineSnapshot();
        snapshot.setSnapshotTime(time);
        snapshot.setOnlineCount(online);
        snapshot.setTotalUsers(totalUsers);
        snapshot.setMaleOnline((int) (online * 0.55));
        snapshot.setFemaleOnline((int) (online * 0.40));
        snapshot.setOtherOnline(Math.max(0, online - snapshot.getMaleOnline() - snapshot.getFemaleOnline()));
        snapshot.setAge1824((int) (online * 0.25));
        snapshot.setAge2534((int) (online * 0.35));
        snapshot.setAge3544((int) (online * 0.20));
        snapshot.setAge4554((int) (online * 0.12));
        snapshot.setAge55Plus(Math.max(0, online - snapshot.getAge1824() - snapshot.getAge2534()
                - snapshot.getAge3544() - snapshot.getAge4554()));

        Map<String, Integer> regions = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            regions.put(PROVINCES[random.nextInt(PROVINCES.length)], 1 + random.nextInt(online / 3 + 1));
        }
        try {
            snapshot.setRegionStatsJson(objectMapper.writeValueAsString(regions));
        } catch (Exception e) {
            log.warn("Failed to serialize demo region stats: {}", e.getMessage());
        }
        return snapshot;
    }

    private void seedLoginLogs() {
        if (loginLogRepository.count() > 50) {
            return;
        }
        List<User> users = userRepository.findAll();
        Random random = new Random(99);
        LocalDateTime now = LocalDateTime.now();
        for (int day = 30; day >= 0; day--) {
            LocalDate date = now.toLocalDate().minusDays(day);
            int dailyUsers = 5 + random.nextInt(Math.min(20, users.size()));
            Collections.shuffle(users, random);
            for (int i = 0; i < dailyUsers && i < users.size(); i++) {
                User user = users.get(i);
                UserLoginLog logEntry = new UserLoginLog();
                logEntry.setUserId(user.getId());
                logEntry.setLoginTime(LocalDateTime.of(date, LocalTime.of(8 + random.nextInt(12), random.nextInt(60))));
                logEntry.setProvince(user.getProvince());
                logEntry.setCity(user.getCity());
                loginLogRepository.save(logEntry);
            }
        }
    }
}
