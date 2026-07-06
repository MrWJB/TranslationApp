package com.translationapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 实名认证请求 DTO。
 */
@Data
public class RealNameVerifyRequest {
    @NotBlank(message = "真实姓名不能为空")
    @Size(min = 2, max = 32, message = "真实姓名长度需在 2-32 位之间")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号格式不正确")
    private String idCardNumber;
}
