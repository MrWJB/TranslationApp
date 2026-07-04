package com.translationapp.im.repository;

import com.translationapp.im.entity.UserDepartment;
import com.translationapp.im.entity.UserDepartmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDepartmentRepository extends JpaRepository<UserDepartment, UserDepartmentId> {
    List<UserDepartment> findByIdDepartmentId(Long departmentId);
    List<UserDepartment> findByIdUserId(Long userId);
}
