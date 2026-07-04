package com.translationapp.im.repository;

import com.translationapp.im.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            Long conversationId, LocalDateTime before, Pageable pageable);

    List<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    Optional<Message> findByConversationIdAndClientMsgId(Long conversationId, String clientMsgId);

    @Query(value = "SELECT * FROM messages m WHERE MATCH(m.content) AGAINST (:query IN NATURAL LANGUAGE MODE) " +
            "AND (:conversationId IS NULL OR m.conversation_id = :conversationId) " +
            "ORDER BY m.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Message> searchFullText(@Param("query") String query,
                                 @Param("conversationId") Long conversationId,
                                 @Param("limit") int limit);
}
