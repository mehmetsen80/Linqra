# Chunk Encryption Key Rotation Guide

## Overview

This guide explains how the chunk encryption key rotation system works. **Each team can rotate their own encryption keys independently**, providing better security and flexibility for multi-tenant deployments.

## How Key Architecture Works

The system uses a **two-tier key architecture**:

### 1. Global Master Key (Key Encryption Key - KEK)
- **Location**: Stored in `secrets.json` as `chunk.encryption.master.key`
- **Purpose**: Acts as the Key Encryption Key (KEK) for encrypting team-specific keys
- **Version**: Always treated as "v1" (base version)
- **Usage**: 
  - Encrypts/decrypts team keys stored in MongoDB
  - Used for legacy v1 key derivation (backward compatibility)

### 2. Team-Specific Encryption Keys
- **Location**: Stored in MongoDB `team_chunk_keys` collection
- **Purpose**: Each team has their own encryption key(s) for data encryption
- **Storage**: Team keys are encrypted with the Global Master Key before storage
- **Versions**: Each team can have multiple versions (v2, v3, v4, etc.)
- **Active Key**: Only one active key per team at a time (`isActive: true`)

### Key Version Logic

1. **v1 (Legacy)**: Derived on-the-fly using `HMAC-SHA256(globalMasterKey, teamId)`
   - No database record needed
   - Default fallback if no active team key exists
   - Ensures backward compatibility

2. **v2+ (Team Keys)**: Stored in MongoDB, encrypted with Global Master Key
   - Each team can rotate independently
   - New encryptions use the active key version
   - Old versions remain available for decryption

## How Key Retrieval Works

When encrypting/decrypting data:

1. System checks for active team key in MongoDB (`isActive: true`)
2. If found, uses that version (decrypts with Global Master Key)
3. If not found, falls back to v1 (derived from Global Master Key + teamId)
4. Data is encrypted/decrypted using the appropriate team key

## Per-Team Key Rotation

### Rotating a Team's Encryption Key

Each team can rotate their encryption key independently using the rotation API:

```bash
# Rotate key for a specific team (requires ADMIN or SUPER_ADMIN role)
POST /api/v1/teams/{teamId}/encryption/keys/rotate
Authorization: Bearer <token>
```

**What happens during rotation:**

1. System finds the current active key version (e.g., v2)
2. Generates a new random AES-256 key
3. Encrypts the new key with the Global Master Key
4. Deactivates all existing active keys for that team
5. Creates new key record with next version (e.g., v3) as `isActive: true`
6. New encryptions will use the new key version
7. Old data can still be decrypted using old key versions

**Example:**
```java
// Before rotation
Team "team-123":
  - v2 (active) - can decrypt old data
  - v3 (active) - used for new encryptions ←

// After rotation
Team "team-123":
  - v2 (inactive) - can decrypt old data
  - v3 (inactive) - can decrypt old data
  - v4 (active) - used for new encryptions ←
```

### Key Rotation via API

The rotation is handled by `ChunkEncryptionService.rotateKey(teamId)`:

```java
Mono<String> rotateKey(String teamId);
```

This method:
- Generates a new AES-256 encryption key
- Encrypts it with the Global Master Key
- Saves it to MongoDB with next version number
- Deactivates previous active keys
- Returns the new version number

### Key Rotation via Controller (if implemented)

If there's a REST endpoint:

```bash
# Rotate encryption key for a team
curl -X POST \
  http://localhost:8080/api/teams/team-123/encryption/rotate-key \
  -H "Authorization: Bearer <token>"
```

**Response:**
```json
{
  "teamId": "team-123",
  "newVersion": "v3",
  "message": "Key rotation completed successfully"
}
```

## Data Migration After Rotation

After rotating a team's key:

### Automatic Behavior
- ✅ New data is automatically encrypted with the new key version
- ✅ Old data can still be decrypted (system tries all versions if needed)
- ✅ No data migration required for existing encrypted chunks

### Gradual Migration (Optional)

If you want to re-encrypt old data with the new key:

1. **Hard delete old chunks/documents** (via UI or API)
2. **Re-upload documents** - they will be encrypted with the new key version automatically

Or use bulk migration scripts to:
- Read old encrypted data (using old key version)
- Re-encrypt with new key version
- Update `encryptionKeyVersion` field

## Global Master Key Changes

**Important**: Changing the Global Master Key is different from team key rotation.

### If Global Master Key Changes:

