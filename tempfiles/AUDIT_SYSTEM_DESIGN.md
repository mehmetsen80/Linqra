# Audit System Design - Brainstorming & Options

## Overview

This document explores different design approaches for implementing a comprehensive audit logging system in Linqra. The system needs to track all critical user actions and data access operations for compliance (HIPAA, SOX, GDPR) while remaining scalable and cost-effective.

---

## What Should Be Audited?

### High-Priority Events (Must Track)

1. **Data Access Events**
   - Chunk read/access operations ✅ **Implemented**
   - Chunk decryption events (when encrypted data is decrypted) ✅ **Implemented**
   - Document access (viewing documents) ✅ **Implemented**
   - Knowledge graph entity/relationship queries
   - Metadata access

2. **Data Modification Events**
   - Document upload
   - Document deletion (hard delete)
   - Metadata extraction/update
   - Graph extraction/update
   - Encryption key rotation

3. **Data Export Events**
   - Collection export job initiation
   - Collection export completion
   - Export file downloads
   - Bulk data export requests

4. **Authentication & Authorization Events**
   - User login/logout
   - Failed authentication attempts
   - Role changes
   - Permission modifications
   - SSO token refresh

5. **Security Events**
   - Failed access attempts (unauthorized access) ✅ **Implemented**
   - Encryption/decryption failures ✅ **Implemented**
   - Vault access (vault file decryption)
   - Key rotation events
   - PII detection events (when PII is detected in chat messages) ✅ **Implemented**

6. **Administrative Actions**
   - User creation/deletion
   - Team creation/modification
   - Collection creation/deletion
   - Configuration changes
   - Vault operations

7. **AI Assistant Events**
   - Chat execution started/completed/failed
   - PII detection events (when PII is detected in user or assistant messages)
   - Agent task executions
   - Workflow executions

### Event Schema

```json
{
  "eventId": "uuid",
  "timestamp": "ISO-8601",
  "eventType": "DOCUMENT_ACCESSED | DOCUMENT_DELETED | CHUNK_DECRYPTED | EXPORT_INITIATED | PII_DETECTED | ...",
  "userId": "user-id",
  "username": "display-name",
  "teamId": "team-id",
  "ipAddress": "client-ip",
  "userAgent": "browser-info",
  "action": "READ | CREATE | UPDATE | DELETE | EXPORT",
  "result": "SUCCESS | FAILED | DENIED",
  "resourceType": "DOCUMENT | CHUNK | METADATA | EXPORT | USER | ...",
  "resourceId": "document-id | chunk-id | ...",
  "documentId": "document-id (if applicable)",
  "collectionId": "collection-id (if applicable)",
  "metadata": {
    "reason": "why the access occurred",
    "context": "additional context",
    "durationMs": 123,
    "bytesAccessed": 1024,
    "errorMessage": "error details (if failed)"
  },
  "complianceFlags": {
    "containsPII": false,
    "sensitiveData": true,
    "requiresRetention": true
  }
}
```

---

## Storage Architecture Options

### Option 1: MongoDB-Only (Single Store)

**Architecture:**
- All audit logs stored in MongoDB collection `audit_logs`
- Indexed by `teamId`, `timestamp`, `eventType`, `userId`
- TTL indexes for automatic cleanup after retention period
- Separate collections for different event types if needed (e.g., `audit_logs_access`, `audit_logs_export`)

**Pros:**
- ✅ Simple implementation (single database)
- ✅ Easy querying and reporting
- ✅ Consistent with existing architecture
- ✅ Supports real-time queries
- ✅ Transactional consistency if needed

**Cons:**
- ❌ MongoDB storage costs grow linearly with usage
- ❌ Performance degradation as collection grows (even with indexes)
- ❌ Potential storage limits with large audit volumes
- ❌ Not truly immutable (could be modified/deleted)
- ❌ No automatic archival

