package org.lite.gateway.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.lang.NonNull;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
@EnableReactiveMongoRepositories(basePackages = "org.lite.gateway.repository")
@EnableReactiveMongoAuditing
@Slf4j
public class MongoReactiveConfig extends AbstractReactiveMongoConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database}")
    private String databaseName;

    @Override
    protected @NonNull String getDatabaseName() {
        return databaseName;
    }

    @Autowired
    private org.springframework.core.env.Environment env;

    @Override
    @Bean
    public @NonNull MongoClient reactiveMongoClient() {
        if (env.matchesProfiles("remote-dev")) {
            return createRemoteDevMongoClient();
        }
        return MongoClients.create(mongoUri);
    }

    private MongoClient createRemoteDevMongoClient() {
        log.warn("⚠️ REMOTE-DEV PROFILE ACTIVE: Bypassing MongoDB SSL Certificate Validation");
        try {
            // Create a trust manager that does not validate certificate chains
            final javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // Install the all-trusting trust manager
            final javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            com.mongodb.ConnectionString connectionString = new com.mongodb.ConnectionString(mongoUri);

            com.mongodb.MongoClientSettings settings = com.mongodb.MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .applyToSslSettings(builder -> {
                        builder.enabled(true);
                        builder.invalidHostNameAllowed(true);
                        builder.context(sslContext);
                    })
                    .build();

            return MongoClients.create(settings);
        } catch (Exception e) {
            log.error("Failed to configure SSL bypass for remote-dev", e);
            throw new RuntimeException("Could not configure MongoDB SSL bypass", e);
        }
    }

    @Bean
    public @NonNull ReactiveMongoTemplate reactiveMongoTemplate() {
        ReactiveMongoTemplate template = new ReactiveMongoTemplate(reactiveMongoClient(), getDatabaseName());
        MappingMongoConverter converter = (MappingMongoConverter) template.getConverter();
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        converter.setMapKeyDotReplacement("_");
        return template;
    }

    // Very important, do not remove this, otherwise the HealthCheckConfig "save"
    // throws error although it really saves
    @Bean
    @Override
    public @NonNull MappingMongoConverter mappingMongoConverter(
            @NonNull ReactiveMongoDatabaseFactory databaseFactory,
            @NonNull MongoCustomConversions customConversions,
            @NonNull MongoMappingContext mappingContext) {

        MappingMongoConverter converter = super.mappingMongoConverter(databaseFactory, customConversions,
                mappingContext);
        converter.setMapKeyDotReplacement("_"); // Replace dots with underscores in map keys
        return converter;
    }

    @Bean
    public ReactiveTransactionManager transactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory);
    }

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}