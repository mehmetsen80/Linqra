# Chunk Text Encryption Options for Law Firm Confidentiality

## Executive Summary

**Current Status:**
- ✅ **Vault System**: Fully implemented and operational (AES-256-GCM encrypted vault file)
- ✅ **Chunk Encryption**: Fully implemented and operational
  - ✅ `ChunkEncryptionService` implemented with AES-256-GCM encryption
  - ✅ MongoDB chunk text encryption/decryption
  - ✅ Milvus vector database chunk text encryption/decryption
  - ✅ Neo4j Knowledge Graph entity property encryption/decryption
  - ✅ Document metadata encryption/decryption
  - ✅ Processed JSON (S3) encryption/decryption
  - ✅ Encryption key version tracking implemented
  - ✅ Decryption API endpoint for frontend access

**Goal:** Encrypt all chunk text at rest so database administrators cannot view confidential client data, meeting law firm compliance requirements (HIPAA, GDPR, ABA Model Rules, etc.).

**Next Steps (Optional):**
1. ⚠️ Migrate existing unencrypted chunks (if any legacy data exists)
2. ⚠️ Implement audit logging for encryption/decryption operations
3. ⚠️ Key rotation testing and documentation

---

## Problem Statement
Law firms need to upload confidential client data to Linqra AI Assistant. The chunk text data must be encrypted so that:
- It cannot be seen in plaintext in MongoDB chunks collection
- It cannot be seen in plaintext in Milvus vector database
- Even database administrators cannot view the actual content
- Only authorized users can decrypt and view chunks through the application

## Current Architecture

### ✅ Already Implemented
- **Vault System**: Custom vault system using `vault.encrypted` file with AES-256-GCM encryption
  - Master key stored in `VAULT_MASTER_KEY` environment variable
  - Vault-reader CLI for managing secrets
  - `VaultPropertySource` for reading secrets at application startup
  - File permissions: `600` (owner read/write only)
  - Directory permissions: `700` for secrets directory
  - Supports multiple environments (dev, ec2, etc.)
  - `chunk.encryption.master.key` stored in vault

- **ChunkEncryptionService**: Application-level encryption service
  - ✅ AES-256-GCM encryption with authenticated encryption
  - ✅ Per-team encryption keys:
    - Legacy v1: Derived from Global Master Key + teamId using HMAC-SHA256 (backward compatibility)
    - v2+: Stored in MongoDB `team_chunk_keys` collection, encrypted with Global Master Key
    - Independent per-team key rotation (each team can rotate to v2, v3, etc. independently)
  - ✅ Encryption key version tracking support (v1, v2, etc.)
  - ✅ Integration with LinqraVaultService for key retrieval

- **MongoDB Chunk Encryption**: 
  - ✅ `KnowledgeHubChunk.text` field encrypted before storage
  - ✅ `encryptionKeyVersion` field added to track key version
  - ✅ Automatic decryption during retrieval for embedding generation
  - ✅ Implemented in `KnowledgeHubDocumentProcessingServiceImpl`

- **Milvus Vector Database Encryption**:
  - ✅ Chunk `text` field encrypted before storage in Milvus
  - ✅ Automatic decryption during vector search retrieval
  - ✅ `encryptionKeyVersion` stored with Milvus records
  - ✅ Implemented in `KnowledgeHubDocumentEmbeddingServiceImpl` and `LinqMilvusStoreServiceImpl`

- **Neo4j Knowledge Graph Entity Encryption**:
  - ✅ Sensitive entity properties encrypted before storage
  - ✅ Property-level encryption version tracking (`[property]_encryption_version`)
  - ✅ Entity-level encryption version tracking (`encryptionKeyVersion`)
  - ✅ Automatic decryption during entity retrieval
  - ✅ Sensitive properties encrypted: name, address, contactInfo, email, phone, documentNumber, etc.
  - ✅ Implemented in `Neo4jGraphServiceImpl`

- **Document Metadata Encryption**:
  - ✅ `KnowledgeHubDocumentMetaData` sensitive fields encrypted
  - ✅ Fields encrypted: title, author, subject, keywords, creator, producer, customMetadata
  - ✅ `encryptionKeyVersion` field added to metadata
  - ✅ Automatic decryption during metadata retrieval
  - ✅ Implemented in `KnowledgeHubDocumentMetaDataServiceImpl`

- **Processed JSON (S3) Encryption**:
  - ✅ `ProcessedDocumentDto` sensitive fields encrypted before saving to S3
  - ✅ Chunk text, extracted metadata, and form field values encrypted
  - ✅ `encryptionKeyVersion` stored at DTO and nested object levels
  - ✅ Automatic decryption when reading from S3
  - ✅ Implemented in `KnowledgeHubDocumentProcessingServiceImpl` and `KnowledgeHubDocumentEmbeddingServiceImpl`

- **Frontend Access**:
  - ✅ Decrypted API endpoint: `GET /api/documents/view/{documentId}/processed`
  - ✅ Returns decrypted processed JSON for frontend viewing
  - ✅ Team-based access control and automatic decryption
  - ✅ Implemented in `KnowledgeHubDocumentViewController`

### ⚠️ Future Enhancements (Optional)
- **Audit Logging**: Track all encryption/decryption operations for compliance
- **Key Rotation**: Automated key rotation with gradual migration support
- **Performance Monitoring**: Metrics for encryption/decryption operations

**Goal**: Encrypt chunk text and entity properties at rest so database administrators cannot view confidential client data.

## What Was Implemented

The following encryption features have been fully implemented and are operational:

### Core Encryption Service
- **`ChunkEncryptionService`** interface and implementation
  - AES-256-GCM encryption with authenticated encryption
  - Per-team encryption keys:
    - Legacy v1: Derived on-the-fly using HMAC-SHA256(Global Master Key, teamId)
    - v2+: Stored in MongoDB, encrypted with Global Master Key (Key Encryption Key)
    - Independent per-team rotation (teams rotate to v2, v3, v4, etc. independently)
  - Automatic key version detection (checks MongoDB for active team key, falls back to v1)
  - Base64 encoding for storage

### Data Storage Encryption
1. **MongoDB Chunks** (`KnowledgeHubChunk`)
   - `text` field encrypted before storage
   - `encryptionKeyVersion` field tracks key version
   - Implemented in: `KnowledgeHubDocumentProcessingServiceImpl`

