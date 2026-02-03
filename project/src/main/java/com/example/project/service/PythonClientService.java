package com.example.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.project.controller.dto.ChatRequest;
import com.example.project.domain.Document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.net.URL;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;

@Service
public class PythonClientService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    // 생성자
    public PythonClientService(RestTemplate restTemplate,
                              @Value("${python.base-url}") String baseUrl,
                              ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    // 테스트용 코드
    public Map<String, Object> ping(){
        String url = baseUrl + "/ping";
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        return response;
    }

    public Map<String, Object> ingestDocument(Document document) {
        String url = baseUrl + "/ingest";

        PythonIngestRequest req = new PythonIngestRequest(
                List.of(document.getId()),
                document.getFilePath(),
                document.getTitle()
        );

        // 이게 파이썬 서버로 보내거나 받는 코드. POST역할을 함. url = 목적지, req = 보낼 데이터
        // 즉, 보낸후 응답을 줄때까지 기다리다가 받는 코드를 의미함. 
        Map<String, Object> response = restTemplate.postForObject(url, req, Map.class);
        return response;
    }

    //파이썬 chat post로 이동.
    public Map<String, Object> chat(List<Long> documentIds, String question, Integer topK) {
    // [테스트용] 일반 채팅(Non-streaming)에서도 mock: 지원
    if (question != null && question.trim().startsWith("mock:")) {
        return Map.of(
            "answer", "이것은 파이썬 서버 없이 Java에서 생성된 테스트 응답입니다. (Non-streaming)",
            "citations", List.of()
        );
    }
    String url = baseUrl + "/chat";

    // 파이썬 서버가 document_id를 단일 int로 요구합니다.
    // 리스트에 0이 포함되어 있거나(전체/일반), 리스트가 비어있으면 0으로 설정합니다.
    // 그 외의 경우 첫 번째 문서 ID를 사용합니다.
    Long documentId = (documentIds == null || documentIds.isEmpty() || documentIds.contains(0L)) 
            ? 0L 
            : documentIds.get(0);

    PythonChatRequest req = new PythonChatRequest(documentId, question, topK == null ? 5 : topK);

    Map<String, Object> response = restTemplate.postForObject(url, req, java.util.Map.class);
    return response;
    }   
    
    // 그대로 읽어서 vue로 전달.
    public void forwardSseToClient(ChatRequest req, SseEmitter emitter, Consumer<String> onComplete) throws Exception {

        // 연결 설정. Python의 post와 연결 하기 설정.
        URL url = new URL(baseUrl + "/chat_stream");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("Accept", "text/event-stream"); // 파이썬과 연결 성공.

        // JSON 바디 쓰기 (간단 버전 - Jackson 쓰는 게 정석) 질문을 json으로
        String json = toJson(req); // 아래에 간단 구현 제공
        try (OutputStream os = con.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        
        // 
        int code = con.getResponseCode();
        if (code >= 400) {
            String err = readAll(con.getErrorStream());
            throw new RuntimeException("Python SSE error: " + code + " " + err);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            String eventName = "message";
            StringBuilder dataBuf = new StringBuilder();
            StringBuilder fullAnswer = new StringBuilder(); // 전체 답변 누적용

            while ((line = br.readLine()) != null) {
                // SSE는 빈 줄이 "이벤트 끝" 구분자
                if (line.isEmpty()) {
                    if (dataBuf.length() > 0) {
                        String payload = dataBuf.toString();
                        // 답변 텍스트 누적 (delta 이벤트인 경우)
                        try {
                            JsonNode node = objectMapper.readTree(payload);
                            if (node.has("type") && "delta".equals(node.get("type").asText()) && node.has("text")) {
                                fullAnswer.append(node.get("text").asText());
                            }
                        } catch (Exception ignore) {}

                        emitter.send(SseEmitter.event().name(eventName).data(payload));
                        dataBuf.setLength(0);
                        eventName = "message";
                    }
                    continue;
                }

                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    // 여러 줄 data 지원 위해 누적
                    String payload = line.substring("data:".length()).trim();
                    if (dataBuf.length() > 0) dataBuf.append("\n");
                    dataBuf.append(payload);
                }
            }
            // 스트리밍 종료 후 전체 답변 콜백 실행
            if (onComplete != null) {
                onComplete.accept(fullAnswer.toString());
            }
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }

    // ⚠️ 임시 JSON 직렬화 (정석은 ObjectMapper)
    private String toJson(ChatRequest req) {
        // req: documentIds(List<Long>), question(String), topK(Integer)
        
        // 1. document_ids (List) -> document_id (int) 변환
        // app.py의 ChatRequest가 document_id(int)를 기대하므로 첫 번째 ID만 추출하거나 0(일반 채팅)으로 설정
        List<Long> ids = req.getDocumentIds();
        long docId = (ids == null || ids.isEmpty() || ids.contains(0L)) ? 0L : ids.get(0);

        String q = req.getQuestion().replace("\\", "\\\\").replace("\"", "\\\"");
        int topK = (req.getTopK() == null) ? 5 : req.getTopK();
        String model = (req.getModel() == null) ? "ollama" : req.getModel();

        return "{"
                + "\"document_id\":" + docId + ","
                + "\"question\":\"" + q + "\","
                + "\"top_k\":" + topK + ","
                + "\"model\":\"" + model + "\""
                + "}";
    }

}
