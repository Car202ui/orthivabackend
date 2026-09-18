package com.orthiva.core.shared.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    /** Public liveness probe (no token required). */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "orthiva-core", "time", Instant.now());
    }
}
