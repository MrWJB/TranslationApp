package com.translationapp.service;

import com.translationapp.analytics.service.AnalyticsService;
import com.translationapp.dto.LoginRequest;
import com.translationapp.dto.LoginResponse;
import com.translationapp.dto.PhoneRegisterRequest;
import com.translationapp.dto.RegisterRequest;
import com.translationapp.entity.Role;
import com.translationapp.entity.User;
import com.translationapp.repository.RoleRepository;
import com.translationapp.repository.UserRepository;
import com.translationapp.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SmsCodeService smsCodeService;
    private final CaptchaService captchaService;
    private final AnalyticsService analyticsService;

    public LoginResponse login(LoginRequest request) {
        captchaService.validate(request.getCaptchaId(), request.getCaptchaCode());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        analyticsService.recordLogin(user.getId(), null);
        return buildLoginResponse(user, request.isRememberMe());
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已被占用");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(blankToNull(request.getEmail()));
        user.setRealName(blankToNull(request.getRealName()) != null
                ? request.getRealName().trim()
                : request.getUsername());
        user.setIsEnabled(true);
        user.setIsLocked(false);
        user.setPhoneVerified(false);
        user.setRoles(defaultUserRoles());

        user = userRepository.save(user);
        return buildLoginResponse(user);
    }

    public void sendPhoneCode(String phone) {
        if (userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("该手机号已注册");
        }
        smsCodeService.sendCode(phone);
    }

    @Transactional
    public LoginResponse registerByPhone(PhoneRegisterRequest request) {
        if (!smsCodeService.verifyCode(request.getPhone(), request.getCode())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("该手机号已注册");
        }

        String username = uniquePhoneUsername(request.getPhone());
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setPhone(request.getPhone());
        user.setPhoneVerified(true);
        user.setRealName(blankToNull(request.getRealName()) != null
                ? request.getRealName().trim()
                : "用户" + request.getPhone().substring(7));
        user.setIsEnabled(true);
        user.setIsLocked(false);
        user.setRoles(defaultUserRoles());

        user = userRepository.save(user);
        return buildLoginResponse(user);
    }

    private Set<Role> defaultUserRoles() {
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalArgumentException("Default USER role not found"));
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        return roles;
    }

    private LoginResponse buildLoginResponse(User user) {
        return buildLoginResponse(user, false);
    }

    private LoginResponse buildLoginResponse(User user, boolean rememberMe) {
        String roleName = user.getRoles() != null && !user.getRoles().isEmpty()
                ? user.getRoles().iterator().next().getName()
                : "USER";
        String token = jwtUtil.generateToken(user.getUsername(), roleName, user.getId(), rememberMe);
        return new LoginResponse(token, user.getUsername(), roleName, user.getId());
    }

    private String uniquePhoneUsername(String phone) {
        String base = "u" + phone;
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        return base + "_" + UUID.randomUUID().toString().substring(0, 4);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
