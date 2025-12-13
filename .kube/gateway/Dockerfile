# Dockerfile for api-gateway

# Stage 1: Build the application using Maven
FROM maven:3.9.8-eclipse-temurin-21-jammy AS build

# Set the working directory in the build image
WORKDIR /app

# Copy the entire project to the Docker container
COPY . .

# Build only the `api-gateway` module and package it as a JAR
RUN mvn clean install -pl api-gateway -am -DskipTests

# Stage 2: Create the runtime image with OpenJDK and application JAR
FROM eclipse-temurin:21-jdk-jammy

# Install necessary packages
RUN apt-get update && apt-get install -y net-tools iputils-ping && apt-get install -y openssl ca-certificates && rm -rf /var/lib/apt/lists/*

# Set the working directory in the runtime image
WORKDIR /app

# Copy the application JAR from the build image
COPY --from=build /app/api-gateway/target/LiteGateway.jar LiteGateway.jar

# Copy the keystore and truststore files
COPY keys/gateway-keystore-container.jks gateway-keystore.jks
COPY keys/gateway-truststore.jks gateway-truststore.jks

# Set permissions for keystore and truststore files
RUN chmod 644 /app/gateway-truststore.jks /app/gateway-keystore.jks

# Copy vault-reader.jar and entrypoint script for reading secrets from vault
RUN mkdir -p /app/vault-reader
COPY vault-reader/target/vault-reader.jar /app/vault-reader/vault-reader.jar
COPY scripts/vault-entrypoint.sh /app/vault-entrypoint.sh
RUN chmod +x /app/vault-entrypoint.sh

# Expose the API gateway port
EXPOSE 7777

# Note: Certificate import into Java cacerts is not needed because:
# 1. Spring Boot uses the custom truststore directly via application.yml configuration
# 2. SSL configuration (keystore/truststore) is handled by Spring Boot using vault secrets at runtime
# 3. All service URLs (Redis, Keycloak, Milvus, etc.) come from vault via application.yml
# 4. This eliminates the need for hardcoded secrets and environment variables!

# Run the API gateway-service application with memory management
# vault-entrypoint.sh reads secrets from vault and exports them, then detects Java command
# For API Gateway, vault-entrypoint.sh injects Java SSL system properties for Eureka Client
# The Eureka Client uses Java's default SSL context, so we need javax.net.ssl.trustStore system properties
ENTRYPOINT ["/app/vault-entrypoint.sh", "java", \
            "-Xms1g", \
            "-Xmx2g", \
            "-XX:+UseG1GC", \
            "-XX:MaxGCPauseMillis=200", \
            "-XX:+UnlockExperimentalVMOptions", \
            "-XX:+UseStringDeduplication", \
            "-Dhttps.protocols=TLSv1.2,TLSv1.3", \
            "-Djava.net.preferIPv4Stack=true", \
            "-Djava.awt.headless=true", \
            "-Dfile.encoding=UTF-8", \
            "-Dsun.net.client.defaultConnectTimeout=5000", \
            "-Dsun.net.client.defaultReadTimeout=30000", \
            "-jar", "LiteGateway.jar"]