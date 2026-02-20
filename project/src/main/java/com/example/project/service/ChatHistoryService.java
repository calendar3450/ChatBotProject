package com.example.project.service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public void saveMessage(String role, String content,String userId) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        msg.setUserId(userId);
        chatMessageRepository.save(msg);

    }

    // 최근 대화 내용을 가져와서 Python 서버에 보낼 형식(List<Map>)으로 변환
    public List<Map<String, String>> getRecentMessagesForPrompt(int limit, String userId) {
        // 1. DB에서 최신순으로 limit개 가져오기 (createdAt 기준 내림차순)
        Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
        
        // [수정] findAll() 후 필터링하면 다른 사람 글 때문에 내 글이 잘릴 수 있음 -> DB 조회 단계에서 필터링
        long startTime = System.currentTimeMillis(); // 시작 시간
        
        List<ChatMessage> messages = chatMessageRepository.findByUserId(userId, pageable);

        long endTime = System.currentTimeMillis(); // 종료 시간
        System.out.println("DB 조회 소요 시간: " + (endTime - startTime) + "ms");
        

        // 2. 과거 -> 최신 순으로 정렬 (LLM 문맥 유지를 위해 뒤집기)
        List<ChatMessage> reversed = new ArrayList<>(messages);
        Collections.reverse(reversed);

        // 3. Map 형태로 변환 (Python: [{"role": "user", "content": "..."}, ...])
        return reversed.stream().map(msg -> {
            Map<String, String> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            return map;
        }).toList();
    }
}
