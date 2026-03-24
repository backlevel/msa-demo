package com.example.order.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Saga 패턴에서 사용되는 Kafka 이벤트 클래스 모음
 *
 * 흐름:
 * 1. Order Service  → [order.created]       → Product Service
 * 2. Product Service → [stock.decreased]    → Order Service  (성공)
 *    Product Service → [stock.failed]       → Order Service  (실패)
 * 3. Order Service  → [order.confirmed]     → (완료)
 *    Order Service  → [order.cancelled]     → (롤백 완료)
 */
public class OrderSagaEvent {

    // ── 1단계: Order Service → Product Service ──────────────────
    /** 주문 생성 이벤트 - 재고 차감 요청 */
    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class OrderCreated implements Serializable {
        private Long orderId;
        private Long userId;
        private Long productId;
        private Integer quantity;
        private LocalDateTime createdAt;
    }

    // ── 2단계: Product Service → Order Service ──────────────────
    /** 재고 차감 성공 이벤트 */
    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class StockDecreased implements Serializable {
        private Long orderId;
        private Long productId;
        private Integer quantity;
        private String message;
    }

    /** 재고 차감 실패 이벤트 (보상 트랜잭션 트리거) */
    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class StockDecreaseFailed implements Serializable {
        private Long orderId;
        private Long productId;
        private String reason;      // 실패 원인 (재고 부족 등)
        private LocalDateTime failedAt;
    }

    // ── 3단계: Order Service → 완료 ──────────────────────────────
    /** 주문 확정 완료 이벤트 */
    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class OrderConfirmed implements Serializable {
        private Long orderId;
        private Long userId;
        private LocalDateTime confirmedAt;
    }

    /** 주문 취소(보상) 완료 이벤트 */
    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class OrderCancelled implements Serializable {
        private Long orderId;
        private Long userId;
        private String reason;
        private LocalDateTime cancelledAt;
    }
}
