package com.example.order.client;

import com.example.order.dto.OrderDto;
import org.springframework.stereotype.Component;

/**
 * User Service 장애 시 Fallback 응답
 * - 서킷브레이커가 열리거나 타임아웃 발생 시 호출됨
 */
@Component
public class UserClientFallback implements UserClient {

    @Override
    public OrderDto.UserInfo getUserById(Long id) {
        // 장애 시 기본값 반환 (서비스 중단 방지)
        return new OrderDto.UserInfo(id, "Unknown User", "N/A");
    }
}
