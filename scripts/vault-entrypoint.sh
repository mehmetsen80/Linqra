#!/bin/bash
# Generic entrypoint script that reads secrets from vault and exports them
# Then executes the original command passed as arguments
#
# Usage: This script is used as an entrypoint in docker-compose services
# It reads VAULT_MASTER_KEY, VAULT_ENVIRONMENT, and VAULT_REQUIRED_VARS from environment variables
# and vault file from /app/secrets/vault.encrypted (mounted volume)
#
# VAULT_REQUIRED_VARS: Comma-separated list of environment variable names this service needs
#   Example: "POSTGRES_DB,POSTGRES_USER,POSTGRES_PASSWORD"

set -e

VAULT_FILE="${VAULT_FILE:-/app/secrets/vault.encrypted}"
VAULT_READER_JAR="${VAULT_READER_JAR:-/app/vault-reader/vault-reader.jar}"
VAULT_MASTER_KEY="${VAULT_MASTER_KEY}"
VAULT_ENVIRONMENT="${VAULT_ENVIRONMENT:-dev}"
VAULT_REQUIRED_VARS="${VAULT_REQUIRED_VARS}"

# Check if vault file and reader exist
if [ ! -f "$VAULT_FILE" ]; then
    echo "ERROR: Vault file not found at $VAULT_FILE"
    exit 1
fi

if [ ! -f "$VAULT_READER_JAR" ]; then
    echo "ERROR: Vault reader JAR not found at $VAULT_READER_JAR"
    exit 1
fi

if [ -z "$VAULT_MASTER_KEY" ]; then
    echo "ERROR: VAULT_MASTER_KEY environment variable not set"
    exit 1
fi

# Verify Java is available
# Try multiple methods to find Java (different base images use different paths)
# Priority: Java 21 first (required for vault-reader.jar), then fallback to any Java
JAVA_CMD=""
# Try Java 21 paths first (vault-reader.jar requires Java 21)
if [ -f /opt/java21/bin/java ]; then
    # Manually installed Java 21 (Neo4j)
    JAVA_CMD=/opt/java21/bin/java
elif [ -f /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]; then
    # Debian/Ubuntu path for Java 21
    JAVA_CMD=/usr/lib/jvm/java-21-openjdk-amd64/bin/java
elif [ -f /usr/lib/jvm/java-21-openjdk/bin/java ]; then
    # Alternative Java 21 path
    JAVA_CMD=/usr/lib/jvm/java-21-openjdk/bin/java
elif command -v java >/dev/null 2>&1; then
    # Check if default java is Java 21
    JAVA_VERSION=$(java -version 2>&1 | head -1 | grep -oP 'version "21' || echo "")
    if [ -n "$JAVA_VERSION" ]; then
        JAVA_CMD=$(command -v java)
    fi
fi

# If Java 21 not found, try other paths (but vault-reader might fail)
if [ -z "$JAVA_CMD" ]; then
    if command -v java >/dev/null 2>&1; then
        JAVA_CMD=$(command -v java)
    elif [ -f /opt/java/openjdk/bin/java ]; then
        # Neo4j's default path (Java 17)
        JAVA_CMD=/opt/java/openjdk/bin/java
    elif [ -f /usr/bin/java ]; then
        JAVA_CMD=/usr/bin/java
    elif [ -n "$(find /usr -name java -type f 2>/dev/null | head -1)" ]; then
        JAVA_CMD=$(find /usr -name java -type f 2>/dev/null | head -1)
    fi
fi

if [ -z "$JAVA_CMD" ] || [ ! -f "$JAVA_CMD" ] || [ ! -x "$JAVA_CMD" ]; then
    echo "ERROR: Java command not found or not executable."
    echo "Searched in: /opt/java21/bin/java, /usr/lib/jvm/java-21-openjdk-amd64/bin/java, /usr/lib/jvm/java-21-openjdk/bin/java, /opt/java/openjdk/bin/java, /usr/bin/java"
    echo "Attempting to find Java..."
    find /usr -name "java" -type f 2>/dev/null | head -5 || echo "Java not found"
    exit 1
fi

echo "DEBUG: Found Java at: $JAVA_CMD" >&2

# Verify Java binary is executable
if [ ! -x "$JAVA_CMD" ]; then
    echo "ERROR: Java binary at $JAVA_CMD is not executable" >&2
    ls -la "$JAVA_CMD" >&2
    exit 1
fi

# Verify Java version (vault-reader.jar requires Java 21)
JAVA_VERSION_OUTPUT=$($JAVA_CMD -version 2>&1 | head -1 || echo "")
if [ -z "$JAVA_VERSION_OUTPUT" ]; then
    echo "ERROR: Failed to get Java version. Java binary may not be compatible with this architecture." >&2
    file "$JAVA_CMD" >&2
    exit 1
