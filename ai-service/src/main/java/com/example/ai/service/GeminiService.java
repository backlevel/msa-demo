package com.example.ai.service;

import com.example.ai.config.GeminiConfig;
import com.example.ai.dto.AiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final WebClient geminiWebClient;
    private final GeminiConfig geminiConfig;

    /**
     * Gemini API 단순 텍스트 생성
     */
    public String generate(String prompt) {
        log.debug("[Gemini] 요청: {}", prompt.substring(0, Math.min(100, prompt.length())));

        AiDto.GeminiRequest request = AiDto.GeminiRequest.builder()
                .contents(List.of(
                        AiDto.GeminiRequest.Content.builder()
                                .role("user")
                                .parts(List.of(AiDto.GeminiRequest.Part.builder().text(prompt).build()))
                                .build()
                ))
                .generationConfig(
                        AiDto.GeminiRequest.GenerationConfig.builder()
                                .maxOutputTokens(geminiConfig.getMaxTokens())
                                .temperature(0.7)
                                .build()
                )
                .build();

        AiDto.GeminiResponse response = geminiWebClient.post()
                .uri("/v1beta/models/{model}:generateContent?key={key}",
                        geminiConfig.getModel(), geminiConfig.getApiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiDto.GeminiResponse.class)
                .block();

        String result = (response != null) ? response.extractText() : "응답 없음";
        log.debug("[Gemini] 응답: {}", result.substring(0, Math.min(100, result.length())));
        return result;
    }

    /**
     * 대화 컨텍스트를 포함한 멀티턴 생성
     */
    public String generateWithContext(List<String> contextMessages, String newMessage) {
        // 시스템 프롬프트 + 이전 대화 + 새 메시지 결합
        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append(buildSystemPrompt()).append("\n\n");

        // 이전 대화 컨텍스트 추가
        for (int i = 0; i < contextMessages.size(); i++) {
            if (i % 2 == 0) {
                fullPrompt.append("고객: ").append(contextMessages.get(i)).append("\n");
            } else {
                fullPrompt.append("상담원: ").append(contextMessages.get(i)).append("\n");
            }
        }
        fullPrompt.append("고객: ").append(newMessage).append("\n상담원:");

        return generate(fullPrompt.toString());
    }

    /**
     * API 명세 생성용 프롬프트
     */
    public String generateApiSpec(AiDto.ApiSpecRequest req) {
        String prompt = String.format("""
                다음 API 엔드포인트에 대한 상세 명세를 작성해주세요. 반드시 아래 JSON 형식으로만 응답하세요.

                서비스명: %s
                엔드포인트: %s %s
                요청 바디 예시: %s
                응답 바디 예시: %s
                추가 설명: %s

                응답 형식 (JSON만 출력, 다른 텍스트 없이):
                {
                  "summary": "한 줄 요약",
                  "description": "상세 설명",
                  "requestSchema": "요청 필드별 타입과 설명",
                  "responseSchema": "응답 필드별 타입과 설명",
                  "errorCases": "발생 가능한 에러 코드와 원인",
                  "curlExample": "실제 실행 가능한 curl 명령어"
                }
                """,
                req.getServiceName(),
                req.getMethod(), req.getEndpoint(),
                req.getRequestBody() != null ? req.getRequestBody() : "없음",
                req.getResponseBody() != null ? req.getResponseBody() : "없음",
                req.getDescription() != null ? req.getDescription() : "없음"
        );

        return generate(prompt);
    }

    private String buildSystemPrompt() {
        return """
                당신은 MSA 쇼핑몰의 친절한 고객 지원 AI입니다.
                - 주문 조회, 상품 문의, 배송 관련 질문에 답변합니다.
                - 답변할 수 없는 경우 "담당자 연결이 필요합니다"라고 명시하세요.
                - 항상 한국어로 답변하세요.
                - 답변은 간결하고 명확하게 작성하세요.
                - 민감한 개인정보는 절대 요청하지 마세요.
                """;
    }
}
