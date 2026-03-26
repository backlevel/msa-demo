package com.example.ai.service;

import com.example.ai.config.SlackConfig;
import com.example.ai.dto.AiDto;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackService {

    private final MethodsClient slackMethodsClient;
    private final SlackConfig slackConfig;

    /**
     * 고객 문의 에스컬레이션 알림
     * AI가 해결하지 못한 문의를 담당자에게 전달
     */
    public boolean notifyEscalation(AiDto.SlackNotification notification) {
        try {
            String message = buildEscalationMessage(notification);

            slackMethodsClient.chatPostMessage(
                    ChatPostMessageRequest.builder()
                            .channel(slackConfig.getChannel())
                            .text(message)
                            .build()
            );

            log.info("[Slack] 에스컬레이션 알림 전송 - sessionId={}", notification.getSessionId());
            return true;

        } catch (Exception e) {
            log.error("[Slack] 알림 전송 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 주문 이벤트 알림 (Saga 완료, 주문 확정 등)
     */
    public boolean notifyOrderEvent(String channel, String message) {
        try {
            slackMethodsClient.chatPostMessage(
                    ChatPostMessageRequest.builder()
                            .channel(channel != null ? channel : slackConfig.getChannel())
                            .text(message)
                            .build()
            );
            log.info("[Slack] 주문 이벤트 알림 전송");
            return true;
        } catch (Exception e) {
            log.error("[Slack] 알림 전송 실패: {}", e.getMessage());
            return false;
        }
    }

    private String buildEscalationMessage(AiDto.SlackNotification notification) {
        return String.format("""
                :warning: *고객 문의 에스컬레이션 알림*
                
                *세션 ID:* %s
                *고객 ID:* %s
                *발생 시간:* %s
                
                *고객 문의:*
                > %s
                
                *AI 응답:*
                > %s
                
                *에스컬레이션 사유:* %s
                
                담당자 확인이 필요합니다. :raised_hands:
                """,
                notification.getSessionId(),
                notification.getUserId() != null ? notification.getUserId() : "비회원",
                notification.getTimestamp(),
                notification.getUserMessage(),
                notification.getAiAnswer(),
                notification.getReason()
        );
    }
}
