package com.translationapp.repository;

import com.translationapp.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    boolean existsByCode(String code);
    List<Permission> findByParentId(Long parentId);
    List<Permission> findByParentIdIsNull();
    List<Permission> findByModuleName(String moduleName);
}