# Knowledge Graph UI Design

## Overview
Add Knowledge Graph features to the Knowledge Hub, allowing users to:
- View graph statistics and overview
- Extract entities and relationships from documents
- Browse entities by type
- View entity relationships
- Access Neo4j Browser for advanced visualization

## UI Structure

### 1. Knowledge Hub Page Enhancement
**Location:** `edge-service/src/pages/KnowledgeHub/index.jsx`

**Changes:**
- Add a new **"Knowledge Graph"** tab/section alongside the collections table
- Add graph statistics cards at the top
- Add "View Knowledge Graph" button in header (links to Neo4j Browser)

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│ Knowledge Hub                              [View Graph] │
├─────────────────────────────────────────────────────────┤
│ [Collections] [Knowledge Graph]                         │
├─────────────────────────────────────────────────────────┤
│ Graph Statistics                                         │
│ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐          │
│ │Forms │ │Orgs  │ │People│ │Dates │ │Locns │          │
│ │ 12   │ │ 8    │ │ 25   │ │ 45   │ │ 15   │          │
│ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘          │
│                                                         │
│ Total Entities: 105    Total Relationships: 87         │
├─────────────────────────────────────────────────────────┤
│ Entity Browser                                          │
│ [Type: All ▼] [Search...]                    [Refresh] │
│                                                         │
│ Entity Type    │ Count │ Actions                      │
│────────────────┼───────┼─────────────────────────────│
│ Form           │  12   │ [View] [Related]            │
│ Organization   │   8   │ [View] [Related]            │
│ Person         │  25   │ [View] [Related]            │
│ Date           │  45   │ [View] [Related]            │
│ Location       │  15   │ [View] [Related]            │
│ Document       │   0   │ [View] [Related]            │
└─────────────────────────────────────────────────────────┘
```

### 2. Collection Detail Page Enhancement
**Location:** `edge-service/src/pages/KnowledgeHub/ViewCollection/index.jsx`

**Changes:**
- Add "Extract Graph" button/action in document actions column
- Show extraction status badge on documents
- Add graph statistics for the collection

**Document Actions:**
```
[View] [Extract Graph ▼] [Delete]
                         ├─ Extract Entities
                         ├─ Extract Relationships
                         └─ Extract All
```

**Document Status:**
- Add badge: "Entities: 5, Relationships: 3" if extracted
- Show extraction cost if available

### 3. Document Detail Page Enhancement
**Location:** `edge-service/src/pages/KnowledgeHub/ViewCollection/ViewDocument/index.jsx`

**Changes:**
- Add "Knowledge Graph" tab/section
- Show extracted entities and relationships
- Allow manual extraction triggers

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│ Document: form-i130.pdf                                 │
├─────────────────────────────────────────────────────────┤
│ [Details] [Chunks] [Knowledge Graph]                    │
├─────────────────────────────────────────────────────────┤
│ Graph Extraction                                        │
│ Status: ✓ Extracted (5 entities, 3 relationships)       │
│ Cost: $0.0045 (Entity: $0.0021, Relationship: $0.0024) │
│                                                         │
│ [Extract Entities] [Extract Relationships] [Extract All]│
├─────────────────────────────────────────────────────────┤
│ Extracted Entities                                      │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Form: I-130 (Petition for Alien Relative)          │ │
│ │ Organization: USCIS                                 │ │
│ │ Person: John Doe                                    │ │
│ │ Date: 2024-01-15                                    │ │
│ │ Location: San Francisco, CA                         │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ Relationships                                           │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ I-130 ─[REQUIRES]→ I-864 (Affidavit of Support)   │ │
│ │ John Doe ─[SUBMITS_TO]→ USCIS                      │ │
│ │ I-130 ─[FILED_BY]→ John Doe                        │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ [View in Neo4j Browser]                                 │
└─────────────────────────────────────────────────────────┘
```

### 4. Entity Browser Component (New)
**Location:** `edge-service/src/components/knowledgeHub/EntityBrowser.jsx`

**Features:**
- Filter entities by type (Form, Organization, Person, Date, Location, Document)
- Search entities by name/id
- View entity details
- View related entities
- Delete entities

### 5. Graph Statistics Component (New)
**Location:** `edge-service/src/components/knowledgeHub/GraphStatistics.jsx`

**Features:**
- Display total entities by type
- Display total relationships
- Display extraction costs (if available)
- Refresh button

### 6. Service Layer
**Location:** `edge-service/src/services/knowledgeHubGraphService.jsx` (NEW)

**Methods:**
```javascript
{
  getGraphStatistics: () => Promise,
  findEntities: (entityType, filters) => Promise,
  findRelatedEntities: (entityType, entityId, options) => Promise,
  extractEntitiesFromDocument: (documentId) => Promise,
  extractRelationshipsFromDocument: (documentId) => Promise,
  extractAllFromDocument: (documentId) => Promise,
  deleteEntity: (entityType, entityId) => Promise,
  executeCypherQuery: (query, parameters) => Promise
}
```

## Implementation Plan

### Phase 1: Foundation
1. ✅ Create `knowledgeHubGraphService.jsx`
2. ✅ Add graph statistics to Knowledge Hub page
3. ✅ Add "View Graph" button (links to Neo4j Browser: `/neo4j/`)

### Phase 2: Entity Browser
4. ✅ Create `EntityBrowser` component
5. ✅ Add "Knowledge Graph" tab to Knowledge Hub page
6. ✅ Implement entity filtering and search

### Phase 3: Document Integration
7. ✅ Add extraction actions to collection view
8. ✅ Add "Knowledge Graph" tab to document detail page
9. ✅ Show extraction status and results

### Phase 4: Advanced Features
10. ✅ Entity detail modal/panel
11. ✅ Related entities view
12. ✅ Custom Cypher query interface (optional)

## API Endpoints Used

- `GET /api/knowledge-graph/statistics` - Get graph statistics
- `GET /api/knowledge-graph/entities/{entityType}` - Find entities by type
- `GET /api/knowledge-graph/entities/{entityType}/{entityId}/related` - Find related entities
- `POST /api/knowledge-graph/documents/{documentId}/extract-entities` - Extract entities
- `POST /api/knowledge-graph/documents/{documentId}/extract-relationships` - Extract relationships
- `POST /api/knowledge-graph/documents/{documentId}/extract-all` - Extract both
- `DELETE /api/knowledge-graph/entities/{entityType}/{entityId}` - Delete entity
- `POST /api/knowledge-graph/query` - Execute Cypher query

## Entity Types Supported

- **Form** - USCIS forms, government forms
- **Organization** - Government agencies, companies
- **Person** - Individual names
- **Date** - Important dates, deadlines
- **Location** - Addresses, cities, countries
- **Document** - Document types, certificates

## Relationship Types Supported

- **MENTIONS** - Entity mentions another entity
- **REQUIRES** - Form requires another form/document
- **SUBMITS_TO** - Form submitted to organization
- **FILED_BY** - Form filed by person
- **LOCATED_AT** - Person/organization located at location
- **RELATED_TO** - General relationship

## UI Components Needed

1. **GraphStatistics.jsx** - Statistics cards and overview
2. **EntityBrowser.jsx** - Entity browsing with filters
3. **EntityDetailModal.jsx** - Entity details and relationships
4. **ExtractionStatusBadge.jsx** - Show extraction status on documents
5. **ExtractGraphDropdown.jsx** - Dropdown menu for extraction actions

## Styling

- Use existing Bootstrap components and custom styles
- Match Knowledge Hub design patterns
- Use badges for counts and status
- Use icons: `HiGraph`, `HiCube`, `HiLink` from react-icons/hi

