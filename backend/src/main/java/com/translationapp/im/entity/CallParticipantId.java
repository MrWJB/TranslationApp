package com.translationapp.im.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Data
@Embeddable
public class CallParticipantId implements Serializable {
    @Column(name = "call_session_id")
    private Long callSessionId;

    @Column(name = "user_id")
    private Long userId;
}
