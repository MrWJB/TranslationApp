package com.translationapp.repository;

import com.translationapp.entity.UserOauthBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserOauthBindingRepository extends JpaRepository<UserOauthBinding, Long> {
    Optional<UserOauthBinding> findByProviderAndOpenId(String provider, String openId);
    boolean existsByProviderAndOpenId(String provider, String openId);
}
