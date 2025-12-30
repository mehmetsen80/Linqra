package org.lite.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

@Configuration
@Slf4j
public class Neo4jConfig {

    @Value("${neo4j.uri}")
    private String neo4jUri;

    @Value("${neo4j.username}")
    private String neo4jUsername;

    @Value("${neo4j.password}")
    private String neo4jPassword;

    private Driver driverInstance;

    @Bean
    public Driver neo4jDriver() {
        // Validate and normalize URI - Neo4j Java driver requires bolt:// or neo4j://
        // protocols
        if (neo4jUri != null && neo4jUri.startsWith("http://")) {
            log.warn(
                    "Neo4j URI uses http:// scheme, converting to bolt://. Use bolt:// or neo4j:// for the Java driver (http:// is for Browser UI)");
            neo4jUri = neo4jUri.replace("http://", "bolt://");
        } else if (neo4jUri != null && neo4jUri.startsWith("https://")) {
            log.warn(
                    "Neo4j URI uses https:// scheme, converting to neo4j+s://. Use bolt:// or neo4j:// for the Java driver");
            neo4jUri = neo4jUri.replace("https://", "neo4j+s://");
        }

        if (neo4jUri == null || (!neo4jUri.startsWith("bolt://") && !neo4jUri.startsWith("neo4j://")
                && !neo4jUri.startsWith("neo4j+s://") && !neo4jUri.startsWith("neo4j+ssc://"))) {
            throw new IllegalArgumentException(
                    "Neo4j URI must use a supported protocol (bolt://, neo4j://, neo4j+s://). Found: " + neo4jUri);
        }

        log.info("Initializing Neo4j driver with URI: {}", neo4jUri);
        log.info("Neo4j username: {}, password configured: {}", neo4jUsername,
                neo4jPassword != null && !neo4jPassword.isEmpty());

        try {
            // Neo4j requires authentication (configured in docker-compose)
            if (neo4jUsername == null || neo4jUsername.isEmpty() || neo4jPassword == null || neo4jPassword.isEmpty()) {
                log.error(
                        "Neo4j username or password is required but not provided. Please set NEO4J_USERNAME and NEO4J_PASSWORD environment variables");
                throw new RuntimeException("Neo4j authentication credentials are required");
            }

            driverInstance = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUsername, neo4jPassword));
            log.info("Using Neo4j authentication with username: {}", neo4jUsername);

            // Verify connection
            try (var session = driverInstance.session()) {
                session.run("RETURN 1").consume();
                log.info("Successfully connected to Neo4j at {}", neo4jUri);
            }

            return driverInstance;
        } catch (Exception e) {
            log.error("Failed to initialize Neo4j driver: {}", e.getMessage(), e);
            throw new RuntimeException("Neo4j initialization failed", e);
        }
    }

    @PreDestroy
    public void closeDriver() {
        if (driverInstance != null) {
            log.info("Closing Neo4j driver connection");
            driverInstance.close();
        }
    }
}
