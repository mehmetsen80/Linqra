# Data Export Feature Design - Brainstorming

## Current Situation

### Per-File Processed JSON Download
- **Location**: View Document page → "Download JSON" button
- **Current Behavior**: Downloads encrypted processed JSON (not useful)
- **Security**: Only team-based access (any team member can download)
- **Problem**: 
  - Downloads encrypted data (useless to users)
  - No clear use case for per-file download
  - Security risk if decrypted (contains all chunk text, form fields, metadata)
  - No audit logging

### What Processed JSON Contains
- **All chunk text** - Full document content (highly sensitive)
- **Form field values** - Potentially PII, financial data, legal information
- **Extracted metadata** - Title, author, subject, keywords (potentially sensitive)
- **Processing metadata** - Timestamps, models used

## Options Analysis

### Option 1: Remove Per-File Download ✅ **RECOMMENDED**

**Rationale:**
- Per-file processed JSON download has no clear user use case
- Users can already view document content through the UI
- Reduces attack surface and security risks
- Simpler codebase (less code to maintain)
- Users don't typically need raw processed chunks per file

**Action Items:**
1. Remove "Download JSON" button from View Document page
2. Remove `/view/{documentId}/processed/download` endpoint
3. Keep `/view/{documentId}/processed` endpoint (for UI viewing) - this already decrypts

**Pros:**
- ✅ Eliminates security risk
- ✅ Simpler UX
- ✅ Less code to maintain
- ✅ Users still have access to document content through UI

**Cons:**
- ❌ Users can't export individual files (but do they need to?)

---

### Option 2: Bulk Export Feature (Data Portability)

**Use Cases:**
- **Data Portability**: Users want to export all their AI data to move to another system
- **Backup**: Create backups of AI knowledge base
- **Compliance**: GDPR/CCPA "Right to Access" - export all user data
- **Migration**: Move data between environments or systems

**Design Requirements:**

#### 2.1 Authorization & Access Control
- **Restrict to ADMIN or SUPER_ADMIN only**
- Team-based export (admins can export their team's data)
- SUPER_ADMIN can export any team's data
- Multi-factor confirmation required

#### 2.2 Export Scope Options
- **Collection Export**: Export all documents in a collection
- **Team Export**: Export all documents for a team
- **Custom Export**: Select specific documents to export
- **Full Export**: Export everything (SUPER_ADMIN only)

#### 2.3 Export Format
- **Structured JSON**: Well-formatted JSON files
- **ZIP Archive**: Multiple JSON files organized by collection/document
- **Include**:
  - Document metadata
  - Processed JSON (decrypted chunks, metadata, form fields)
  - Knowledge Graph entities and relationships (decrypted)
  - Embedding metadata (collection info, but not vectors themselves)

#### 2.4 Security & Safety Measures

**Confirmation Requirements:**
- Warning modal explaining what's being exported
- User must type "EXPORT" or confirmation code to proceed
- Multiple confirmations for large exports

**Rate Limiting:**
- Maximum 1 export per day per team
- Maximum export size limits (e.g., 10GB)
- Queue large exports for background processing

**Audit Logging:**
- Log all export requests (who, when, what scope)
- Log export completion (file size, document count)
- Store audit logs in immutable storage (S3)
- Include export metadata in audit log

**Data Handling:**
- All exports are decrypted (user's own data)
- Exports expire after download (e.g., 24 hours)
- Downloads require re-authentication
- Files are stored temporarily in S3 with short expiration

#### 2.5 Implementation Approach

**Backend:**
- New endpoint: `POST /api/documents/export`
- Request body:
  ```json
  {
    "scope": "collection" | "team" | "custom" | "full",
    "collectionId": "optional",
    "documentIds": ["optional", "for custom scope"],
    "format": "json" | "zip",
    "includeVectors": false  // Always false - vectors are too large
  }
  ```
- Returns: Export job ID
- Background job processes export asynchronously
- Notifies user when export is ready (via WebSocket or polling)

**Export Job Flow:**
1. User requests export → Create export job record
2. Job validates authorization and scope
3. Job collects all documents in scope
4. Job decrypts all processed JSON files
5. Job exports knowledge graph data (decrypted entities/relationships)
6. Job creates ZIP/JSON file(s)
7. Job uploads to temporary S3 location (24-hour expiration)
8. Job notifies user with download link
9. Job cleans up after expiration

**Frontend:**
- New page: "Export AI Data" (in Knowledge Hub settings)
- Export configuration form
- Export history table (past exports)
- Download links for completed exports
- Status indicators (pending, processing, ready, expired)

---

### Option 3: Admin-Only Per-File Download (NOT RECOMMENDED)

**Why Not Recommended:**
- Still unclear use case for per-file download
- Adds complexity without clear benefit
- Better to have bulk export feature instead

**If Implemented:**
- Restrict to ADMIN/SUPER_ADMIN only
- Add audit logging
- Decrypt before download
- Add confirmation modal
- Still risky - why would admin need to download processed chunks?

---

## Recommendation

### Phase 1: Immediate Action ✅
**Remove per-file download feature**
- Remove "Download JSON" button
- Remove download endpoint
- Keep viewing endpoint for UI

**Rationale:**
- No clear use case
- Security risk
- Users can view content in UI already

### Phase 2: Future Enhancement (If Needed)
**Implement bulk export feature** if users request it:
- Proper authorization (ADMIN only)
- Strong confirmations
- Audit logging
- Rate limiting
- Clear use case (data portability)

**When to Build:**
- If users explicitly request data export capability
- For GDPR/CCPA compliance requirements
- For enterprise customers who need data portability

---

## Comparison Table

| Feature | Per-File Download | Bulk Export |
|---------|-------------------|-------------|
| **Use Case** | Unclear | Clear (data portability) |
| **Authorization** | Team member | ADMIN only |
| **Audit Logging** | No | Yes |
| **Rate Limiting** | No | Yes |
| **Confirmation** | No | Multi-step |
| **Security Risk** | High | Low (with controls) |
| **Implementation** | Simple | Complex |
| **User Benefit** | Low | High |

---

## Questions to Consider

1. **Do users actually need to download processed JSON?**
   - If yes, for what purpose?
   - Can they achieve their goal through the UI?

2. **Is data portability a requirement?**
   - For compliance (GDPR/CCPA)?
   - For customer requests?
   - For migration purposes?

3. **Who should have export access?**
   - Only SUPER_ADMIN?
   - Team ADMINs?
   - All users (risky)?

4. **What should be exported?**
   - All document content?
   - Metadata only?
   - Knowledge graph?
   - Embeddings (vectors are too large)?

---

## Next Steps

1. ✅ **Decision**: Remove per-file download (immediate)
2. ⏳ **Research**: Survey users if they need data export
3. ⏳ **Design**: Detailed bulk export feature design (if needed)
4. ⏳ **Implement**: Bulk export feature (if users request it)

---

**Recommendation**: Remove per-file download now, implement bulk export later if users request it.

