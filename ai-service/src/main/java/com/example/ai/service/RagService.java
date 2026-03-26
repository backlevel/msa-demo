package com.example.ai.service;

import com.example.ai.dto.AiDto;
import com.example.ai.rag.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final GeminiService geminiService;
    private final SlackService slackService;
    private final KnowledgeBase knowledgeBase;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${rag.context-ttl-minutes:30}")
    private int contextTtlMinutes;

    @Value("${rag.max-context-messages:10}")
    private int maxContextMessages;

    private static final String CONTEXT_KEY_PREFIX = "rag:context:";
    private static final String UNRESOLVED_KEYWORD = "담당자 연결이 필요합니다";

    /**
     * RAG 기반 고객 응대 메인 메서드
     * 1. 세션 관리 (Redis)
     * 2. 지식베이스에서 관련 문서 검색
     * 3. 컨텍스트 + 지식 + 질문을 Gemini에 전달
     * 4. 미해결 시 Slack 알림
     */
    public AiDto.ChatResponse chat(AiDto.ChatRequest request) {
        // 세션 ID 설정
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isEmpty())
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        log.info("[RAG] 고객 질문 - sessionId={}, userId={}", sessionId, request.getUserId());

        // 1. 이전 대화 컨텍스트 로드
        List<String> context = loadContext(sessionId);

        // 2. 지식베이스에서 관련 내용 검색 (RAG)
        String relevantKnowledge = knowledgeBase.search(request.getMessage());
        log.debug("[RAG] 관련 지식: {}", relevantKnowledge);

        // 3. 최종 프롬프트 구성 (지식 + 컨텍스트 + 질문)
        String augmentedMessage = buildAugmentedPrompt(relevantKnowledge, request.getMessage());

        // 4. Gemini API 호출
        String answer = geminiService.generateWithContext(context, augmentedMessage);

        // 5. 컨텍스트 업데이트 (Redis 저장)
        saveContext(sessionId, request.getMessage(), answer);

        // 6. 미해결 여부 판단 → Slack 알림
        boolean resolved = !answer.contains(UNRESOLVED_KEYWORD);
        boolean slackNotified = false;

        if (!resolved && request.isNotifySlack()) {
            slackNotified = slackService.notifyEscalation(
                    AiDto.SlackNotification.builder()
                            .sessionId(sessionId)
                            .userId(request.getUserId())
                            .userMessage(request.getMessage())
                            .aiAnswer(answer)
                            .reason("AI가 해결하지 못한 고객 문의")
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }

        return AiDto.ChatResponse.builder()
                .sessionId(sessionId)
                .answer(answer)
                .resolved(resolved)
                .slackNotified(slackNotified)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 지식베이스 내용을 포함한 증강 프롬프트 생성
     */
    private String buildAugmentedPrompt(String knowledge, String question) {
        if (knowledge == null || knowledge.isBlank()) {
            return question;
        }
        return String.format("""
                [참고 정보]
                %s

                [고객 질문]
                %s
                """, knowledge, question);
    }

    /**
     * Redis에서 대화 컨텍스트 로드
     */
    @SuppressWarnings("unchecked")
    private List<String> loadContext(String sessionId) {
        String key = CONTEXT_KEY_PREFIX + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    /**
     * 대화 내용을 Redis에 저장 (TTL 적용)
     */
    private void saveContext(String sessionId, String userMessage, String aiAnswer) {
        String key = CONTEXT_KEY_PREFIX + sessionId;
        List<String> context = loadContext(sessionId);
        context.add(userMessage);
        context.add(aiAnswer);

        // 최대 컨텍스트 크기 유지
        while (context.size() > maxContextMessages * 2) {
            context.remove(0);
            context.remove(0);
        }

        redisTemplate.opsForValue().set(key, context, contextTtlMinutes, TimeUnit.MINUTES);
        log.debug("[RAG] 컨텍스트 저장 - sessionId={}, size={}", sessionId, context.size());
    }
}
