package com.example.project.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class DocumentCreateRequest {

    @NotBlank
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }


}
