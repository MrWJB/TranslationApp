package com.translationapp.dto;

import lombok.Data;
import java.util.Set;

@Data
public class UserUpdateRequest {
    private String email;
    private String phone;
    private String realName;
    private String avatar;
    private Boolean isEnabled;
    private Boolean isLocked;
    private Set<Long> roleIds;
}