FROM maven:3.9.8-eclipse-temurin-21-jammy AS build

# Set the working directory in the build image
WORKDIR /app

# Copy the entire project to the Docker container
COPY . .

# Build only the `discovery-server` module and package it as a JAR
RUN mvn clean install -pl discovery-server -am -DskipTests

# Stage 2: Create the runtime image with OpenJDK and application JAR
FROM eclipse-temurin:21-jdk-jammy

# Install necessary packages
RUN apt-get update && apt-get install -y net-tools iputils-ping && apt-get install -y openssl ca-certificates && rm -rf /var/lib/apt/lists/*

# Set the working directory in the runtime image
WORKDIR /app

# Copy the application JAR from the build image
COPY --from=build /app/discovery-server/target/DiscoveryService.jar DiscoveryService.jar

# Copy keystore and truststore
COPY keys/eureka-keystore-container.jks eureka-keystore.jks
COPY keys/eureka-truststore.jks eureka-truststore.jks

# Set permissions for the keystore and truststore files
RUN chmod 644 /app/eureka-truststore.jks /app/eureka-keystore.jks

# Expose the secure port for Eureka
EXPOSE 8761

# Note: Certificate import into Java cacerts is not needed because:
# 1. Spring Boot uses the custom truststore directly via application.yml configuration
# 2. SSL configuration (keystore/truststore) is handled by Spring Boot using vault secrets at runtime
# 3. This eliminates the need for build-time password (no hardcoded secrets!)

# Run the Eureka server
# SSL configuration is handled by Spring Boot via application.yml (using vault secrets)
# No need for Java system properties - Spring Boot reads from vault at runtime
ENTRYPOINT ["java", "-Dhttps.protocols=TLSv1.2,TLSv1.3", "-jar", "DiscoveryService.jar"]
