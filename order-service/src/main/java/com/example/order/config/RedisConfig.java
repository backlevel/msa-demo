package com.example.order.config;

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
@EnableCaching
public class RedisConfig {

    /**
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

    private RedisCacheConfiguration defaultCacheConfig() {
        // 내부에서 전용 Mapper를 생성하여 Serializer에 주입
        ObjectMapper mapper = createRedisObjectMapper();

        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
    }

    /**
     * orders         : 주문 단건        → TTL 5분
     * orders-by-user : 유저별 주문 목록 → TTL 3분 (자주 바뀜)
     */
    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory factory) {

        RedisCacheConfiguration base = defaultCacheConfig();

        Map<String, RedisCacheConfiguration> configs = Map.of(
            "orders",         base.entryTtl(Duration.ofMinutes(5)),
            "orders-by-user", base.entryTtl(Duration.ofMinutes(3))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(base)
            .withInitialCacheConfigurations(configs)
            .build();
    }
}
