package com.translationapp.im.controller;

import com.translationapp.im.dto.CallCreateDTO;
import com.translationapp.im.dto.CallSessionDTO;
import com.translationapp.im.security.ImSecurity;
import com.translationapp.im.service.CallSignalingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/im/calls")
@RequiredArgsConstructor
public class ImCallController {

    private final CallSignalingService callSignalingService;

    @PostMapping
    public ResponseEntity<CallSessionDTO> initiate(@Valid @RequestBody CallCreateDTO request) {
        return ResponseEntity.ok(callSignalingService.initiateCall(ImSecurity.getCurrentUserId(), request));
    }

    @PutMapping("/{id}/answer")
    public ResponseEntity<CallSessionDTO> answer(@PathVariable Long id) {
        return ResponseEntity.ok(callSignalingService.answerCall(ImSecurity.getCurrentUserId(), id));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<CallSessionDTO> reject(@PathVariable Long id) {
        return ResponseEntity.ok(callSignalingService.rejectCall(ImSecurity.getCurrentUserId(), id));
    }

    @PutMapping("/{id}/end")
    public ResponseEntity<CallSessionDTO> end(@PathVariable Long id) {
        return ResponseEntity.ok(callSignalingService.endCall(ImSecurity.getCurrentUserId(), id));
    }
}
