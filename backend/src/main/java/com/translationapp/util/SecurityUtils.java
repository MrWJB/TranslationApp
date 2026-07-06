package com.translationapp.util;

import com.translationapp.im.security.ImUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 上下文工具类，用于获取当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前用户 ID
     * @throws IllegalArgumentException 未登录或会话无效时抛出
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ImUserPrincipal principal)) {
            throw new IllegalArgumentException("未登录或会话已失效");
        }
        return principal.getUserId();
    }
}
