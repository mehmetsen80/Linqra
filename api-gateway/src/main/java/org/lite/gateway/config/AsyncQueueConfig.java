package org.lite.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.lite.gateway.dto.LinqResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

@Configuration
@EnableScheduling
@Slf4j
public class AsyncQueueConfig {
    
    @Bean
    @Qualifier("asyncStepStatusRedisTemplate")
    public ReactiveRedisTemplate<String, LinqResponse.AsyncStepStatus> asyncStepStatusRedisTemplate(
            @Qualifier("redisConnectionFactory") ReactiveRedisConnectionFactory connectionFactory) {
        log.info("Initializing asyncStepStatusRedisTemplate");
        
        // Configure ObjectMapper with Java Time API support
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Create serializer with ObjectMapper in constructor
        Jackson2JsonRedisSerializer<LinqResponse.AsyncStepStatus> serializer = 
            new Jackson2JsonRedisSerializer<>(mapper, LinqResponse.AsyncStepStatus.class);
        
        RedisSerializationContext.RedisSerializationContextBuilder<String, LinqResponse.AsyncStepStatus> builder =
            RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        
        RedisSerializationContext<String, LinqResponse.AsyncStepStatus> context = 
            builder.value(serializer).build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    @Bean
    @Qualifier("asyncStepQueueRedisTemplate")
    public ReactiveRedisTemplate<String, String> asyncStepQueueRedisTemplate(
            @Qualifier("redisConnectionFactory") ReactiveRedisConnectionFactory connectionFactory) {
        log.info("Initializing asyncStepQueueRedisTemplate");
        RedisSerializationContext.RedisSerializationContextBuilder<String, String> builder =
            RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        
        RedisSerializationContext<String, String> context = 
            builder.value(new StringRedisSerializer()).build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
} 