package com.example.project.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.project.controller.dto.DocumentCreateRequest;
import com.example.project.controller.dto.DocumentResponse;
import com.example.project.domain.Document;
import com.example.project.repository.DocumentRepository;
import com.example.project.service.DocumentService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;

    public DocumentController(DocumentRepository documentRepository, DocumentService documentService) {
        this.documentRepository = documentRepository;
        this.documentService = documentService;

    }

    @GetMapping("/documents")
    public List<DocumentResponse> list() {
        return documentRepository.findAll()
            .stream()
            .map(DocumentResponse::from)
            .toList();
    }

    @PostMapping("/documents")
    public DocumentResponse create(@Valid @RequestBody DocumentCreateRequest req) {
        Document d = documentService.create(req.getTitle());
        return DocumentResponse.from(d);

    }

    @PostMapping("/documents/{id}/upload")
    public DocumentResponse upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {

        Document d = documentService.uploadFile(id, file);
        return DocumentResponse.from(d);
    }
    
    
}