**Storage Estimate:**
- Average audit log size: ~500 bytes
- 1000 events/day/team × 100 teams = 100,000 events/day = ~50 MB/day
- Over 7 years (typical retention): ~127 GB (just for one collection)
- With multiple teams and growth: Could reach TBs over time

**Best For:**
- Small to medium deployments
- Teams that need real-time querying
- Budget-conscious organizations
- Initial MVP implementation

---

### Option 2: Hybrid MongoDB + S3 (Recommended)

**Architecture:**
- **Hot Storage (MongoDB)**: Recent audit logs (e.g., last 90 days)
  - Fast queries for recent events
  - Real-time dashboards
  - Active compliance reviews
  
- **Cold Storage (S3)**: Archived audit logs (older than 90 days)
  - Immutable storage (S3 versioning enabled)
  - Cost-effective long-term retention
  - Periodic batch archival jobs
  
- **Archive Process**: 
  - Scheduled job runs daily
  - Moves logs older than retention threshold to S3
  - Stores as compressed JSON files (one per day, partitioned by team)
  - Deletes from MongoDB after successful S3 upload

**Pros:**
- ✅ Best of both worlds: fast queries + cost-effective storage
- ✅ Immutable audit trail (S3 versioning)
- ✅ Scales to large volumes over time
- ✅ Cost-effective for long-term retention
- ✅ Real-time access to recent events
- ✅ Compliance-ready (immutable storage)

**Cons:**
- ⚠️ More complex implementation (archival logic)
- ⚠️ Queries for old data require S3 access (slower)
- ⚠️ Two storage systems to manage

**Storage Estimate:**
- MongoDB: ~4.5 GB (90 days of hot data)
- S3: ~120 GB for 7 years (compressed), ~$2-3/month storage cost
- Archival costs: Minimal (batch uploads)

**Best For:**
- Production deployments
- Compliance-focused organizations
- Long-term retention requirements
- Growing user base

---

### Option 3: S3-Only with Index in MongoDB

**Architecture:**
- **S3**: All audit logs stored immediately
  - Partitioned by date/team (e.g., `audits/2025/01/15/team-id/events.json.gz`)
  - Immutable storage (S3 versioning)
  - Compressed JSON files
  
- **MongoDB Index**: Lightweight index collection
  - Stores only: `eventId`, `timestamp`, `teamId`, `userId`, `eventType`, `s3Key`
  - Used for fast lookups to find which S3 file contains the event
  - TTL index for automatic cleanup (after archival period)

**Pros:**
- ✅ Maximum immutability (all logs in S3 immediately)
- ✅ Most cost-effective long-term
- ✅ True separation of concerns
- ✅ Easy to query recent events via index

**Cons:**
- ❌ More complex queries (need to fetch from S3)
- ❌ Slightly higher latency for real-time queries
- ❌ Requires S3 query logic implementation

**Storage Estimate:**
- MongoDB: ~100 MB (just index metadata)
- S3: ~127 GB for 7 years (compressed)
- Cost: ~$2-3/month

**Best For:**
- High-compliance requirements
- Maximum immutability needs
- Cost-sensitive long-term retention

---

### Option 4: Time-Series Database (InfluxDB/TimeScaleDB)

**Architecture:**
- Use a dedicated time-series database for audit logs
- Optimized for time-based queries and retention policies
- Automatic data expiration based on retention policies
- Better compression than MongoDB

**Pros:**
- ✅ Optimized for time-series data
- ✅ Better query performance for time-range queries
- ✅ Automatic retention management
- ✅ Efficient compression

**Cons:**
- ❌ Additional infrastructure component
- ❌ Learning curve and operational overhead
- ❌ Migration complexity if switching from MongoDB
- ❌ May be overkill for audit-only use case

**Best For:**
- High-volume audit logging (millions of events/day)
- Organizations already using time-series databases
- Advanced analytics on audit data

---

## Recommended Approach: Hybrid MongoDB + S3

Based on the requirements and scalability concerns, **Option 2 (Hybrid MongoDB + S3)** is the recommended approach.

### Implementation Phases

