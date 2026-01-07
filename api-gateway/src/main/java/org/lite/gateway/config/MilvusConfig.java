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

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Value("${milvus.username:#{null}}")
    private String milvusUsername;

    @Value("${milvus.password:#{null}}")
    private String milvusPassword;

    @Value("${milvus.uri:#{null}}")
    private String milvusUri;

    @Value("${milvus.token:#{null}}")
    private String milvusToken;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        try {
            ConnectParam.Builder builder = ConnectParam.newBuilder();

            if (milvusUri != null && !milvusUri.isBlank()) {
                // Cloud Mode (URI-based)
                log.info("Initializing Milvus client with URI: {}", milvusUri);
                builder.withUri(milvusUri);

                // Use Token if provided (Preferred for Cloud)
                if (milvusToken != null && !milvusToken.isBlank()) {
                    builder.withToken(milvusToken);
                    log.info("Using Milvus token authentication");
                }
                // Fallback to User/Pass if Token missing but User/Pass present
                else if (milvusUsername != null && !milvusUsername.isBlank()) {
                    builder.withAuthorization(milvusUsername, milvusPassword);
                    log.info("Using Milvus username/password authentication for Cloud");
                }
            } else {
                // Local Mode (Host/Port-based)
                log.info("Initializing Milvus client with host: {} and port: {}", milvusHost, milvusPort);
                builder.withHost(milvusHost)
                        .withPort(milvusPort);

                if (milvusUsername != null && !milvusUsername.isBlank()) {
                    builder.withAuthorization(milvusUsername, milvusPassword);
                    log.info("Using Milvus username/password authentication");
                }
            }

            // Explicitly set database name to empty string to avoid "default" fallback
            // which causes "Collection Not Found" on Milvus Cloud (Cluster mode)
            builder.withDatabaseName("");

            MilvusServiceClient client = new MilvusServiceClient(builder.build());
            log.info("Successfully connected to Milvus");
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize Milvus client: {}", e.getMessage(), e);
            throw new RuntimeException("Milvus initialization failed", e);
        }
    }
}