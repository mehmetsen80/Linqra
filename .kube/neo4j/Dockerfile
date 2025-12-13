FROM neo4j:5.26.16-community

# Neo4j configuration is handled via environment variables in docker-compose
# No additional setup needed - the official image includes APOC plugins via NEO4J_PLUGINS env var

# Install Java 21 (needed for vault-reader.jar compiled with Java 21)
# Neo4j comes with Java 17, but vault-reader.jar requires Java 21
# Neo4j's base image uses older Debian without Java 21 in repos, so download manually
# Detect architecture and download appropriate Java version
USER root
RUN apt-get update && \
    apt-get install -y curl tar && \
    rm -rf /var/lib/apt/lists/* && \
    cd /tmp && \
    ARCH=$(uname -m) && \
    if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then \
        echo "Installing Java 21 for ARM64..." && \
        curl -L -o java21.tar.gz "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jre_aarch64_linux_hotspot_21.0.9_10.tar.gz"; \
    else \
        echo "Installing Java 21 for x64..." && \
        curl -L -o java21.tar.gz "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jre_x64_linux_hotspot_21.0.9_10.tar.gz"; \
    fi && \
    tar -xzf java21.tar.gz && \
    mv jdk-21.0.9+10-jre /opt/java21 && \
    chmod +x /opt/java21/bin/java && \
    chmod -R 755 /opt/java21 && \
    rm -f java21.tar.gz && \
    echo "Verifying Java 21 installation..." && \
    /opt/java21/bin/java -version 2>&1 | head -1 && \
    echo "Java 21 installed successfully"

# Create directory for vault-reader (Neo4j runs as neo4j user, so ensure proper permissions)
RUN mkdir -p /app/vault-reader && \
    chown -R neo4j:neo4j /app

# Copy vault-reader.jar and entrypoint script (will be mounted at runtime)
COPY vault-reader/target/vault-reader.jar /app/vault-reader/vault-reader.jar
COPY scripts/vault-entrypoint.sh /app/vault-entrypoint.sh
RUN chmod +x /app/vault-entrypoint.sh && \
    chown neo4j:neo4j /app/vault-entrypoint.sh /app/vault-reader/vault-reader.jar

# Switch back to neo4j user
USER neo4j

# Save Neo4j's original entrypoint before replacing it
# Neo4j's default entrypoint is usually /startup/docker-entrypoint.sh
# Use vault-entrypoint as entrypoint (it will read secrets and then run neo4j)
# Preserve Neo4j's default CMD (usually ["neo4j"]) so it gets passed to the entrypoint
ENTRYPOINT ["/app/vault-entrypoint.sh"]
# Neo4j's default CMD is typically ["neo4j"] - this will be passed to our entrypoint which will then pass it to Neo4j's entrypoint
CMD ["neo4j"]
