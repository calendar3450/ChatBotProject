package com.example.project.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<ChatResponse> list(@RequestParam(name = "page", defaultValue = "0") int page,
                                   @RequestParam(name = "size", defaultValue = "20") int size) {
        // 최신순(내림차순)으로 페이징하여 가져옴
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        List<ChatResponse> list = chatMessageRepository.findAll(pageable)
                .stream()
                .map(ChatResponse::from)
                .toList();
        
        // 프론트엔드에서는 과거 -> 최신 순으로 보여줘야 하므로 순서를 뒤집음
        List<ChatResponse> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }
}
