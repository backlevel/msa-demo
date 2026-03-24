package com.example.order.dto;

import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

// ── 요청 DTO ────────────────────────────────────────────────
public class OrderDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private Long userId;
        private Long productId;
        private Integer quantity;
    }

    // ── 응답 DTO ──────────────────────────────────────────────
    @Getter
    @NoArgsConstructor   // Redis 역직렬화에 필요
    @AllArgsConstructor
    public static class Response implements Serializable {
        private Long id;
        private Long userId;
        private String userName;
        private Long productId;
        private String productName;
        private Integer quantity;
        private OrderStatus status;
        private LocalDateTime createdAt;

        public static Response of(Order order, String userName, String productName) {
            return new Response(
                order.getId(),
                order.getUserId(),
                userName,
                order.getProductId(),
                productName,
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
            );
        }
    }

    // ── 외부 서비스 응답 DTO ────────────────────────────────────
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String name;
        private String email;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductInfo {
        private Long id;
        private String name;
        private Integer stock;
    }
}
