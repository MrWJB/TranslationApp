package com.translationapp.im.repository;

import com.translationapp.im.entity.MessageRead;
import com.translationapp.im.entity.MessageReadId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReadRepository extends JpaRepository<MessageRead, MessageReadId> {
}
