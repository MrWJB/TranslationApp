package com.translationapp.repository;

import com.translationapp.entity.CrawlTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CrawlTaskRepository extends JpaRepository<CrawlTask, Long> {
    List<CrawlTask> findByStatus(String status);
    List<CrawlTask> findByOrderByCreatedAtDesc();
    Page<CrawlTask> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
