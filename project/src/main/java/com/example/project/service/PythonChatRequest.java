package com.example.project.service;

import java.util.List;

public class PythonChatRequest {
    private List<Long> document_ids;
    private String question;
    private Integer top_k;

    public PythonChatRequest(List<Long> documentIds, String question, Integer topK) {
        this.document_ids = documentIds;
        this.question = question;
        this.top_k = topK;
    }

    public List<Long> getDocument_id() { return document_ids; }
    public String getQuestion() { return question; }
    public Integer getTop_k() { return top_k; }
}