fi
echo "$JAVA_VERSION_OUTPUT" >&2

# Check if Java 21 or higher (simple check)
if echo "$JAVA_VERSION_OUTPUT" | grep -q "version \"1[789]\." || echo "$JAVA_VERSION_OUTPUT" | grep -q "version \"[0-9]\."; then
    # Check if it contains "21" or higher
    if ! echo "$JAVA_VERSION_OUTPUT" | grep -qE "version \"(2[1-9]|[3-9][0-9])"; then
        echo "WARNING: vault-reader.jar requires Java 21 or higher, but found: $JAVA_VERSION_OUTPUT" >&2
        echo "WARNING: This may cause vault-reader to fail or hang" >&2
    fi
fi

# Read secrets from vault
echo "Reading secrets from vault (environment: $VAULT_ENVIRONMENT)..." >&2

# Debug information (force output to stderr so it's visible)
echo "DEBUG: Java command: $JAVA_CMD" >&2
echo "DEBUG: Vault file: $VAULT_FILE" >&2
echo "DEBUG: Vault reader JAR: $VAULT_READER_JAR" >&2
echo "DEBUG: Vault file exists: $([ -f "$VAULT_FILE" ] && echo "YES" || echo "NO")" >&2
echo "DEBUG: Vault reader JAR exists: $([ -f "$VAULT_READER_JAR" ] && echo "YES" || echo "NO")" >&2
echo "DEBUG: Vault reader JAR readable: $([ -r "$VAULT_READER_JAR" ] && echo "YES" || echo "NO")" >&2

# Run vault-reader with explicit Java path
# Use a temp file to capture output and ensure no stdin blocking
echo "DEBUG: Executing vault-reader command..." >&2
echo "DEBUG: Full command: $JAVA_CMD -jar $VAULT_READER_JAR --file $VAULT_FILE --master-key <REDACTED> --environment $VAULT_ENVIRONMENT --format env" >&2

TEMP_OUTPUT=$(mktemp)
# Run the command with explicit stdin redirection to /dev/null to prevent blocking
echo "DEBUG: Running vault-reader..." >&2
echo "DEBUG: Architecture: $(uname -m)" >&2

# Try to run vault-reader directly (timeout command may have issues, so run without it)
# The Java command itself should handle timeouts if needed
set +e  # Temporarily disable exit on error to capture exit code
$JAVA_CMD -jar "$VAULT_READER_JAR" \
    --file "$VAULT_FILE" \
    --master-key "$VAULT_MASTER_KEY" \
    --environment "$VAULT_ENVIRONMENT" \
    --format env < /dev/null > "$TEMP_OUTPUT" 2>&1
VAULT_EXIT_CODE=$?
set -e  # Re-enable exit on error

echo "DEBUG: Java command finished with exit code: $VAULT_EXIT_CODE" >&2
VAULT_OUTPUT=$(cat "$TEMP_OUTPUT" 2>/dev/null || echo "")
echo "DEBUG: Output length: ${#VAULT_OUTPUT} characters" >&2
if [ -n "$VAULT_OUTPUT" ]; then
    echo "DEBUG: First 200 chars of output: ${VAULT_OUTPUT:0:200}" >&2
fi
rm -f "$TEMP_OUTPUT"

echo "DEBUG: Vault-reader completed with exit code: $VAULT_EXIT_CODE" >&2

if [ $VAULT_EXIT_CODE -ne 0 ]; then
    echo "ERROR: Failed to read secrets from vault (exit code: $VAULT_EXIT_CODE):"
    echo "$VAULT_OUTPUT"
    exit 1
fi

if [ -z "$VAULT_OUTPUT" ]; then
    echo "ERROR: Vault reader returned empty output"
    exit 1
fi

echo "Successfully read secrets from vault" >&2

