FROM milvusdb/milvus:latest

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV MILVUS_STANDALONE=true
ENV ETCD_CFG_AUTO_COMPACTION_MODE=revision
ENV ETCD_CFG_AUTO_COMPACTION_RETENTION=1000
ENV COMMON_CFG_RETENTION_DURATION=168h

# Expose ports
EXPOSE 19530 9091

# Start Milvus
CMD ["milvus", "run", "standalone"]
