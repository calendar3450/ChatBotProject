package com.example.project.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.project.controller.dto.ChatRequest;
import com.example.project.repository.ChatMessageRepository;
import com.example.project.service.PythonClientService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
public class ChatController {

    private final PythonClientService pythonClientService;

    // 생성자
    public ChatController(PythonClientService pythonClientService) {
        this.pythonClientService = pythonClientService;
    }
    
    // 매써드
    @PostMapping("/chat")
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest req) {
        return pythonClientService.chat(req.getDocumentIds(), req.getQuestion(), req.getTopK());
    }

    // 여기도 고쳐줘!저장된 채팅을 DB로 부터 받아오기.

    // @GetMapping("/chat")
    // public Map<String, Object> chatList() {
    //     return ChatMessageRepository.findAll()
    //         .stream()
    //         .toList();
        
    // }
    

    
}

