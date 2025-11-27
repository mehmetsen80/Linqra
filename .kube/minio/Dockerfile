FROM minio/minio:RELEASE.2023-03-20T20-16-18Z

# Install Java (needed for vault-reader.jar) - using JDK 21 to match other services
# MinIO uses Red Hat UBI 8 Minimal, which uses microdnf as package manager
# Enable necessary repositories and install Java
RUN microdnf update -y && \
    microdnf install -y --nodocs java-21-openjdk-headless && \
    microdnf clean all

# Verify Java installation
RUN java -version || (echo "Java installation verification failed" && exit 1)

# Create directory for vault-reader
RUN mkdir -p /app/vault-reader

# Copy vault-reader.jar and entrypoint script (will be mounted at runtime)
COPY vault-reader/target/vault-reader.jar /app/vault-reader/vault-reader.jar
COPY scripts/vault-entrypoint.sh /app/vault-entrypoint.sh
RUN chmod +x /app/vault-entrypoint.sh

# Use vault-entrypoint as entrypoint (it will read secrets and then run minio)
ENTRYPOINT ["/app/vault-entrypoint.sh"]

