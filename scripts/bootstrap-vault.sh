#!/bin/bash
# Bootstrap script to initialize vault with secrets BEFORE api-gateway starts
# This solves the chicken-and-egg problem:
#   - api-gateway needs secrets from vault to start
#   - But we need api-gateway running to use the REST API to store secrets
# Solution: Use vault-reader CLI to bootstrap the vault BEFORE starting services
# 
# This script does EVERYTHING automatically:
#   1. Automatically loads .env file if it exists (for VAULT_MASTER_KEY)
#   2. Generates VAULT_MASTER_KEY if not set (saves to .env file)
#   3. Builds vault-reader package (always rebuilds to ensure latest code)
#   4. Creates and populates the vault
#   5. Optionally exports secrets as environment variables for docker-compose
# 
# Usage:
#   # Auto-detect secrets.json file (default environment: dev)
#   ./scripts/bootstrap-vault.sh
#
#   # Specify JSON file explicitly
#   ./scripts/bootstrap-vault.sh ./secrets/secrets.json
#
#   # Specify JSON file and environment
#   ./scripts/bootstrap-vault.sh ./secrets/secrets.json ec2
#
#   # Or use environment variable for environment
#   VAULT_ENVIRONMENT=ec2 ./scripts/bootstrap-vault.sh ./secrets/secrets.json
#
#   # Bootstrap and auto-export (if sourced)
#   source ./scripts/bootstrap-vault.sh ./secrets/secrets.json dev
#   # This will bootstrap AND export env vars automatically
#
#   # Or just export without bootstrapping (vault must already exist)
#   source ./scripts/bootstrap-vault.sh --export
#   # Or with environment:
#   VAULT_ENVIRONMENT=dev-docker source ./scripts/bootstrap-vault.sh --export
#
#   # Rotate chunk encryption key (adds new version while keeping old key)
#   ./scripts/bootstrap-vault.sh --rotate-chunk-key [environment]
#   # Example:
#   ./scripts/bootstrap-vault.sh --rotate-chunk-key dev

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

VAULT_FILE="$PROJECT_ROOT/secrets/vault.encrypted"
VAULT_READER_JAR="${VAULT_READER_JAR:-$PROJECT_ROOT/vault-reader/target/vault-reader.jar}"
VAULT_READER_DIR="${VAULT_READER_DIR:-$PROJECT_ROOT/vault-reader}"
ENV_FILE="${ENV_FILE:-$PROJECT_ROOT/.env}"

# Detect if script was sourced (not executed directly)
# If $0 == ${BASH_SOURCE[0]}, script was executed directly
# If $0 != ${BASH_SOURCE[0]}, script was sourced
IS_SOURCED=false
if [[ "${BASH_SOURCE[0]}" != "${0}" ]]; then
    IS_SOURCED=true
fi

# Automatically load .env file if it exists (before parsing arguments)
if [ -f "$ENV_FILE" ]; then
    # Source .env file to load VAULT_MASTER_KEY and other variables
    set -a  # Automatically export all variables
    source "$ENV_FILE"
    set +a  # Stop automatically exporting
fi

# Parse arguments
# Check for --export flag (load secrets as environment variables)
EXPORT_MODE=false
ROTATE_CHUNK_KEY_MODE=false

# Check for --rotate-chunk-key flag
if [[ "$1" == "--rotate-chunk-key" ]]; then
    ROTATE_CHUNK_KEY_MODE=true
    # Remove --rotate-chunk-key from arguments, environment is next arg or defaults to dev
    ENVIRONMENT="${2:-${VAULT_ENVIRONMENT:-dev}}"
    # Skip to rotation logic at the end
elif [[ "$1" == "--export" ]] || [[ "$2" == "--export" ]] || [[ "$3" == "--export" ]]; then
    EXPORT_MODE=true
    # Remove --export from arguments
    ARGS=()
    for arg in "$@"; do
        if [[ "$arg" != "--export" ]]; then
            ARGS+=("$arg")
        fi
    done
    set -- "${ARGS[@]}"
fi

