package com.translationapp.im.dto;

import com.translationapp.im.domain.FriendshipStatus;
import com.translationapp.im.domain.PresenceStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendDTO {
    private Long userId;
    private String username;
    private String realName;
    private String avatar;
    private String remark;
    private FriendshipStatus status;
    private PresenceStatus presenceStatus;
    private LocalDateTime createdAt;
}
