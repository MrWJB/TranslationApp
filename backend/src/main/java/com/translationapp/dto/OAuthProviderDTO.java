package com.translationapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuthProviderDTO {
    private String id;
    private String name;
    private boolean configured;
}