**What happens:**
- ⚠️ All team keys stored in MongoDB become undecryptable (they're encrypted with the old Global Master Key)
- ⚠️ Legacy v1 derived keys will change (they're derived from the Global Master Key)
- ⚠️ All encrypted data becomes undecryptable

**This is a CRITICAL operation** that requires:
1. Decrypting all team keys with OLD Global Master Key
2. Re-encrypting all team keys with NEW Global Master Key
3. Updating all data encrypted with v1 (since derivation will change)

**Recommendation**: Avoid changing the Global Master Key. Treat it as a long-term infrastructure key.

## Common Scenarios

### Scenario 1: Team Wants to Rotate Their Key

```bash
# Rotate key for team-123
POST /api/teams/team-123/encryption/rotate-key

# Result: New version v3 created, v2 deactivated
# - Old data (encrypted with v2) still decryptable
# - New data encrypted with v3
```

### Scenario 2: Multiple Teams Rotate Independently

```bash
# Team A rotates
POST /api/teams/team-a/encryption/rotate-key
# Result: team-a now uses v5

# Team B rotates (independent)
POST /api/teams/team-b/encryption/rotate-key  
# Result: team-b now uses v3

# Teams are independent - no conflicts
```

### Scenario 3: New Team Created

When a new team is created:
- No team key exists in MongoDB
- System defaults to v1 (derived from Global Master Key + teamId)
- First encryption/decryption works automatically
- Team can rotate to v2+ when ready

## Key Security Model

```
┌─────────────────────────────────────────┐
│   Global Master Key (v1)                │
│   Stored in: secrets.json               │
│   Purpose: KEK for team keys            │
└───────────────┬─────────────────────────┘
                │
                ├─────────────────┬──────────────────┐
                │                 │                  │
        ┌───────▼──────┐  ┌──────▼──────┐  ┌───────▼────────┐
        │  Team A Key  │  │  Team B Key │  │  Team C Key    │
        │  v2 (active) │  │  v4 (active)│  │  v1 (derived)  │
        │  Encrypted   │  │  Encrypted  │  │  (no DB entry) │
        │  with KEK    │  │  with KEK   │  │                │
        └──────────────┘  └─────────────┘  └────────────────┘
                │                 │                  │
                └─────────────────┴──────────────────┘
                          │
                   ┌──────▼──────┐
                   │   Encrypted │
                   │    Data     │
                   │  (Chunks)   │
                   └─────────────┘
```

## Database Schema

### `team_chunk_keys` Collection

```javascript
{
  _id: ObjectId,
  teamId: String (indexed),
  version: String,           // "v2", "v3", "v4", etc.
  encryptedKey: String,      // AES-256 key encrypted with Global Master Key
  isActive: Boolean,         // Only one active per team
  createdAt: Date
}

// Indexes
- Compound index: {teamId: 1, version: 1} (unique)
- Index: {teamId: 1, isActive: 1} (for finding active key)
```

## Best Practices

### ✅ DO:
- Rotate team keys periodically (e.g., annually or after security incidents)
- Keep Global Master Key secure and avoid changing it
- Monitor key rotation events in audit logs
- Test key rotation in development before production

### ❌ DON'T:
- Don't change Global Master Key without a migration plan
- Don't manually delete team keys from MongoDB (use rotation API)
- Don't share team keys between teams
- Don't store Global Master Key in code or version control

## Troubleshooting

### Issue: "Encryption key not found: v3"

**Cause**: Team key doesn't exist in MongoDB for that version.

**Solution**: 
- Check if team has any keys: `db.team_chunk_keys.find({teamId: "team-123"})`
- If no keys exist, system will use v1 (derived)
- If key exists but wrong version, check the `encryptionKeyVersion` stored with the data

### Issue: "Failed to decrypt team key from storage"

**Cause**: Global Master Key changed or is incorrect.

**Solution**:
- Verify Global Master Key in `secrets.json` matches what was used to encrypt team keys
- If Global Master Key was changed, you need to decrypt and re-encrypt all team keys

### Issue: Multiple active keys for same team

**Cause**: Race condition or manual database edit.

**Solution**:
- Use rotation API to properly rotate (it handles deactivation)
- Or manually deactivate old keys: `db.team_chunk_keys.updateMany({teamId: "team-123", isActive: true}, {$set: {isActive: false}})`
- Then set one as active: `db.team_chunk_keys.updateOne({teamId: "team-123", version: "v3"}, {$set: {isActive: true}})`

## Quick Reference

### Rotate Team Key
```bash
# Via API endpoint
POST /api/v1/teams/{teamId}/encryption/keys/rotate
# Requires: ADMIN or SUPER_ADMIN role for the team

# Or programmatically
chunkEncryptionService.rotateKey(teamId)
```

### Check Team Key Status
```javascript
// MongoDB query
db.team_chunk_keys.find({teamId: "team-123"}).sort({version: 1})

// Check active key
db.team_chunk_keys.findOne({teamId: "team-123", isActive: true})
```

### List All Team Keys
```javascript
db.team_chunk_keys.find({}).sort({teamId: 1, version: 1})
```

## Why This Architecture?

1. **Independent Rotation**: Teams can rotate keys independently without affecting others
2. **Backward Compatible**: Legacy v1 keys still work (derived on-the-fly)
3. **Scalable**: No global key rotation needed - teams manage their own keys
4. **Secure**: Team keys encrypted at rest with Global Master Key
5. **Flexible**: Teams can rotate on their own schedule or after security incidents
6. **Multi-Tenant**: Perfect for SaaS where different teams need different key rotation policies