# Argument 1: JSON file (optional, will auto-detect if not provided)
# Argument 2: Environment (optional, will use $VAULT_ENVIRONMENT or default to 'dev')
JSON_FILE="${1:-}"
if [ -z "$JSON_FILE" ] || [[ "$JSON_FILE" =~ ^(dev|ec2|prod|dev-docker)$ ]]; then
    # First argument is actually an environment, not a file
    ENVIRONMENT="${1:-${VAULT_ENVIRONMENT:-dev}}"
    JSON_FILE=""
else
    # First argument is a file, second is environment
    ENVIRONMENT="${2:-${VAULT_ENVIRONMENT:-dev}}"
fi

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Linqra Vault Bootstrap ===${NC}"
echo ""

# Step 1: Handle VAULT_MASTER_KEY
echo -e "${GREEN}Step 1: Checking VAULT_MASTER_KEY...${NC}"

# Note: .env file is already loaded at the top of the script if it exists

if [ -z "$VAULT_MASTER_KEY" ]; then
    echo -e "${YELLOW}   VAULT_MASTER_KEY not found. Generating new key...${NC}"
    VAULT_MASTER_KEY=$(openssl rand -base64 32)
    
    # Save to .env file
    echo "   Saving to .env file..."
    if [ -f "$ENV_FILE" ]; then
        # Update existing .env file
        if grep -q "VAULT_MASTER_KEY=" "$ENV_FILE"; then
            # macOS-compatible sed (needs empty string after -i)
            if [[ "$OSTYPE" == "darwin"* ]]; then
                sed -i '' "s|VAULT_MASTER_KEY=.*|VAULT_MASTER_KEY=\"$VAULT_MASTER_KEY\"|" "$ENV_FILE"
            else
                sed -i "s|VAULT_MASTER_KEY=.*|VAULT_MASTER_KEY=\"$VAULT_MASTER_KEY\"|" "$ENV_FILE"
            fi
        else
            echo "VAULT_MASTER_KEY=\"$VAULT_MASTER_KEY\"" >> "$ENV_FILE"
        fi
    else
        # Create new .env file
        echo "VAULT_MASTER_KEY=\"$VAULT_MASTER_KEY\"" > "$ENV_FILE"
        chmod 600 "$ENV_FILE" 2>/dev/null || true
    fi
    
    export VAULT_MASTER_KEY
    echo -e "${GREEN}   ✓ Master key generated and saved to .env${NC}"
else
    export VAULT_MASTER_KEY  # Explicitly export to ensure it's available in subshells
    echo -e "${GREEN}   ✓ VAULT_MASTER_KEY is set${NC}"
fi

echo ""

# Step 2: Build vault-reader (always rebuild)
echo -e "${GREEN}Step 2: Building vault-reader...${NC}"

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is required to build vault-reader"
    echo "Install: brew install maven (macOS) or apt-get install maven (Linux)"
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is required to build vault-reader"
    exit 1
fi

echo "   Building vault-reader module..."
cd "$VAULT_READER_DIR"

# Build without quiet mode to see errors (always rebuild)
if ! mvn clean package; then
    echo "ERROR: Maven build failed. See errors above."
    cd "$PROJECT_ROOT"
    exit 1
fi

cd "$PROJECT_ROOT"

if [ ! -f "$VAULT_READER_JAR" ]; then
    echo "ERROR: Failed to build vault-reader JAR at: $VAULT_READER_JAR"
    echo "Please check the Maven build output above for errors."
    cd "$PROJECT_ROOT"
    exit 1
fi

# Clean up original JAR backup created by shade plugin
ORIGINAL_JAR="${VAULT_READER_DIR}/target/original-vault-reader.jar"
if [ -f "$ORIGINAL_JAR" ]; then
    rm -f "$ORIGINAL_JAR"
fi

echo -e "${GREEN}   ✓ vault-reader.jar built successfully${NC}"

# Verify Java is available for running vault-reader
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is required to run vault-reader"
    exit 1
fi

echo ""