2. **Milvus Vector Database**
   - Chunk `text` field encrypted in vector records
   - `encryptionKeyVersion` stored with records
   - Automatic decryption during vector search
   - Implemented in: `KnowledgeHubDocumentEmbeddingServiceImpl`, `LinqMilvusStoreServiceImpl`

3. **Neo4j Knowledge Graph**
   - Sensitive entity properties encrypted (name, address, contactInfo, email, phone, etc.)
   - Property-level and entity-level encryption version tracking
   - Automatic decryption during entity retrieval
   - Implemented in: `Neo4jGraphServiceImpl`

4. **Document Metadata** (`KnowledgeHubDocumentMetaData`)
   - Sensitive fields encrypted: title, author, subject, keywords, creator, producer, customMetadata
   - `encryptionKeyVersion` field added
   - Implemented in: `KnowledgeHubDocumentMetaDataServiceImpl`

5. **Processed JSON (S3)**
   - `ProcessedDocumentDto` sensitive fields encrypted before saving to S3
   - Chunks, extracted metadata, and form fields encrypted
   - `encryptionKeyVersion` at DTO and nested object levels
   - Automatic decryption when reading from S3
   - Implemented in: `KnowledgeHubDocumentProcessingServiceImpl`, `KnowledgeHubDocumentEmbeddingServiceImpl`

### API Endpoints
- **Frontend Decryption Endpoint**: `GET /api/documents/view/{documentId}/processed`
  - Returns decrypted processed JSON for frontend viewing
  - Team-based access control
  - Automatic decryption
  - Implemented in: `KnowledgeHubDocumentViewController`

### Key Management
- Master key stored in vault: `chunk.encryption.master.key`
- Key version tracking: Each encrypted chunk/entity stores `encryptionKeyVersion` field
- Integration with `LinqraVaultService` for Global Master Key retrieval
- Per-team key storage in MongoDB `team_chunk_keys` collection
- Independent per-team key rotation (teams rotate to v2, v3, v4, etc. independently)

### Key Rotation Strategy
- **Per-Team Rotation**: Each team rotates their keys independently via API
  - `POST /api/v1/teams/{teamId}/encryption/keys/rotate`
  - Generates new team key, encrypts with Global Master Key, stores in MongoDB
  - Old data still decryptable with old key versions
  - New data encrypted with new active key version
  
- **Data Migration (Optional)**: Hard Delete + Re-encrypt
  - Delete all encrypted data for a team
  - Re-process documents from source
  - Re-encrypt with new key version
  - Cleaner codebase, no dual-key migration logic needed

## Encryption Options

### Option 1: Application-Level Field Encryption ⭐ RECOMMENDED

**Approach**: Encrypt chunk text at the application layer before storing in MongoDB and Milvus. Decrypt only when needed for embedding generation and display.

**Implementation**:
- Use AES-256-GCM encryption (authenticated encryption)
- Per-team encryption keys:
  - Legacy v1: Derived from Global Master Key + teamId (HMAC-SHA256)
  - v2+: Stored in MongoDB, encrypted with Global Master Key (per-team rotation)
- Encrypt `text` field before saving to MongoDB
- Encrypt `text` field before saving to Milvus
- **Encrypt sensitive entity properties** before saving to Neo4j Knowledge Graph
- Decrypt when:
  - Generating embeddings (needs plaintext)
  - Displaying chunks in AI responses (needs plaintext)
  - Querying/displaying entities in Knowledge Graph (needs plaintext)

**Pros**:
- ✅ Full control over encryption/decryption
- ✅ Database admins cannot read encrypted data
- ✅ Can encrypt only sensitive fields (text, entity properties), leave metadata unencrypted
- ✅ Per-team keys provide tenant isolation
- ✅ Can audit all decryption operations
- ✅ Works with existing MongoDB, Milvus, and Neo4j infrastructure
- ✅ Embeddings remain searchable (vectors are not affected)
- ✅ Entity relationships remain searchable (only properties encrypted, not graph structure)

**Cons**:
- ❌ Requires encryption/decryption on every read/write (performance overhead ~5-10%)
- ❌ Need secure key management (AWS KMS, HashiCorp Vault, or similar)
- ❌ Key rotation requires re-encryption of all chunks **and entities**
- ❌ Need to handle decryption errors gracefully
- ❌ Neo4j property queries become more complex (must decrypt to search/filter)

**Key Management Options**:
1. **AWS KMS** (Recommended for AWS deployments)
   - Use Customer Master Keys (CMK) per team
   - Automatic key rotation
   - Audit logging
   
2. **HashiCorp Vault**
   - Key versioning support
   - Key rotation policies
   - Fine-grained access control

3. **Linqra Vault System** ⭐ **RECOMMENDED - Already Implemented**
   - Master key stored in `VAULT_MASTER_KEY` environment variable
   - Secrets managed via `vault.encrypted` file (AES-256-GCM encrypted)
   - Vault-reader CLI for key management operations
   - Integrates with existing vault infrastructure
   - Store `chunk.encryption.master.key` (Global Master Key) as a secret in vault
   - Global Master Key acts as Key Encryption Key (KEK) for team keys
   - Legacy v1 keys: Derived on-the-fly using `HMAC-SHA256(Global Master Key, teamId)`
   - v2+ keys: Stored in MongoDB `team_chunk_keys` collection, encrypted with Global Master Key
   - Teams can rotate their keys independently (v2, v3, v4, etc.)
   - File permissions protect vault file (`600` for file, `700` for directory)

**Performance Impact**:
- Encryption: ~1-2ms per chunk (negligible for async processing)
- Decryption: ~1-2ms per chunk (only during embedding and retrieval)
- Overall: <10% performance overhead

---

### Option 2: MongoDB Client-Side Field-Level Encryption (FLE)

**Approach**: Use MongoDB's built-in Client-Side Field-Level Encryption feature to automatically encrypt/decrypt fields.

**Implementation**:
- Configure MongoDB FLE with encryption schema
- Mark `text` field as encrypted in the schema
- MongoDB Java driver handles encryption/decryption transparently
- Requires Key Management Service (KMS) integration

**Pros**:
- ✅ Transparent to application code (driver handles it)
- ✅ Automatic encryption/decryption
- ✅ Industry-standard implementation
- ✅ Supports encryption key rotation

