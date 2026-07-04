package com.translationapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PhoneRegisterRequest {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的中国大陆手机号")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 8, message = "验证码格式不正确")
    private String code;

    /** Optional display name; defaults to phone-based username */
    private String realName;
}
