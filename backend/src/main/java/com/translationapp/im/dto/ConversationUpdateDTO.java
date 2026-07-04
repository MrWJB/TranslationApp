package com.translationapp.im.dto;

import lombok.Data;

@Data
public class ConversationUpdateDTO {
    private String title;
    private String avatarUrl;
    private String announcement;
}
