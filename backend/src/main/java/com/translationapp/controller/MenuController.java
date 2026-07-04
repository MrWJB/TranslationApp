package com.translationapp.controller;

import com.translationapp.dto.*;
import com.translationapp.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
public class MenuController {
    private final MenuService menuService;

    @GetMapping
    public ResponseEntity<List<MenuDTO>> list() {
        return ResponseEntity.ok(menuService.findAll());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<MenuDTO>> tree() {
        return ResponseEntity.ok(menuService.findTree());
    }

    @GetMapping("/visible")
    public ResponseEntity<List<MenuDTO>> visibleTree() {
        return ResponseEntity.ok(menuService.findVisibleTree());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.findById(id));
    }

    @PostMapping
    public ResponseEntity<MenuDTO> create(@Valid @RequestBody MenuCreateRequest request) {
        return ResponseEntity.ok(menuService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuDTO> update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        return ResponseEntity.ok(menuService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ResponseEntity.ok().build();
    }
}