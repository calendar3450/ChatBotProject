package com.example.project.controller;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.project.controller.dto.ChatRequest;
import com.example.project.service.PythonClientService;

@RestController
public class ChatStreamController {
    private final PythonClientService pythonClientService;

    //생성자
    public ChatStreamController(PythonClientService pythonClientService) {
        this.pythonClientService = pythonClientService;
    }

    //메서드
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest req) {
        // 서버가 연결이 되었는지 안되었는지 확인하기 위한 코드.
        SseEmitter emitter = new SseEmitter(0L); // timeout 무제한(개발 편의)

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                pythonClientService.forwardSseToClient(req, emitter);
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignore) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private List<Long> parseIds(String s) {
    return Arrays.stream(s.split(","))
            .map(String::trim)
            .filter(x -> !x.isEmpty())
            .map(Long::parseLong)
            .toList();
    }


    @GetMapping("/chat/stream")
    public SseEmitter chatStreamGet(@RequestParam("docIds") String docIds,
                                    @RequestParam("q") String q,
                                    @RequestParam(value="topK", required=false) Integer topK) {
        ChatRequest req = new ChatRequest();
        req.setDocumentIds(parseIds(docIds)); // "1,2,3" -> List<Long>
        req.setQuestion(q);
        req.setTopK(topK == null ? 5 : topK);
        return chatStream(req);
    }

}
