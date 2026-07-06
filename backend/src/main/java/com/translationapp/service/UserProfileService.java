package com.translationapp.service;

import com.translationapp.dto.ChangePasswordRequest;
import com.translationapp.dto.RealNameVerifyRequest;
import com.translationapp.dto.UserProfileDTO;
import com.translationapp.dto.UserProfileUpdateRequest;
import com.translationapp.entity.Role;
import com.translationapp.entity.User;
import com.translationapp.repository.UserRepository;
import com.translationapp.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 用户个人资料服务，负责资料查询、更新、头像上传、实名认证与密码修改。
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.avatar.upload-dir:uploads/avatars}")
    private String avatarUploadDir;

    /**
     * 获取当前登录用户的个人资料。
     *
     * @return 用户资料 DTO
     */
    public UserProfileDTO getCurrentProfile() {
        return toProfileDTO(getCurrentUser());
    }

    /**
     * 更新当前登录用户的个人资料。
     *
     * @param request 资料更新请求
     * @return 更新后的用户资料 DTO
     */
    @Transactional
    public UserProfileDTO updateProfile(UserProfileUpdateRequest request) {
        User user = getCurrentUser();

        if (request.getRealName() != null) {
            user.setRealName(blankToNull(request.getRealName()));
        }
        if (request.getAvatar() != null) {
            user.setAvatar(blankToNull(request.getAvatar()));
        }
        if (request.getGender() != null) {
            user.setGender(blankToNull(request.getGender()));
        }
        if (request.getBirthDate() != null) {
            user.setBirthDate(request.getBirthDate());
        }
        if (request.getProvince() != null) {
            user.setProvince(blankToNull(request.getProvince()));
        }
        if (request.getCity() != null) {
            user.setCity(blankToNull(request.getCity()));
        }
        if (request.getEmail() != null) {
            String email = blankToNull(request.getEmail());
            if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("邮箱已被其他账户使用");
            }
            user.setEmail(email);
        }
        if (request.getPhone() != null) {
            String phone = blankToNull(request.getPhone());
            if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
                throw new IllegalArgumentException("手机号已被其他账户使用");
            }
            user.setPhone(phone);
            if (phone != null && !Boolean.TRUE.equals(user.getPhoneVerified())) {
                user.setPhoneVerified(false);
            }
        }

        return toProfileDTO(userRepository.save(user));
    }

    /**
     * 上传并保存用户头像。
     *
     * @param file 头像图片文件
     * @return 更新后的用户资料 DTO
     * @throws IOException 文件写入失败时抛出
     */
    @Transactional
    public UserProfileDTO uploadAvatar(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片格式");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("头像大小不能超过 2MB");
        }

        User user = getCurrentUser();
        Path dir = Paths.get(avatarUploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String ext = extensionFromContentType(contentType);
        String filename = user.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("非法文件名");
        }
        file.transferTo(target);

        String avatarUrl = "/api/images/avatars/" + filename;
        user.setAvatar(avatarUrl);
        return toProfileDTO(userRepository.save(user));
    }

    /**
     * 提交实名认证信息。
     *
     * @param request 实名认证请求
     * @return 更新后的用户资料 DTO
     */
    @Transactional
    public UserProfileDTO verifyRealName(RealNameVerifyRequest request) {
        User user = getCurrentUser();
        if (Boolean.TRUE.equals(user.getRealNameVerified())) {
            throw new IllegalArgumentException("已完成实名认证，无需重复提交");
        }

        user.setRealName(request.getRealName().trim());
        user.setIdCardNumber(request.getIdCardNumber().trim().toUpperCase());
        user.setRealNameVerified(true);
        return toProfileDTO(userRepository.save(user));
    }

    /**
     * 修改当前登录用户密码。
     *
     * @param request 密码修改请求
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private User getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private UserProfileDTO toProfileDTO(User user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setPhoneVerified(user.getPhoneVerified());
        dto.setRealName(user.getRealName());
        dto.setAvatar(user.getAvatar());
        dto.setGender(user.getGender());
        dto.setBirthDate(user.getBirthDate());
        dto.setProvince(user.getProvince());
        dto.setCity(user.getCity());
        dto.setRealNameVerified(user.getRealNameVerified());
        dto.setIdCardMasked(maskIdCard(user.getIdCardNumber()));
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRole(resolveRoleName(user.getRoles()));
        return dto;
    }

    private static String resolveRoleName(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return "USER";
        }
        return roles.iterator().next().getName();
    }

    private static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return null;
        }
        int len = idCard.length();
        return idCard.substring(0, 3) + "*".repeat(len - 7) + idCard.substring(len - 4);
    }

    private static String extensionFromContentType(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
