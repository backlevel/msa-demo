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

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    private RedisCacheConfiguration defaultCacheConfig(ObjectMapper mapper) {
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
            RedisConnectionFactory factory,
            ObjectMapper redisObjectMapper) {

        RedisCacheConfiguration base = defaultCacheConfig(redisObjectMapper);

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
