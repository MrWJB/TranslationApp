package com.translationapp.repository;

import com.translationapp.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Map;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByCrawlTaskId(Long taskId);
    List<Document> findByCrawlTaskIdOrderBySortOrderAsc(Long taskId);
    List<Document> findByTitleContainingIgnoreCase(String title);
    Page<Document> findByCrawlTaskId(Long taskId, Pageable pageable);
    long countByCrawlTaskId(Long taskId);
    void deleteByCrawlTaskId(Long taskId);
    
    @Query("SELECT d.category, COUNT(d) FROM Document d GROUP BY d.category")
    List<Object[]> countByCategoryRaw();
    
    default Map<String, Long> countByCategory() {
        List<Object[]> results = countByCategoryRaw();
        return results.stream()
            .collect(java.util.stream.Collectors.toMap(
                row -> row[0] != null && !row[0].toString().isBlank() ? row[0].toString() : "other",
                row -> ((Number) row[1]).longValue(),
                (existing, replacement) -> existing
            ));
    }
    
    List<Document> findByCategory(String category);
    
    boolean existsByLocalPath(String localPath);
}
