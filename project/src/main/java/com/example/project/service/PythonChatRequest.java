package com.example.project.service;

public class PythonChatRequest {
    private Long document_id;
    private String question;
    private Integer top_k;

    public PythonChatRequest(Long documentId, String question, Integer topK) {
        this.document_id = documentId;
        this.question = question;
        this.top_k = topK;
    }

    public Long getDocument_id() { return document_id; }
    public String getQuestion() { return question; }
    public Integer getTop_k() { return top_k; }
}
