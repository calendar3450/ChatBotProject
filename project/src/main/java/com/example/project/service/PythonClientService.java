package com.example.project.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.example.project.domain.Document;

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
                document.getId(),
                document.getFilePath(),
                document.getTitle()
        );

        // 이게 파이썬 서버로 보내는 코드. POST역할을 함. url = 목적지, req = 보낼 데이터
        Map<String, Object> response = restTemplate.postForObject(url, req, Map.class);
        return response;
    }
}
