package com.translationapp.im.controller;

import com.translationapp.im.dto.DepartmentCreateDTO;
import com.translationapp.im.dto.DepartmentTreeDTO;
import com.translationapp.im.dto.DepartmentUpdateDTO;
import com.translationapp.im.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/departments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ImDepartmentAdminController {

    private final DepartmentService departmentService;

    @GetMapping("/tree")
    public ResponseEntity<java.util.List<DepartmentTreeDTO>> tree() {
        return ResponseEntity.ok(departmentService.getDepartmentTree());
    }

    @PostMapping
    public ResponseEntity<DepartmentTreeDTO> create(@Valid @RequestBody DepartmentCreateDTO request) {
        return ResponseEntity.ok(departmentService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentTreeDTO> update(@PathVariable Long id, @RequestBody DepartmentUpdateDTO request) {
        return ResponseEntity.ok(departmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/users/{userId}")
    public ResponseEntity<Void> assignUser(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean primary) {
        departmentService.assignUser(id, userId, primary);
        return ResponseEntity.ok().build();
    }
}
