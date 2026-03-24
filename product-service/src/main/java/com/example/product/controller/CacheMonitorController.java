package com.example.product.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 캐시 상태를 실시간으로 확인하는 모니터링 API
 * 포트폴리오에서 캐시 히트/미스 시연 시 활용
 */
@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheMonitorController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    /**
     * GET /cache/keys - 현재 Redis에 저장된 캐시 키 전체 목록
     */
    @GetMapping("/keys")
    public ResponseEntity<Map<String, Object>> getCacheKeys() {
        Set<String> keys = redisTemplate.keys("*");
        Map<String, Object> result = new HashMap<>();
        result.put("total", keys == null ? 0 : keys.size());
        result.put("keys", keys);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /cache/keys/{pattern} - 패턴으로 키 검색
     * 예) /cache/keys/products* → products 관련 키만
     */
    @GetMapping("/keys/{pattern}")
    public ResponseEntity<Map<String, Object>> getCacheKeysByPattern(@PathVariable String pattern) {
        Set<String> keys = redisTemplate.keys(pattern + "*");
        Map<String, Object> result = new HashMap<>();
        result.put("pattern", pattern + "*");
        result.put("total", keys == null ? 0 : keys.size());

        // TTL 포함하여 반환
        Map<String, Long> keysWithTtl = new HashMap<>();
        if (keys != null) {
            keys.forEach(key -> {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                keysWithTtl.put(key, ttl);
            });
        }
        result.put("keysWithTtl", keysWithTtl);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /cache/stats - 캐시 이름별 등록 현황
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        cacheManager.getCacheNames().forEach(cacheName -> {
            Set<String> keys = redisTemplate.keys(cacheName + "*");
            stats.put(cacheName, Map.of(
                "keyCount", keys == null ? 0 : keys.size()
            ));
        });
        return ResponseEntity.ok(stats);
    }

    /**
     * DELETE /cache/evict/{cacheName} - 특정 캐시 전체 삭제
     * 예) DELETE /cache/evict/products
     */
    @DeleteMapping("/evict/{cacheName}")
    public ResponseEntity<Map<String, Object>> evictCache(@PathVariable String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        Map<String, Object> result = new HashMap<>();
        if (cache != null) {
            cache.clear();
            result.put("status", "cleared");
            result.put("cacheName", cacheName);
        } else {
            result.put("status", "not found");
            result.put("cacheName", cacheName);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /cache/evict-all - 전체 Redis 캐시 초기화 (개발용)
     */
    @DeleteMapping("/evict-all")
    public ResponseEntity<Map<String, String>> evictAll() {
        cacheManager.getCacheNames()
            .forEach(name -> {
                var cache = cacheManager.getCache(name);
                if (cache != null) cache.clear();
            });
        return ResponseEntity.ok(Map.of("status", "all caches cleared"));
    }
}
