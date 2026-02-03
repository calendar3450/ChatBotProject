package com.example.project.service;

import com.example.project.domain.ChatMessage;
import com.example.project.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public void saveMessage(String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }
}
