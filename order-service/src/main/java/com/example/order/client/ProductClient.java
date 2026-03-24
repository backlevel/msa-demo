package com.example.order.client;

import com.example.order.dto.OrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "PRODUCT-SERVICE",
    fallback = ProductClientFallback.class
)
public interface ProductClient {

    @GetMapping("/products/{id}")
    OrderDto.ProductInfo getProductById(@PathVariable("id") Long id);
}
