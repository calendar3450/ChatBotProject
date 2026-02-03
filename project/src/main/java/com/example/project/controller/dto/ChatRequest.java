package com.example.project.controller.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChatRequest {

    @NotNull
    private List<Long> documentIds;

    @NotBlank
    private String question;
    private Integer topK = 5;
    private String model = "qwen3-vl:8b";

    // 여기 고쳐줘!!!! 저장된 채팅을 DB로 부터 받아오기.

    // public static void ChatRequest from (ChatMessage msg) {
    //     ChatRequest c = new ChatRequest();
    //     c.documentIds = msg.getDocumentIds();
    //     c.question = msg.getQuestion();
    //     c.topK = msg.getTopK();
    //     c.model = msg.getModel();
    //     return c;
    // }

    public List<Long> getDocumentIds() { return documentIds; }
    public void setDocumentIds(List<Long> documentIds) { this.documentIds = documentIds; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public String getModel() {return model;}
    public void setModel(String model) {this.model = model;}
}
