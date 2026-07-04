package com.translationapp.im.repository;

import com.translationapp.im.domain.FriendRequestStatus;
import com.translationapp.im.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    List<FriendRequest> findByToUserIdAndStatus(Long toUserId, FriendRequestStatus status);
    Optional<FriendRequest> findByFromUserIdAndToUserIdAndStatus(Long fromUserId, Long toUserId, FriendRequestStatus status);
}
