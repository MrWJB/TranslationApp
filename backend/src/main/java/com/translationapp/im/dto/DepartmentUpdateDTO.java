package com.translationapp.im.dto;

import lombok.Data;

@Data
public class DepartmentUpdateDTO {
    private String name;
    private Long parentId;
    private Integer sortOrder;
    private Long leaderUserId;
}
