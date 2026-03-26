package com.example.ai.controller;

import com.example.ai.dto.AiDto;
import com.example.ai.service.ApiSpecService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai/spec")
@RequiredArgsConstructor
public class ApiSpecController {

    private final ApiSpecService apiSpecService;

    /**
     * 단일 엔드포인트 API 명세 생성
     * POST /ai/spec/generate
     *
     * 요청 예시:
     * {
     *   "serviceName": "order-service",
     *   "method": "POST",
     *   "endpoint": "/api/orders",
     *   "requestBody": "{\"userId\": 1, \"productId\": 1, \"quantity\": 2}",
     *   "responseBody": "{\"id\": 1, \"status\": \"PENDING\"}",
     *   "description": "주문 생성 API"
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<AiDto.ApiSpecResponse> generateSpec(
            @RequestBody AiDto.ApiSpecRequest request) {
        log.info("[API] 명세 생성 요청 - {} {}", request.getMethod(), request.getEndpoint());
        return ResponseEntity.ok(apiSpecService.generateSpec(request));
    }

    /**
     * 전체 서비스 API 명세 일괄 생성
     * POST /ai/spec/generate-all
     * (시간이 걸릴 수 있음 - 엔드포인트당 Gemini 호출)
     */
    @PostMapping("/generate-all")
    public ResponseEntity<Map<String, AiDto.ApiSpecResponse>> generateAllSpecs() {
        log.info("[API] 전체 명세 생성 요청");
        return ResponseEntity.ok(apiSpecService.generateAllSpecs());
    }
}
