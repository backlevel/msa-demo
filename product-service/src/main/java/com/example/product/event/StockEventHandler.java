package com.example.product.event;

import com.example.product.domain.Product;
import com.example.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Product Service Saga 참여자 (Participant)
 *
 * 1. order.created 이벤트 수신
 * 2. 재고 차감 시도
 * 3. 성공 → stock.decreased 발행
 *    실패 → stock.failed   발행  (Order Service가 보상 트랜잭션 실행)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventHandler {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_STOCK_DECREASED = "stock.decreased";
    private static final String TOPIC_STOCK_FAILED    = "stock.failed";

    @KafkaListener(topics = "order.created", groupId = "product-saga-group")
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products",     key = "#payload['productId']", condition = "#payload['productId'] != null"),
        @CacheEvict(value = "products-all", allEntries = true)
    })
    public void handleOrderCreated(Map<String, Object> payload) {
        Long orderId   = Long.valueOf(payload.get("orderId").toString());
        Long productId = Long.valueOf(payload.get("productId").toString());
        int  quantity  = Integer.parseInt(payload.get("quantity").toString());

        log.info("[Stock] order.created 수신 - orderId={}, productId={}, qty={}", orderId, productId, quantity);

        productRepository.findById(productId).ifPresentOrElse(
            product -> {
                if (product.getStock() >= quantity) {
                    // ── 성공: 재고 차감 후 stock.decreased 발행 ──────────
                    product.decreaseStock(quantity);
                    log.info("[Stock] 재고 차감 성공 - productId={}, 남은재고={}", productId, product.getStock());

                    Map<String, Object> success = new HashMap<>();
                    success.put("orderId",   orderId);
                    success.put("productId", productId);
                    success.put("quantity",  quantity);
                    success.put("message",   "재고 차감 성공");
                    kafkaTemplate.send(TOPIC_STOCK_DECREASED, String.valueOf(orderId), success);

                } else {
                    // ── 실패: 재고 부족 → stock.failed 발행 ─────────────
                    String reason = String.format("재고 부족 (요청=%d, 현재=%d)", quantity, product.getStock());
                    log.warn("[Stock] 재고 부족 - productId={}, {}", productId, reason);
                    publishFailure(orderId, productId, reason);
                }
            },
            () -> {
                // ── 실패: 상품 없음 ──────────────────────────────────
                String reason = "상품을 찾을 수 없음 id=" + productId;
                log.warn("[Stock] 상품 없음 - {}", reason);
                publishFailure(orderId, productId, reason);
            }
        );
    }

    private void publishFailure(Long orderId, Long productId, String reason) {
        Map<String, Object> failure = new HashMap<>();
        failure.put("orderId",   orderId);
        failure.put("productId", productId);
        failure.put("reason",    reason);
        failure.put("failedAt",  LocalDateTime.now().toString());
        kafkaTemplate.send(TOPIC_STOCK_FAILED, String.valueOf(orderId), failure);
    }
}
