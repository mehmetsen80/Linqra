FROM chromadb/chroma:latest

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Base configuration
ENV CHROMA_DB_IMPL=duckdb+parquet
ENV PERSIST_DIRECTORY=/chroma/data
ENV ANONYMIZED_TELEMETRY=false
ENV CHROMA_SERVER_HOST=0.0.0.0
ENV CHROMA_SERVER_PORT=8000
ENV CHROMA_SERVER_CORS_ALLOW_ORIGINS=["*"]

# Create data directory
RUN mkdir -p /chroma/data

# Expose the port
EXPOSE 8000
