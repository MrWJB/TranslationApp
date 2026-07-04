package com.translationapp.im.repository;

import com.translationapp.im.domain.CallStatus;
import com.translationapp.im.entity.CallSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CallSessionRepository extends JpaRepository<CallSession, Long> {
    List<CallSession> findByConversationIdAndStatus(Long conversationId, CallStatus status);
    Optional<CallSession> findFirstByConversationIdAndStatusOrderByStartedAtDesc(Long conversationId, CallStatus status);
}
