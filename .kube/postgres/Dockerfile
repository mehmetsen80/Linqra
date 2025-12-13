FROM postgres:18

# Set custom data directory to avoid overlay filesystem issues
ENV PGDATA=/var/lib/postgresql/pgdata

# Install Java (needed for vault-reader.jar) - using JDK 21 to match other services
# Note: gosu is already available in postgres:18 image for user switching
RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    rm -rf /var/lib/apt/lists/*

# Create directory for vault-reader
RUN mkdir -p /app/vault-reader

# Copy vault-reader.jar and entrypoint script (will be mounted at runtime)
# These are copied here for the image, but will be overridden by volumes in docker-compose
COPY vault-reader/target/vault-reader.jar /app/vault-reader/vault-reader.jar
COPY scripts/vault-entrypoint.sh /app/vault-entrypoint.sh
RUN chmod +x /app/vault-entrypoint.sh

# Use vault-entrypoint as entrypoint (it will read secrets and then run postgres)
ENTRYPOINT ["/app/vault-entrypoint.sh"]