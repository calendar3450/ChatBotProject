package com.example.project.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.project.domain.Document;
import com.example.project.domain.DocumentStatus;
import com.example.project.repository.DocumentRepository;

import org.springframework.beans.factory.annotation.Value;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final Path uploadDir;
    private final PythonClientService pythonClientService;

    public DocumentService(DocumentRepository documentRepository,
        @Value("${app.upload-dir}") String uploadDir,
        PythonClientService pythonClientService) {
        this.documentRepository = documentRepository;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.pythonClientService = pythonClientService;
    }

    public Document create(String title) {
        Document d = new Document();
        d.setTitle(title);
        d.setStatus(DocumentStatus.UPLOADED);
        return documentRepository.save(d);
    }

    public Document uploadFile(Long documentId, MultipartFile file) throws IOException {
        Document d = documentRepository.findById(documentId).orElseThrow(() -> new IllegalArgumentException("문서가 없습니다." + documentId));
        
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 없습니다.");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!original.endsWith(".pdf")) {
            throw new IllegalArgumentException("PDF 파일만 업로드 가능합니다.");
        }

        Files.createDirectories(uploadDir);

        String safeName = UUID.randomUUID() + ".pdf";
        Path target = uploadDir.resolve(safeName);
        
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        
        //DB 업데이트
        d.setFilePath(target.toString());
        d.setStatus(DocumentStatus.UPLOADED);
        d.setCreatedAt(LocalDateTime.now());
        Document saved = documentRepository.save(d);

        try {
            var resp = pythonClientService.ingestDocument(saved);
            System.out.println("✅ Python ingest 응답: " + resp);
        } catch (Exception e) {
            // 파이썬 서버가 꺼져 있어도 업로드 자체는 실패시키고 싶지 않다면, 여기서만 로그 찍고 무시
            System.err.println("⚠ Python ingest 호출 실패: " + e.getMessage());
        }

        return saved;
    
}

}
