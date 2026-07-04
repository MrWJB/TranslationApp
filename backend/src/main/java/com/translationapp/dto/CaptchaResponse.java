package com.translationapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaResponse {
    private String captchaId;
    /** PNG image encoded as base64 (without data-URI prefix) */
    private String imageBase64;
}
