package com.example.project.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.project.controller.dto.ChatRequest;
import com.example.project.service.PythonClientService;

import jakarta.validation.Valid;

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

    
}

