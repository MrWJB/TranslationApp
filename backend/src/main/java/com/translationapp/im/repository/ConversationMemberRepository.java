package com.translationapp.im.repository;

import com.translationapp.im.entity.ConversationMember;
import com.translationapp.im.entity.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {
    List<ConversationMember> findByIdUserId(Long userId);

    Optional<ConversationMember> findByIdConversationIdAndIdUserId(Long conversationId, Long userId);

    List<ConversationMember> findByIdConversationId(Long conversationId);

    @Query("SELECT cm FROM ConversationMember cm WHERE cm.id.userId = :userId AND cm.id.conversationId IN " +
           "(SELECT cm2.id.conversationId FROM ConversationMember cm2 WHERE cm2.id.userId = :otherUserId)")
    List<ConversationMember> findDirectConversationBetween(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);
}
