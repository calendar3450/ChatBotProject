package com.example.project.controller;

import com.example.project.controller.dto.ChatRequest;
import com.example.project.service.PythonClientService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@RestController
public class ChatController {

    private final PythonClientService pythonClientService;

    public ChatController(PythonClientService pythonClientService) {
        this.pythonClientService = pythonClientService;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest req) {
        return pythonClientService.chat(req.getDocumentId(), req.getQuestion(), req.getTopK());
    }
}