# If export mode, skip bootstrap and just export secrets
if [ "$EXPORT_MODE" = true ]; then
    # Just need to validate vault file exists and export
    if [ ! -f "$VAULT_FILE" ]; then
        echo -e "${RED}ERROR: Vault file not found: $VAULT_FILE${NC}"
        echo "Please bootstrap the vault first: ./scripts/bootstrap-vault.sh ./secrets/secrets..json"
        exit 1
    fi
    
    # Skip directly to export section at the end
    SKIP_BOOTSTRAP=true
elif [ "$ROTATE_CHUNK_KEY_MODE" = true ]; then
    # Rotation mode - skip bootstrap and handle rotation at the end
    SKIP_BOOTSTRAP=true
else
    SKIP_BOOTSTRAP=false
    
    # Create vault directory if it doesn't exist
    VAULT_DIR="$(dirname "$VAULT_FILE")"
    mkdir -p "$VAULT_DIR"
    chmod 700 "$VAULT_DIR" 2>/dev/null || true
    
    # Check if vault file already exists
    if [ -f "$VAULT_FILE" ]; then
        echo "WARNING: Vault file already exists: $VAULT_FILE"
        echo "Automatically overwriting (backing up existing file)..."
        cp "$VAULT_FILE" "${VAULT_FILE}.backup.$(date +%Y%m%d_%H%M%S)" 2>/dev/null || true
        echo "Removing old vault file to create fresh one..."
        rm -f "$VAULT_FILE"
    fi
fi

# Skip bootstrap steps if in export-only mode
if [ "$SKIP_BOOTSTRAP" = false ]; then
    # Step 3: Create empty vault
    echo -e "${GREEN}Step 3: Creating vault file...${NC}"
    echo "   Vault file: $VAULT_FILE"
    echo "   Environment: $ENVIRONMENT"
    echo ""
    
    # Validate VAULT_MASTER_KEY is set
    if [ -z "$VAULT_MASTER_KEY" ]; then
        echo -e "${RED}ERROR: VAULT_MASTER_KEY is not set!${NC}"
        echo "This should have been set in Step 1."
        exit 1
    fi
    
    # Validate vault-reader JAR exists
    if [ ! -f "$VAULT_READER_JAR" ]; then
        echo -e "${RED}ERROR: vault-reader.jar not found at: $VAULT_READER_JAR${NC}"
        echo "This should have been built in Step 2."
        exit 1
    fi
    
    # Note: We don't create an empty vault here because vault-reader will create it
    # automatically when writing the first secret. This avoids issues with environment
    # initialization (e.g., dev-docker environment not existing in the empty vault).
    # Just ensure the file doesn't exist if we're overwriting.
    if [ -f "$VAULT_FILE" ]; then
        rm -f "$VAULT_FILE"
    fi
    
    echo -e "${GREEN}   ✓ Vault file ready (will be created on first secret write)${NC}"
    echo ""
    
    # Step 4: Load secrets from JSON file
    echo -e "${GREEN}Step 4: Loading secrets from JSON file...${NC}"

# Auto-detect secrets.json file if not provided or if provided file doesn't exist
if [ -z "$JSON_FILE" ] || [ ! -f "$JSON_FILE" ]; then
    SECRETS_DIR="$PROJECT_ROOT/secrets"
    
    # Try to find secrets.json file automatically
    if [ -z "$JSON_FILE" ]; then
        # Try common filenames in order
        for possible_file in "$SECRETS_DIR/secrets.json" "$SECRETS_DIR/secrets..json"; do
            if [ -f "$possible_file" ]; then
                JSON_FILE="$possible_file"
                echo -e "${YELLOW}   Auto-detected: $JSON_FILE${NC}"
                break
            fi
        done
    elif [ ! -f "$JSON_FILE" ]; then
        # User provided a file but it doesn't exist, try alternatives
        JSON_DIR="$(dirname "$JSON_FILE")"
        JSON_BASENAME="$(basename "$JSON_FILE")"
        
        # Try alternative filenames
        if [[ "$JSON_BASENAME" == "secrets.json" ]] && [ -f "$JSON_DIR/secrets..json" ]; then
            JSON_FILE="$JSON_DIR/secrets..json"
            echo -e "${YELLOW}   Using: $JSON_FILE${NC}"
        elif [[ "$JSON_BASENAME" == "secrets..json" ]] && [ -f "$JSON_DIR/secrets.json" ]; then
            JSON_FILE="$JSON_DIR/secrets.json"
            echo -e "${YELLOW}   Using: $JSON_FILE${NC}"
        fi
    fi
