# Stage 1: Install curl using a Debian base image
FROM debian:bookworm-slim AS curl-builder
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Stage 2: Use the Keycloak image
FROM quay.io/keycloak/keycloak:26.4.6

# Switch to root to create directories and install files
USER root

# Copy curl binary from builder stage
# Note: If curl fails at runtime due to missing libraries, we can use wget or shell-based health check instead
COPY --from=curl-builder /usr/bin/curl /usr/bin/curl

# Create /app directory structure (volumes will override, but we need the entrypoint script)
RUN mkdir -p /app/vault-reader && \
    chown -R 1000:1000 /app

# Copy vault-reader.jar and entrypoint script (will be mounted at runtime, but keep in image as fallback)
COPY vault-reader/target/vault-reader.jar /app/vault-reader/vault-reader.jar
COPY scripts/vault-entrypoint.sh /app/vault-entrypoint.sh
RUN chmod +x /app/vault-entrypoint.sh && \
    chown 1000:1000 /app/vault-entrypoint.sh /app/vault-reader/vault-reader.jar

# Switch back to keycloak user (default user in the base image)
USER 1000

# Use vault-entrypoint as entrypoint (it will read secrets and then run keycloak)
ENTRYPOINT ["/app/vault-entrypoint.sh"]