#### Phase 1: MongoDB-Only MVP (Quick Start)
- Implement audit logging service
- Store all logs in MongoDB collection `audit_logs`
- Add comprehensive indexing
- Build basic query/reporting interface
- **Timeline**: 1-2 weeks

#### Phase 2: Add S3 Archival (Scalability)
- Implement archival service
- Move logs older than 90 days to S3
- Compress logs before archival
- Implement S3 query interface for historical data
- **Timeline**: 2-3 weeks

#### Phase 3: Enhanced Features (Optional)
- Real-time audit dashboards
- Alerting on suspicious patterns
- Automated compliance reports
- **Timeline**: 2-3 weeks

---

## MongoDB Schema Design

### Collection: `audit_logs`

```javascript
{
  _id: ObjectId,
  eventId: String (UUID, unique),
  timestamp: Date (indexed),
  eventType: String (indexed), // "DOCUMENT_ACCESSED", "CHUNK_DECRYPTED", etc.
  userId: String (indexed),
  username: String,
  teamId: String (indexed),
  ipAddress: String,
  userAgent: String,
  action: String, // "READ", "CREATE", "UPDATE", "DELETE", "EXPORT"
  result: String, // "SUCCESS", "FAILED", "DENIED"
  resourceType: String, // "DOCUMENT", "CHUNK", "METADATA", "EXPORT"
  resourceId: String,
  documentId: String (indexed, optional),
  collectionId: String (indexed, optional),
  metadata: {
    reason: String,
    context: Object,
    durationMs: Number,
    bytesAccessed: Number,
    errorMessage: String
  },
  complianceFlags: {
    containsPII: Boolean,
    sensitiveData: Boolean,
    requiresRetention: Boolean
  },
  archivedAt: Date (null if not archived),
  s3Key: String (null if not archived)
}
```

### Indexes

```javascript
// Primary queries
db.audit_logs.createIndex({ "teamId": 1, "timestamp": -1 })
db.audit_logs.createIndex({ "userId": 1, "timestamp": -1 })
db.audit_logs.createIndex({ "eventType": 1, "timestamp": -1 })
db.audit_logs.createIndex({ "documentId": 1, "timestamp": -1 })

// Compound indexes for common queries
db.audit_logs.createIndex({ "teamId": 1, "eventType": 1, "timestamp": -1 })
db.audit_logs.createIndex({ "teamId": 1, "result": 1, "timestamp": -1 })

// TTL index for automatic cleanup (after archival)
db.audit_logs.createIndex({ "timestamp": 1 }, { expireAfterSeconds: 7776000 }) // 90 days
```

---

## S3 Archival Strategy

### S3 Structure

```
s3://linqra-audit-logs/
  {year}/
    {month}/
      {day}/
        {teamId}/
          events-{timestamp}.json.gz
        events-{timestamp}.json.gz  (all teams combined)
```

### Archive File Format

Each archive file contains multiple audit log entries (newline-delimited JSON):

```json
{"eventId":"...","timestamp":"...","eventType":"DOCUMENT_ACCESSED",...}
{"eventId":"...","timestamp":"...","eventType":"CHUNK_DECRYPTED",...}
```

Compressed with gzip for storage efficiency.

### S3 Configuration

- **Versioning**: Enabled (immutable audit trail)
- **Encryption**: SSE-S3 (server-side encryption)
- **Lifecycle Policy**: 
  - Move to Glacier after 1 year (if needed)
  - Never delete (compliance requirement)
- **Access Control**: Private bucket, service role access only

---

## Query Patterns & API Design

### Query Interface

```java
// Recent events (from MongoDB)
Mono<AuditLogPage> queryRecentAuditLogs(
    String teamId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    List<String> eventTypes,
    String userId,
    Pageable pageable
);

// Historical events (from S3)
Mono<AuditLogPage> queryHistoricalAuditLogs(
    String teamId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    List<String> eventTypes
);
```

### Common Queries