fi

# Validate JSON file is provided
if [ -z "$JSON_FILE" ]; then
    echo -e "${RED}ERROR: JSON file is required${NC}"
    echo ""
    echo "Usage: ./scripts/bootstrap-vault.sh [secrets-file] [environment]"
    echo "   Example: ./scripts/bootstrap-vault.sh ./secrets/secrets.json dev"
    echo "   Example: ./scripts/bootstrap-vault.sh ./secrets/secrets.json ec2"
    echo ""
    echo "If no file is provided, script will auto-detect secrets.json in ./secrets/ directory"
    exit 1
fi

# Validate JSON file exists
if [ ! -f "$JSON_FILE" ]; then
    echo -e "${RED}ERROR: JSON file not found: $JSON_FILE${NC}"
    echo ""
    
    # List available JSON files in secrets directory
    SECRETS_DIR="$(dirname "$JSON_FILE")"
    if [ -d "$SECRETS_DIR" ]; then
        echo "Available JSON files in $SECRETS_DIR:"
        ls -1 "$SECRETS_DIR"/*.json 2>/dev/null | sed 's|^|   |' || echo "   (no JSON files found)"
        echo ""
    fi
    
    echo "Usage: ./scripts/bootstrap-vault.sh [secrets-file] [environment]"
    exit 1
fi

echo "   JSON file: $JSON_FILE"

# Check if jq is available (for JSON parsing)
if ! command -v jq &> /dev/null; then
    echo "ERROR: jq is required to parse JSON file"
    echo "Install: brew install jq (macOS) or apt-get install jq (Linux)"
    exit 1
fi

# Check if environment exists in JSON
if ! jq -e "has(\"$ENVIRONMENT\")" "$JSON_FILE" > /dev/null 2>&1; then
    echo "ERROR: Environment '$ENVIRONMENT' not found in JSON file"
    echo "Available environments:"
    jq -r 'keys[]' "$JSON_FILE" | sed 's/^/  - /'
    exit 1
fi

# Extract secrets for the environment
SECRETS=$(jq -r ".\"$ENVIRONMENT\" | to_entries | .[] | \"\(.key)|\(.value)\"" "$JSON_FILE")

if [ -z "$SECRETS" ]; then
    echo "WARNING: No secrets found for environment '$ENVIRONMENT'"
    exit 0
fi

# Count secrets
SECRET_COUNT=$(echo "$SECRETS" | wc -l | tr -d ' ')
echo "   Found $SECRET_COUNT secret(s)"
echo ""

# Write each secret
echo "   Writing secrets to vault..."
COUNTER=0
while IFS='|' read -r key value; do
    COUNTER=$((COUNTER + 1))
    echo "   [$COUNTER/$SECRET_COUNT] Writing: $key"
    
    # Write secret (show errors if they occur)
    set +e  # Temporarily disable exit on error for this command
    OUTPUT=$(java -jar "$VAULT_READER_JAR" \
        --operation write \
        --file "$VAULT_FILE" \
        --master-key "$VAULT_MASTER_KEY" \
        --environment "$ENVIRONMENT" \
        --key "$key" \
        --value "$value" \
        2>&1)
    EXIT_CODE=$?
    set -e  # Re-enable exit on error
    
    if [ $EXIT_CODE -ne 0 ]; then
        echo "       ❌ Failed to write secret: $key"
        echo "       Error output: $OUTPUT"
        exit 1
    else
        echo "       ✅ Success"
    fi
done <<< "$SECRETS"

    echo ""
    echo -e "${GREEN}✅ Vault bootstrapped successfully from JSON!${NC}"
    echo ""
    echo "Loaded secrets:"
    echo "$SECRETS" | sed 's/|.*/  - /' | sed 's/^/    /'
    
    echo ""
    echo -e "${GREEN}Step 5: Validating vault file...${NC}"
    
    # Validate the vault file by trying to decrypt and read it
    VALIDATION_TEMP=$(mktemp)
    VALIDATION_ERROR=$(mktemp)
    
    if java -jar "$VAULT_READER_JAR" \
        --file "$VAULT_FILE" \
        --master-key "$VAULT_MASTER_KEY" \
        --environment "$ENVIRONMENT" \
        --format json > "$VALIDATION_TEMP" 2>"$VALIDATION_ERROR"; then
        
        # Check if we got valid JSON
        if jq empty "$VALIDATION_TEMP" 2>/dev/null; then
            # The outputJson returns EnvironmentSecrets object with "secrets" key containing the actual secrets
            VALIDATED_COUNT=$(jq -r ".secrets | keys | length" "$VALIDATION_TEMP" 2>/dev/null || echo "0")
            echo -e "${GREEN}   ✓ Vault file validated successfully${NC}"
            echo "   ✓ Decryption works correctly"
            echo "   ✓ Contains $VALIDATED_COUNT secret(s) for environment '$ENVIRONMENT'"
            echo ""
        else
            echo -e "${YELLOW}   ⚠ Vault file created but validation failed (invalid JSON)${NC}"
            echo "   This might be okay if the vault file format is different."
            echo ""
        fi
    else
        echo -e "${YELLOW}   ⚠ Vault file created but validation failed${NC}"
        echo "   Error: $(cat "$VALIDATION_ERROR" 2>/dev/null || echo "Unknown error")"
        echo ""
    fi
    
    # Cleanup temp files
    rm -f "$VALIDATION_TEMP" "$VALIDATION_ERROR"
    
    # After successful bootstrap, if script was sourced (not executed directly),
    # automatically export environment variables for immediate docker-compose use
    if [ "$IS_SOURCED" = true ] && [ "$EXPORT_MODE" = false ]; then
        echo ""
        echo -e "${GREEN}Auto-export: Script was sourced, exporting secrets as environment variables...${NC}"
        echo "   Environment: $ENVIRONMENT"
        echo ""
        
        # Use vault-reader to extract secrets and export as environment variables
        # The reader outputs "export VAR=value" statements that we can eval
        eval "$(java -jar "$VAULT_READER_JAR" \
            --file "$VAULT_FILE" \
            --master-key "$VAULT_MASTER_KEY" \
            --environment "$ENVIRONMENT" \
            --format env)"
        

        echo -e "${GREEN}✓ Vault secrets exported as environment variables${NC}"
        echo ""
        echo "Loaded environment variables:"
        env | grep -E "^(MONGO|NEO4J|MILVUS|MINIO|EUREKA|SPRING_PROFILES_ACTIVE|POSTGRES|KEYCLOAK|AWS)_" | sed 's/=.*/=***/' || true
        echo ""
        echo "Note: Variables exported to shell for docker-compose."
        echo "      Run: docker-compose -f docker-compose-dev.yml up"
        echo "      (Variables will be available to docker-compose from shell environment)"
        echo ""
    fi
