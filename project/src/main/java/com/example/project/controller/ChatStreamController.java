package com.example.project.controller;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.project.controller.dto.ChatRequest;
import com.example.project.service.PythonClientService;
import com.example.project.service.ChatHistoryService;

@RestController
public class ChatStreamController {
    private final PythonClientService pythonClientService;
    private final ChatHistoryService chatHistoryService;
    // 스레드 풀을 필드로 선언하여 재사용 (매 요청마다 생성 방지)
    private final ExecutorService executor = Executors.newCachedThreadPool();

    //생성자
    public ChatStreamController(PythonClientService pythonClientService, ChatHistoryService chatHistoryService) {
        this.pythonClientService = pythonClientService;
        this.chatHistoryService = chatHistoryService;
    }

    //메서드
    @PostMapping("/chats/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest req) {
        // 서버가 연결이 되었는지 안되었는지 확인하기 위한 코드.
        SseEmitter emitter = new SseEmitter(0L); // timeout 무제한(개발 편의)

        executor.submit(() -> {
            try {
                // 1. 답변 스트리밍 및 완료 시 저장
                pythonClientService.forwardSseToClient(req, emitter, 
                    (fullAnswer) -> {
                        // 1) 답변 스트리밍이 완료가 된다면, 바로 질문과 답변 DB저장.
                        chatHistoryService.saveMessage("user", req.getQuestion());
                        chatHistoryService.saveMessage("assistant", fullAnswer);
                    });
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


    @GetMapping("/chats/stream")
    public SseEmitter chatStreamGet(@RequestParam("docIds") String docIds,
                                    @RequestParam("q") String q,
                                    @RequestParam(value="topK", required=false) Integer topK,
                                    @RequestParam("model") String model) {
        ChatRequest req = new ChatRequest();
        req.setDocumentIds(parseIds(docIds)); // "1,2,3" -> List<Long>
        req.setQuestion(q);
        req.setTopK(topK == null ? 5 : topK);
        req.setModel(model);
        return chatStream(req);
    }

}
