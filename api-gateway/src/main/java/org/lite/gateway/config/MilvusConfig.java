package org.lite.gateway.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MilvusConfig {

    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private int milvusPort;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("Initializing Milvus client with host: {} and port: {}", milvusHost, milvusPort);
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(milvusHost)
                    .withPort(milvusPort)
                    .withDatabaseName("default")
                    .build();
            MilvusServiceClient client = new MilvusServiceClient(connectParam);
            log.info("Successfully connected to Milvus at {}:{} with database 'default'", milvusHost, milvusPort);
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize Milvus client: {}", e.getMessage(), e);
            throw new RuntimeException("Milvus initialization failed", e);
        }
    }
} 