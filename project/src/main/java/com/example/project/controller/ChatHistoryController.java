package com.example.project.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.project.controller.dto.ChatResponse;
import com.example.project.repository.ChatMessageRepository;
import com.example.project.service.ChatHistoryService;

@RestController
public class ChatHistoryController {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatMessageRepository chatMessageRepository, ChatHistoryService chatHistoryService) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatHistoryService = chatHistoryService;
    }
    
    // 챗 기록들 가져오기.
    @GetMapping("/chats")
    public List<ChatResponse> list(@RequestParam(name = "page", defaultValue = "0") int page,
                                   @RequestParam(name = "size", defaultValue = "20") int size,
                                    @RequestParam(name = "userId",defaultValue = "") String userId) {
        // 최신순(내림차순)으로 페이징하여 가져옴
        Pageable pageable = PageRequest.of(page, size ,Sort.by("createdAt").descending());
        
        List<ChatResponse> list = chatMessageRepository.findAll(pageable)
                .stream()
                .filter(chat -> userId.equals(chat.getUserId()))
                .map(ChatResponse::from)
                .toList();
        
        // 프론트엔드에서는 과거 -> 최신 순으로 보여줘야 하므로 순서를 뒤집음
        List<ChatResponse> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }

    // Python 서버가 호출할 API: 최근 대화 기록을 프롬프트용 포맷으로 반환
    @GetMapping("/chats/history")
    public List<Map<String, String>> getHistoryForPrompt(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return chatHistoryService.getRecentMessagesForPrompt(limit);
    }
}
