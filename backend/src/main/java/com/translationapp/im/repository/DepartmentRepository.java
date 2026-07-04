package com.translationapp.im.repository;

import com.translationapp.im.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByParentIdOrderBySortOrderAsc(Long parentId);
    List<Department> findByParentIdIsNullOrderBySortOrderAsc();
}
