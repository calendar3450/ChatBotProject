package com.example.project.service;

public class PythonChatRequest {
    private Long document_id;
    private String question;
    private Integer top_k;
    private String document_name;

    public PythonChatRequest(Long documentId, String question, Integer topK,String document_name) {
        this.document_id = documentId;
        this.question = question;
        this.top_k = topK;
        this.document_name = document_name;
    }

    public Long getDocument_id() { return document_id; }
    public String getQuestion() { return question; }
    public Integer getTop_k() { return top_k; }
    public String getDocument_name() {return document_name;}
}
