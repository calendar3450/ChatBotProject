package com.example.project.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

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
    
     // [최적화] 스레드 3개 고정, 대기열(Queue)은 100개까지만 허용
    // 100개가 꽉 차면 요청을 거절(Reject)하여 서버 다운을 방지합니다.
    private final ExecutorService executor = new ThreadPoolExecutor(
        3,3,
        0L,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(100)
    ); 
    
    // Kafka 도입 시, 응답을 돌려줄 Emitter를 찾기 위한 저장소
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public ChatStreamController(PythonClientService pythonClientService, ChatHistoryService chatHistoryService) {
        this.pythonClientService = pythonClientService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping("/chats/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest req) {
        // 1. Emitter 생성 및 ID 발급
        SseEmitter emitter = new SseEmitter(0L);
        String requestId = UUID.randomUUID().toString();

        // 2. Emitter 저장 (Kafka Consumer가 나중에 이 ID로 Emitter를 찾음)
        activeEmitters.put(requestId, emitter);

        // 연결 종료/타임아웃 시 Map에서 제거
        emitter.onCompletion(() -> activeEmitters.remove(requestId));
        emitter.onTimeout(() -> activeEmitters.remove(requestId));
        emitter.onError((e) -> activeEmitters.remove(requestId));

        // 3. [Producer 역할] 요청을 큐(Kafka)로 전송
        // 나중에 Kafka 도입 시: kafkaTemplate.send("chat-requests", requestId, req);
        try{
            executor.execute(() -> processQueue(requestId, req));
        } catch (Exception e) {
            emitter.completeWithError(new RuntimeException("서버가 혼잡하여 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요."));
        }
        return emitter;
    }

    // 4. [Consumer 역할] 큐에서 메시지를 꺼내 실제 처리 (나중에 @KafkaListener 메서드로 분리 가능)
    private void processQueue(String requestId, ChatRequest req) {
        SseEmitter emitter = activeEmitters.get(requestId);
        if (emitter == null) {
            System.out.println(">>> [Queue] Emitter 없음 (연결 종료됨): " + requestId);
            return; // 이미 연결이 끊긴 경우
        }

        try {
            System.out.println(">>> [Queue] Python 서버로 요청 전송 중... " + requestId);
            pythonClientService.forwardSseToClient(req, emitter,
                    (fullAnswer) -> {
                        System.out.println(">>> [Queue] 응답 완료 및 DB 저장: " + requestId);
                        chatHistoryService.saveMessage("user", req.getQuestion(), req.getUserId());
                        chatHistoryService.saveMessage("assistant", fullAnswer, req.getUserId());
                    });
            emitter.complete();
        } catch (Exception e) {
            System.err.println(">>> [Queue] 에러 발생: " + e.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (Exception ignore) {}
            emitter.completeWithError(e);
        }
    }

    private List<Long> parseIds(String s) {
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    // q는 질문임.
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
