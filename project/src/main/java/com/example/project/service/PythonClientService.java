package com.example.project.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.project.controller.dto.ChatRequest;
import com.example.project.domain.Document;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PythonClientService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper; // JSON 파싱용

    // 생성자
    public PythonClientService(RestTemplate restTemplate,
                              @Value("${python.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
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
    String url = baseUrl + "/chat";

    // 파이썬 서버가 document_id를 단일 int로 요구합니다.
    // 리스트에 0이 포함되어 있거나(전체/일반), 리스트가 비어있으면 0으로 설정합니다.
    // 그 외의 경우 첫 번째 문서 ID를 사용합니다.
    Long documentId = (documentIds == null || documentIds.isEmpty() || documentIds.contains(0L)) 
            ? 0L 
            : documentIds.get(0);

    PythonChatRequest req = new PythonChatRequest(documentId, question, topK == null ? 5 : topK);

    // 파이썬 서버로 요청 url = 파이썬 app.post(), req = 요청 데이터.
    Map<String, Object> response = restTemplate.postForObject(url, req, Map.class);
    return response;
    }   
    
    // 그대로 읽어서 vue로 전달 + 완료 시 콜백 호출
    public void forwardSseToClient(ChatRequest req, SseEmitter emitter, Consumer<String> onComplete) throws Exception {
        // ✅ Python이 GET 방식으로 변경되었으므로, 쿼리 파라미터로 변환
        String docIds = req.getDocumentIds().toString().replaceAll("[\\[\\] ]", ""); // [1, 2] -> 1,2
        String q = URLEncoder.encode(req.getQuestion(), StandardCharsets.UTF_8);
        int topK = (req.getTopK() == null) ? 3 : req.getTopK();
        String model = req.getModel();

        // URL 생성: /chat/stream?docIds=1,2&q=질문&topK=3 여기서 모델을 인식해야함
        String query = String.format("?docIds=%s&q=%s&topK=%d&model=%s", docIds, q, topK, model);
        URL url = new URL(baseUrl + "/chat/stream" + query);

        // 파이썬 연결 시키기.
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET"); // GET으로 변경
        con.setRequestProperty("Accept", "text/event-stream"); // 파이썬과 연결 성공.

        // POST Body 전송 코드 삭제 (GET은 Body를 보내지 않음)
        // try (OutputStream os = con.getOutputStream()) { ... }
        int code = con.getResponseCode();
        if (code >= 400) {
            String err = readAll(con.getErrorStream());
            throw new RuntimeException("Python SSE error: " + code + " " + err);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            String eventName = "message";
            StringBuilder dataBuf = new StringBuilder();
            StringBuilder fullAnswer = new StringBuilder(); // 전체 답변 수집용

            while ((line = br.readLine()) != null) {
                // SSE는 빈 줄이 "이벤트 끝" 구분자
                if (line.isEmpty()) {
                    if (dataBuf.length() > 0) {
                        String dataStr = dataBuf.toString();
                        emitter.send(SseEmitter.event().name(eventName).data(dataStr));
                        
                        // 답변 내용 수집 (delta 이벤트의 text 필드)
                        if ("delta".equals(eventName)) {
                            try {
                                Map<String, Object> map = objectMapper.readValue(dataStr, Map.class);
                                if (map.containsKey("text")) {
                                    fullAnswer.append(map.get("text"));
                                }
                            } catch (Exception e) {
                                // JSON 파싱 실패 시 무시
                            }
                        }

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
            
            // 스트리밍 종료 후 콜백 호출 (DB 저장용)
            if (onComplete != null) {
                onComplete.accept(fullAnswer.toString());
            }
        }
    }
    
    //에러 발생시. 그 에러 뭐가 문제인지 파악해줌.
    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }


}
