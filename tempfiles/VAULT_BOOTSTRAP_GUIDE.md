# Vault Bootstrap Guide - Solving the Chicken-and-Egg Problem

## The Problem

You've identified a critical issue:

1. **api-gateway needs secrets from vault to start** (MongoDB password, JWT secret, etc.)
2. **Secrets MUST NOT be created or accessed through api-gateway REST API** (security requirement)
3. **Result:** We need to populate the vault BEFORE starting api-gateway using the vault-reader CLI! 🐔🥚

## The Solution: Bootstrap with vault-reader CLI

Use the **vault-reader CLI utility** to initialize and populate the vault **BEFORE** starting api-gateway!

### Architecture

```
1. Bootstrap Phase (BEFORE api-gateway starts):
   vault-reader CLI → Creates/Updates vault file → Encrypted vault ready

2. Runtime Phase (AFTER api-gateway starts):
   api-gateway → Decrypts secrets internally via VaultPropertySource (NO REST API access)
```

## Bootstrap Workflow

### Step 1: Build Vault Reader CLI

```bash
cd vault-reader
mvn clean package
```

This creates: `vault-reader/target/vault-reader.jar`

### Step 2: Generate Master Key

```bash
# Generate a secure 256-bit (32-byte) master key
openssl rand -base64 32

# Output example:
# 7xKp9mN2qR4wL8vB1cT6hY0zX5aS3dF7gJ4nM9pQ2rE=
```

### Step 3: Set Environment Variables (Optional - Recommended)

The `bootstrap-vault.sh` script automatically loads the `.env` file if it exists, so you can store your configuration there:

**Option A: Use `.env` file (Recommended)**
```bash
# Create .env file in project root
cat > .env << EOF
VAULT_MASTER_KEY=<generated-key-from-step-2>
VAULT_ENVIRONMENT=dev
EOF
```

**Option B: Export manually**
```bash
export VAULT_MASTER_KEY="<generated-key-from-step-2>"
export VAULT_ENVIRONMENT="dev"  # or "dev-docker" or "ec2"
```

**Note:** If `.env` exists, the script will automatically load it, so you don't need to manually export these variables.

### Step 4: Bootstrap Vault (From JSON File - Recommended)

**Option A: Bootstrap only (run script directly)**
```bash
# Run bootstrap script with secrets file
# Syntax: ./scripts/bootstrap-vault.sh <json-file> [environment]
# The script uses secrets.json
./scripts/bootstrap-vault.sh ./secrets/secrets.json dev
```

**Option B: Bootstrap and export (source script - recommended for Docker Compose)**
```bash
# Source the script to bootstrap AND export environment variables in one step
# This is useful when you need env vars for docker-compose immediately after
source ./scripts/bootstrap-vault.sh ./secrets/secrets.json dev
```

**What it does:**
- ✅ Automatically load `.env` file if it exists (for `VAULT_MASTER_KEY` and `VAULT_ENVIRONMENT`)
- ✅ Always rebuild `vault-reader.jar` to ensure it's up-to-date
- ✅ Create empty vault file if it doesn't exist
- ✅ Load all secrets from the JSON file for the specified environment
- ✅ Validate that secrets were loaded correctly
- ✅ Support JSON values (complex configurations)
- ✅ **If sourced:** Also export secrets as environment variables for Docker Compose

**Notes:**
- **Filename:** The script uses `secrets.json`.
- **Source vs Direct:**
  - Running directly (`./scripts/bootstrap-vault.sh ...`) only bootstraps the vault
  - Using `source` (`source ./scripts/bootstrap-vault.sh ...`) bootstraps AND exports env vars into your current shell session (useful before running `docker-compose`)

**Environments:**
- `dev` - Local development (localhost URLs, local paths)
- `dev-docker` - Docker Compose development (service names, container paths)
- `ec2` - Production/EC2 environment (service names, container paths)

### Step 4 (Alternative): Bootstrap Vault Manually (Advanced)

#### 4a. Create Empty Vault

