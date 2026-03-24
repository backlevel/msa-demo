package com.example.product.service;

import com.example.product.domain.Product;
import com.example.product.domain.ProductRepository;
import com.example.product.dto.ProductDto;
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
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 상품 생성
     * - 전체 목록 캐시(products-all) 무효화
     */
    @Transactional
    @CacheEvict(value = "products-all", allEntries = true)
    public ProductDto.Response createProduct(ProductDto.CreateRequest request) {
        log.info("[Cache] products-all 캐시 삭제 - 신규 상품 등록");
        Product product = Product.create(request.getName(), request.getPrice(), request.getStock());
        return ProductDto.Response.from(productRepository.save(product));
    }

    /**
     * 상품 단건 조회
     * - 캐시 히트: Redis에서 즉시 반환 (DB 조회 없음)
     * - 캐시 미스: DB 조회 후 "products::{id}" 키로 저장 → TTL 10분
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public ProductDto.Response getProduct(Long id) {
        log.info("[Cache MISS] products::{} - DB에서 조회", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("상품을 찾을 수 없습니다. id=" + id));
        return ProductDto.Response.from(product);
    }

    /**
     * 전체 상품 조회
     * - "products-all::all" 단일 키로 전체 목록 캐싱 → TTL 5분
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "products-all", key = "'all'")
    public List<ProductDto.Response> getAllProducts() {
        log.info("[Cache MISS] products-all::all - DB에서 전체 조회");
        return productRepository.findAll().stream()
                .map(ProductDto.Response::from)
                .collect(Collectors.toList());
    }

    /**
     * 재고 차감
     * - 단건 캐시(products::{id}) + 전체 목록 캐시(products-all) 동시 무효화
     * - 재고가 바뀌면 캐시된 값이 틀려지므로 반드시 evict
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products",     key = "#id"),
        @CacheEvict(value = "products-all", allEntries = true)
    })
    public ProductDto.Response decreaseStock(Long id, int quantity) {
        log.info("[Cache] products::{} + products-all 캐시 삭제 - 재고 차감", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("상품을 찾을 수 없습니다. id=" + id));
        product.decreaseStock(quantity);
        return ProductDto.Response.from(product);
    }
}
