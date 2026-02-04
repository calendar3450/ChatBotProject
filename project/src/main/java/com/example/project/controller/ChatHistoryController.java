package com.example.project.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.project.controller.dto.ChatResponse;
import com.example.project.repository.ChatMessageRepository;

@RestController
public class ChatHistoryController {

    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryController(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }
    
    // 챗 기록들 가져오기.
    @GetMapping("/chats")
    public List<ChatResponse> list() {
        return chatMessageRepository.findAll()
                .stream()
                .map(ChatResponse::from)
                .toList();
    }
}
