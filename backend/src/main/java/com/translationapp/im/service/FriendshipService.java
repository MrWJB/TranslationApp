package com.translationapp.im.service;

import com.translationapp.entity.User;
import com.translationapp.im.domain.FriendRequestStatus;
import com.translationapp.im.domain.FriendshipStatus;
import com.translationapp.im.dto.FriendDTO;
import com.translationapp.im.dto.FriendRequestCreateDTO;
import com.translationapp.im.dto.FriendRequestDTO;
import com.translationapp.im.dto.UserSummaryDTO;
import com.translationapp.im.entity.FriendRequest;
import com.translationapp.im.entity.Friendship;
import com.translationapp.im.repository.FriendRequestRepository;
import com.translationapp.im.repository.FriendshipRepository;
import com.translationapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;

    public List<FriendDTO> listFriends(Long userId) {
        return friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED).stream()
                .map(f -> toFriendDTO(f.getFriendUserId(), f.getRemark(), f.getStatus(), f.getCreatedAt()))
                .toList();
    }

    public List<FriendRequestDTO> listPendingRequests(Long userId) {
        return friendRequestRepository.findByToUserIdAndStatus(userId, FriendRequestStatus.PENDING).stream()
                .map(this::toRequestDTO)
                .toList();
    }

    @Transactional
    public FriendRequestDTO createRequest(Long fromUserId, FriendRequestCreateDTO request) {
        if (fromUserId.equals(request.getToUserId())) {
            throw new IllegalArgumentException("Cannot add yourself as friend");
        }
        userRepository.findById(request.getToUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (friendshipRepository.existsByUserIdAndFriendUserIdAndStatus(
                fromUserId, request.getToUserId(), FriendshipStatus.ACCEPTED)) {
            throw new IllegalArgumentException("Already friends");
        }

        friendRequestRepository.findByFromUserIdAndToUserIdAndStatus(
                fromUserId, request.getToUserId(), FriendRequestStatus.PENDING)
                .ifPresent(r -> { throw new IllegalArgumentException("Request already pending"); });

        FriendRequest fr = new FriendRequest();
        fr.setFromUserId(fromUserId);
        fr.setToUserId(request.getToUserId());
        fr.setMessage(request.getMessage());
        fr.setStatus(FriendRequestStatus.PENDING);
        fr = friendRequestRepository.save(fr);
        return toRequestDTO(fr);
    }

    @Transactional
    public void acceptRequest(Long userId, Long requestId) {
        FriendRequest fr = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!fr.getToUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized");
        }
        if (fr.getStatus() != FriendRequestStatus.PENDING) {
            throw new IllegalArgumentException("Request already processed");
        }
        fr.setStatus(FriendRequestStatus.ACCEPTED);
        friendRequestRepository.save(fr);
        createFriendshipPair(fr.getFromUserId(), fr.getToUserId());
    }

    @Transactional
    public void rejectRequest(Long userId, Long requestId) {
        FriendRequest fr = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!fr.getToUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized");
        }
        fr.setStatus(FriendRequestStatus.REJECTED);
        friendRequestRepository.save(fr);
    }

    @Transactional
    public void deleteFriend(Long userId, Long friendUserId) {
        friendshipRepository.findByUserIdAndFriendUserId(userId, friendUserId)
                .ifPresent(friendshipRepository::delete);
        friendshipRepository.findByUserIdAndFriendUserId(friendUserId, userId)
                .ifPresent(friendshipRepository::delete);
    }

    @Transactional
    public void blockFriend(Long userId, Long friendUserId) {
        Friendship friendship = friendshipRepository.findByUserIdAndFriendUserId(userId, friendUserId)
                .orElseGet(() -> {
                    Friendship f = new Friendship();
                    f.setUserId(userId);
                    f.setFriendUserId(friendUserId);
                    return f;
                });
        friendship.setStatus(FriendshipStatus.BLOCKED);
        friendshipRepository.save(friendship);
    }

    public boolean areFriends(Long userId, Long otherUserId) {
        return friendshipRepository.existsByUserIdAndFriendUserIdAndStatus(
                userId, otherUserId, FriendshipStatus.ACCEPTED);
    }

    private void createFriendshipPair(Long userA, Long userB) {
        saveFriendship(userA, userB);
        saveFriendship(userB, userA);
    }

    private void saveFriendship(Long userId, Long friendUserId) {
        Friendship f = friendshipRepository.findByUserIdAndFriendUserId(userId, friendUserId)
                .orElseGet(() -> {
                    Friendship nf = new Friendship();
                    nf.setUserId(userId);
                    nf.setFriendUserId(friendUserId);
                    return nf;
                });
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);
    }

    private FriendDTO toFriendDTO(Long friendUserId, String remark, FriendshipStatus status, java.time.LocalDateTime createdAt) {
        User user = userRepository.findById(friendUserId).orElse(null);
        FriendDTO dto = new FriendDTO();
        dto.setUserId(friendUserId);
        dto.setRemark(remark);
        dto.setStatus(status);
        dto.setCreatedAt(createdAt);
        if (user != null) {
            dto.setUsername(user.getUsername());
            dto.setRealName(user.getRealName());
            dto.setAvatar(user.getAvatar());
        }
        dto.setPresenceStatus(presenceService.getStatus(friendUserId));
        return dto;
    }

    private FriendRequestDTO toRequestDTO(FriendRequest fr) {
        FriendRequestDTO dto = new FriendRequestDTO();
        dto.setId(fr.getId());
        dto.setFromUserId(fr.getFromUserId());
        dto.setToUserId(fr.getToUserId());
        dto.setMessage(fr.getMessage());
        dto.setStatus(fr.getStatus());
        dto.setCreatedAt(fr.getCreatedAt());
        dto.setFromUser(toUserSummary(fr.getFromUserId()));
        return dto;
    }

    private UserSummaryDTO toUserSummary(Long userId) {
        return userRepository.findById(userId).map(this::mapUser).orElse(null);
    }

    private UserSummaryDTO mapUser(User user) {
        UserSummaryDTO summary = new UserSummaryDTO();
        summary.setId(user.getId());
        summary.setUsername(user.getUsername());
        summary.setRealName(user.getRealName());
        summary.setAvatar(user.getAvatar());
        summary.setPhone(user.getPhone());
        summary.setPresenceStatus(presenceService.getStatus(user.getId()));
        return summary;
    }
}
