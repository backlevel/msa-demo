package com.example.ai.service;

import com.example.ai.dto.AiDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiSpecService {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    /**
     * LLM 기반 API 명세 자동 생성
     * - Gemini에게 엔드포인트 정보를 주고 OpenAPI 스타일 명세 생성
     */
    public AiDto.ApiSpecResponse generateSpec(AiDto.ApiSpecRequest request) {
        log.info("[ApiSpec] 명세 생성 요청 - {} {}", request.getMethod(), request.getEndpoint());

        // Gemini로 명세 생성
        String rawSpec = geminiService.generateApiSpec(request);

        // JSON 파싱 시도
        AiDto.ApiSpecResponse response = parseSpecResponse(rawSpec, request);

        log.info("[ApiSpec] 명세 생성 완료 - {}", request.getEndpoint());
        return response;
    }

    /**
     * 전체 서비스의 API 명세 일괄 생성
     */
    public Map<String, AiDto.ApiSpecResponse> generateAllSpecs() {
        log.info("[ApiSpec] 전체 API 명세 생성 시작");

        Map<String, AiDto.ApiSpecRequest> endpoints = buildEndpointDefinitions();
        Map<String, AiDto.ApiSpecResponse> results = new java.util.LinkedHashMap<>();

        for (Map.Entry<String, AiDto.ApiSpecRequest> entry : endpoints.entrySet()) {
            try {
                results.put(entry.getKey(), generateSpec(entry.getValue()));
                Thread.sleep(500); // API 요청 간 딜레이 (Rate Limit 방지)
            } catch (Exception e) {
                log.error("[ApiSpec] {} 명세 생성 실패: {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("[ApiSpec] 전체 명세 생성 완료 - {}개", results.size());
        return results;
    }

    private AiDto.ApiSpecResponse parseSpecResponse(String raw, AiDto.ApiSpecRequest request) {
        try {
            // JSON 블록 추출 (마크다운 코드블록 제거)
            String json = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            Map<?, ?> parsed = objectMapper.readValue(json, Map.class);

            return AiDto.ApiSpecResponse.builder()
                    .serviceName(request.getServiceName())
                    .endpoint(request.getEndpoint())
                    .method(request.getMethod())
                    .summary(getString(parsed, "summary"))
                    .description(getString(parsed, "description"))
                    .requestSchema(getString(parsed, "requestSchema"))
                    .responseSchema(getString(parsed, "responseSchema"))
                    .errorCases(getString(parsed, "errorCases"))
                    .curlExample(getString(parsed, "curlExample"))
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.warn("[ApiSpec] JSON 파싱 실패, 원본 텍스트 사용: {}", e.getMessage());
            // 파싱 실패 시 원본 텍스트로 폴백
            return AiDto.ApiSpecResponse.builder()
                    .serviceName(request.getServiceName())
                    .endpoint(request.getEndpoint())
                    .method(request.getMethod())
                    .description(raw)
                    .generatedAt(LocalDateTime.now())
                    .build();
        }
    }

    private String getString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    /**
     * 현재 프로젝트의 주요 엔드포인트 정의
     */
    private Map<String, AiDto.ApiSpecRequest> buildEndpointDefinitions() {
        Map<String, AiDto.ApiSpecRequest> endpoints = new java.util.LinkedHashMap<>();

        endpoints.put("POST /api/users", AiDto.ApiSpecRequest.builder()
                .serviceName("user-service")
                .method("POST")
                .endpoint("/api/users")
                .requestBody("""
                        {"name": "홍길동", "email": "hong@example.com"}
                        """)
                .responseBody("""
                        {"id": 1, "name": "홍길동", "email": "hong@example.com"}
                        """)
                .description("신규 유저를 생성합니다.")
                .build());

        endpoints.put("GET /api/users/{id}", AiDto.ApiSpecRequest.builder()
                .serviceName("user-service")
                .method("GET")
                .endpoint("/api/users/{id}")
                .responseBody("""
                        {"id": 1, "name": "홍길동", "email": "hong@example.com"}
                        """)
                .description("유저 ID로 단건 조회합니다.")
                .build());

        endpoints.put("POST /api/products", AiDto.ApiSpecRequest.builder()
                .serviceName("product-service")
                .method("POST")
                .endpoint("/api/products")
                .requestBody("""
                        {"name": "노트북", "price": 1200000, "stock": 50}
                        """)
                .responseBody("""
                        {"id": 1, "name": "노트북", "price": 1200000, "stock": 50}
                        """)
                .description("신규 상품을 등록합니다.")
                .build());

        endpoints.put("POST /api/orders", AiDto.ApiSpecRequest.builder()
                .serviceName("order-service")
                .method("POST")
                .endpoint("/api/orders")
                .requestBody("""
                        {"userId": 1, "productId": 1, "quantity": 2}
                        """)
                .responseBody("""
                        {"id": 1, "userId": 1, "productId": 1, "quantity": 2, "status": "PENDING"}
                        """)
                .description("주문을 생성합니다. Kafka Saga 패턴으로 재고 확인 후 CONFIRMED/CANCELLED 처리됩니다.")
                .build());

        endpoints.put("PATCH /api/orders/{id}/confirm", AiDto.ApiSpecRequest.builder()
                .serviceName("order-service")
                .method("PATCH")
                .endpoint("/api/orders/{id}/confirm")
                .responseBody("""
                        {"id": 1, "status": "CONFIRMED"}
                        """)
                .description("주문을 수동으로 확정합니다.")
                .build());

        return endpoints;
    }
}
