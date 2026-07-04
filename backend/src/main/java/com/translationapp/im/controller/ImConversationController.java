package com.translationapp.im.controller;

import com.translationapp.im.dto.*;
import com.translationapp.im.security.ImSecurity;
import com.translationapp.im.service.ConversationService;
import com.translationapp.im.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/im/conversations")
@RequiredArgsConstructor
public class ImConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<List<ConversationDTO>> list() {
        return ResponseEntity.ok(conversationService.listConversations(ImSecurity.getCurrentUserId()));
    }

    @PostMapping("/direct")
    public ResponseEntity<ConversationDTO> createDirect(@Valid @RequestBody DirectConversationCreateDTO request) {
        return ResponseEntity.ok(conversationService.getOrCreateDirect(
                ImSecurity.getCurrentUserId(), request.getTargetUserId()));
    }

    @PostMapping("/group")
    public ResponseEntity<ConversationDTO> createGroup(@Valid @RequestBody GroupConversationCreateDTO request) {
        return ResponseEntity.ok(conversationService.createGroup(ImSecurity.getCurrentUserId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.getConversation(ImSecurity.getCurrentUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversationDTO> update(@PathVariable Long id, @RequestBody ConversationUpdateDTO request) {
        return ResponseEntity.ok(conversationService.updateConversation(ImSecurity.getCurrentUserId(), id, request));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMembers(@PathVariable Long id, @Valid @RequestBody AddMembersDTO request) {
        conversationService.addMembers(ImSecurity.getCurrentUserId(), id, request.getMemberIds());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        conversationService.removeMember(ImSecurity.getCurrentUserId(), id, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @Valid @RequestBody MarkReadDTO request) {
        messageService.markRead(ImSecurity.getCurrentUserId(), id, request.getLastMessageId());
        return ResponseEntity.ok().build();
    }
}
