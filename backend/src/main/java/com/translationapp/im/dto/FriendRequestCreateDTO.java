package com.translationapp.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FriendRequestCreateDTO {
    @NotNull
    private Long toUserId;
    private String message;
}