1. **Compliance Review**: "Show all document access for user X in last 30 days"
2. **Incident Response**: "Show all failed access attempts for team Y in last week"
3. **Audit Trail**: "Show complete lifecycle of document Z (create → access → delete)"
4. **Export Tracking**: "Show all data exports for team Y"
5. **Security Audit**: "Show all decryption events for sensitive documents"

---

## Retention Policies

### Retention by Event Type

| Event Type | Hot Storage (MongoDB) | Cold Storage (S3) | Total Retention |
|------------|----------------------|-------------------|-----------------|
| All Audit Events | ~90 days (until archived) | 7 years | 7 years |
| Data Exports | N/A | 7 days | 7 days |
| Documents (Trash) | 30 days (Soft Delete) | N/A | 30 days |

### Configurable Retention

- Allow teams to configure retention per compliance requirements
- Default: 7 years (HIPAA requirement)
- Some law firms may need 10+ years for certain document types

---

## Cost Analysis - Self-Hosted MongoDB

> **Note:** Since you're running your own MongoDB cluster (not MongoDB Atlas), the cost model is completely different. There's no per-GB pricing - the real considerations are **disk space**, **performance**, and **backup storage**.

### Key Differences: Self-Hosted vs MongoDB Atlas

| Consideration | MongoDB Atlas | Self-Hosted (Your Setup) |
|---------------|---------------|--------------------------|
| **Storage Cost** | $0.10/GB/month | $0 (uses existing infrastructure) |
| **Real Cost** | Monthly billing | Disk space availability |
| **Primary Concern** | Monthly bills | Performance degradation |
| **Scaling** | Auto-scales | Manual disk expansion |

### MongoDB-Only (Option 1) - Self-Hosted

**Assumptions:**
- 100,000 events/day
- 500 bytes/event (average)
- 7-year retention in MongoDB (no archival)
- Self-hosted MongoDB (your own infrastructure)

**Storage Growth:**
- Daily: 100,000 events × 500 bytes = ~50 MB/day
- Monthly: ~1.5 GB/month
- Yearly: ~18 GB/year
- Over 7 years: ~127 GB total

**Cost Considerations for Self-Hosted:**

**If running on EC2:**
- EBS Storage: 127 GB × $0.10/GB/month = **~$12.70/month** (incremental)
- But: Likely using existing EBS volume with capacity
- Real cost: Just the additional disk space needed

**If running on local infrastructure:**
- No additional cost if disk space available
- Cost = 0 (uses existing MongoDB cluster)

**Real Concerns (Not Cost):**
- ⚠️ **Performance Impact**: Large collections slow queries (even with indexes)
- ⚠️ **Disk Space**: Need to ensure 127+ GB available over time
- ⚠️ **Backup Storage**: 127 GB × 2-3x for backups = ~250-380 GB additional
- ⚠️ **Index Size**: Multiple indexes can double storage (~250 GB total)
- ⚠️ **Query Performance**: Degrades as collection grows beyond ~50 GB

### Hybrid MongoDB + S3 (Option 2) - Self-Hosted

**Assumptions:**
- Same volume: 100,000 events/day
- 90-day hot retention in MongoDB
- 7-year cold retention in S3 (compressed ~50% reduction)

**Storage:**
- MongoDB (90 days): ~4.5 GB
- S3 (7 years, compressed): ~64 GB

**Cost for Self-Hosted:**
- MongoDB: **$0** (uses existing cluster, minimal incremental storage)
- S3 Storage: 64 GB × $0.023/GB = **~$1.47/month**
- S3 Requests: Minimal (batch uploads) = **~$0.01/month**
- **Total: ~$1.48/month**

**Benefits (Not Just Cost):**
- ✅ **Performance**: MongoDB collection stays small (~4.5 GB max)
- ✅ **Query Speed**: Fast queries on recent data (last 90 days)
- ✅ **Disk Space**: Minimal impact on MongoDB cluster
- ✅ **Backup Size**: Much smaller MongoDB backups
- ✅ **Scalability**: S3 handles unlimited historical data