# If VAULT_REQUIRED_VARS is set, only export those variables
# Otherwise, export all variables (backward compatibility)
if [ -n "$VAULT_REQUIRED_VARS" ]; then
    echo "Filtering vault secrets to only required variables: $VAULT_REQUIRED_VARS" >&2
    # Create a temp file with all vault output
    TEMP_FILE=$(mktemp)
    echo "$VAULT_OUTPUT" > "$TEMP_FILE"
    
    # Extract only the required variables (handle comma-separated list)
    IFS=',' read -ra REQUIRED_VARS <<< "$VAULT_REQUIRED_VARS"
    for VAR_NAME in "${REQUIRED_VARS[@]}"; do
        VAR_NAME=$(echo "$VAR_NAME" | xargs)  # Trim whitespace
        # Find the export line for this variable (case-sensitive)
        EXPORT_LINE=$(grep "^export ${VAR_NAME}=" "$TEMP_FILE" || true)
        if [ -n "$EXPORT_LINE" ]; then
            eval "$EXPORT_LINE"
            echo "  ✓ Exported: $VAR_NAME" >&2
        else
            echo "  ⚠ WARNING: Required variable '$VAR_NAME' not found in vault" >&2
        fi
    done
    rm -f "$TEMP_FILE"
    
    # Special handling for Neo4j: set NEO4J_AUTH from NEO4J_USERNAME and NEO4J_PASSWORD
    # Only do this if Neo4j variables were requested (i.e., for neo4j-service)
    if [[ "$VAULT_REQUIRED_VARS" == *"NEO4J_USERNAME"* ]] || [[ "$VAULT_REQUIRED_VARS" == *"NEO4J_PASSWORD"* ]]; then
        # Unset any existing NEO4J_AUTH first to avoid conflicts
        unset NEO4J_AUTH
        if [ -n "${NEO4J_USERNAME:-}" ] && [ -n "${NEO4J_PASSWORD:-}" ]; then
            # Validate that username and password don't contain forward slashes (would break NEO4J_AUTH format)
            if [[ "$NEO4J_USERNAME" == *"/"* ]] || [[ "$NEO4J_PASSWORD" == *"/"* ]]; then
                echo "  ⚠ WARNING: NEO4J_USERNAME or NEO4J_PASSWORD contains forward slash, which may cause issues" >&2
            fi
            export NEO4J_AUTH="${NEO4J_USERNAME}/${NEO4J_PASSWORD}"
            echo "  ✓ Set NEO4J_AUTH from vault secrets" >&2
            echo "  ✓ NEO4J_AUTH format: username/password (length: ${#NEO4J_AUTH} chars)" >&2
            # Verify the export worked
            if [ -z "${NEO4J_AUTH:-}" ]; then
                echo "  ✗ ERROR: Failed to set NEO4J_AUTH!" >&2
                exit 1
            fi
            # Unset NEO4J_USERNAME and NEO4J_PASSWORD to prevent Neo4j from trying to parse them as config settings
            # Neo4j only needs NEO4J_AUTH, and having NEO4J_PASSWORD as an env var might cause config parsing errors
            unset NEO4J_USERNAME
            unset NEO4J_PASSWORD
            echo "  ✓ Unset NEO4J_USERNAME and NEO4J_PASSWORD (Neo4j only needs NEO4J_AUTH)" >&2
        else
            echo "  ✗ ERROR: NEO4J_USERNAME or NEO4J_PASSWORD not set, cannot configure NEO4J_AUTH" >&2
            echo "  ⚠ NEO4J_USERNAME=${NEO4J_USERNAME:-<unset>} (length: ${#NEO4J_USERNAME:-0})" >&2
            echo "  ⚠ NEO4J_PASSWORD=${NEO4J_PASSWORD:+<set>}${NEO4J_PASSWORD:-<unset>} (length: ${#NEO4J_PASSWORD:-0})" >&2
            exit 1
        fi
    fi
    
    # Special handling for Keycloak: set KC_DB_* variables from POSTGRES_* variables
    # Only do this if PostgreSQL variables were requested (i.e., for keycloak-service)
    if [[ "$VAULT_REQUIRED_VARS" == *"POSTGRES_USER"* ]] || [[ "$VAULT_REQUIRED_VARS" == *"POSTGRES_PASSWORD"* ]] || [[ "$VAULT_REQUIRED_VARS" == *"POSTGRES_DB"* ]]; then
        if [ -n "${POSTGRES_USER:-}" ] && [ -n "${POSTGRES_PASSWORD:-}" ] && [ -n "${POSTGRES_DB:-}" ]; then
            export KC_DB_USERNAME="${POSTGRES_USER}"
            export KC_DB_PASSWORD="${POSTGRES_PASSWORD}"
            # KC_DB_URL must be set here because Docker Compose resolves $POSTGRES_DB at compose time (before vault-entrypoint runs)
            # At that point, $POSTGRES_DB is empty, so we construct the full URL here
            export KC_DB_URL="jdbc:postgresql://postgres-service:5432/${POSTGRES_DB}"
            echo "  ✓ Set KC_DB_USERNAME from vault secrets" >&2
            echo "  ✓ Set KC_DB_PASSWORD from vault secrets" >&2
            echo "  ✓ Set KC_DB_URL from vault secrets: jdbc:postgresql://postgres-service:5432/${POSTGRES_DB}" >&2
        fi
    fi
    
    # Special handling for API Gateway: export GATEWAY_TRUSTSTORE_PASSWORD if requested
    # This is used to set Java SSL system properties for Eureka Client (which uses default SSL context)
    if [[ "$VAULT_REQUIRED_VARS" == *"GATEWAY_TRUSTSTORE_PASSWORD"* ]]; then
        if [ -z "${GATEWAY_TRUSTSTORE_PASSWORD:-}" ]; then
            echo "  ✗ ERROR: GATEWAY_TRUSTSTORE_PASSWORD not found in vault" >&2
            exit 1
        fi
        echo "  ✓ Exported: GATEWAY_TRUSTSTORE_PASSWORD (for Eureka Client SSL)" >&2
    fi
