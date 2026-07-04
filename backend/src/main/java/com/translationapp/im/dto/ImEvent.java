package com.translationapp.im.dto;

import com.translationapp.im.domain.ImEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImEvent {
    private ImEventType event;
    private Object payload;
    private String timestamp;

    public static ImEvent of(ImEventType event, Object payload) {
        return ImEvent.builder()
                .event(event)
                .payload(payload)
                .timestamp(Instant.now().toString())
                .build();
    }
}
