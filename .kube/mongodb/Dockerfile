FROM mongo:8.0.13

# Install Java (needed for vault-reader.jar) - using JDK 21 to match other services
RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    rm -rf /var/lib/apt/lists/*

# Create directory for vault-reader
RUN mkdir -p /app/vault-reader

# Copy vault-reader.jar and entrypoint script (will be mounted at runtime)
COPY vault-reader/target/vault-reader.jar /app/vault-reader/vault-reader.jar
COPY scripts/vault-entrypoint.sh /app/vault-entrypoint.sh
RUN chmod +x /app/vault-entrypoint.sh

# Use vault-entrypoint as entrypoint (it will read secrets and then run mongod)
ENTRYPOINT ["/app/vault-entrypoint.sh"]