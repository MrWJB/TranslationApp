package com.translationapp.im.dto;

import com.translationapp.im.domain.PresenceStatus;
import lombok.Data;

@Data
public class UserSummaryDTO {
    private Long id;
    private String username;
    private String realName;
    private String avatar;
    private String phone;
    private PresenceStatus presenceStatus;
}
