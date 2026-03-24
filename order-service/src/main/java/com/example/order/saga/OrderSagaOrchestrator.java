package com.example.order.saga;

import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.event.OrderSagaEvent;
import com.example.order.exception.OrderNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Saga Orchestrator (주문 서비스)
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  [주문 생성]                                                 │
 * │  OrderService.createOrder()                                 │
 * │       │                                                     │
 * │       ▼  PUBLISH: order.created                            │
 * │  ┌─────────────┐     ┌───────────────────┐                 │
 * │  │ Order Svc   │────▶│  Product Svc      │                 │
 * │  │ (Orchest.)  │     │  재고 차감 처리    │                 │
 * │  └─────────────┘     └───────────────────┘                 │
 * │       ▲                      │                             │
 * │       │  PUBLISH: stock.decreased (성공)                   │
 * │       │           stock.failed    (실패 → 보상)            │
 * │       │                                                     │
 * │  handleStockResult() → 주문 CONFIRMED / CANCELLED          │
 * └─────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    // Kafka 토픽 이름 상수
    public static final String TOPIC_ORDER_CREATED   = "order.created";
    public static final String TOPIC_ORDER_CONFIRMED = "order.confirmed";
    public static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    /**
     * Saga 1단계: 주문 생성 후 재고 차감 요청 이벤트 발행
     * Order Service → Kafka(order.created) → Product Service
     */
    public void startOrderSaga(Order order) {
        OrderSagaEvent.OrderCreated event = new OrderSagaEvent.OrderCreated(
            order.getId(),
            order.getUserId(),
            order.getProductId(),
            order.getQuantity(),
            LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC_ORDER_CREATED, String.valueOf(order.getId()), event);
        log.info("[SAGA] 1단계 시작 - order.created 발행: orderId={}, productId={}, qty={}",
            order.getId(), order.getProductId(), order.getQuantity());
    }

    /**
     * Saga 2단계: Product Service의 재고 처리 결과 수신
     *
     * - stock.decreased  → 주문 CONFIRMED (정상 완료)
     * - stock.failed     → 주문 CANCELLED (보상 트랜잭션)
     */
    @KafkaListener(topics = {"stock.decreased", "stock.failed"}, groupId = "order-saga-group")
    @Transactional
    public void handleStockResult(Map<String, Object> payload) {
        log.info("[SAGA] 재고 처리 결과 수신: {}", payload);

        try {
            Long orderId = Long.valueOf(payload.get("orderId").toString());
            boolean isSuccess = payload.containsKey("message")
                && !payload.containsKey("reason");  // reason 없으면 성공

            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Saga 처리 중 주문 없음: " + orderId));

            if (isSuccess) {
                // ── 정상: 주문 확정 ─────────────────────────────
                order.confirm();
                log.info("[SAGA] 2단계 성공 - 주문 CONFIRMED: orderId={}", orderId);

                OrderSagaEvent.OrderConfirmed confirmed = new OrderSagaEvent.OrderConfirmed(
                    orderId, order.getUserId(), LocalDateTime.now()
                );
                kafkaTemplate.send(TOPIC_ORDER_CONFIRMED, String.valueOf(orderId), confirmed);

            } else {
                // ── 실패: 보상 트랜잭션 - 주문 취소 ────────────
                String reason = payload.getOrDefault("reason", "재고 부족").toString();
                order.cancel();
                log.warn("[SAGA] 2단계 실패 - 보상 트랜잭션 실행 (주문 CANCELLED): orderId={}, reason={}",
                    orderId, reason);

                OrderSagaEvent.OrderCancelled cancelled = new OrderSagaEvent.OrderCancelled(
                    orderId, order.getUserId(), reason, LocalDateTime.now()
                );
                kafkaTemplate.send(TOPIC_ORDER_CANCELLED, String.valueOf(orderId), cancelled);
            }

        } catch (Exception e) {
            log.error("[SAGA] 재고 결과 처리 실패: {}", e.getMessage(), e);
        }
    }
}