### S3-Only (Option 3) - Self-Hosted

**Storage:**
- MongoDB Index: ~100 MB (lightweight index only)
- S3: ~127 GB (compressed ~64 GB) for 7 years

**Cost for Self-Hosted:**
- MongoDB Index: **$0** (uses existing cluster, negligible space)
- S3 Storage: 64 GB × $0.023/GB = **~$1.47/month**
- S3 Requests: More frequent (real-time) = **~$0.10/month**
- **Total: ~$1.57/month**

---

## Decision Matrix: When to Choose What? (Self-Hosted MongoDB)

### Choose MongoDB-Only (Option 1) If:

✅ **Your Situation:**
- Small to medium deployment (< 50,000 events/day)
- Plenty of disk space available (> 500 GB free)
- Need simple implementation (MVP)
- Query patterns focus on recent events (last 30-90 days)
- Can migrate to hybrid later if needed

✅ **Benefits:**
- Simplest implementation
- No S3 integration needed initially
- Fast queries on all data (no archival lookup)
- Zero additional infrastructure costs

⚠️ **Watch Out For:**
- Collection growth beyond ~50 GB → performance degradation
- Disk space usage over time
- Backup storage requirements
- Query performance on large historical datasets

### Choose Hybrid MongoDB + S3 (Option 2) If:

✅ **Your Situation:**
- Medium to large deployment (> 50,000 events/day)
- Limited disk space on MongoDB cluster
- Need long-term retention (7+ years)
- Performance-critical queries on recent data
- Compliance requires immutable audit trail

✅ **Benefits:**
- Keeps MongoDB collection small and fast
- Cost-effective long-term storage
- Immutable audit trail (S3 versioning)
- Scales to unlimited historical data

⚠️ **Trade-offs:**
- More complex implementation (archival logic)
- Historical queries require S3 access (slower)
- Two storage systems to manage

### Choose S3-Only (Option 3) If:

✅ **Your Situation:**
- Maximum immutability requirements
- Very limited MongoDB disk space
- Historical queries are rare
- Compliance mandates immediate immutable storage

⚠️ **Trade-offs:**
- All queries require S3 access (higher latency)
- More complex query implementation

---

## Practical Recommendations for Self-Hosted MongoDB

### Start with MongoDB-Only (MVP)

**Why:**
1. **Simple to implement** - Single database, no archival logic
2. **Fast queries** - All data in MongoDB, no S3 lookups
3. **No additional costs** - Uses existing infrastructure
4. **Easy to migrate later** - Can add S3 archival when needed

**When to migrate to Hybrid:**
- Collection exceeds **10-20 GB** (performance starts degrading)
- Disk space becomes concern
- Need compliance-grade immutability
- Want to reduce MongoDB backup sizes

**How to monitor:**
- Track `audit_logs` collection size weekly
- Monitor query performance (slow query logs)
- Watch disk space usage trends

### Storage Estimates (Realistic)

| Event Volume | Daily Size | 90 Days | 7 Years | Decision |
|--------------|------------|---------|---------|----------|
| 10,000/day   | 5 MB       | 450 MB  | 13 GB   | ✅ MongoDB-only OK |
| 50,000/day   | 25 MB      | 2.25 GB | 64 GB   | ✅ MongoDB-only OK (but watch) |
| 100,000/day  | 50 MB      | 4.5 GB  | 127 GB  | ⚠️ Consider hybrid after 1-2 years |
| 500,000/day  | 250 MB     | 22 GB   | 640 GB  | ❌ Start with hybrid |

---

## Scalability Considerations

3. ✅ **Partitioning**: Separate collections by time period
   - `audit_logs_2025_01`, `audit_logs_2025_02`, etc.
   - Easier archival and cleanup
   - Requires query routing logic

### S3 Scaling

- **Automatic**: S3 scales automatically to any volume
- **Partitioning**: Already partitioned by date/team
- **Query Optimization**: Use S3 Select for filtering without full file download

---

## Current Implementation Status

