package com.example.order.service;

import com.example.order.client.ProductClient;
import com.example.order.client.UserClient;
import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.dto.OrderDto;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.saga.OrderSagaOrchestrator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final OrderSagaOrchestrator sagaOrchestrator;

    /**
     * 주문 생성 - Saga 패턴 시작점
     * [동기]  유저 확인(FeignClient) + 주문 저장 (PENDING)
     * [비동기] Kafka order.created → Product 재고 차감 → CONFIRMED/CANCELLED
     */
    @Transactional
    @CircuitBreaker(name = "order-service", fallbackMethod = "fallbackCreateOrder")
    @Retry(name = "order-service")
    @CacheEvict(value = "orders-by-user", key = "#request.userId")
    public OrderDto.Response createOrder(OrderDto.CreateRequest request) {
        log.info("[Order] 주문 생성 - userId={}, productId={}", request.getUserId(), request.getProductId());
        OrderDto.UserInfo user = userClient.getUserById(request.getUserId());
        Order order = Order.create(request.getUserId(), request.getProductId(), request.getQuantity());
        orderRepository.save(order);
        log.info("[Order] 저장 완료 orderId={} PENDING", order.getId());
        sagaOrchestrator.startOrderSaga(order);
        return OrderDto.Response.of(order, user.getName(), "처리 중");
    }

    public OrderDto.Response fallbackCreateOrder(OrderDto.CreateRequest request, Throwable t) {
        log.error("[Order] fallback - {}", t.getMessage());
        throw new RuntimeException("주문 처리 불가. 잠시 후 재시도 (" + t.getMessage() + ")");
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "orders", key = "#orderId")
    public OrderDto.Response getOrder(Long orderId) {
        log.info("[Cache MISS] orders::{}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("주문 없음 id=" + orderId));
        OrderDto.UserInfo    user = userClient.getUserById(order.getUserId());
        OrderDto.ProductInfo prod = productClient.getProductById(order.getProductId());
        return OrderDto.Response.of(order, user.getName(), prod.getName());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "orders-by-user", key = "#userId")
    public List<OrderDto.Response> getOrdersByUser(Long userId) {
        log.info("[Cache MISS] orders-by-user::{}", userId);
        return orderRepository.findByUserId(userId).stream()
                .map(o -> OrderDto.Response.of(o,
                        userClient.getUserById(o.getUserId()).getName(),
                        productClient.getProductById(o.getProductId()).getName()))
                .collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",         key = "#orderId"),
        @CacheEvict(value = "orders-by-user", allEntries = true)
    })
    public OrderDto.Response confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("주문 없음 id=" + orderId));
        order.confirm();
        return OrderDto.Response.of(order,
                userClient.getUserById(order.getUserId()).getName(),
                productClient.getProductById(order.getProductId()).getName());
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",         key = "#orderId"),
        @CacheEvict(value = "orders-by-user", allEntries = true)
    })
    public OrderDto.Response cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("주문 없음 id=" + orderId));
        order.cancel();
        return OrderDto.Response.of(order,
                userClient.getUserById(order.getUserId()).getName(),
                productClient.getProductById(order.getProductId()).getName());
    }
}
