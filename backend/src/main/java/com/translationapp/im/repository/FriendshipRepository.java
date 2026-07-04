package com.translationapp.im.repository;

import com.translationapp.im.domain.FriendshipStatus;
import com.translationapp.im.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByUserIdAndStatus(Long userId, FriendshipStatus status);
    Optional<Friendship> findByUserIdAndFriendUserId(Long userId, Long friendUserId);
    boolean existsByUserIdAndFriendUserIdAndStatus(Long userId, Long friendUserId, FriendshipStatus status);
}
