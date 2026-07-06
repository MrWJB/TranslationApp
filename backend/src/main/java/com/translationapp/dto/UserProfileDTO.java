package com.translationapp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户个人资料响应 DTO。
 */
@Data
public class UserProfileDTO {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private Boolean phoneVerified;
    private String realName;
    private String avatar;
    private String gender;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
    private String province;
    private String city;
    private Boolean realNameVerified;
    /** 脱敏后的身份证号，例如 110***********1234 */
    private String idCardMasked;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    private String role;
}
