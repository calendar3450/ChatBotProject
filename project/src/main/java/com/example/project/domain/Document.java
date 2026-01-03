package com.example.project.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "documents")

public class Document {
    @Id //Primary Key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동증가 하는 방식.
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    // 일단 미리 만들어 놓는것. nullable을 붙여 나야 하지만 작성중일때는 딱히 필요 없기도 하고 나중에 넣어도 됨.
    @Column(length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getTitle(){
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public DocumentStatus getStatus() {
        return status;
    }
    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }


    
}
