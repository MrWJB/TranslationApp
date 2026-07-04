package com.translationapp.repository;

import com.translationapp.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findByParentId(Long parentId);
    List<Menu> findByParentIdIsNullOrderBySortOrder();
    List<Menu> findByIsVisibleTrueOrderBySortOrder();
    Optional<Menu> findByPath(String path);
}