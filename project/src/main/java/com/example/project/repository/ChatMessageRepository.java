package com.example.project.repository;

import com.example.project.domain.ChatMessage;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserId(String userId, Pageable pageable);
}
