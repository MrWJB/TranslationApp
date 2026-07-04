package com.translationapp.im.dto;

import com.translationapp.im.domain.FriendRequestStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendRequestDTO {
    private Long id;
    private Long fromUserId;
    private UserSummaryDTO fromUser;
    private Long toUserId;
    private String message;
    private FriendRequestStatus status;
    private LocalDateTime createdAt;
}