**Cons**:
- ❌ Complex setup (requires KMS, encryption schemas, key documents)
- ❌ Milvus text field still needs separate encryption (FLE doesn't help there)
- ❌ Can be harder to debug encryption issues
- ❌ Limited flexibility compared to application-level encryption
- ❌ Still need to encrypt Milvus chunks separately

**Verdict**: Not ideal because Milvus doesn't support FLE, so you'd need both FLE and application-level encryption.

---

### Option 3: Transparent Data Encryption (TDE)

**Approach**: Encrypt entire databases at rest using database-level encryption.

**Implementation**:
- MongoDB: Enable WiredTiger encryption at rest
- Milvus: Use filesystem-level encryption or database encryption
- Encryption keys managed by the database engine

**Pros**:
- ✅ Simple to enable (database-level configuration)
- ✅ Encrypts all data automatically
- ✅ Good for compliance requirements (encryption at rest)

**Cons**:
- ❌ **Does NOT protect from database administrators** (they can still read data)
- ❌ Data is decrypted in memory (accessible to anyone with DB access)
- ❌ Not suitable for law firm confidentiality requirements
- ❌ Doesn't solve the "admin can't see data" requirement

**Verdict**: ❌ **Not sufficient** - does not meet the requirement that admins cannot view data.

---

### Option 4: Hybrid Approach (Application + TDE)

**Approach**: Combine application-level field encryption with TDE for defense in depth.

**Implementation**:
- Application-level encryption for chunk text (meets admin access requirement)
- TDE for additional protection at rest
- SSL/TLS for encryption in transit

**Pros**:
- ✅ Defense in depth (multiple layers)
- ✅ Meets compliance requirements
- ✅ Protects against admin access (application-level)
- ✅ Protects against physical access (TDE)

**Cons**:
- ❌ Most complex implementation
- ❌ Highest performance overhead
- ❌ Most expensive (KMS costs, TDE overhead)

**Verdict**: Overkill for most use cases, but good for high-security environments.

---

## Recommended Implementation: Option 1 (Application-Level Encryption)

### Architecture Overview

```
┌─────────────────┐
│  Client Upload  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────┐
│   Chunk Text    │────▶│   Encrypt    │ (AES-256-GCM)
│   (Plaintext)   │     │  with Team   │
└─────────────────┘     │     Key      │
                        └──────┬───────┘
                               │
                ┌──────────────┼──────────────┬──────────────┐
                │              │              │              │
                ▼              ▼              ▼              ▼
        ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
        │   MongoDB   │ │   Milvus    │ │   Embedding │ │    Neo4j    │
        │  (Encrypted)│ │  (Encrypted)│ │ Generation  │ │ Knowledge   │
        │     Text    │ │     Text    │ │  (Decrypt)  │ │    Graph    │
        └─────────────┘ └─────────────┘ └──────┬──────┘ │(Encrypted   │
                                                │        │ Properties) │
                                                ▼        └─────────────┘
                                        ┌──────────────┐
                                        │   Embedding  │
                                        │    Vector    │
                                        └──────────────┘

Vector Search Flow:
┌─────────────────┐     ┌──────────────┐     ┌──────────────┐
│  User Query     │────▶│ Vector Search│────▶│ Find Chunks  │
└─────────────────┘     │   (Milvus)   │     │  by ChunkId  │
                        └──────────────┘     └──────┬───────┘
                                                     │
                                                     ▼
                                            ┌──────────────┐
                                            │   Decrypt    │
                                            │  Chunk Text  │
                                            └──────┬───────┘
                                                   │
                                                   ▼
                                            ┌──────────────┐
                                            │  Return to   │
                                            │ AI Assistant │
                                            └──────────────┘

Knowledge Graph Entity Flow:
┌─────────────────┐     ┌──────────────┐     ┌──────────────┐
│ Entity Extract  │────▶│   Encrypt    │────▶│ Store Entity │
│  (Plaintext)    │     │   Sensitive  │     │  in Neo4j    │
│                 │     │  Properties  │     │  (Encrypted) │
└─────────────────┘     └──────────────┘     └──────┬───────┘
                                                      │
                                                      │ (Query)
                                                      ▼
                                            ┌──────────────┐
                                            │   Decrypt    │
                                            │  Properties  │
                                            └──────┬───────┘
                                                   │
                                                   ▼
                                            ┌──────────────┐
                                            │  Return to   │
                                            │ AI Assistant │
                                            └──────────────┘
```

### Implementation Steps

#### Step 1: Create Encryption Service

```java
@Service
public class ChunkEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    
    @Autowired
    private LinqraVaultService vaultService; // Read from vault system
    
    private String masterKeyBase64;
    
    @PostConstruct
    public void init() {
        // Read encryption master key from vault
        this.masterKeyBase64 = vaultService.getSecret("chunk.encryption.master.key");
        if (this.masterKeyBase64 == null || this.masterKeyBase64.isEmpty()) {
            throw new IllegalStateException("chunk.encryption.master.key not found in vault. " +
                "Add it using: vault-reader --operation write --key chunk.encryption.master.key --value <base64-key>");
        }
    }
    
    /**
     * Encrypt chunk text using team-specific key
     */
    public String encryptChunkText(String plaintext, String teamId) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            // Get team-specific key (checks MongoDB for v2+, falls back to v1 derivation)
            SecretKey teamKey = getTeamKey(teamId);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, teamKey);
            
            // Encrypt
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            
            // Combine IV + encrypted data (GCM includes tag)
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            // Return Base64 encoded
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt chunk text", e);
        }
    }
    
    /**
     * Decrypt chunk text using team-specific key
     */
    public String decryptChunkText(String encryptedText, String teamId) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            // Decode Base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV and encrypted data
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encryptedBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            
            // Get team-specific key (checks MongoDB for v2+, falls back to v1 derivation)
            SecretKey teamKey = getTeamKey(teamId);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, teamKey, spec);
            
            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt chunk text for team: " + teamId, e);
        }
    }
    
    /**
     * Derive team-specific encryption key from master key
     */
    private SecretKey deriveTeamKey(String teamId) {
        try {
            // Decode master key
            byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);
            
            // Derive team key using HKDF
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec masterKeySpec = new SecretKeySpec(masterKey, "HmacSHA256");
            hmac.init(masterKeySpec);
            byte[] teamKeyBytes = hmac.doFinal(teamId.getBytes(StandardCharsets.UTF_8));
            
            return new SecretKeySpec(teamKeyBytes, "AES");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive team key", e);
        }
    }
}
```

#### Step 2: Modify Chunk Storage (MongoDB)

```java
// In KnowledgeHubDocumentProcessingServiceImpl.saveChunksToMongo()
private Mono<Void> saveChunksToMongo(...) {
    List<KnowledgeHubChunk> chunks = chunkResults.stream()
        .map(chunk -> {
            // Encrypt chunk text before storing
            String encryptedText = chunkEncryptionService.encryptChunkText(
                chunk.getText(), 
                document.getTeamId()
            );
            
            return KnowledgeHubChunk.builder()
                .text(encryptedText) // Store encrypted
                // ... other fields
                .build();
        })
        .toList();
    
    return chunkRepository.saveAll(chunks).then();
}
```

#### Step 3: Modify Chunk Storage (Milvus)

```java
// In KnowledgeHubDocumentEmbeddingServiceImpl.storeChunkRecord()
private Mono<Void> storeChunkRecord(...) {
    // Encrypt chunk text before storing in Milvus
    String encryptedText = chunkEncryptionService.encryptChunkText(
        chunk.getText(),
        document.getTeamId()
    );
    
    record.put(textFieldName, encryptedText); // Store encrypted
    // ... rest of the code
}
```

#### Step 4: Decrypt During Embedding Generation

```java
// In KnowledgeHubDocumentEmbeddingServiceImpl.embedChunk()
private Mono<Void> embedChunk(...) {
    // Decrypt chunk text for embedding generation
    String plaintext = chunkEncryptionService.decryptChunkText(
        chunk.getText(), // This is encrypted from MongoDB
        document.getTeamId()
    );
    
    // Use plaintext for embedding
    return getEmbeddingWithCache(plaintext, ...)
        .flatMap(embedding -> storeChunkRecord(...))
        .then();
}
```

#### Step 5: Decrypt During Vector Search Retrieval

```java
// In LinqMilvusStoreServiceImpl.queryRecords()
// After retrieving from Milvus, decrypt the text field
for (Map<String, Object> record : results) {
    String encryptedText = (String) record.get(textFieldName);
    if (encryptedText != null) {
        String plaintext = chunkEncryptionService.decryptChunkText(
            encryptedText,
            teamId
        );
        record.put(textFieldName, plaintext); // Replace with decrypted
    }
}
```

#### Step 6: Encrypt Knowledge Graph Entity Properties (Neo4j)

**Sensitive entity properties that must be encrypted:**
- **People**: `name`, `contactInfo`, `title`, `role`, `affiliation`
- **Organizations**: `address`, `phone`, `email`, `website`
- **Locations**: `street`, `city`, `state`, `zipCode`, `country`, `coordinates`, `address`
- **Documents**: `documentType`, `documentNumber`, `issuingAuthority`
- **Forms**: `name`, `description`, `requiredFields`, `filingInstructions`, `purpose`
- **General**: `name`, `description` (if they contain sensitive information)

**Non-sensitive fields that can remain plaintext:**
- `id` (entity identifier - needed for graph queries)
- `entityType` (label/type - needed for graph queries)
- `documentId` (reference to document)
- `extractedAt`, `createdAt`, `updatedAt` (timestamps)
- Structural fields needed for graph traversal

**Implementation in `Neo4jGraphServiceImpl.upsertEntity()`:**

```java
// In Neo4jGraphServiceImpl.upsertEntity()
public Mono<String> upsertEntity(String entityType, String entityId, Map<String, Object> properties, String teamId) {
    // List of sensitive property keys that should be encrypted
    Set<String> sensitiveKeys = Set.of(
        "name", "description", "address", "phone", "email", "website",
        "contactInfo", "title", "role", "affiliation",
        "street", "city", "state", "zipCode", "country", "coordinates",
        "documentType", "documentNumber", "issuingAuthority",
        "requiredFields", "filingInstructions", "purpose"
    );
    
    // Get current encryption key version
    String encryptionKeyVersion = chunkEncryptionService.getCurrentKeyVersion();
    
    // Encrypt sensitive properties before storing
    Map<String, Object> encryptedProperties = new HashMap<>(properties);
    for (String key : sensitiveKeys) {
        Object value = encryptedProperties.get(key);
        if (value instanceof String && !((String) value).isEmpty()) {
            // Encrypt the property value with current key version
            String encryptedValue = chunkEncryptionService.encryptChunkText(
                (String) value,
                teamId,
                encryptionKeyVersion
            );
            encryptedProperties.put(key, encryptedValue);
            // Store key version per property (for decryption later)
            encryptedProperties.put(key + "_encryption_version", encryptionKeyVersion);
        }
    }
    
    // Store entity-level encryption version (for tracking)
    encryptedProperties.put("encryptionKeyVersion", encryptionKeyVersion);
    
    // Store encrypted properties in Neo4j (rest of existing code...)
    // ...
}
```

**Implementation in `Neo4jGraphServiceImpl.findEntities()`:**

```java
// In Neo4jGraphServiceImpl.findEntities()
public Flux<Map<String, Object>> findEntities(String entityType, Map<String, Object> filters, String teamId) {
    // ... existing query logic ...
    
    return Flux.fromIterable(results)
        .map(record -> {
            Map<String, Object> entity = record.asMap();
            
            // Get entity-level encryption version (fallback if property-level version missing)
            String entityEncryptionVersion = (String) entity.get("encryptionKeyVersion");
            
            // Decrypt sensitive properties
            Set<String> sensitiveKeys = Set.of(
                "name", "description", "address", "phone", "email", "website",
                "contactInfo", "title", "role", "affiliation",
                "street", "city", "state", "zipCode", "country", "coordinates",
                "documentType", "documentNumber", "issuingAuthority",
                "requiredFields", "filingInstructions", "purpose"
            );
            
            for (String key : sensitiveKeys) {
                Object value = entity.get(key);
                if (value instanceof String && !((String) value).isEmpty()) {
                    try {
                        // Get key version for this property (prefer property-level, fallback to entity-level)
                        String keyVersion = (String) entity.get(key + "_encryption_version");
                        if (keyVersion == null || keyVersion.isEmpty()) {
                            keyVersion = entityEncryptionVersion != null ? entityEncryptionVersion : "v1"; // Default to v1
                        }
                        
                        // Decrypt using the correct key version
                        String plaintext = chunkEncryptionService.decryptChunkText(
                            (String) value,
                            teamId,
                            keyVersion
                        );
                        entity.put(key, plaintext);
                    } catch (Exception e) {
                        log.warn("Failed to decrypt property '{}' for entity {}:{} (version: {}): {}", 
                            key, entityType, entity.get("id"), 
                            entity.get(key + "_encryption_version"), e.getMessage());
                        // Keep encrypted value or set to null based on requirements
                    }
                }
            }
            
            return entity;
        });
}
```

**Important Notes for Knowledge Graph Encryption:**
1. **Graph Structure Preserved**: Only property values are encrypted, not the graph structure (nodes, relationships, labels)
2. **Query Limitations**: Encrypted properties cannot be used in Cypher `WHERE` clauses for filtering/searching (must decrypt in application layer)
3. **Index Considerations**: Encrypted properties cannot be indexed efficiently in Neo4j
4. **Encryption Key Version Tracking**: Must store `encryptionKeyVersion` field to track which key was used (see Migration Strategy section)
5. **Migration Strategy**: Existing plaintext entities need to be migrated (see Migration Strategy section)
6. **Performance Impact**: Entity queries will decrypt properties on-demand, similar to chunk retrieval

#### Step 7: Add Master Key to Vault

**Before implementing chunk encryption, add the encryption master key to your vault:**

```bash
# Generate a secure 256-bit (32-byte) encryption master key
CHUNK_MASTER_KEY=$(openssl rand -base64 32)

# Add to vault using vault-reader CLI
java -jar vault-reader/target/vault-reader.jar \
  --operation write \
  --file ./secrets/vault.encrypted \
  --master-key "$VAULT_MASTER_KEY" \
  --environment dev \
  --key chunk.encryption.master.key \
  --value "$CHUNK_MASTER_KEY"

# Or use bootstrap script (if updating secrets.json)
# Add to secrets/secrets.json under the appropriate environment:
# {
#   "dev": {
#     "secrets": {
#       "chunk.encryption.master.key": "<generated-base64-key>",
#       ...
#     }
#   }
# }
```

**Configuration in application.yml:**

```yaml
# application.yml
chunk:
  encryption:
    enabled: true  # Feature flag to enable/disable chunk encryption
    master-key-vault-key: chunk.encryption.master.key  # Key name in vault
```

**Note**: The master key will be automatically retrieved from vault via `LinqraVaultService` during service initialization.

### Key Hierarchy & Rotation Impact

**Important**: There are **TWO separate keys** with different purposes:

```
┌─────────────────────────────────────────────────────────────┐
│                    Key Hierarchy                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  VAULT_MASTER_KEY (Environment Variable)                    │
│  └─> Encrypts/decrypts vault.encrypted file                 │
│                                                              │
│      ┌────────────────────────────────────────┐             │
│      │  vault.encrypted (AES-256-GCM)         │             │
│      │  ┌──────────────────────────────────┐  │             │
│      │  │ Secrets (encrypted by vault key):│  │             │
│      │  │  • mongodb.password              │  │             │
│      │  │  • postgres.password             │  │             │
│      │  │  • chunk.encryption.master.key ←┼──┼── Used by   │
│      │  │  • other secrets...              │  │    chunks   │
│      │  └──────────────────────────────────┘  │             │
│      └────────────────────────────────────────┘             │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ (retrieved at runtime)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  chunk.encryption.master.key (stored in vault)              │
│  └─> Encrypts/decrypts chunk text data                      │
│                                                              │
│      ┌──────────────────┐        ┌──────────────────┐       │
│      │  MongoDB Chunks  │        │  Milvus Records  │       │
│      │  (text:encrypted)│        │  (text:encrypted)│       │
│      └──────────────────┘        └──────────────────┘       │
└─────────────────────────────────────────────────────────────┘

Rotation Impact:
• Rotate VAULT_MASTER_KEY          → Re-encrypt vault.encrypted only
• Rotate chunk.encryption.master.key → Re-encrypt ALL chunks (expensive)
```

#### Key 1: `VAULT_MASTER_KEY` (Vault File Encryption)
- **Purpose**: Encrypts/decrypts the `vault.encrypted` file itself
- **Contains**: All secrets (database passwords, API keys, `chunk.encryption.master.key`, etc.)
- **Stored**: Environment variable (`VAULT_MASTER_KEY`)
- **Rotation Impact**: 
  - ✅ **You DO need to re-encrypt the vault file** (`vault.encrypted`)
  - ❌ **You DO NOT need to re-encrypt chunks** (chunks use a different key stored inside the vault)
  - **Process**: 
    1. Decrypt vault file with old key
    2. Re-encrypt vault file with new key
    3. Update `VAULT_MASTER_KEY` environment variable
    4. No chunk re-encryption needed

#### Key 2: `chunk.encryption.master.key` (Chunk Text & Entity Property Encryption)
- **Purpose**: Encrypts/decrypts chunk text data in MongoDB/Milvus **and entity properties in Neo4j**
- **Stored**: Inside the vault file (as a secret value)
- **Rotation Impact**: 
  - ✅ **You DO need to handle all encrypted data** (chunks in MongoDB/Milvus and entities in Neo4j)
  - ❌ **You DO NOT need to re-encrypt the vault file**
  - **Two Approaches Available**:
    
    **Approach A: Hard Delete + Re-encrypt** ⭐ **RECOMMENDED**
    - Delete all encrypted chunks and entities
    - Re-process original documents from scratch
    - Re-encrypt with new key during re-processing
    - **Pros**: Cleaner code, no dual-key migration logic, ensures consistency
    - **Cons**: Requires original documents, re-embedding costs
    
    **Approach B: In-Place Re-encryption** 
    - Keep old key temporarily for decryption
    - Read chunks/entities, decrypt with old key, re-encrypt with new key
    - Update in place
    - **Pros**: Faster, no re-embedding costs, preserves existing data
    - **Cons**: More complex code (dual-key handling), migration scripts needed

**Summary**: 
- **`VAULT_MASTER_KEY` rotation**: Simple - only re-encrypts the vault file itself
- **`chunk.encryption.master.key` rotation**: Two approaches available:
  - **Hard Delete + Re-encrypt** (Recommended): Delete all chunks/entities, re-process documents from scratch. Cleaner, simpler code, but requires original documents and re-embedding.
  - **In-Place Re-encryption**: Decrypt with old key, re-encrypt with new key in place. Faster, no re-embedding costs, but requires dual-key migration logic.

### Key Management Best Practices

1. **Store Master Key Securely** (Using Linqra Vault System):
   - ✅ **Recommended**: Store `chunk.encryption.master.key` in `vault.encrypted` file
     - Managed via vault-reader CLI
     - Encrypted with AES-256-GCM (by `VAULT_MASTER_KEY`)
     - Protected by file permissions (`600` for vault file, `700` for directory)
     - `VAULT_MASTER_KEY` stored in environment variable (not in Git)
   - **Alternative for Production**: Store `VAULT_MASTER_KEY` in AWS Secrets Manager
     - Vault file itself can remain in vault.encrypted
     - Only the vault master key needs to be in AWS Secrets Manager
     - Provides additional security layer
   - **Never commit keys to Git**: Both `VAULT_MASTER_KEY` and vault contents are gitignored

2. **Key Rotation Strategy**:

   **For `VAULT_MASTER_KEY` Rotation** (Less Frequent):
   ```bash
   # 1. Generate new vault master key
   NEW_VAULT_KEY=$(openssl rand -base64 32)
   
   # 2. Decrypt vault with old key, re-encrypt with new key
   # (Use vault-reader CLI with both keys, or create migration script)
   
   # 3. Update environment variable
   export VAULT_MASTER_KEY="$NEW_VAULT_KEY"
   
   # 4. Restart services
   # No chunk re-encryption needed!
   ```

   **For Team Key Rotation** (Per-Team, Independent):

   **Using API Endpoint** ⭐ **RECOMMENDED - Automatic**:
   ```bash
   # Rotate key for a specific team via API
   POST /api/v1/teams/{teamId}/encryption/keys/rotate
   Authorization: Bearer <token>
   # Requires: ADMIN or SUPER_ADMIN role for the team
   
   # What happens automatically:
   # 1. System finds current active key version (e.g., v2)
   # 2. Generates new random AES-256 key
   # 3. Encrypts new key with Global Master Key
   # 4. Creates new key record with next version (e.g., v3) as active
   # 5. Deactivates old active keys
   # 6. New encryptions use new key version
   # 7. Old data still decryptable with old key versions
   ```

   **Optional: Data Migration After Rotation** (Hard Delete + Re-encrypt):
   ```bash
   # After team key rotation, optionally migrate old data:
   # 1. Hard delete all encrypted chunks and entities for the team
   #    - Delete all chunks from MongoDB
   #    - Delete all chunk records from Milvus
   #    - Delete all encrypted entities from Neo4j Knowledge Graph
   #    - Preserve original documents (source files remain intact)
   
   # 2. Re-process all documents to re-encrypt with new key
   #    - Re-chunk documents (from original source)
   #    - Re-encrypt chunks with new active key version
   #    - Re-generate embeddings (from plaintext)
   #    - Re-extract entities (from plaintext)
   #    - Store everything with new encryption
   
   # Note: Migration is optional - old data remains decryptable
   ```
   
   **Important**: Do NOT change Global Master Key without careful planning:
   - Changing Global Master Key affects all team keys stored in MongoDB
   - All team keys become undecryptable if Global Master Key changes
   - Requires decrypting and re-encrypting all team keys with new Global Master Key

   **Comparison:**

   | Aspect | In-Place Re-encryption | Hard Delete + Re-encrypt |
   |--------|----------------------|-------------------------|
   | **Complexity** | More complex (dual-key handling) | Simpler (clean slate) |
   | **Time** | Faster (just re-encrypt) | Slower (re-process documents) |
   | **Cost** | Lower (no LLM calls) | Higher (re-embedding costs) |
   | **Data Safety** | Risk if migration fails | Safe (source documents preserved) |
   | **Code Complexity** | Requires migration logic | Simpler (no dual-key support) |
   | **Best For** | Large datasets, cost-sensitive | Fresh start, simpler codebase |

   **Recommendation**: Use **Hard Delete + Re-encrypt** if:
   - Original source documents are still available
   - You want simpler code (no migration logic needed)
   - Re-embedding costs are acceptable
   - You prefer a clean approach

   Use **In-Place Re-encryption** if:
   - Source documents are no longer available
   - Re-embedding costs are prohibitive
   - You need to minimize downtime
   - You have large datasets with many chunks

3. **Rotation Frequency Recommendations**:
   - `VAULT_MASTER_KEY`: Rotate annually or on security events (simpler, less impact)
   - `chunk.encryption.master.key`: Rotate only when necessary (compromise, compliance requirement, etc.) - requires chunk re-encryption

3. **Access Control**:
   - Only application services can read from vault (via `LinqraVaultService`)
   - Vault file permissions (`600`) prevent unauthorized access
   - `VAULT_MASTER_KEY` should only be accessible to application containers
   - Audit vault access via application logs (future enhancement)

### Migration Strategy

For existing unencrypted chunks and entities, or when rotating `chunk.encryption.master.key`:

**Approach: Hard Delete + Re-encrypt** ⭐ **RECOMMENDED**

This approach is cleaner and simpler than in-place migration, but requires **encryption key version tracking** to handle gradual migration:

1. **Delete all encrypted data**:
   - Delete all chunks from MongoDB
   - Delete all chunk records from Milvus
   - Delete all entities from Neo4j Knowledge Graph
   - **Note**: Original source documents are preserved (stored separately)

2. **Re-process all documents**:
   - Re-chunk documents from original source files
   - Encrypt chunks with new key during processing
   - Re-generate embeddings (from plaintext chunks)
   - Re-extract entities (from plaintext chunks)
   - Store everything with new encryption

3. **Important: Encryption Key Version Tracking**:
   - ⚠️ **Must track which key version was used** for each chunk/entity
   - ⚠️ During gradual migration, some chunks may use old key, others use new key
   - ⚠️ Decryption logic must check version and use correct key
   - ⚠️ Prevents decryption failures when trying to decrypt with wrong key

4. **Benefits of this approach**:
   - ✅ Simpler code - no migration logic needed
   - ✅ Clean slate - all data encrypted with new key
   - ✅ No dual-key handling complexity
   - ✅ Ensures consistency across all data

5. **Considerations**:
   - ⚠️ Requires original source documents to be available
   - ⚠️ Re-embedding costs (LLM calls to regenerate embeddings)
   - ⚠️ Re-processing time (can be run in background)
   - ⚠️ **Must implement key version tracking** (see below)

**Implementation: Encryption Key Version Tracking**

**Step 1: Add Version Field to Chunk Entity**

```java
// In KnowledgeHubChunk.java
@Document(collection = "knowledge_hub_chunks")
public class KnowledgeHubChunk {
    // ... existing fields ...
    
    private String text; // Encrypted chunk text
    
    /**
     * Encryption key version used to encrypt this chunk.
     * Format: "v1", "v2", etc.
     * Null or "v1" = default/legacy key
     * "v2" = new key after rotation
     */
    private String encryptionKeyVersion; // e.g., "v1", "v2"
    
    // ... other fields ...
}
```

**Step 2: Update Encryption Service to Handle Versions**

```java
@Service
public class ChunkEncryptionService {
    
    @Autowired
    private LinqraVaultService vaultService;
    
    private String currentKeyVersion = "v1"; // Default version
    private Map<String, String> keyVersions = new HashMap<>(); // version -> base64 key
    
    @PostConstruct
    public void init() {
        // Load current master key (default version)
        String masterKey = vaultService.getSecret("chunk.encryption.master.key");
        keyVersions.put("v1", masterKey);
        
        // Check for newer key versions (e.g., v2, v3)
        // Format: chunk.encryption.master.key.v2, chunk.encryption.master.key.v3
        int version = 2;
        while (true) {
            String keyName = "chunk.encryption.master.key.v" + version;
            String versionedKey = vaultService.getSecret(keyName);
            if (versionedKey == null || versionedKey.isEmpty()) {
                break;
            }
            keyVersions.put("v" + version, versionedKey);
            currentKeyVersion = "v" + version; // Update to latest version
            version++;
        }
        
        log.info("Loaded encryption key versions: {}", keyVersions.keySet());
        log.info("Current encryption key version: {}", currentKeyVersion);
    }
    
    /**
     * Encrypt with current key version
     */
    public String encryptChunkText(String plaintext, String teamId) {
        return encryptChunkText(plaintext, teamId, currentKeyVersion);
    }
    
    /**
     * Encrypt with specific key version
     */
    public String encryptChunkText(String plaintext, String teamId, String keyVersion) {
        String masterKeyBase64 = keyVersions.get(keyVersion);
        if (masterKeyBase64 == null) {
            throw new IllegalStateException("Encryption key version not found: " + keyVersion);
        }
        // ... encryption logic using masterKeyBase64 ...
        // Store keyVersion with encrypted data (in chunk entity)
    }
    
    /**
     * Decrypt with key version from chunk
     */
    public String decryptChunkText(String encryptedText, String teamId, String keyVersion) {
        if (keyVersion == null || keyVersion.isEmpty()) {
            keyVersion = "v1"; // Default to v1 for legacy data
        }
        
        String masterKeyBase64 = keyVersions.get(keyVersion);
        if (masterKeyBase64 == null) {
            throw new IllegalStateException("Encryption key version not found: " + keyVersion + 
                ". Cannot decrypt chunk. Key may have been rotated.");
        }
        // ... decryption logic using masterKeyBase64 ...
    }
}
```

**Step 3: Update Chunk Storage to Include Version**

```java
// In KnowledgeHubDocumentProcessingServiceImpl.saveChunksToMongo()
private Mono<Void> saveChunksToMongo(...) {
    List<KnowledgeHubChunk> chunks = chunkResults.stream()
        .map(chunk -> {
            // Encrypt chunk text with current key version
            String encryptedText = chunkEncryptionService.encryptChunkText(
                chunk.getText(), 
                document.getTeamId()
            );
            
            return KnowledgeHubChunk.builder()
                .text(encryptedText)
                .encryptionKeyVersion(chunkEncryptionService.getCurrentKeyVersion()) // Store version
                // ... other fields
                .build();
        })
        .toList();
    
    return chunkRepository.saveAll(chunks).then();
}
```

**Step 4: Update Chunk Retrieval to Use Version**

```java
// In KnowledgeHubDocumentEmbeddingServiceImpl.embedChunk()
private Mono<Void> embedChunk(KnowledgeHubChunk chunk, ...) {
    // Decrypt using version from chunk entity
    String plaintext = chunkEncryptionService.decryptChunkText(
        chunk.getText(), 
        document.getTeamId(),
        chunk.getEncryptionKeyVersion() // Use version from chunk
    );
    
    // Use plaintext for embedding
    return getEmbeddingWithCache(plaintext, ...)
        .flatMap(embedding -> storeChunkRecord(...))
        .then();
}
```

**Step 5: Key Rotation Job with Version Tracking**

```java
// Key rotation job: Hard delete + re-encrypt
public Mono<Void> rotateEncryptionKey(String teamId) {
    log.info("Starting encryption key rotation for team: {}", teamId);
    
    // Step 1: Generate new key version
    String newKeyVersion = "v2"; // Increment version
    String newKey = generateNewKey();
    
    // Step 2: Store new key in vault with version suffix
    vaultService.writeSecret("chunk.encryption.master.key.v2", newKey);
    
    // Step 3: Update current key version (after restart, service will use v2)
    // For now, we'll update the current key directly
    vaultService.writeSecret("chunk.encryption.master.key", newKey);
    
    // Step 4: Delete all encrypted chunks (hard delete)
    return chunkRepository.deleteAllByTeamId(teamId)
        .then()
        // Step 5: Delete all Milvus records
        .then(milvusService.deleteAllByTeamId(teamId))
        // Step 6: Delete all entities
        .then(deleteAllEntities(teamId))
        // Step 7: Re-process all documents (will encrypt with new key)
        .then(reprocessAllDocuments(teamId))
        .doOnSuccess(v -> log.info("Key rotation completed for team: {}", teamId))
        .doOnError(e -> log.error("Key rotation failed for team: {}", teamId, e));
}

private Mono<Void> deleteAllEntities(String teamId) {
    Set<String> entityTypes = Set.of("Person", "Organization", "Location", "Document", "Form");
    return Flux.fromIterable(entityTypes)
        .flatMap(entityType -> graphService.deleteAllEntities(entityType, teamId))
        .then();
}

private Mono<Void> reprocessAllDocuments(String teamId) {
    // Re-process all documents from source files
    // This will trigger re-chunking, re-encryption with NEW key, re-embedding, re-entity extraction
    return documentRepository.findByTeamId(teamId)
        .flatMap(document -> {
            // Re-process document (will use current key version during encryption)
            return documentProcessingService.processDocument(document.getDocumentId(), teamId);
        }, 10) // Concurrency limit
        .then();
}
```

**Important Notes:**
- ✅ **Chunks/entities must store `encryptionKeyVersion` field** (e.g., "v1", "v2")
- ✅ **Decryption must check version and use correct key** from version map
- ⚠️ **Even with hard delete approach, version tracking is essential** because:
  - New chunks may be created while old chunks still exist (during gradual migration across multiple documents/teams)
  - If migration fails partway, you need to know which key version to use for each chunk
  - Multiple teams may be at different migration stages simultaneously
  - Provides audit trail of which encryption key was used for each chunk/entity
  - Handles edge cases where migration is interrupted or rolled back
- ✅ After hard delete + re-encrypt completes, all new chunks/entities are encrypted with new key version (e.g., "v2")
- ✅ Old key versions can be removed from vault after migration completes (but keep temporarily for rollback safety)
- ✅ Version field also helps identify unencrypted legacy data (null or missing version = potentially unencrypted)

**Alternative: In-Place Migration** (For scenarios where hard delete is not feasible)

If original documents are not available or re-embedding costs are prohibitive:

1. Create a migration job to:
   - Read all chunks from MongoDB
   - Decrypt with old key (if available), encrypt with new key
   - Update chunk in MongoDB
   - Update chunk in Milvus

2. Run migration in batches to avoid database overload

3. Requires dual-key handling logic (more complex)

### Security Considerations

- ✅ **Encryption at Rest**: Chunks are encrypted in MongoDB/Milvus, entity properties in Neo4j
- ✅ **Admin Protection**: Database admins cannot read plaintext chunks or entity data
- ✅ **Key Isolation**: Per-team keys provide tenant isolation
- ✅ **Authenticated Encryption**: GCM mode prevents tampering
- ⚠️ **In-Memory**: Plaintext exists briefly in application memory (acceptable)
- ⚠️ **Logging**: Ensure decrypted text/entities are not logged (audit logs should only contain IDs)
- ⚠️ **Neo4j Queries**: Encrypted properties cannot be filtered in Cypher queries (must decrypt in application layer)

### Performance Impact

- **Storage**: ~33% increase (Base64 encoding overhead)
- **Write**: ~1-2ms per chunk (encryption overhead)
- **Read**: ~1-2ms per chunk (decryption overhead)
- **Overall**: <10% performance impact, acceptable for law firm use case

---

## Recommendation Summary

**Use Option 1: Application-Level Field Encryption**

This approach:
- ✅ Meets all requirements (admin can't see data)
- ✅ Reasonable implementation complexity
- ✅ Acceptable performance overhead
- ✅ Works with existing infrastructure
- ✅ Provides per-team key isolation

### Implementation Status

#### Phase 1: Setup ✅ COMPLETED
1. ✅ Vault system already implemented
2. ✅ `chunk.encryption.master.key` added to vault
3. ✅ `ChunkEncryptionService` created with vault integration
4. ✅ Encryption key version tracking implemented

#### Phase 2: Core Implementation ✅ COMPLETED
4. ✅ Modified chunk save operations to encrypt (MongoDB + Milvus)
5. ✅ Modified chunk retrieval operations to decrypt
6. ✅ Modified embedding generation to decrypt before processing
7. ✅ Added `encryptionKeyVersion` field to chunks collection schema
8. ✅ Modified entity save operations to encrypt sensitive properties (Neo4j)
9. ✅ Modified entity retrieval operations to decrypt properties
10. ✅ Added encryption version tracking to entity properties
11. ✅ Document metadata encryption implemented
12. ✅ Processed JSON (S3) encryption implemented
13. ✅ Frontend decryption API endpoint implemented

#### Phase 3: Migration ⚠️ OPTIONAL (If Legacy Data Exists)
11. ⚠️ Create migration script for existing unencrypted chunks (if needed)
12. ⚠️ Create migration script for existing unencrypted entities (if needed)
13. ⚠️ Run chunk migration in batches (monitor performance)
14. ⚠️ Run entity migration in batches (monitor performance)
15. ⚠️ Verify all chunks and entities are encrypted

**Note**: If no legacy unencrypted data exists, Phase 3 can be skipped. All new data is automatically encrypted.

#### Phase 4: Testing & Monitoring ⚠️ IN PROGRESS
16. ⚠️ Test thoroughly with encrypted chunks
17. ⚠️ Monitor performance impact in production
18. ⚠️ Add metrics for encryption/decryption operations (future enhancement)
19. ✅ Documentation updated

#### Phase 5: Future Enhancements (Optional)
20. ⚠️ Implement audit logging for encryption/decryption operations
21. ⚠️ Automated key rotation with migration scripts
22. ⚠️ Performance optimization if needed
23. ⚠️ Key rotation testing and documentation

### Alternative: Enhanced Security with AWS KMS (Future Enhancement)

For production environments requiring additional security, consider a hybrid approach:

**Option A: AWS KMS for Vault Master Key**
- Store `VAULT_MASTER_KEY` in AWS Secrets Manager (encrypted with KMS)
- Vault file itself still uses `vault.encrypted`
- Provides additional security layer without changing vault architecture

**Option B: AWS KMS for Chunk Encryption Key**
- Store `chunk.encryption.master.key` directly in AWS Secrets Manager
- Use AWS KMS to encrypt/decrypt the master key
- Provides automatic key rotation, CloudTrail audit logging, HSM protection

**Implementation Example:**

```java
@Service
public class AwsKmsChunkEncryptionService {
    
    @Autowired
    private KmsClient kmsClient;
    
    @Value("${aws.kms.chunk.key.id}")
    private String kmsKeyId;
    
    public String encryptChunkText(String plaintext, String teamId) {
        // Option 1: Use KMS to encrypt data key per chunk (expensive, not recommended)
        // Option 2: Use KMS to encrypt master key, derive team keys (recommended)
        // Option 3: Use KMS only for vault master key (simplest, current recommendation)
    }
}
```

**Current Recommendation**: Use Linqra Vault System (already implemented) with `VAULT_MASTER_KEY` optionally stored in AWS Secrets Manager for additional security. This provides:
- ✅ Simpler architecture (uses existing vault system)
- ✅ Good security (AES-256-GCM encryption)
- ✅ Per-team key isolation
- ✅ Easy to enhance later with AWS KMS for vault master key



