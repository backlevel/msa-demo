package com.example.product.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer stock;

    public static Product create(String name, Integer price, Integer stock) {
        Product p = new Product();
        p.name = name;
        p.price = price;
        p.stock = stock;
        return p;
    }

    public void decreaseStock(int quantity) {
        if (this.stock < quantity) throw new IllegalStateException("재고 부족");
        this.stock -= quantity;
    }
}
