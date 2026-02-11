package com.example.project.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.project.controller.dto.DocumentResponse;
import com.example.project.domain.Document;
import com.example.project.repository.DocumentRepository;
import com.example.project.service.DocumentService;

import org.springframework.web.bind.annotation.PostMapping;
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
    public List<DocumentResponse> list(@RequestParam(value = "userId", required = false) String userId)
     {
        return documentRepository.findAll()
            .stream()
            // userId가 파라미터로 왔다면 해당 유저의 문서만 필터링 (없으면 전체 조회)
            .filter(doc -> userId == null || userId.equals(doc.getUserId()))
            .map(DocumentResponse::from)
            .toList();
    }

    // legacy 코드
    // @PostMapping("/documents")
    // public DocumentResponse create(@Valid @RequestBody DocumentCreateRequest req) {
    //     Document d = documentService.create(req.getTitle());
    //     return DocumentResponse.from(d);

    // }

    // @PostMapping("/documents/{id}/upload")
    // public DocumentResponse upload(@PathVariable("id") Long id, @RequestParam("file") MultipartFile file) throws IOException {

    //     Document d = documentService.uploadFile(id, file);
    //     return DocumentResponse.from(d);
    // }

    // @PostMapping("/documents/upload")
    // public DocumentResponse uploadOneShot(@RequestParam("title") String title,
    //                                     @RequestParam("file") MultipartFile file) throws IOException {
    //     Document d = documentService.createAndUpload(title, file);
    //     return DocumentResponse.from(d);
    // }

    //파일 업로드
    @PostMapping("/documents/uploads")
    public List<DocumentResponse> uploadMany (@RequestParam("files") List<MultipartFile> files,
                                              @RequestParam("userId") List<String> userIds) throws IOException{
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("파일이 없습니다.");
        
        }
        List<DocumentResponse> results = new ArrayList<>();
        // 파일을 2개이상 보낼때, 유저 id도 2개 이상씩 나오니, 그중 1개 맨 앞의 리스트값 사용.
        String userId = userIds.isEmpty() ? null : userIds.get(0);
        
        for (MultipartFile f : files) {
            String original = (f.getOriginalFilename() == null) ? "untitled.pdf" : f.getOriginalFilename();
            String title = original;
            Document d = documentService.createAndUpload(title, f, userId);
            results.add(DocumentResponse.from(d));
        }

        return results;
    }
    
    // 파일 임베딩
    @PostMapping("/documents/{id}/reingest")
    public DocumentResponse reingest(@PathVariable("id") Long id) {
        return DocumentResponse.from(documentService.reingest(id));
    }
    
    // 파일 삭제
    @DeleteMapping("/documents/{id}/delete")
    public void delete(@PathVariable("id") Long id) {
        documentService.delete(id);
    }
    
}
