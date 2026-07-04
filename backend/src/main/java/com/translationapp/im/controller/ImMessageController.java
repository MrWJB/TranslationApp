package com.translationapp.im.controller;

import com.translationapp.im.dto.MessageDTO;
import com.translationapp.im.dto.SendMessageDTO;
import com.translationapp.im.security.ImSecurity;
import com.translationapp.im.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/im/messages")
@RequiredArgsConstructor
public class ImMessageController {

    private final MessageService messageService;

    @GetMapping("/conversations/{id}")
    public ResponseEntity<List<MessageDTO>> history(
            @PathVariable Long id,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(messageService.getHistory(ImSecurity.getCurrentUserId(), id, before, limit));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MessageDTO>> search(
            @RequestParam("q") String q,
            @RequestParam(required = false) Long conversationId) {
        return ResponseEntity.ok(messageService.search(ImSecurity.getCurrentUserId(), q, conversationId));
    }

    @PostMapping
    public ResponseEntity<MessageDTO> send(@Valid @RequestBody SendMessageDTO request) {
        return ResponseEntity.ok(messageService.sendMessage(ImSecurity.getCurrentUserId(), request));
    }
}
