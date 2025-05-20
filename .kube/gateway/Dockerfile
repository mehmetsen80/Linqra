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

# Set environment variables for keystore and truststore
ENV GATEWAY_KEY_STORE=/app/gateway-keystore.jks
ENV GATEWAY_KEY_STORE_PASSWORD=123456
ENV GATEWAY_TRUST_STORE=/app/gateway-truststore.jks
ENV GATEWAY_TRUST_STORE_PASSWORD=123456
ENV CACERTS_PATH=${JAVA_HOME}/lib/security/cacerts

# Verify truststore exists
RUN if [ ! -f "${GATEWAY_TRUST_STORE}" ]; then \
        echo "client-truststore.jks not found at ${GATEWAY_TRUST_STORE}"; \
        exit 1; \
    fi

# Verify cacerts file exists and is writable
RUN if [ ! -f "${CACERTS_PATH}" ]; then \
        echo "cacerts file not found at ${CACERTS_PATH}"; \
        if [ -d "${CACERTS_PATH}" ]; then \
            echo "cacerts is a directory, listing contents:"; \
            ls -ltr "${CACERTS_PATH}"; \
            exit 1; \
        fi; \
        echo "Attempting to restore cacerts from base image"; \
        cp /opt/java/openjdk/lib/security/cacerts.bak "${CACERTS_PATH}" 2>/dev/null || { \
            echo "Failed to restore cacerts. Ensure base image contains cacerts."; \
            exit 1; \
        }; \
    fi && \
    chown root:root "${CACERTS_PATH}" && \
    chmod 664 "${CACERTS_PATH}"


RUN chown root:root "${CACERTS_PATH}" && chmod 664 "${CACERTS_PATH}"

# Import all certificates from gateway-truststore.jks into the default cacerts truststore
RUN keytool -importkeystore -srckeystore /app/gateway-truststore.jks \
            -destkeystore "${CACERTS_PATH}" \
            -srcstorepass "${GATEWAY_TRUST_STORE_PASSWORD}" \
            -deststorepass "${GATEWAY_TRUST_STORE_PASSWORD}" \
            -noprompt -srcstoretype JKS -deststoretype JKS > keytool_import.log 2>&1 || { \
                cat keytool_import.log; \
                echo "keytool import failed"; \
                exit 1; \
            }


# Verify the import by listing the cacerts contents (optional, for debugging)
RUN keytool -list -cacerts -storepass "${GATEWAY_TRUST_STORE_PASSWORD}" > cacerts_list.log

# Set other service-specific environment variables
ENV REDIS_GATEWAY_URL=redis-service
ENV KEYCLOAK_GATEWAY_URL=keycloak-service
ENV KEYCLOAK_GATEWAY_PORT=8080
ENV EUREKA_GATEWAY_URL=discovery-service
ENV GATEWAY_API_HOST=api-gateway-service
ENV GATEWAY_API_PORT=7777

# Expose the API gateway port
EXPOSE 7777

# Run the API gateway-service application with SSL configuration
ENTRYPOINT ["java", \
            "-Djavax.net.debug=all", \
            "-Djavax.net.debug=ssl", \
            "-Dhttps.protocols=TLSv1.2,TLSv1.3", \
            "-Djavax.net.ssl.keyStore=${GATEWAY_KEY_STORE}", \
            "-Djavax.net.ssl.keyStorePassword=${GATEWAY_KEY_STORE_PASSWORD}", \
            "-Djavax.net.ssl.trustStore=${CACERTS_PATH}", \
            "-Djavax.net.ssl.trustStorePassword=${GATEWAY_TRUST_STORE_PASSWORD}", \
            "-jar", "LiteGateway.jar"]