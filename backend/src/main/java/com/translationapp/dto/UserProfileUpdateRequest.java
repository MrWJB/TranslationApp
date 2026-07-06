package com.translationapp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户个人资料更新请求 DTO。
 */
@Data
public class UserProfileUpdateRequest {
    private String realName;
    private String avatar;
    private String gender;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
    private String province;
    private String city;
    @Email(message = "邮箱格式不正确")
    private String email;
    private String phone;
}
