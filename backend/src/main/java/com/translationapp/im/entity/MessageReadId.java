package com.translationapp.im.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Data
@Embeddable
public class MessageReadId implements Serializable {
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "user_id")
    private Long userId;
}