else
    # Export all variables (backward compatibility)
    echo "No VAULT_REQUIRED_VARS specified, exporting all vault secrets" >&2
    eval "$VAULT_OUTPUT"
fi

# Show Neo4j-related environment variables before executing Neo4j
if [ -f "/startup/docker-entrypoint.sh" ]; then
    echo "DEBUG: Neo4j environment variables before execution:" >&2
    env | grep -E "^NEO4J_" | sed 's/PASSWORD=.*/PASSWORD=***/' >&2 || echo "  (none found)" >&2
fi

# Execute the original command passed as arguments
echo "DEBUG: Preparing to execute command: $*" >&2
echo "DEBUG: Number of arguments: $#" >&2

# For Neo4j, call its original entrypoint script (usually at /startup/docker-entrypoint.sh)
# Neo4j's entrypoint script uses 'set -u' which requires $1 to be set
if [ -f "/startup/docker-entrypoint.sh" ]; then
    echo "DEBUG: Found Neo4j entrypoint at /startup/docker-entrypoint.sh" >&2
    # Neo4j entrypoint script has 'set -u', so we must provide at least one argument
    # Neo4j's default CMD is "neo4j" - use that if no arguments provided
    if [ $# -eq 0 ]; then
        echo "DEBUG: No arguments provided, using Neo4j default command: neo4j" >&2
        set -- "neo4j"
    fi
    exec /startup/docker-entrypoint.sh "$@"
# For PostgreSQL, switch to postgres user if running as root
elif [ "$(id -u)" = "0" ] && [ "$1" = "postgres" ]; then
    # PostgreSQL requires non-root user
    # Use gosu (available in postgres image) to switch to postgres user
    exec gosu postgres "$@"
# For MinIO, the command starts with "server" - find and use minio binary
elif [ "$1" = "server" ]; then
    # MinIO: find the minio binary and execute it with the command arguments
    MINIO_BIN=$(command -v minio 2>/dev/null || find /usr -name "minio" -type f 2>/dev/null | head -1)
    if [ -n "$MINIO_BIN" ] && [ -x "$MINIO_BIN" ]; then
        exec "$MINIO_BIN" "$@"
    else
        echo "ERROR: MinIO binary not found. Cannot execute: $*" >&2
        exit 1
    fi
# For Keycloak, the command starts with "start-dev" - call Keycloak's entrypoint
elif [ "$1" = "start-dev" ] || [ "$1" = "start" ]; then
    # Keycloak: call the original Keycloak entrypoint script
    KC_ENTRYPOINT="/opt/keycloak/bin/kc.sh"
    if [ -f "$KC_ENTRYPOINT" ] && [ -x "$KC_ENTRYPOINT" ]; then
        exec "$KC_ENTRYPOINT" "$@"
    else
        echo "ERROR: Keycloak entrypoint not found at $KC_ENTRYPOINT. Cannot execute: $*" >&2
        exit 1
    fi
# For API Gateway, inject Java SSL system properties for Eureka Client
# Detect API Gateway by checking if GATEWAY_TRUSTSTORE_PASSWORD is set (it's in VAULT_REQUIRED_VARS for api-gateway-service)
elif [ "$1" = "java" ] && [ -n "${GATEWAY_TRUSTSTORE_PASSWORD:-}" ]; then
    # API Gateway: The Eureka Client uses Java's default SSL context, not Spring Boot's server SSL config
    # So we need to set javax.net.ssl.trustStore system properties for outbound HTTPS connections
    echo "Configuring Eureka Client SSL with custom truststore..." >&2
    # Skip first argument ("java") - use all arguments from $2 onwards
    # Build command with SSL properties prepended
    exec java \
        -Djavax.net.ssl.trustStore=/app/gateway-truststore.jks \
        -Djavax.net.ssl.trustStorePassword="${GATEWAY_TRUSTSTORE_PASSWORD}" \
        -Djavax.net.ssl.trustStoreType=JKS \
        "${@:2}"
elif [ $# -eq 0 ]; then
    # No arguments provided - try to find and execute default service command
    echo "WARNING: No command arguments provided. Attempting to find default service..." >&2
    if [ -f "/startup/docker-entrypoint.sh" ]; then
        echo "Executing Neo4j default entrypoint with default command: neo4j" >&2
        exec /startup/docker-entrypoint.sh "neo4j"
    else
        echo "ERROR: No command provided and no default entrypoint found" >&2
        exit 1
    fi
else
    # Execute normally for other services
    exec "$@"
fi

