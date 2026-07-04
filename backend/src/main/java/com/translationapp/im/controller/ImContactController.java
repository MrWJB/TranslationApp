package com.translationapp.im.controller;

import com.translationapp.im.dto.*;
import com.translationapp.im.security.ImSecurity;
import com.translationapp.im.service.DepartmentService;
import com.translationapp.im.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/im/contacts")
@RequiredArgsConstructor
public class ImContactController {

    private final DepartmentService departmentService;
    private final FriendshipService friendshipService;

    @GetMapping("/departments/tree")
    public ResponseEntity<List<DepartmentTreeDTO>> departmentTree() {
        return ResponseEntity.ok(departmentService.getDepartmentTree());
    }

    @GetMapping("/departments/{id}/users")
    public ResponseEntity<List<UserSummaryDTO>> departmentUsers(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentUsers(id));
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<UserSummaryDTO>> searchUsers(@RequestParam("q") String q) {
        return ResponseEntity.ok(departmentService.searchUsers(q));
    }

    @GetMapping("/friends")
    public ResponseEntity<List<FriendDTO>> friends() {
        return ResponseEntity.ok(friendshipService.listFriends(ImSecurity.getCurrentUserId()));
    }

    @GetMapping("/friend-requests")
    public ResponseEntity<List<FriendRequestDTO>> pendingRequests() {
        return ResponseEntity.ok(friendshipService.listPendingRequests(ImSecurity.getCurrentUserId()));
    }

    @PostMapping("/friend-requests")
    public ResponseEntity<FriendRequestDTO> createRequest(@RequestBody FriendRequestCreateDTO request) {
        return ResponseEntity.ok(friendshipService.createRequest(ImSecurity.getCurrentUserId(), request));
    }

    @PutMapping("/friend-requests/{id}/accept")
    public ResponseEntity<Void> acceptRequest(@PathVariable Long id) {
        friendshipService.acceptRequest(ImSecurity.getCurrentUserId(), id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/friend-requests/{id}/reject")
    public ResponseEntity<Void> rejectRequest(@PathVariable Long id) {
        friendshipService.rejectRequest(ImSecurity.getCurrentUserId(), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/friends/{userId}")
    public ResponseEntity<Void> deleteFriend(@PathVariable Long userId) {
        friendshipService.deleteFriend(ImSecurity.getCurrentUserId(), userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/friends/{userId}/block")
    public ResponseEntity<Void> blockFriend(@PathVariable Long userId) {
        friendshipService.blockFriend(ImSecurity.getCurrentUserId(), userId);
        return ResponseEntity.ok().build();
    }
}
