package com.example.project.controller;

import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import com.example.project.service.PythonClientService;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
public class PythonTestController {

    private final PythonClientService pythonClientService;

    public PythonTestController(PythonClientService pythonClientService) {
        this.pythonClientService = pythonClientService;
    }

    @GetMapping("/python/ping")
    public Map<String, Object> ping() {
        return pythonClientService.ping();
    }
}
