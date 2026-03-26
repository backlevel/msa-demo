package com.example.ai.controller;

import com.example.ai.dto.AiDto;
import com.example.ai.service.GeminiService;
import com.example.ai.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;
    private final GeminiService geminiService;

    /**
     * RAG 기반 고객 응대 채팅
     * POST /ai/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<AiDto.ChatResponse> chat(@RequestBody AiDto.ChatRequest request) {
        log.info("[API] 고객 채팅 요청 - userId={}", request.getUserId());
        return ResponseEntity.ok(ragService.chat(request));
    }

    /**
     * 단순 Gemini 직접 호출 (테스트용)
     * POST /ai/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(@RequestBody Map<String, String> body) {
        String prompt = body.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "prompt is required"));
        }
        String result = geminiService.generate(prompt);
        return ResponseEntity.ok(Map.of("result", result));
    }

    /**
     * 헬스체크
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "ai-service"));
    }
}
