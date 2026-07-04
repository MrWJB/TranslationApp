package com.translationapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {
    private Long id;
    private String title;
    private String url;
    private Long taskId;
    private Integer sortOrder;
    private Integer sectionLevel;
    private String sectionId;
    private Long parentDocumentId;
    private String originalContent;
    private String translatedContent;
    private String localPath;
    private String translatedLocalPath;
    private String category;
}
