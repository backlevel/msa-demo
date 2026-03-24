package com.example.product.dto;

import com.example.product.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.io.Serializable;

public class ProductDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String name;
        private Integer price;
        private Integer stock;
    }

    @Getter
    @NoArgsConstructor   // Redis 역직렬화에 필요
    @AllArgsConstructor
    public static class Response implements Serializable {
        private Long id;
        private String name;
        private Integer price;
        private Integer stock;

        public static Response from(Product product) {
            return new Response(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock()
            );
        }
    }
}
