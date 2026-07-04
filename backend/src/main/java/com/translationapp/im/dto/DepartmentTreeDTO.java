package com.translationapp.im.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DepartmentTreeDTO {
    private Long id;
    private String name;
    private Long parentId;
    private Integer sortOrder;
    private Long leaderUserId;
    private int memberCount;
    private List<DepartmentTreeDTO> children = new ArrayList<>();
}
