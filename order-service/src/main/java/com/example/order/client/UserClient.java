package com.example.order.client;

import com.example.order.dto.OrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * User Service 호출용 FeignClient
 * - name: Eureka에 등록된 서비스명
 * - fallback: User Service 장애 시 대체 응답
 */
@FeignClient(
    name = "USER-SERVICE",
    fallback = UserClientFallback.class
)
public interface UserClient {

    @GetMapping("/users/{id}")
    OrderDto.UserInfo getUserById(@PathVariable("id") Long id);
}