### ✅ Phase 1: MongoDB-Only MVP (Completed)
- Audit logging service implemented
- All logs stored in MongoDB `audit_logs` collection
- Comprehensive indexing on `teamId`, `timestamp`, `eventType`, `userId`
- Basic query/reporting interface via API

### ✅ Phase 2: Add S3 Archival (Completed)
- Archival service implemented (`AuditArchivalService`)
- Automated daily job moves logs > 90 days to S3
- **Encrypted Backup**:
  - Logs compressed (GZIP) and **Encrypted** using Team Key (`ChunkEncryptionService`)
  - Authenticated encryption (AES-256-GCM) ensures confidentiality even from storage admins
  - Filename includes key version: `events-{timestamp}-{version}.json.gz` for automatic key rotation support
  - S3 Server-Side Encryption (SSE-S3) enables double-layer protection
- **Unified Search**:
  - API supports `includeArchived` flag
  - Merges results from MongoDB (hot) and S3 (cold)
  - S3 search handles decryption and decompression on-the-fly
  - Frontend UI includes toggle for searching archives
- **Data Retention & Minimization**:
  - **MongoDB Pruning**: Logs are automatically deleted from MongoDB after successful encrypted archival to S3.
  - **S3 Lifecycle**: Audit logs expire after 7 years; Exports expire after 7 days.
  - **Document Trash Can**: Soft-deleted documents are retained for 30 days before permanent deletion.

### Phase 3: Enhanced Features (In Progress)
- ✅ **Security Incident Response System** (Implemented): I
  - `SecuritySentinelService` implemented (`MassExfiltration`, `BruteForce`)
  - Integration with Audit Stream for real-time detection
  - Incident Management UI (`SecurityIncidents` page)
  - Security Bell notifications & Email Alerts
  - Auto-response workflows (Account Locking)
- Automated compliance reports (GDPR Artifact Generation implemented)
- Real-time audit dashboards (Partially covered by Security Dashboard)

---

## Security Considerations

### Audit Log Protection

1. **Encryption at Rest**
   - MongoDB: Use encrypted storage (MongoDB Atlas encryption)
   - S3: Enable SSE-S3 or SSE-KMS

2. **Access Control**
   - Audit logs only readable by ADMIN/SUPER_ADMIN roles
   - Separate service role for archival jobs
   - Audit log access itself should be logged (meta-audit)

3. **Immutable Storage**
   - S3 versioning prevents deletion
   - Write-once, read-many pattern
   - Regular integrity checks

4. **Data Minimization**
   - Never log sensitive data (passwords, tokens, decrypted content)
   - Only log IDs and metadata
   - Hash PII if needed for analytics
   - **PII Detection**: When PII is detected in chat messages, only log metadata (types detected, counts) - never log actual PII values
   - **PII Redaction**: Actual PII values are automatically redacted (replaced with placeholders) before saving messages

### PII Detection and Audit Logging

**Overview:**
The system includes automated PII (Personally Identifiable Information) detection and redaction capabilities for AI Assistant chat conversations. When enabled, the system scans all user and assistant messages for common PII patterns and automatically redacts detected values before storage.

**How It Works:**
1. **PII Detection Enabled**: When an AI Assistant has "Enable PII Detection" enabled:
   - All user messages and assistant responses are scanned for PII patterns
   - Detected PII types: SSN, Email, Phone, Credit Card, Driver's License, Passport, Bank Account, IP Address
   - PII values are automatically redacted (replaced with placeholders like `[SSN_REDACTED]`, `[EMAIL_REDACTED]`)
   - Messages are marked with `piiDetected: true` in metadata
   - Original PII values are never stored in conversations

2. **Audit Logging for PII**: PII detection events are logged to audit logs only when:
   - **Both** "Enable PII Detection" AND "Enable Audit Logging" are enabled for the AI Assistant
   - A PII detection event (`PII_DETECTED`) is created with:
     - Event type: `PII_DETECTED`
     - Resource type: `CHAT`
     - Metadata includes: PII types detected (e.g., `["SSN", "EMAIL"]`), counts per type, total matches
     - **Important**: Audit logs contain NO actual PII values - only metadata about what types were detected

