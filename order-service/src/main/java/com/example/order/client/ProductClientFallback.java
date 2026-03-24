package com.example.order.client;

import com.example.order.dto.OrderDto;
import org.springframework.stereotype.Component;

@Component
public class ProductClientFallback implements ProductClient {

    @Override
    public OrderDto.ProductInfo getProductById(Long id) {
        return new OrderDto.ProductInfo(id, "Unknown Product", 0);
    }
}
