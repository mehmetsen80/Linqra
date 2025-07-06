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

    @Value("${milvus.username:}")
    private String milvusUsername;

    @Value("${milvus.password:}")
    private String milvusPassword;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("Initializing Milvus client with host: {} and port: {}", milvusHost, milvusPort);
        try {
            ConnectParam.Builder builder = ConnectParam.newBuilder()
                    .withHost(milvusHost)
                    .withPort(milvusPort)
                    .withDatabaseName("default");
            
            // Add authentication if username and password are provided
            if (milvusUsername != null && !milvusUsername.isEmpty()) {
                builder.withAuthorization(milvusUsername, milvusPassword);
                log.info("Using Milvus authentication with username: {}", milvusUsername);
            } else {
                log.info("Connecting to Milvus without authentication");
            }
            
            MilvusServiceClient client = new MilvusServiceClient(builder.build());
            log.info("Successfully connected to Milvus at {}:{} with database 'default'", milvusHost, milvusPort);
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize Milvus client: {}", e.getMessage(), e);
            throw new RuntimeException("Milvus initialization failed", e);
        }
    }
} 