package com.example.project.controller;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.project.controller.dto.ChatRequest;
import com.example.project.service.ChatHistoryService;
import com.example.project.service.PythonClientService;

@RestController
public class ChatStreamController {
    private final PythonClientService pythonClientService;
    private final ChatHistoryService chatHistoryService;
    private final ExecutorService executor = Executors.newFixedThreadPool(20); // 로컬 PC 부하 방지

    public ChatStreamController(PythonClientService pythonClientService, ChatHistoryService chatHistoryService) {
        this.pythonClientService = pythonClientService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping("/chats/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(0L);

        executor.submit(() -> {
            try {
                pythonClientService.forwardSseToClient(req, emitter,
                        (fullAnswer) -> {
                            chatHistoryService.saveMessage("user", req.getQuestion(),req.getUserId());
                            chatHistoryService.saveMessage("assistant", fullAnswer,req.getUserId());
                        });
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignore) {
                }
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
            @RequestParam(value = "topK", required = false) Integer topK,
            @RequestParam("model") String model,
            @RequestParam("documentName") String documentName,
            @RequestParam(value = "userId", required = false) String userId) {

        ChatRequest req = new ChatRequest();

        req.setDocumentIds(parseIds(docIds));
        req.setQuestion(q);
        req.setTopK(topK == null ? 5 : topK);
        req.setModel(model);
        req.setDocumentName(documentName);
        req.setUserId(userId);

        return chatStream(req);
    }
}
