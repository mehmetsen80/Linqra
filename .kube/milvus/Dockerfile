FROM milvusdb/milvus:latest

# Install curl for healthcheck and Python for database operations
RUN apt-get update && apt-get install -y curl python3 python3-pip python3-dev build-essential && rm -rf /var/lib/apt/lists/*

# Install Milvus Python client and additional dependencies
RUN pip3 install pymilvus grpcio grpcio-tools protobuf

# Set environment variables
ENV MILVUS_STANDALONE=true
ENV ETCD_CFG_AUTO_COMPACTION_MODE=revision
ENV ETCD_CFG_AUTO_COMPACTION_RETENTION=1000
ENV COMMON_CFG_RETENTION_DURATION=168h

# Expose ports
EXPOSE 19530 9091

# Start Milvus
CMD ["milvus", "run", "standalone"]
