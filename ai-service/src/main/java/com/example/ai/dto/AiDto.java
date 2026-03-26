package com.example.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class AiDto {

    // ── Gemini API 요청/응답 ──────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiRequest {
        private List<Content> contents;
        private GenerationConfig generationConfig;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Content {
            private String role;
            private List<Part> parts;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Part {
            private String text;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class GenerationConfig {
            private int maxOutputTokens;
            private double temperature;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiResponse {
        private List<Candidate> candidates;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Candidate {
            private Content content;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Content {
            private List<Part> parts;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Part {
            private String text;
        }

        public String extractText() {
            if (candidates == null || candidates.isEmpty()) return "";
            if (candidates.get(0).content == null) return "";
            if (candidates.get(0).content.parts == null || candidates.get(0).content.parts.isEmpty()) return "";
            return candidates.get(0).content.parts.get(0).text;
        }
    }

    // ── 고객 응대 RAG ─────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        private String sessionId;   // 대화 세션 ID (없으면 신규 생성)
        private String userId;      // 유저 ID
        private String message;     // 고객 질문
        private boolean notifySlack; // 미해결 시 Slack 알림 여부
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponse {
        private String sessionId;
        private String answer;
        private boolean resolved;    // AI가 해결했는지 여부
        private boolean slackNotified; // Slack 알림 전송 여부
        private LocalDateTime timestamp;
    }

    // ── API 명세 자동 생성 ────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiSpecRequest {
        private String serviceName;  // 대상 서비스명
        private String endpoint;     // 엔드포인트 경로
        private String method;       // HTTP 메서드
        private String requestBody;  // 요청 바디 예시 (JSON)
        private String responseBody; // 응답 바디 예시 (JSON)
        private String description;  // 추가 설명 (선택)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiSpecResponse {
        private String serviceName;
        private String endpoint;
        private String method;
        private String summary;         // 한 줄 요약
        private String description;     // 상세 설명
        private String requestSchema;   // 요청 스키마 설명
        private String responseSchema;  // 응답 스키마 설명
        private String errorCases;      // 에러 케이스
        private String curlExample;     // curl 예시
        private LocalDateTime generatedAt;
    }

    // ── Slack 알림 ────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackNotification {
        private String channel;
        private String userId;
        private String sessionId;
        private String userMessage;
        private String aiAnswer;
        private String reason;  // 에스컬레이션 이유
        private LocalDateTime timestamp;
    }
}
