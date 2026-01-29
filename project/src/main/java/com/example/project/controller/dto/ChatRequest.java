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

    public List<Long> getDocumentIds() { return documentIds; }
    public void setDocumentIds(List<Long> documentIds) { this.documentIds = documentIds; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public String getModel() {return model;}
    public void setModel(String model) {this.model = model;}
}