3. **Compliance Flags**: PII detection events automatically set:
   - `complianceFlags.containsPII: true`
   - `complianceFlags.sensitiveData: true`
   - `complianceFlags.requiresRetention: true` (for long-term compliance retention)

**Example PII Detection Audit Log Entry:**
```json
{
  "eventId": "uuid",
  "timestamp": "2025-01-15T10:30:00Z",
  "eventType": "PII_DETECTED",
  "action": "READ",
  "resourceType": "CHAT",
  "resourceId": "conversation-123",
  "userId": "user-456",
  "username": "john.doe",
  "teamId": "team-789",
  "result": "SUCCESS",
  "metadata": {
    "reason": "PII detected in USER message: 2 type(s), 3 total matches",
    "context": {
      "conversationId": "conversation-123",
      "assistantId": "assistant-abc",
      "messageRole": "USER",
      "piiDetected": true,
      "piiTypes": [
        { "type": "SSN", "count": 1 },
        { "type": "EMAIL", "count": 2 }
      ],
      "totalMatches": 3
    }
  },
  "complianceFlags": {
    "containsPII": true,
    "sensitiveData": true,
    "requiresRetention": true
  }
}
```

**Key Compliance Points:**
- ✅ **No PII in Audit Logs**: Only metadata (types and counts) is logged, never actual values
- ✅ **Automatic Redaction**: PII values are replaced before storage, ensuring compliance
- ✅ **HIPAA Compliant**: Meets HIPAA audit control requirements without storing ePHI
- ✅ **GDPR Compliant**: Follows data minimization principles - only necessary metadata is logged

---

## Compliance Features

### HIPAA Requirements
- ✅ Track all access to ePHI (electronic Protected Health Information)
- ✅ Immutable audit trail (S3 versioning)
- ✅ 7-year retention
- ✅ Query interface for compliance reviews
- ✅ PII detection and redaction for chat conversations
- ✅ Audit logging of PII detection events (metadata only, no actual PII values)

### SOX Requirements
- ✅ Track all data modifications
- ✅ Immutable audit trail
- ✅ Access control audit logs
- ✅ Regular compliance reporting

### GDPR Requirements
- ✅ Track data access (for "right to access" requests)
- ✅ Track data deletion (for "right to erasure")
- ✅ Data lifecycle tracking
- ✅ Breach detection capabilities
- ✅ PII detection and automatic redaction in chat messages
- ✅ Data minimization: Only metadata logged (types/counts), never actual PII values
- ✅ Right to erasure: Can identify messages containing PII via `piiDetected` flag

---

## Next Steps

1. **Decide on Storage Approach**: MongoDB-only vs Hybrid vs S3-only
2. **Define Event Types**: Finalize list of events to audit
3. **Design Service Interface**: Create `AuditService` interface
4. **Implement MVP**: Start with MongoDB-only approach
5. **Add Instrumentation**: Add audit logging calls throughout codebase
6. **Build Query Interface**: Create API endpoints for audit log queries
7. **Monitor Volume**: Track actual audit log volume and patterns
8. **Plan Archival**: Implement S3 archival when needed

---

## Questions to Answer

1. **What's the expected audit log volume?**
   - Events per day per team?
   - Total teams/users?

2. **What's the retention requirement?**
   - Default: 7 years?
   - Configurable per team?

3. **What's the query pattern?**
   - Mostly recent events (last 30 days)?
   - Or frequent historical queries?

4. **What's the budget constraint?**
   - MongoDB-only acceptable?
   - Or need cost-effective long-term storage?

5. **What's the immutability requirement?**
   - Is MongoDB sufficient?
   - Or need S3 versioning?

---

## Recommendation Summary

**For MVP/Initial Implementation:**
- **Start with MongoDB-only** (Option 1)
- Simple, fast to implement
- Easy to query and report
- Can migrate to hybrid later

