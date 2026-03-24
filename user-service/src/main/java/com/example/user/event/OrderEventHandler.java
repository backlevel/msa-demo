package com.example.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * User Service Saga 참여자
 *
 * order.confirmed → 주문 완료 알림 처리 (예: 구매 이력 업데이트)
 * order.cancelled → 주문 취소 알림 처리 (예: 알림 발송)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    @KafkaListener(topics = "order.confirmed", groupId = "user-order-confirmed-group")
    public void handleOrderConfirmed(Map<String, Object> payload) {
        Long orderId = Long.valueOf(payload.get("orderId").toString());
        Long userId  = Long.valueOf(payload.get("userId").toString());
        log.info("[User] 주문 확정 수신 - orderId={}, userId={} → 구매 이력 업데이트", orderId, userId);
        // TODO: 구매 횟수 증가, 등급 업데이트, 알림 발송 등 확장 가능
    }

    @KafkaListener(topics = "order.cancelled", groupId = "user-order-cancelled-group")
    public void handleOrderCancelled(Map<String, Object> payload) {
        Long orderId = Long.valueOf(payload.get("orderId").toString());
        Long userId  = Long.valueOf(payload.get("userId").toString());
        String reason = payload.getOrDefault("reason", "알 수 없음").toString();
        log.warn("[User] 주문 취소 수신 - orderId={}, userId={}, reason={}", orderId, userId, reason);
        // TODO: 취소 알림 발송, 포인트 복구 등 확장 가능
    }
}
