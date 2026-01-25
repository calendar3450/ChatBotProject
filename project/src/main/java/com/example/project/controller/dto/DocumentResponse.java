package com.example.project.controller.dto;

import java.time.LocalDateTime;

import com.example.project.domain.Document;
import com.example.project.domain.DocumentStatus;

public class DocumentResponse {

    private Long id;
    private String title;
    private String filePath;
    private DocumentStatus status;
    private LocalDateTime createAt;

    public static DocumentResponse from (Document d) {
        DocumentResponse r = new DocumentResponse();
        r.id = d.getId();
        r.title = d.getTitle();
        r.filePath = d.getFilePath();
        r.status = d.getStatus() == null ? null : d.getStatus();
        r.createAt = d.getCreatedAt() == null ? null : d.getCreatedAt();
        return r;
    }
    
    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getFilePath() {
        return filePath;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }
}
