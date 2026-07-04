package com.translationapp.im.service;

import com.translationapp.entity.User;
import com.translationapp.im.domain.PresenceStatus;
import com.translationapp.im.dto.DepartmentCreateDTO;
import com.translationapp.im.dto.DepartmentTreeDTO;
import com.translationapp.im.dto.DepartmentUpdateDTO;
import com.translationapp.im.dto.UserSummaryDTO;
import com.translationapp.im.entity.Department;
import com.translationapp.im.entity.UserDepartment;
import com.translationapp.im.entity.UserDepartmentId;
import com.translationapp.im.repository.DepartmentRepository;
import com.translationapp.im.repository.UserDepartmentRepository;
import com.translationapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;

    public List<DepartmentTreeDTO> getDepartmentTree() {
        List<Department> all = departmentRepository.findAll();
        Map<Long, Long> memberCounts = userDepartmentRepository.findAll().stream()
                .collect(Collectors.groupingBy(ud -> ud.getId().getDepartmentId(), Collectors.counting()));

        Map<Long, DepartmentTreeDTO> map = new HashMap<>();
        for (Department dept : all) {
            DepartmentTreeDTO dto = new DepartmentTreeDTO();
            dto.setId(dept.getId());
            dto.setName(dept.getName());
            dto.setParentId(dept.getParentId());
            dto.setSortOrder(dept.getSortOrder());
            dto.setLeaderUserId(dept.getLeaderUserId());
            dto.setMemberCount(memberCounts.getOrDefault(dept.getId(), 0L).intValue());
            map.put(dept.getId(), dto);
        }

        List<DepartmentTreeDTO> roots = new ArrayList<>();
        for (DepartmentTreeDTO dto : map.values()) {
            if (dto.getParentId() == null) {
                roots.add(dto);
            } else {
                DepartmentTreeDTO parent = map.get(dto.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dto);
                } else {
                    roots.add(dto);
                }
            }
        }
        roots.sort(Comparator.comparing(DepartmentTreeDTO::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
        return roots;
    }

    public List<UserSummaryDTO> getDepartmentUsers(Long departmentId) {
        return userDepartmentRepository.findByIdDepartmentId(departmentId).stream()
                .map(ud -> toUserSummary(ud.getId().getUserId()))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<UserSummaryDTO> searchUsers(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return userRepository.searchByKeyword(query.trim()).stream()
                .map(u -> toUserSummary(u.getId()))
                .toList();
    }

    @Transactional
    public DepartmentTreeDTO create(DepartmentCreateDTO request) {
        Department dept = new Department();
        dept.setName(request.getName());
        dept.setParentId(request.getParentId());
        dept.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        dept.setLeaderUserId(request.getLeaderUserId());
        dept = departmentRepository.save(dept);
        return toTreeDto(dept, 0);
    }

    @Transactional
    public DepartmentTreeDTO update(Long id, DepartmentUpdateDTO request) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        if (request.getName() != null) {
            dept.setName(request.getName());
        }
        if (request.getParentId() != null) {
            dept.setParentId(request.getParentId());
        }
        if (request.getSortOrder() != null) {
            dept.setSortOrder(request.getSortOrder());
        }
        if (request.getLeaderUserId() != null) {
            dept.setLeaderUserId(request.getLeaderUserId());
        }
        dept = departmentRepository.save(dept);
        int count = userDepartmentRepository.findByIdDepartmentId(id).size();
        return toTreeDto(dept, count);
    }

    @Transactional
    public void delete(Long id) {
        if (!departmentRepository.findByParentIdOrderBySortOrderAsc(id).isEmpty()) {
            throw new IllegalArgumentException("Cannot delete department with children");
        }
        departmentRepository.deleteById(id);
    }

    @Transactional
    public void assignUser(Long departmentId, Long userId, boolean primary) {
        UserDepartmentId udId = new UserDepartmentId();
        udId.setUserId(userId);
        udId.setDepartmentId(departmentId);
        UserDepartment ud = new UserDepartment();
        ud.setId(udId);
        ud.setIsPrimary(primary);
        userDepartmentRepository.save(ud);
    }

    private UserSummaryDTO toUserSummary(Long userId) {
        return userRepository.findById(userId).map(this::mapUser).orElse(null);
    }

    private UserSummaryDTO mapUser(User user) {
        UserSummaryDTO dto = new UserSummaryDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRealName(user.getRealName());
        dto.setAvatar(user.getAvatar());
        dto.setPhone(user.getPhone());
        dto.setPresenceStatus(presenceService.getStatus(user.getId()));
        return dto;
    }

    private DepartmentTreeDTO toTreeDto(Department dept, int memberCount) {
        DepartmentTreeDTO dto = new DepartmentTreeDTO();
        dto.setId(dept.getId());
        dto.setName(dept.getName());
        dto.setParentId(dept.getParentId());
        dto.setSortOrder(dept.getSortOrder());
        dto.setLeaderUserId(dept.getLeaderUserId());
        dto.setMemberCount(memberCount);
        return dto;
    }
}