fi

# If export mode, load and export secrets as environment variables
if [ "$EXPORT_MODE" = true ]; then
    echo ""
    echo -e "${GREEN}Export Mode: Loading secrets as environment variables...${NC}"
    echo "   Environment: $ENVIRONMENT"
    echo ""
    
    # Use vault-reader to extract secrets and export as environment variables
    # The reader outputs "export VAR=value" statements that we can eval
    eval "$(java -jar "$VAULT_READER_JAR" \
        --file "$VAULT_FILE" \
        --master-key "$VAULT_MASTER_KEY" \
        --environment "$ENVIRONMENT" \
        --format env)"
    
    echo -e "${GREEN}✓ Vault secrets exported as environment variables${NC}"
    echo ""
    echo "Loaded environment variables:"
    env | grep -E "^(MONGO|NEO4J|MILVUS|MINIO|EUREKA|SPRING_PROFILES_ACTIVE|POSTGRES|KEYCLOAK|AWS)_" | sed 's/=.*/=***/' || true
    echo ""
    echo "Note: Variables exported to shell for docker-compose."
    echo "      Run: docker-compose -f docker-compose-dev.yml up"
    echo ""
elif [ "$ROTATE_CHUNK_KEY_MODE" = true ]; then
    # Rotation mode: Add new chunk encryption key version
    echo -e "${BLUE}=== Chunk Encryption Key Rotation ===${NC}"
    echo ""
    
    # Check if vault-reader JAR exists, build if needed
    if [ ! -f "$VAULT_READER_JAR" ]; then
        echo -e "${YELLOW}vault-reader JAR not found. Building it...${NC}"
        if ! command -v mvn &> /dev/null; then
            echo -e "${RED}ERROR: Maven is required to build vault-reader${NC}"
            exit 1
        fi
        cd "$VAULT_READER_DIR"
        mvn clean package -q
        cd "$PROJECT_ROOT"
        if [ ! -f "$VAULT_READER_JAR" ]; then
            echo -e "${RED}ERROR: Failed to build vault-reader JAR${NC}"
            exit 1
        fi
        echo -e "${GREEN}   ✓ vault-reader built successfully${NC}"
        echo ""
    fi
    
    # Step 1: Check if vault file exists
    if [ ! -f "$VAULT_FILE" ]; then
        echo -e "${RED}ERROR: Vault file not found: $VAULT_FILE${NC}"
        echo "Please bootstrap the vault first: ./scripts/bootstrap-vault.sh ./secrets/secrets.json"
        exit 1
    fi
    
    # Step 2: Check if base key exists
    echo -e "${GREEN}Step 1: Checking base key (chunk.encryption.master.key)...${NC}"
    if ! java -jar "$VAULT_READER_JAR" \
        --operation read \
        --file "$VAULT_FILE" \
        --master-key "$VAULT_MASTER_KEY" \
        --environment "$ENVIRONMENT" \
        --key chunk.encryption.master.key > /dev/null 2>&1; then
        echo -e "${RED}   ✗ ERROR: Base key (chunk.encryption.master.key) not found!${NC}"
        echo ""
        echo "   The base key is required. Please add it to your secrets.json file or use vault-reader:"
        echo "     java -jar $VAULT_READER_JAR \\"
        echo "       --operation write \\"
        echo "       --file $VAULT_FILE \\"
        echo "       --master-key \"\$VAULT_MASTER_KEY\" \\"
        echo "       --environment $ENVIRONMENT \\"
        echo "       --key chunk.encryption.master.key \\"
        echo "       --value \"<your-key>\""
        exit 1
    fi
    echo -e "${GREEN}   ✓ Base key exists (will be kept as v1)${NC}"
    echo ""
    
    # Step 3: Find next version number
    echo -e "${GREEN}Step 2: Checking existing key versions...${NC}"
    NEXT_VERSION=2
    while true; do
        KEY_NAME="chunk.encryption.master.key.v${NEXT_VERSION}"
        if java -jar "$VAULT_READER_JAR" \
            --operation read \
            --file "$VAULT_FILE" \
            --master-key "$VAULT_MASTER_KEY" \
            --environment "$ENVIRONMENT" \
            --key "$KEY_NAME" > /dev/null 2>&1; then
            echo -e "${YELLOW}   ⚠ Version v${NEXT_VERSION} already exists${NC}"
            NEXT_VERSION=$((NEXT_VERSION + 1))
        else
            break
        fi
    done
    echo -e "${GREEN}   ✓ Next version to add: v${NEXT_VERSION}${NC}"
    echo ""
    
    # Step 4: Generate new key
    echo -e "${GREEN}Step 3: Generating new encryption key...${NC}"
    NEW_KEY=$(openssl rand -base64 32)
    echo -e "${GREEN}   ✓ New key generated${NC}"
    echo ""
    
    # Step 5: Confirm before adding
    echo -e "${YELLOW}Ready to add new key version:${NC}"
    echo "   Environment: $ENVIRONMENT"
    echo "   New version: v${NEXT_VERSION}"
    echo "   Key name: chunk.encryption.master.key.v${NEXT_VERSION}"
    echo ""
    echo -e "${YELLOW}⚠️  Important:${NC}"
    echo "   • The old key (chunk.encryption.master.key) will remain as v1"
    echo "   • The new key will be added as v${NEXT_VERSION}"
    echo "   • Existing data encrypted with v1 will still be decryptable"
    echo "   • New data will be encrypted with v${NEXT_VERSION}"
    echo ""
    read -p "Continue? (y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled."
        exit 0
    fi
    
    # Step 6: Add new key version
    echo ""
    echo -e "${GREEN}Step 4: Adding new key version to vault...${NC}"
    if java -jar "$VAULT_READER_JAR" \
        --operation write \
        --file "$VAULT_FILE" \
        --master-key "$VAULT_MASTER_KEY" \
        --environment "$ENVIRONMENT" \
        --key "chunk.encryption.master.key.v${NEXT_VERSION}" \
        --value "$NEW_KEY" 2>&1; then
        echo -e "${GREEN}   ✓ New key version v${NEXT_VERSION} added successfully${NC}"
    else
        echo -e "${RED}   ✗ Failed to add new key version${NC}"
        exit 1
    fi
    echo ""
    
    # Step 7: Verify the key was added
    echo -e "${GREEN}Step 5: Verifying key was added...${NC}"
    if java -jar "$VAULT_READER_JAR" \
        --operation read \
        --file "$VAULT_FILE" \
        --master-key "$VAULT_MASTER_KEY" \
        --environment "$ENVIRONMENT" \
        --key "chunk.encryption.master.key.v${NEXT_VERSION}" > /dev/null 2>&1; then
        echo -e "${GREEN}   ✓ Key v${NEXT_VERSION} verified in vault${NC}"
    else
        echo -e "${RED}   ✗ ERROR: Key verification failed${NC}"
        exit 1
    fi
    echo ""
    
    # Step 8: Success message with next steps
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}✅ Key Rotation Setup Complete!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${BLUE}Next Steps:${NC}"
    echo ""
    echo "1. ${YELLOW}Restart your api-gateway-service${NC}"
    echo "   The service will detect the new key version on startup"
    echo ""
    echo "2. ${YELLOW}Check the service logs${NC}"
    echo "   You should see:"
    echo "     Loaded encryption key version v1"
    echo "     Loaded encryption key version v${NEXT_VERSION}"
    echo "     Current encryption key version: v${NEXT_VERSION}"
    echo ""
    echo "3. ${YELLOW}Verify version warnings appear${NC}"
    echo "   • Navigate to KnowledgeHub pages"
    echo "   • You should see warnings for entities encrypted with v1"
    echo "   • New documents will be encrypted with v${NEXT_VERSION}"
    echo ""
    echo "4. ${YELLOW}Migrate old data (optional)${NC}"
    echo "   • Use 'Hard Delete All Entities' in KnowledgeHub to remove old data"
    echo "   • Re-upload documents - they will be encrypted with v${NEXT_VERSION}"
    echo ""
    echo -e "${BLUE}Current Key Status:${NC}"
    echo "   • v1 (base): chunk.encryption.master.key ✓"
    echo "   • v${NEXT_VERSION} (new): chunk.encryption.master.key.v${NEXT_VERSION} ✓"
    echo ""
else
    echo ""
    echo -e "${BLUE}=== Setup Complete ===${NC}"
    echo ""
    echo "Your vault is ready! Next steps:"
    echo ""
    echo "  1. Start your services:"
    echo "     docker-compose -f docker-compose-ec2.yml up"
    echo ""
    echo "  2. Or export vault secrets as environment variables for docker-compose:"
    echo "     source ./scripts/bootstrap-vault.sh --export"
    echo ""
    echo "  3. To rotate chunk encryption key:"
    echo "     ./scripts/bootstrap-vault.sh --rotate-chunk-key [environment]"
    echo ""
    echo "Note: The vault file (vault.encrypted) is binary/encrypted, so it won't"
    echo "      display in text editors. This is normal and expected! ✅"
    echo ""
    echo "Note: The script automatically loads .env file if it exists."
    echo ""
fi