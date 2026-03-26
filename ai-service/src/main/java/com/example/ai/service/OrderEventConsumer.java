package com.example.ai.service;

import com.example.ai.dto.AiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Saga 완료 이벤트 Consumer
 *
 * order.confirmed → 주문 확정 Slack 알림
 * order.cancelled → 재고 부족 취소 Slack 알림 (고객 케어 필요)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final SlackService slackService;

    @KafkaListener(topics = "order.confirmed", groupId = "ai-order-confirmed-group")
    public void handleOrderConfirmed(Map<String, Object> payload) {
        log.info("[AI-Slack] 주문 확정 이벤트 수신: {}", payload);
        try {
            Long orderId = Long.valueOf(payload.get("orderId").toString());
            Long userId  = Long.valueOf(payload.get("userId").toString());

            String message = String.format(
                    ":white_check_mark: *주문 확정 완료*\n" +
                    "• 주문 ID: `%d`\n" +
                    "• 유저 ID: `%d`\n" +
                    "• 처리 시간: %s",
                    orderId, userId, LocalDateTime.now()
            );
            slackService.notifyOrderEvent(null, message);

        } catch (Exception e) {
            log.error("[AI-Slack] 주문 확정 알림 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "order.cancelled", groupId = "ai-order-cancelled-group")
    public void handleOrderCancelled(Map<String, Object> payload) {
        log.info("[AI-Slack] 주문 취소 이벤트 수신: {}", payload);
        try {
            Long orderId = Long.valueOf(payload.get("orderId").toString());
            Long userId  = Long.valueOf(payload.get("userId").toString());
            String reason = payload.getOrDefault("reason", "재고 부족").toString();

            String message = String.format(
                    ":x: *주문 취소 (보상 트랜잭션)*\n" +
                    "• 주문 ID: `%d`\n" +
                    "• 유저 ID: `%d`\n" +
                    "• 취소 사유: %s\n" +
                    "• 처리 시간: %s\n" +
                    "_고객 케어가 필요할 수 있습니다._",
                    orderId, userId, reason, LocalDateTime.now()
            );
            slackService.notifyOrderEvent(null, message);

        } catch (Exception e) {
            log.error("[AI-Slack] 주문 취소 알림 실패: {}", e.getMessage());
        }
    }
}
