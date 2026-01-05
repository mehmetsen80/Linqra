# Vault Security Guide - Preventing Unauthorized Decryption

## Current Security Measures

### ✅ Already Implemented

1. **File Permissions**
   - Vault file (`secrets/vault.encrypted`) is automatically set to `600` (owner read/write only)
   - Implemented in `VaultReaderCli.java` (lines 427-434)
   - Your vault file shows: `-rw-------` ✅

2. **Encryption**
   - AES-256-GCM encryption (industry standard)
   - Environment-specific keys (HMAC-SHA256 derivation)
   - Prevents tampering (authenticated encryption)

3. **Master Key Storage**
   - Stored in `.env` file (gitignored)
   - Not committed to version control

## Security Risks & Mitigations

### ⚠️ Risk: Unauthorized User Gets Both Files

**Scenario:** An unauthorized user gains access to:
- The vault file (`secrets/vault.encrypted`)
- The master key (`.env` file or environment variable)

**Result:** They can decrypt all secrets.

### 🛡️ Recommended Security Layers

#### 1. **Protect `.env` File** (HIGH PRIORITY)

```bash
# Set .env file permissions to 600 (owner read/write only)
chmod 600 .env

# Verify permissions
ls -l .env
# Should show: -rw------- (600)

# Set ownership (if shared server)
chown $USER:$USER .env
```

**Add to `bootstrap-vault.sh`** to automatically protect `.env`:
```bash
# Protect .env file permissions
if [ -f "$ENV_FILE" ]; then
    chmod 600 "$ENV_FILE"
    echo "✓ Protected .env file permissions (600)"
fi
```

#### 2. **Protect `secrets/` Directory** (HIGH PRIORITY)

```bash
# Set directory permissions to 700 (owner read/write/execute only)
chmod 700 secrets/

# Verify
ls -ld secrets/
# Should show: drwx------ (700)
```

#### 3. **Use OS-Level Access Controls** (MEDIUM PRIORITY)

**Linux/Unix:**
```bash
# Create dedicated user for vault access
sudo useradd -r -s /bin/false vault-user

# Change ownership
sudo chown -R vault-user:vault-user secrets/
sudo chown vault-user:vault-user .env

# Set permissions
sudo chmod 600 .env
sudo chmod 700 secrets/
sudo chmod 600 secrets/vault.encrypted

# Run services as vault-user
# (Update docker-compose or use sudo -u vault-user)
```

**Docker Containers:**
- Containers already run as non-root users (good!)
- Volume mounts preserve host permissions
- Ensure host user has correct permissions

#### 4. **Use External Key Management** (RECOMMENDED FOR PRODUCTION)

**Option A: AWS Secrets Manager**
```bash
# Store master key in AWS Secrets Manager
aws secretsmanager create-secret \
  --name linqra/vault-master-key \
  --secret-string "your-base64-key"

# Retrieve at runtime
VAULT_MASTER_KEY=$(aws secretsmanager get-secret-value \
  --secret-id linqra/vault-master-key \
  --query SecretString --output text)
```

**Option B: HashiCorp Vault**
```bash
# Store master key in HashiCorp Vault
vault kv put secret/linqra vault-master-key="your-base64-key"

# Retrieve at runtime
VAULT_MASTER_KEY=$(vault kv get -field=vault-master-key secret/linqra)
```

**Option C: Kubernetes Secrets** (if using K8s)
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: vault-master-key
type: Opaque
stringData:
  key: "your-base64-key"
```

#### 5. **Audit Logging** (RECOMMENDED)

Add logging to track vault access:
- Who accessed the vault file
- When it was accessed
- Which secrets were read
- Failed decryption attempts

**Implementation:** Add audit logging to `VaultReaderCli.java`:
```java
// Log vault access
System.err.println("AUDIT: User=" + System.getProperty("user.name") + 
                   " Time=" + Instant.now() + 
                   " File=" + vaultFilePath +
                   " Environment=" + environment);
