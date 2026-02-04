package com.example.project.service;

import org.springframework.stereotype.Service;

import com.example.project.domain.ChatMessage;
import com.example.project.repository.ChatMessageRepository;

@Service
public class ChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    // DB에 저장하려고 msg에서 각각의 것들 빼와 가져오고 저장.
    public void saveMessage(String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }
}