**For Production/Scale:**
- **Implement Hybrid MongoDB + S3** (Option 2)
- Best balance of performance and cost
- Immutable audit trail (compliance-ready)
- Scales to large volumes

**Timeline:**
- Phase 1 (MongoDB-only): 1-2 weeks
- Phase 2 (Add S3 archival): 2-3 weeks (when needed)
- Phase 3 (Enhanced features): Ongoing

---

## Implementation Status

### ✅ Completed Features

#### 1. Access Denied Logging

**Implementation:**
- ✅ Automatic logging of `AccessDeniedException` via `GlobalExceptionHandler`
- ✅ Logs `ACCESS_DENIED` audit events with user and team context
- ✅ Extracts context from `ReactiveSecurityContextHolder` when available
- ✅ Falls back to SYSTEM user if context is not available
- ✅ Integrated into reactive chain (no `.subscribe()`)

**Event Details:**
- Event Type: `ACCESS_DENIED`
- Result: `DENIED`
- Captures: username, teamId, reason, timestamp

#### 2. Chunk Access & Decryption Logging

**Implementation:**
- ✅ Audit logging in `ChunkEncryptionServiceImpl.decryptChunkText()`
- ✅ Audit logging in `ChunkEncryptionServiceImpl.decryptFile()`
- ✅ Logs `CHUNK_DECRYPTED` events on successful decryption
- ✅ Logs `DECRYPTION_FAILED` events on failed decryption attempts
- ✅ Extracts user and team context from security context
- ✅ Integrated into reactive chains with proper error handling

**Event Details:**
- Event Types: `CHUNK_DECRYPTED`, `DECRYPTION_FAILED`
- Resource Type: `CHUNK` (for chunk text) or `FILE` (for file decryption)
- Captures: username, teamId, keyVersion, decryption success/failure details

#### 3. PII Detection Service

**Implementation:**
- ✅ Pattern-based detection for common PII types (SSN, Email, Phone, Credit Card, Driver's License, Passport, Bank Account, IP Address)
- ✅ Automatic redaction of detected PII values (replaces with placeholders like `[SSN_REDACTED]`)
- ✅ Metadata extraction for audit logging (types and counts only, no actual PII values)

**AI Assistant Integration:**
- ✅ Configurable per AI Assistant via "Enable PII Detection" checkbox
- ✅ Automatic detection and redaction in both user and assistant messages
- ✅ Messages marked with `piiDetected: true` flag in metadata
- ✅ Redaction happens automatically when PII Detection is enabled (no separate redaction toggle)

**Audit Logging Integration:**
- ✅ PII detection events (`PII_DETECTED`) logged when both PII Detection and Audit Logging are enabled
- ✅ Audit logs contain only metadata (types detected, counts per type, total matches) - never actual PII values
- ✅ Compliance flags automatically set:
  - `containsPII: true`
  - `sensitiveData: true`
  - `requiresRetention: true`

**Compliance:**
- ✅ HIPAA compliant: Tracks PII detection without storing actual ePHI values
- ✅ GDPR compliant: Data minimization - only necessary metadata logged, automatic redaction prevents PII storage
- ✅ SOX compliant: Immutable audit trail for sensitive data access

### Configuration

AI Assistants can configure PII detection via Guardrails settings:

- **Enable PII Detection**: 
  - Detects PII patterns in all chat messages (user and assistant)
  - Automatically redacts detected PII values before storage
  - Marks messages with `piiDetected: true` in metadata
  - Works independently - does not require Audit Logging

- **Enable Audit Logging**: 
  - Logs all assistant interactions for audit purposes
  - When combined with PII Detection, also logs `PII_DETECTED` events
  - PII detection events only logged when both checkboxes are enabled

**Note:** These settings are independent features that work together:
- PII Detection can be enabled without Audit Logging (detection and redaction still work)
- Audit Logging can be enabled without PII Detection (other chat events are still logged)
- Both enabled: Full PII protection + audit trail for compliance

