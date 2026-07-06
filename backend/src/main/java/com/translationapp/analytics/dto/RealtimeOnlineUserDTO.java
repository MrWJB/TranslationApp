package com.translationapp.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实时在线用户列表项 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeOnlineUserDTO {
    private Long userId;
    private String username;
    private String realName;
    private String gender;
    private Integer age;
    private String province;
    private String city;
    private String lastSeen;
}
