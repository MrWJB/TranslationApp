package com.translationapp.im.controller;

import com.translationapp.im.dto.AttachmentCompleteDTO;
import com.translationapp.im.dto.PresignRequestDTO;
import com.translationapp.im.dto.PresignResponseDTO;
import com.translationapp.im.entity.Attachment;
import com.translationapp.im.security.ImSecurity;
import com.translationapp.im.service.AttachmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/im/attachments")
@RequiredArgsConstructor
public class ImAttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping("/presign")
    public ResponseEntity<PresignResponseDTO> presign(@Valid @RequestBody PresignRequestDTO request) {
        return ResponseEntity.ok(attachmentService.presign(ImSecurity.getCurrentUserId(), request));
    }

    @PostMapping("/complete")
    public ResponseEntity<Attachment> complete(@Valid @RequestBody AttachmentCompleteDTO request) {
        return ResponseEntity.ok(attachmentService.complete(ImSecurity.getCurrentUserId(), request));
    }

    @GetMapping("/{id}/download-url")
    public ResponseEntity<Map<String, String>> downloadUrl(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentService.getDownloadUrl(ImSecurity.getCurrentUserId(), id));
    }
}
