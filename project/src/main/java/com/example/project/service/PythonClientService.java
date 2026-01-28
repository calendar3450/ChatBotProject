package com.example.project.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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

import org.springframework.beans.factory.annotation.Value;

@Service
public class PythonClientService {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    // 생성자
    public PythonClientService(RestTemplate restTemplate,
                              @Value("${python.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
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
    public void forwardSseToClient(ChatRequest req, SseEmitter emitter) throws Exception {
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

            while ((line = br.readLine()) != null) {
                // SSE는 빈 줄이 "이벤트 끝" 구분자
                if (line.isEmpty()) {
                    if (dataBuf.length() > 0) {
                        emitter.send(SseEmitter.event().name(eventName).data(dataBuf.toString()));
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
        // Python 쪽은 snake_case(document_ids, top_k)로 받는다고 했으니 맞춰줍니다.
        String ids = req.getDocumentIds().toString(); // [1, 2]
        String q = req.getQuestion().replace("\\", "\\\\").replace("\"", "\\\"");
        int topK = (req.getTopK() == null) ? 5 : req.getTopK();

        return "{"
                + "\"document_ids\":" + ids + ","
                + "\"question\":\"" + q + "\","
                + "\"top_k\":" + topK
                + "}";
    }


}
