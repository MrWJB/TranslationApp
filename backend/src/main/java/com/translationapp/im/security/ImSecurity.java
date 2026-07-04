package com.translationapp.im.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ImSecurity {

    private ImSecurity() {
    }

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalArgumentException("Not authenticated");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof ImUserPrincipal imUser) {
            return imUser.getUserId();
        }
        throw new IllegalArgumentException("Invalid authentication principal");
    }

    public static ImUserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ImUserPrincipal imUser) {
            return imUser;
        }
        throw new IllegalArgumentException("Not authenticated");
    }
}