```bash
java -jar vault-reader/target/vault-reader.jar \
  --operation create \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY"
```

#### 4b. Add Secrets One by One

```bash
# MongoDB password
java -jar vault-reader/target/vault-reader.jar \
  --operation write \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY" \
  --environment dev \
  --key mongodb.password \
  --value "your-actual-password"

# Neo4j password
java -jar vault-reader/target/vault-reader.jar \
  --operation write \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY" \
  --environment dev \
  --key neo4j.password \
  --value "your-neo4j-password"

# ... repeat for all secrets
```

### Step 5: Verify Vault

```bash
# List all secrets
java -jar vault-reader/target/vault-reader.jar \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY" \
  --environment dev \
  --format json

# Get specific secret
java -jar vault-reader/target/vault-reader.jar \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY" \
  --environment dev \
  --key mongodb.password
```

### Step 6: Start api-gateway

Now api-gateway can start because vault is populated:

**Option A: If you already sourced during bootstrap (Step 4 Option B)**
```bash
# Env vars are already exported, just start services
docker-compose -f docker-compose-dev.yml up
```

**Option B: Export separately**
```bash
# Load vault secrets as environment variables (for non-Spring Boot services)
source ./scripts/bootstrap-vault.sh --export

# Start services
docker-compose -f docker-compose-dev.yml up
```

**Note:** If you used `source` during bootstrap in Step 4, the environment variables are already available in your shell, so you can skip the export step.

## Complete Workflow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ Phase 1: Bootstrap (BEFORE api-gateway starts)             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Generate master key                                     │
│     openssl rand -base64 32                                 │
│                                                             │
│  2. Build vault-reader                                      │
│     cd vault-reader && mvn clean package                    │
│                                                             │
│  3. Create vault file                                       │
│     vault-reader --operation create                         │
│                                                             │
│  4. Add initial secrets                                     │
│     vault-reader --operation write --key X --value Y        │
│                                                             │
│  5. Verify vault                                            │
│     vault-reader --format json                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ Phase 2: Runtime (AFTER api-gateway starts)                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. api-gateway decrypts secrets internally                 │
│     (via VaultPropertySource in application.yml)            │
│     NO REST API access - vault-reader CLI only!             │
│                                                             │
│  2. Export secrets for docker-compose (if needed)           │
│     source ./scripts/bootstrap-vault.sh --export            │
│                                                             │
│  3. Update secrets via vault-reader CLI (if needed)         │
│     vault-reader --operation write ...                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Bootstrap Script Features

The `scripts/bootstrap-vault.sh` script:

- ✅ **Auto-loads `.env` file** - Automatically sources `.env` if it exists (for `VAULT_MASTER_KEY`, `VAULT_ENVIRONMENT`, etc.)
- ✅ **Always rebuilds `vault-reader.jar`** - Ensures the CLI tool is always up-to-date
- ✅ **Creates empty vault file** - Initializes vault if it doesn't exist
- ✅ **Loads secrets from JSON** - Reads all secrets from `secrets.json` for the specified environment
- ✅ **Validates secrets** - Confirms that secrets were loaded correctly
- ✅ **Backs up existing vault** - Creates timestamped backup if vault already exists
- ✅ **Sets proper file permissions** - 700 for directory, 600 for vault file
- ✅ **Export mode** - Use `--export` flag to export secrets as environment variables for Docker Compose

### Usage Examples

```bash
# Bootstrap vault for dev environment (only bootstraps)
./scripts/bootstrap-vault.sh ./secrets/secrets.json dev

# Bootstrap vault AND export env vars (bootstraps + exports)
source ./scripts/bootstrap-vault.sh ./secrets/secrets.json dev

# Bootstrap vault for dev-docker environment (Docker service names)
./scripts/bootstrap-vault.sh ./secrets/secrets.json dev-docker

# Export secrets as environment variables (vault must already be bootstrapped)
source ./scripts/bootstrap-vault.sh --export

# Export secrets with specific environment
VAULT_ENVIRONMENT=dev-docker source ./scripts/bootstrap-vault.sh --export
```

