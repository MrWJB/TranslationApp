package com.translationapp.dto;

import lombok.Data;

@Data
public class DocumentMenuMatchDTO {
    private boolean matched;
    private String normalizedCategory;
    private String siteKey;
    private String menuPath;
    private String suggestedName;
    private MenuDTO menu;
}
