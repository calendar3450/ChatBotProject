package com.example.project.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.example.project.domain.Document;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

@Service
public class PythonClientService {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PythonClientService(RestTemplate restTemplate,
                              @Value("${python.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        }
        
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
}
