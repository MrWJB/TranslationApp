package com.translationapp.im.dto;

import com.translationapp.im.domain.MemberRole;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationMemberDTO {
    private Long userId;
    private String username;
    private String realName;
    private String avatar;
    private MemberRole role;
    private LocalDateTime joinedAt;
}
