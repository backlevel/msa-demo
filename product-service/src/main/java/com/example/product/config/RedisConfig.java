package com.example.product.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching  // 캐시 기능 활성화
public class RedisConfig {

    /**
     * 직렬화에 쓸 ObjectMapper
     * - activateDefaultTyping: 역직렬화 시 정확한 타입 복원을 위해 타입 정보 포함
     * API용 기본 ObjectMapper와 충돌하지 않도록 @Bean을 제거하고
     * Redis 직렬화 전용으로만 사용합니다.
     */
    private ObjectMapper createRedisObjectMapper()  {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    /**
     * 기본 캐시 설정
     * - Key  : String 직렬화
     * - Value: JSON 직렬화 (타입 정보 포함)
     * - TTL  : 10분 (기본값)
     * - null 값은 캐싱하지 않음
     */
    private RedisCacheConfiguration defaultCacheConfig() {
        // 내부에서 전용 Mapper를 생성하여 Serializer에 주입
        ObjectMapper mapper = createRedisObjectMapper();

        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(mapper);

        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(jsonSerializer));
    }

    /**
     * CacheManager - 캐시명별 TTL 개별 설정
     *
     * products         : 상품 단건   → TTL 10분
     * products-all     : 전체 상품   → TTL 5분  (자주 바뀔 수 있음)
     */
    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory factory) {

        RedisCacheConfiguration base = defaultCacheConfig();

        Map<String, RedisCacheConfiguration> configs = Map.of(
            "products",     base.entryTtl(Duration.ofMinutes(10)),
            "products-all", base.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(base)
            .withInitialCacheConfigurations(configs)
            .build();
    }

    /**
     * RedisTemplate - 직접 키/TTL 조회용 (CacheMonitorController에서 사용)
     */
    @Bean
    public org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory factory,
            ObjectMapper redisObjectMapper) {

        var template = new org.springframework.data.redis.core.RedisTemplate<String, Object>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper));
        template.afterPropertiesSet();
        return template;
    }
}
