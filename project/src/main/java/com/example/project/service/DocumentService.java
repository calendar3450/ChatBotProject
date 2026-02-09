package com.example.project.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        d.setStatus(DocumentStatus.PROCESSING);
        d = documentRepository.save(d);
        Document saved = documentRepository.save(d);

        try {
            var resp = pythonClientService.ingestDocument(saved);
            System.out.println(" Python ingest 응답: " + resp);
            d.setStatus(DocumentStatus.DONE);
            d = documentRepository.save(d);
        } catch (Exception e) {
            // 파이썬 서버가 꺼져 있어도 업로드 자체는 실패시키고 싶지 않다면, 여기서만 로그 찍고 무시
            System.err.println("Python ingest 호출 실패: " + e.getMessage());
            d.setStatus(DocumentStatus.FAILED);
            d = documentRepository.save(d);
        }

        return saved;
    
    }

    //파일 한번에 id 만들어서 바로 전송.
    public Document createAndUpload (String title, MultipartFile file) throws IOException {
        Document d = create(title);
        return uploadFile(d.getId(), file);
    }

    public Document reingest(Long documentId) {
    Document d = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

    if (d.getFilePath() == null || d.getFilePath().isBlank()) {
        throw new IllegalStateException("No filePath for document: " + documentId);
    }

    // 상태를 PROCESSING으로
    d.setStatus(DocumentStatus.PROCESSING);
    d = documentRepository.save(d);

    try {
        pythonClientService.ingestDocument(d);
        d.setStatus(DocumentStatus.DONE);
    } catch (Exception e) {
        d.setStatus(DocumentStatus.FAILED);
    }

    return documentRepository.save(d);
}
    //파일 하나씩 제거.
    public void delete(Long documentId) {
        //파일 제거
         Document d = documentRepository.findById(documentId).orElseThrow(() -> new IllegalArgumentException("문서가 없습니다." + documentId));
         documentRepository.delete(d);

         // 서버내 업로드 pdf 파일 제거
         Path target = Path.of(d.getFilePath());
         System.out.println("파일 위치: " + target);
         try {
            Files.deleteIfExists(target);
         } catch (IOException e) {
            System.err.println("파일 삭제 실패: " + e.getMessage());
         }

         // 서버 내 데이터 청크 벡터 제거
         Path rootPath = Path.of(System.getProperty("user.dir"));
         Path dataPath = rootPath.resolve("data").resolve("doc_"+ documentId);
         try {
            Files.deleteIfExists(dataPath.resolve("chunks.json"));
            Files.deleteIfExists(dataPath.resolve("index.faiss"));
            Files.deleteIfExists(dataPath); // 폴더 삭제 (비어있어야 삭제됨)
            
         } catch (IOException e) {
            System.err.println("벡터 데이터 삭제 실패: " + e.getMessage());
         }
        
    }
}