```

#### 6. **Separate Master Keys Per Environment** (RECOMMENDED)

Use different master keys for:
- Development
- Staging  
- Production

This limits blast radius if one key is compromised.

#### 7. **Key Rotation** (RECOMMENDED)

Periodically rotate the master key:
1. Generate new master key
2. Create new vault file with new key
3. Migrate secrets to new vault
4. Update `.env` with new key
5. Delete old vault file

#### 8. **Limit Access to Vault Reader JAR** (LOW PRIORITY)

```bash
# Restrict access to vault-reader.jar
chmod 750 vault-reader/target/vault-reader.jar
chown root:vault-group vault-reader/target/vault-reader.jar
```

## Implementation Checklist

### ✅ Immediate Actions (Do Now)

- [ ] Verify `.env` file permissions: `chmod 600 .env`
- [ ] Verify `secrets/` directory permissions: `chmod 700 secrets/`
- [ ] Verify vault file permissions: `chmod 600 secrets/vault.encrypted`
- [ ] Check `.env` is in `.gitignore` (already done ✅)
- [ ] Verify vault file is in `.gitignore` (already done ✅)

### 🔄 Recommended Improvements

- [ ] Add automatic permission setting to `bootstrap-vault.sh`
- [ ] Implement audit logging in vault-reader
- [ ] Use separate master keys per environment
- [ ] Move master key to external secrets manager (production)
- [ ] Set up key rotation process
- [ ] Document key recovery process

## Quick Security Audit

Run this to check your current security setup:

```bash
#!/bin/bash
echo "=== Vault Security Audit ==="

# Check .env file
if [ -f .env ]; then
    PERMS=$(stat -f "%A" .env 2>/dev/null || stat -c "%a" .env 2>/dev/null)
    if [ "$PERMS" = "600" ]; then
        echo "✅ .env permissions: $PERMS (secure)"
    else
        echo "⚠️  .env permissions: $PERMS (should be 600)"
        echo "   Fix: chmod 600 .env"
    fi
else
    echo "⚠️  .env file not found"
fi

# Check secrets directory
if [ -d secrets ]; then
    PERMS=$(stat -f "%A" secrets 2>/dev/null || stat -c "%a" secrets 2>/dev/null)
    if [ "$PERMS" = "700" ]; then
        echo "✅ secrets/ permissions: $PERMS (secure)"
    else
        echo "⚠️  secrets/ permissions: $PERMS (should be 700)"
        echo "   Fix: chmod 700 secrets/"
    fi
else
    echo "⚠️  secrets/ directory not found"
fi

# Check vault file
if [ -f secrets/vault.encrypted ]; then
    PERMS=$(stat -f "%A" secrets/vault.encrypted 2>/dev/null || stat -c "%a" secrets/vault.encrypted 2>/dev/null)
    if [ "$PERMS" = "600" ]; then
        echo "✅ vault.encrypted permissions: $PERMS (secure)"
    else
        echo "⚠️  vault.encrypted permissions: $PERMS (should be 600)"
        echo "   Fix: chmod 600 secrets/vault.encrypted"
    fi
else
    echo "⚠️  vault.encrypted file not found"
fi

# Check gitignore
if grep -q "^\.env$" .gitignore 2>/dev/null; then
    echo "✅ .env is in .gitignore"
else
    echo "⚠️  .env not found in .gitignore"
fi

if grep -q "vault.encrypted" .gitignore 2>/dev/null; then
    echo "✅ vault.encrypted is in .gitignore"
else
    echo "⚠️  vault.encrypted not found in .gitignore"
fi

echo "=== Audit Complete ==="
```

## Summary

**Current Protection:**
- ✅ Vault file encrypted (AES-256-GCM)
- ✅ Vault file permissions (600)
- ✅ Git ignore configured

**Critical Improvements:**
1. Protect `.env` file with `chmod 600 .env`
2. Protect `secrets/` directory with `chmod 700 secrets/`
3. Use external key management for production

**Best Practice:**
- **Development:** File permissions + `.env` file (current setup)
- **Production:** File permissions + External secrets manager (AWS Secrets Manager, HashiCorp Vault, etc.)

The **strongest protection** is ensuring unauthorized users cannot access either:
- The vault file (`secrets/vault.encrypted`)
- The master key (`.env` file or environment variable)

File permissions are your first line of defense!

