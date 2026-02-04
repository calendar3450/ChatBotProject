package com.example.project.controller.dto;

import java.time.LocalDateTime;
import com.example.project.domain.ChatMessage;

public class ChatResponse {
    private Long id;
    private String role;
    private String text; // 프론트엔드에서 사용하는 필드명
    private LocalDateTime createdAt;

    public static ChatResponse from(ChatMessage msg) {
        ChatResponse r = new ChatResponse();
        r.id = msg.getId();
        r.role = msg.getRole();
        r.text = msg.getContent(); // DB의 content를 API의 text로 매핑
        r.createdAt = msg.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getRole() { return role; }
    public String getText() { return text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
