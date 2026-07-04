package com.translationapp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "documents")
@JsonIgnoreProperties({"crawlTask", "parentDocumentId"})
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String url;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @JsonIgnore
    private CrawlTask crawlTask;
    
    // Order in the document hierarchy
    @Column(nullable = false)
    private Integer sortOrder = 0;
    
    // Section level (1-6 for h1-h6, 0 for page title)
    private Integer sectionLevel = 0;
    
    // Section ID for anchor links
    private String sectionId;
    
    // Parent document ID for hierarchical structure
    private Long parentDocumentId;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String originalContent;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String translatedContent;
    
    // Table of contents JSON for this document
    @Lob
    @Column(columnDefinition = "TEXT")
    private String tableOfContents;
    
    // Local file path for crawled HTML files
    private String localPath;
    
    private String category;

    // Local file path for translated HTML files
    private String translatedLocalPath;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
