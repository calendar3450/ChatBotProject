package com.example.project.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;;

@Service
public class PythonClientService {

    private final RestTemplate restTemplate;
    private final String baseurl;

    public PythonClientService(RestTemplate restTemplate,
                              @Value("${python.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseurl = baseUrl;
        }
        
    public Map<String, Object> ping(){
        String url = baseurl + "/ping";
        
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        return response;
    }
}
