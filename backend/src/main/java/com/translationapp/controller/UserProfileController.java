package com.translationapp.controller;

import com.translationapp.dto.ChangePasswordRequest;
import com.translationapp.dto.MenuDTO;
import com.translationapp.dto.RealNameVerifyRequest;
import com.translationapp.dto.UserProfileDTO;
import com.translationapp.dto.UserProfileUpdateRequest;
import com.translationapp.service.MenuService;
import com.translationapp.service.UserProfileService;
import com.translationapp.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 用户个人资料 REST 控制器。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final MenuService menuService;

    /**
     * 获取当前用户个人资料。
     *
     * @return 用户资料 DTO
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileDTO> getProfile() {
        return ResponseEntity.ok(userProfileService.getCurrentProfile());
    }

    /**
     * 获取当前用户可访问的侧边栏菜单树（按 sortOrder 排序）。
     */
    @GetMapping("/menus")
    public ResponseEntity<List<MenuDTO>> getMenus() {
        return ResponseEntity.ok(menuService.findMenusForUser(SecurityUtils.getCurrentUserId()));
    }

    /**
     * 更新当前用户个人资料。
     *
     * @param request 资料更新请求
     * @return 更新后的用户资料 DTO
     */
    @PutMapping("/profile")
    public ResponseEntity<UserProfileDTO> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(request));
    }

    /**
     * 上传用户头像。
     *
     * @param file 头像图片文件
     * @return 更新后的用户资料 DTO
     */
    @PostMapping("/avatar")
    public ResponseEntity<UserProfileDTO> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(userProfileService.uploadAvatar(file));
    }

    /**
     * 提交实名认证。
     *
     * @param request 实名认证请求
     * @return 更新后的用户资料 DTO
     */
    @PostMapping("/real-name-verify")
    public ResponseEntity<UserProfileDTO> verifyRealName(@Valid @RequestBody RealNameVerifyRequest request) {
        return ResponseEntity.ok(userProfileService.verifyRealName(request));
    }

    /**
     * 修改当前用户密码。
     *
     * @param request 密码修改请求
     * @return 操作结果消息
     */
    @PostMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }
}
