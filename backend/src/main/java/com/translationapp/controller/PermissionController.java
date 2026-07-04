package com.translationapp.controller;

import com.translationapp.dto.*;
import com.translationapp.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<PermissionDTO>> list() {
        return ResponseEntity.ok(permissionService.findAll());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<PermissionDTO>> tree() {
        return ResponseEntity.ok(permissionService.findTree());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermissionDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<PermissionDTO> create(@Valid @RequestBody PermissionCreateRequest request) {
        return ResponseEntity.ok(permissionService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermissionDTO> update(@PathVariable Long id, @Valid @RequestBody PermissionUpdateRequest request) {
        return ResponseEntity.ok(permissionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ResponseEntity.ok().build();
    }
}