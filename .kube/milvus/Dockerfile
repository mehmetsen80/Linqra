FROM milvusdb/milvus:v2.6.6

# Install curl for healthcheck and Python for database operations
RUN apt-get update && apt-get install -y net-tools iputils-ping netcat-openbsd wget && apt-get install -y curl python3 python3-pip python3-dev build-essential && rm -rf /var/lib/apt/lists/*

# Install Java (needed for vault-reader.jar) - using JDK 21 to match other services
RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    rm -rf /var/lib/apt/lists/*

# Install Milvus Python client and additional dependencies
RUN pip3 install pymilvus grpcio grpcio-tools protobuf

# Set environment variables
ENV MILVUS_STANDALONE=true
ENV ETCD_CFG_AUTO_COMPACTION_MODE=revision
ENV ETCD_CFG_AUTO_COMPACTION_RETENTION=1000
ENV COMMON_CFG_RETENTION_DURATION=168h

# Enable Milvus authentication/authorization
# These are the correct env var names for Milvus 2.x
ENV COMMON_SECURITY_AUTHORIZATIONENABLED=true

# Create directory for vault-reader
RUN mkdir -p /app/vault-reader

# Copy vault-reader.jar and entrypoint script (will be mounted at runtime)
COPY vault-reader/target/vault-reader.jar /app/vault-reader/vault-reader.jar
COPY scripts/vault-entrypoint.sh /app/vault-entrypoint.sh
RUN chmod +x /app/vault-entrypoint.sh

# Expose ports
EXPOSE 19530 9091

# Use vault-entrypoint as entrypoint (it will read secrets and then run milvus)
ENTRYPOINT ["/app/vault-entrypoint.sh"]
CMD ["milvus", "run", "standalone"]
