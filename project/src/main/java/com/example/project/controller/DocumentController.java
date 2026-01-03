package com.example.project.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.project.domain.Document;
import com.example.project.repository.DocumentRepository;


@RestController
public class DocumentController {
    private final DocumentRepository documentRepository;

    public DocumentController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @GetMapping("/documents")
    public List<Document> list() {
        return documentRepository.findAll();
    }

}
