package com.translationapp.im.repository;

import com.translationapp.im.entity.CallParticipant;
import com.translationapp.im.entity.CallParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallParticipantRepository extends JpaRepository<CallParticipant, CallParticipantId> {
    List<CallParticipant> findByIdCallSessionId(Long callSessionId);
}