**Note:** `secrets.json` is used.

## Secret Keys to Bootstrap

The bootstrap script initializes these secrets from `secrets.json` (organized by category):

### Application Configuration
| Key | Description |
|-----|-------------|
| `spring.profiles.active` | Spring profile (dev, dev-docker, ec2) |

### Database Credentials
| Key | Description |
|-----|-------------|
| `mongodb.username` | MongoDB username |
| `mongodb.password` | MongoDB password |
| `neo4j.username` | Neo4j username |
| `neo4j.password` | Neo4j password |
| `neo4j.uri` | Neo4j connection URI (bolt://host:port) |
| `postgres.db` | PostgreSQL database name |
| `postgres.user` | PostgreSQL username |
| `postgres.password` | PostgreSQL password |

### Vector Database (Milvus)
| Key | Description |
|-----|-------------|
| `milvus.username` | Milvus username |
| `milvus.password` | Milvus password |
| `milvus.host` | Milvus host (localhost or service name) |
| `milvus.port` | Milvus port |

### Cache (Redis)
| Key | Description |
|-----|-------------|
| `redis.host` | Redis host (localhost or service name) |

### Gateway Configuration
| Key | Description |
|-----|-------------|
| `gateway.api.host` | Gateway API host (localhost or 0.0.0.0) |
| `gateway.api.port` | Gateway API port |
| `gateway.key.store` | Gateway keystore file path |
| `gateway.keystore.password` | Gateway keystore password |
| `gateway.trust.store` | Gateway truststore file path |
| `gateway.truststore.password` | Gateway truststore password |
| `gateway.alias.name` | Gateway keystore alias name |
| `gateway.client.key.store` | Gateway client keystore file path |
| `gateway.client.trust.store` | Gateway client truststore file path |
| `gateway.client.key.alias` | Gateway client keystore alias name |

### Eureka Service Discovery
| Key | Description |
|-----|-------------|
| `eureka.key.store` | Eureka keystore file path |
| `eureka.keystore.password` | Eureka keystore password |
| `eureka.trust.store` | Eureka truststore file path |
| `eureka.truststore.password` | Eureka truststore password |
| `eureka.alias.name` | Eureka keystore alias name |
| `eureka.gateway.url` | Eureka gateway URL (hostname or service name) |
| `eureka.instance.url` | Eureka instance URL (hostname or service name) |

### Keycloak OAuth2
| Key | Description |
|-----|-------------|
| `keycloak.client.secret` | Keycloak client secret |
| `keycloak.gateway.url` | Keycloak gateway URL (hostname or service name) |
| `keycloak.gateway.port` | Keycloak gateway port |
| `keycloak.admin` | Keycloak admin username |
| `keycloak.admin.password` | Keycloak admin password |
| `oauth2.redirect.uri` | OAuth2 redirect URI |

### Security
| Key | Description |
|-----|-------------|
| `jwt.secret` | JWT signing secret (auto-generated if not provided) |

### Email (SMTP)
| Key | Description |
|-----|-------------|
| `smtp.username` | SMTP username |
| `smtp.password` | SMTP password |
| `smtp.enabled` | SMTP enabled flag (true/false) |

### AWS S3
| Key | Description |
|-----|-------------|
| `aws.region` | AWS region |
| `aws.s3.bucket.name` | S3 bucket name |

### MinIO Object Storage
| Key | Description |
|-----|-------------|
| `minio.access.key` | MinIO access key |
| `minio.secret.key` | MinIO secret key |

### Notifications (Slack)
| Key | Description |
|-----|-------------|
| `slack.webhook.url` | Slack webhook URL (empty if disabled) |
| `slack.enabled` | Slack enabled flag (true/false) |

## Updating Secrets After Bootstrap

**IMPORTANT:** Secrets MUST NOT be created, updated, or read through api-gateway REST API.  
Use vault-reader CLI for ALL vault operations (create, read, update, delete).

### Using vault-reader CLI (ONLY Method)

```bash
# Update a secret
java -jar vault-reader/target/vault-reader.jar \
  --operation write \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY" \
  --environment dev \
  --key mongodb.password \
  --value "actual-password-123"

# Read a secret
java -jar vault-reader/target/vault-reader.jar \
  --operation read \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY" \
  --environment dev \
  --key mongodb.password

# List all secrets
java -jar vault-reader/target/vault-reader.jar \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY" \
  --environment dev \
  --format json
```

**Note:** api-gateway only decrypts secrets internally via `VaultPropertySource` for its own configuration.  
It does NOT expose any REST endpoints for vault operations.

## Security Notes

1.  **Master Key:**
    - Store securely (AWS Secrets Manager, environment variable, `.env` file)
    - Never commit to git
    - Rotate periodically

2.  **Vault File:**
    - Permissions: `600` (owner read/write only)
    - Backup regularly
    - Store in secure location
    - **Persistence**: Vault file persists on host filesystem via Docker volume mount (`./secrets:/app/secrets`)

3.  **Bootstrap Script:**
    - Run only in secure environment
    - Update placeholder values immediately
    - Remove sensitive values from environment after bootstrap

## Vault Persistence

The vault file is stored on the **host machine's filesystem** (not inside containers), ensuring it persists across container restarts and deployments.

### Docker Volume Mount

```yaml
# docker-compose.yml
volumes:
  - ./secrets:/app/secrets:rw  # Host path → Container path
```

**Important:**
- **Host Path**: `./secrets/vault.encrypted` (on your machine/EC2 instance)
- **Container Path**: `/app/secrets/vault.encrypted` (inside container)
- The file on the host persists even when containers are destroyed/recreated
- Always create the secrets directory on the host before starting containers:
  ```bash
  mkdir -p secrets
  chmod 700 secrets
  ```

## Troubleshooting

### "Vault file not found"
```bash
# Ensure vault directory exists
mkdir -p ./secrets
chmod 700 ./secrets
```

### "Master key invalid"
```bash
# Verify master key is base64-encoded
echo "$VAULT_MASTER_KEY" | base64 -d > /dev/null
```

### "Cannot decrypt vault"
```bash
# Verify master key matches the one used to create vault
# Check vault was created with same master key
```

## Summary

✅ **Bootstrap vault BEFORE starting api-gateway** using `bootstrap-vault.sh` script  
✅ **Loads all secrets from `secrets.json`** for the specified environment (dev, dev-docker, ec2)  
✅ **Auto-loads `.env` file** - No need to manually source it first  
✅ **Always rebuilds `vault-reader.jar`** - Ensures CLI tool is up-to-date  
✅ **Export secrets for Docker Compose** - Use `--export` flag to load env vars for third-party services  
✅ **Update secrets** using vault-reader CLI ONLY (NO REST API access)  
✅ **Start services** - api-gateway decrypts secrets internally via `VaultPropertySource`  
✅ **No REST API endpoints** - All vault operations via vault-reader CLI for security  
✅ **No chicken-and-egg problem!** 🎉

### Quick Start

```bash
# 1. Generate master key (if not already in .env)
openssl rand -base64 32

# 2. Add to .env file
echo "VAULT_MASTER_KEY=your-generated-key" >> .env
echo "VAULT_ENVIRONMENT=dev" >> .env

# 3. Bootstrap vault AND export env vars (automatically loads .env)
# Note: The script uses secrets.json
source ./scripts/bootstrap-vault.sh ./secrets/secrets.json dev

# 4. Start services (env vars are already exported from step 3)
docker-compose -f docker-compose-dev.yml up

# Alternative: Bootstrap and export separately
# ./scripts/bootstrap-vault.sh ./secrets/secrets.json dev
# source ./scripts/bootstrap-vault.sh --export
# docker-compose -f docker-compose-dev.yml up
```

The `bootstrap-vault.sh` script consolidates all vault operations into a single, user-friendly tool!
