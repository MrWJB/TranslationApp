package com.translationapp.im.config;

import com.translationapp.entity.User;
import com.translationapp.im.domain.FriendshipStatus;
import com.translationapp.im.entity.Department;
import com.translationapp.im.entity.Friendship;
import com.translationapp.im.entity.UserDepartment;
import com.translationapp.im.entity.UserDepartmentId;
import com.translationapp.im.repository.DepartmentRepository;
import com.translationapp.im.repository.FriendshipRepository;
import com.translationapp.im.repository.UserDepartmentRepository;
import com.translationapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
@RequiredArgsConstructor
public class ImDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final DepartmentRepository departmentRepository;
    private final UserDepartmentRepository userDepartmentRepository;

    @Value("${app.seed-im.enabled:true}")
    private boolean seedImEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedImEnabled) {
            return;
        }
        seedDemoFriendship();
        seedDemoDepartment();
    }

    private void seedDemoFriendship() {
        User admin = userRepository.findByUsername("admin").orElse(null);
        User user = userRepository.findByUsername("user").orElse(null);
        if (admin == null || user == null) {
            return;
        }
        ensureFriendship(admin.getId(), user.getId());
    }

    private void ensureFriendship(Long userA, Long userB) {
        if (friendshipRepository.existsByUserIdAndFriendUserIdAndStatus(userA, userB, FriendshipStatus.ACCEPTED)) {
            return;
        }
        saveFriendship(userA, userB);
        saveFriendship(userB, userA);
    }

    private void saveFriendship(Long userId, Long friendUserId) {
        Friendship friendship = friendshipRepository.findByUserIdAndFriendUserId(userId, friendUserId)
                .orElseGet(() -> {
                    Friendship f = new Friendship();
                    f.setUserId(userId);
                    f.setFriendUserId(friendUserId);
                    return f;
                });
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }

    private void seedDemoDepartment() {
        if (departmentRepository.count() > 0) {
            return;
        }
        User admin = userRepository.findByUsername("admin").orElse(null);
        User user = userRepository.findByUsername("user").orElse(null);
        if (admin == null || user == null) {
            return;
        }

        Department dept = new Department();
        dept.setName("默认部门");
        dept.setSortOrder(0);
        dept.setLeaderUserId(admin.getId());
        dept = departmentRepository.save(dept);

        assignUserToDepartment(admin.getId(), dept.getId(), true);
        assignUserToDepartment(user.getId(), dept.getId(), false);
    }

    private void assignUserToDepartment(Long userId, Long departmentId, boolean primary) {
        UserDepartmentId id = new UserDepartmentId();
        id.setUserId(userId);
        id.setDepartmentId(departmentId);
        if (userDepartmentRepository.existsById(id)) {
            return;
        }
        UserDepartment ud = new UserDepartment();
        ud.setId(id);
        ud.setIsPrimary(primary);
        userDepartmentRepository.save(ud);
    }
}
