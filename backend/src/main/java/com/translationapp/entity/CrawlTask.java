package com.translationapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@Entity
@Table(name = "crawl_tasks")
public class CrawlTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String url;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    
    // 任务类型：document, video, audio, image, mixed, unknown
    @Column(nullable = false)
    private String taskType = "document";
    
    // 文档分类：java, spring, spring-boot, spring-cloud, spring-mvc, mysql, oracle
    private String category;
    
    // 视频分类：anime, short-drama, tv-series, movie, variety, education
    private String mediaCategory;
    
    // 视频配置
    private Integer videoQuality; // 1080, 720, 480, 360
    private Boolean extractAudio = false;
    private Boolean extractSubtitles = false;
    
    // 类型识别置信度
    private Double typeConfidence;
    private String typeReason;
    
    // 用户确认的类型（用户可修改自动识别的类型）
    private Boolean userConfirmedType = false;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String originalContent;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String translatedContent;
    
    // Table of contents JSON structure
    @Column(columnDefinition = "MEDIUMTEXT")
    private String tableOfContents;
    
    private String errorMessage;

    /** Crawl progress: crawling | processing | translating | building_toc */
    @Column(length = 32)
    private String progressPhase;

    private Integer progressCurrent;

    private Integer progressTotal;

    @Column(length = 512)
    private String progressMessage;

    private Integer maxPages;

    /** JSON quality report from crawler (nav coverage, empty pages, etc.) */
    @Column(columnDefinition = "TEXT")
    private String qualityReport;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (taskType == null) {
            taskType = "document";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
