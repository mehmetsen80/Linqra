// Comprehensive MongoDB Index Creation Scripts for All Entities
// Run these commands in MongoDB shell: mongosh Linqra < mongodb_indexes.js
// Or: mongosh Linqra and then copy/paste these commands
//
// NOTE: This script is idempotent - it's safe to run multiple times.
// If an index with the same name and key pattern already exists, createIndex() will do nothing.
// If an index with a different key pattern exists, you may need to drop it first.

// ============================================================================
// AGENTS COLLECTION
// ============================================================================

// 1. Team Enabled Index
db.agents.createIndex(
  { "teamId": 1, "enabled": 1 },
  { 
    "name": "team_enabled_idx",
    "background": true
  }
);

// 2. Team Name Unique Index (unique constraint)
db.agents.createIndex(
  { "teamId": 1, "name": 1 },
  { 
    "name": "team_name_unique_idx",
    "unique": true,
    "background": true
  }
);

// 3. Team Capabilities Index
db.agents.createIndex(
  { "teamId": 1, "capabilities": 1 },
  { 
    "name": "team_capabilities_idx",
    "background": true
  }
);

// 4. Team Created Index
db.agents.createIndex(
  { "teamId": 1, "createdAt": -1 },
  { 
    "name": "team_created_idx",
    "background": true
  }
);

// ============================================================================
// AGENT_EXECUTIONS COLLECTION
// ============================================================================

// 1. Agent Created Index
db.agent_executions.createIndex(
  { "agentId": 1, "createdAt": -1 },
  { 
    "name": "agent_created_idx",
    "background": true
  }
);

// 2. Agent Started Index
db.agent_executions.createIndex(
  { "agentId": 1, "startedAt": -1 },
  { 
    "name": "agent_started_idx",
    "background": true
  }
);

// 3. Agent Completed Index
db.agent_executions.createIndex(
  { "agentId": 1, "completedAt": -1 },
  { 
    "name": "agent_completed_idx",
    "background": true
  }
);

// 4. Agent Status Index
db.agent_executions.createIndex(
  { "agentId": 1, "status": 1 },
  { 
    "name": "agent_status_idx",
    "background": true
  }
);

// 5. Agent Result Index
db.agent_executions.createIndex(
  { "agentId": 1, "result": 1 },
  { 
    "name": "agent_result_idx",
    "background": true
  }
);

// 6. Task Created Index
db.agent_executions.createIndex(
  { "taskId": 1, "createdAt": -1 },
  { 
    "name": "task_created_idx",
    "background": true
  }
);

// 7. Task Status Index
db.agent_executions.createIndex(
  { "taskId": 1, "status": 1 },
  { 
    "name": "task_status_idx",
    "background": true
  }
);

// 8. Team Created Index
db.agent_executions.createIndex(
  { "teamId": 1, "createdAt": -1 },
  { 
    "name": "team_created_idx",
    "background": true
  }
);

// 9. Workflow Execution Index
db.agent_executions.createIndex(
  { "workflowExecutionId": 1, "createdAt": -1 },
  { 
    "name": "workflow_exec_idx",
    "background": true
  }
);

// ============================================================================
// AGENT_TASKS COLLECTION
// ============================================================================

// 1. Next Run Enabled Index
db.agent_tasks.createIndex(
  { "nextRun": 1, "enabled": 1 },
  { 
    "name": "nextRun_enabled_idx",
    "background": true
  }
);

// 2. Agent Enabled Index
db.agent_tasks.createIndex(
  { "agentId": 1, "enabled": 1 },
  { 
    "name": "agent_enabled_idx",
    "background": true
  }
);

// 3. Agent Next Run Index
db.agent_tasks.createIndex(
  { "agentId": 1, "nextRun": 1 },
  { 
    "name": "agent_nextRun_idx",
    "background": true
  }
);

// ============================================================================
// AGENT_TASK_VERSIONS COLLECTION
// ============================================================================

// 1. Task Version Index
db.agent_task_versions.createIndex(
  { "taskId": 1, "version": -1 },
  { 
    "name": "task_version_idx",
    "background": true
  }
);

// 2. Team Created Index
db.agent_task_versions.createIndex(
  { "teamId": 1, "createdAt": -1 },
  { 
    "name": "team_created_idx",
    "background": true
  }
);

// 3. Agent Version Index
db.agent_task_versions.createIndex(
  { "agentId": 1, "version": -1 },
  { 
    "name": "agent_version_idx",
    "background": true
  }
);

// ============================================================================
// AI_ASSISTANTS COLLECTION
// ============================================================================

// 1. Team Name Unique Index (unique constraint)
db.ai_assistants.createIndex(
  { "teamId": 1, "name": 1 },
  { 
    "name": "team_name_unique_idx",
    "unique": true,
    "background": true
  }
);

// 2. Team Created Index
db.ai_assistants.createIndex(
  { "teamId": 1, "createdAt": -1 },
  { 
    "name": "team_created_idx",
    "background": true
  }
);

// 3. Public API Key Index (unique, sparse)
db.ai_assistants.createIndex(
  { "accessControl.publicApiKey": 1 },
  { 
    "name": "public_api_key_idx",
    "unique": true,
    "sparse": true,
    "background": true
  }
);

// ============================================================================
// ALERTS COLLECTION
// ============================================================================

// 1. Route Active Index
db.alerts.createIndex(
  { "routeId": 1, "active": 1 },
  { 
    "name": "route_active_idx",
    "background": true
  }
);

// 2. Route Metric Active Index
db.alerts.createIndex(
  { "routeId": 1, "metric": 1, "active": 1 },
  { 
    "name": "route_metric_active_idx",
    "background": true
  }
);

// 3. Alert Unique Index
db.alerts.createIndex(
  { "routeId": 1, "metric": 1, "condition": 1, "threshold": 1 },
  { 
    "name": "alert_unique_idx",
    "background": true
  }
);

// ============================================================================
// API_ENDPOINT_VERSIONS COLLECTION
// ============================================================================

// 1. Endpoint Version Index
db.apiEndpointVersions.createIndex(
  { "endpointId": 1, "version": -1 },
  { 
    "name": "endpoint_version_idx",
    "background": true
  }
);

// 2. Route Version Index
db.apiEndpointVersions.createIndex(
  { "routeIdentifier": 1, "version": -1 },
  { 
    "name": "route_version_idx",
    "background": true
  }
);

// ============================================================================
// API_ROUTES COLLECTION
// ============================================================================

// 1. Health Check Enabled Index
db.apiRoutes.createIndex(
  { "healthCheck.enabled": 1 },
  { 
    "name": "healthcheck_enabled_idx",
    "background": true
  }
);

// ============================================================================
// API_ROUTE_VERSIONS COLLECTION
// ============================================================================

// 1. Route Version Index
db.apiRouteVersions.createIndex(
  { "routeIdentifier": 1, "version": -1 },
  { 
    "name": "route_version_idx",
    "background": true
  }
);

// 2. RouteId Version Index
db.apiRouteVersions.createIndex(
  { "routeId": 1, "version": -1 },
  { 
    "name": "routeId_version_idx",
    "background": true
  }
);

// 3. Route Created Index
db.apiRouteVersions.createIndex(
  { "routeIdentifier": 1, "createdAt": -1 },
  { 
    "name": "route_created_idx",
    "background": true
  }
);

// ============================================================================
// CONVERSATIONS COLLECTION
// ============================================================================

// 1. Assistant Created Index
db.conversations.createIndex(
  { "assistantId": 1, "createdAt": -1 },
  { 
    "name": "assistant_created_idx",
    "background": true
  }
);

// 2. Team Created Index
db.conversations.createIndex(
  { "teamId": 1, "createdAt": -1 },
  { 
    "name": "team_created_idx",
    "background": true
  }
);

// 3. User Created Index
db.conversations.createIndex(
  { "username": 1, "createdAt": -1 },
  { 
    "name": "user_created_idx",
    "background": true
  }
);

// 4. Assistant Status Index
db.conversations.createIndex(
  { "assistantId": 1, "status": 1 },
  { 
    "name": "assistant_status_idx",
    "background": true
  }
);

// 5. Public Created Index
db.conversations.createIndex(
  { "isPublic": 1, "createdAt": -1 },
  { 
    "name": "public_created_idx",
    "background": true
  }
);

// 6. Assistant Username Started Index
db.conversations.createIndex(
  { "assistantId": 1, "username": 1, "startedAt": -1 },
  { 
    "name": "assistant_username_started_idx",
    "background": true
  }
);

// ============================================================================
// CONVERSATION_MESSAGES COLLECTION
// ============================================================================

// 1. Conversation Sequence Index
db.conversation_messages.createIndex(
  { "conversationId": 1, "sequenceNumber": 1 },
  { 
    "name": "conversation_sequence_idx",
    "background": true
  }
);

// 2. Conversation Created Index
db.conversation_messages.createIndex(
  { "conversationId": 1, "createdAt": 1 },
  { 
    "name": "conversation_created_idx",
    "background": true
  }
);

// 3. Conversation Role Index
db.conversation_messages.createIndex(
  { "conversationId": 1, "role": 1 },
  { 
    "name": "conversation_role_idx",
    "background": true
  }
);

// ============================================================================
// KNOWLEDGE_HUB_CHUNKS COLLECTION
// ============================================================================

// 1. Document Chunk Index (unique constraint)
db.knowledge_hub_chunks.createIndex(
  { "documentId": 1, "chunkIndex": 1 },
  { 
    "name": "document_chunk_idx",
    "unique": true,
    "background": true
  }
);

// 2. Document Team Index
db.knowledge_hub_chunks.createIndex(
  { "documentId": 1, "teamId": 1 },
  { 
    "name": "document_team_idx",
    "background": true
  }
);

// ============================================================================
// KNOWLEDGE_HUB_COLLECTION COLLECTION
// ============================================================================

// 1. Team Name Unique Index (unique constraint)
db.knowledge_hub_collection.createIndex(
  { "teamId": 1, "name": 1 },
  { 
    "name": "team_name_unique_idx",
    "unique": true,
    "background": true
  }
);

// ============================================================================
// KNOWLEDGE_HUB_DOCUMENTS COLLECTION
// ============================================================================

// 1. Team Collection Index
db.knowledge_hub_documents.createIndex(
  { "teamId": 1, "collectionId": 1 },
  { 
    "name": "team_collection_idx",
    "background": true
  }
);

// 2. Team Status Index
db.knowledge_hub_documents.createIndex(
  { "teamId": 1, "status": 1 },
  { 
    "name": "team_status_idx",
    "background": true
  }
);

// 3. Collection Status Index
db.knowledge_hub_documents.createIndex(
  { "collectionId": 1, "status": 1 },
  { 
    "name": "collection_status_idx",
    "background": true
  }
);

// ============================================================================
// KNOWLEDGE_HUB_DOCUMENT_METADATA COLLECTION
// ============================================================================

// 1. Document Team Collection Unique Index (unique constraint)
db.knowledge_hub_document_metadata.createIndex(
  { "documentId": 1, "teamId": 1, "collectionId": 1 },
  { 
    "name": "document_team_collection_unique_idx",
    "unique": true,
    "background": true
  }
);

// 2. Team Collection Index
db.knowledge_hub_document_metadata.createIndex(
  { "teamId": 1, "collectionId": 1 },
  { 
    "name": "team_collection_idx",
    "background": true
  }
);

// ============================================================================
// LINQ_LLM_MODELS COLLECTION
// ============================================================================

// 1. Model Category Model Name Team Index (unique constraint)
db.linq_llm_models.createIndex(
  { "modelCategory": 1, "modelName": 1, "teamId": 1 },
  { 
    "name": "modelcategory_modelname_team_idx",
    "unique": true,
    "background": true
  }
);

// ============================================================================
// LINQ_WORKFLOW_EXECUTIONS COLLECTION
// ============================================================================

// 1. Workflow Executed Index
db.linq_workflow_executions.createIndex(
  { "workflowId": 1, "executedAt": -1 },
  { 
    "name": "workflow_executed_idx",
    "background": true
  }
);

// 2. Team Executed Index
db.linq_workflow_executions.createIndex(
  { "teamId": 1, "executedAt": -1 },
  { 
    "name": "team_executed_idx",
    "background": true
  }
);

// 3. Workflow Team Executed Index
db.linq_workflow_executions.createIndex(
  { "workflowId": 1, "teamId": 1, "executedAt": -1 },
  { 
    "name": "workflow_team_executed_idx",
    "background": true
  }
);

// 4. ID Team Index
db.linq_workflow_executions.createIndex(
  { "_id": 1, "teamId": 1 },
  { 
    "name": "id_team_idx",
    "background": true
  }
);

// ============================================================================
// LINQ_WORKFLOW_VERSIONS COLLECTION
// ============================================================================

// 1. Workflow Team Version Index
db.linq_workflow_versions.createIndex(
  { "workflowId": 1, "teamId": 1, "version": -1 },
  { 
    "name": "workflow_team_version_idx",
    "background": true
  }
);

// ============================================================================
// LLM_PRICING_SNAPSHOTS COLLECTION
// ============================================================================

// 1. Team Month Model Index (unique constraint)
db.llm_pricing_snapshots.createIndex(
  { "teamId": 1, "yearMonth": 1, "model": 1 },
  { 
    "name": "team_month_model_idx",
    "unique": true,
    "background": true
  }
);

// ============================================================================
// TEAM_MEMBERS COLLECTION
// ============================================================================

// 1. Team User Index (unique constraint)
db.team_members.createIndex(
  { "teamId": 1, "userId": 1 },
  { 
    "name": "team_user_idx",
    "unique": true,
    "background": true
  }
);

// ============================================================================
// TEAM_ROUTES COLLECTION
// ============================================================================

// 1. Team Route Index (unique constraint)
db.team_routes.createIndex(
  { "teamId": 1, "routeId": 1 },
  { 
    "name": "team_route_idx",
    "unique": true,
    "background": true
  }
);

// 2. Route Team Index
db.team_routes.createIndex(
  { "routeId": 1, "teamId": 1 },
  { 
    "name": "route_team_idx",
    "background": true
  }
);

// ============================================================================
// GRAPH_EXTRACTION_JOBS COLLECTION
// ============================================================================

// 1. Document Team Status Index
db.graph_extraction_jobs.createIndex(
  { "documentId": 1, "teamId": 1, "status": 1 },
  { 
    "name": "document_team_status_idx",
    "background": true
  }
);

// 2. Team Status Created Index
db.graph_extraction_jobs.createIndex(
  { "teamId": 1, "status": 1, "createdAt": -1 },
  { 
    "name": "team_status_created_idx",
    "background": true
  }
);

// ============================================================================
// COLLECTION_EXPORT_JOBS COLLECTION
// ============================================================================

// 1. JobId Unique Index (unique constraint)
db.collection_export_jobs.createIndex(
  { "jobId": 1 },
  { 
    "name": "jobId_unique_idx",
    "unique": true,
    "background": true
  }
);

// 2. TeamId Index
db.collection_export_jobs.createIndex(
  { "teamId": 1 },
  { 
    "name": "teamId_idx",
    "background": true
  }
);

// 3. ExportedBy Index
db.collection_export_jobs.createIndex(
  { "exportedBy": 1 },
  { 
    "name": "exported_by_idx",
    "background": true
  }
);

// 4. Team Status Created Index (compound)
db.collection_export_jobs.createIndex(
  { "teamId": 1, "status": 1, "createdAt": -1 },
  { 
    "name": "team_status_created_idx",
    "background": true
  }
);

// 5. ExportedBy Created Index (compound)
db.collection_export_jobs.createIndex(
  { "exportedBy": 1, "createdAt": -1 },
  { 
    "name": "exported_by_created_idx",
    "background": true
  }
);

// ============================================================================
// VERIFY ALL INDEXES
// ============================================================================

print("\n=== AGENTS Indexes ===");
db.agents.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== AGENT_EXECUTIONS Indexes ===");
db.agent_executions.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== AGENT_TASKS Indexes ===");
db.agent_tasks.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== AGENT_TASK_VERSIONS Indexes ===");
db.agent_task_versions.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== AI_ASSISTANTS Indexes ===");
db.ai_assistants.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== ALERTS Indexes ===");
db.alerts.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== API_ENDPOINT_VERSIONS Indexes ===");
db.apiEndpointVersions.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== API_ROUTES Indexes ===");
db.apiRoutes.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== API_ROUTE_VERSIONS Indexes ===");
db.apiRouteVersions.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== CONVERSATIONS Indexes ===");
db.conversations.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== CONVERSATION_MESSAGES Indexes ===");
db.conversation_messages.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== KNOWLEDGE_HUB_CHUNKS Indexes ===");
db.knowledge_hub_chunks.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== KNOWLEDGE_HUB_COLLECTION Indexes ===");
db.knowledge_hub_collection.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== KNOWLEDGE_HUB_DOCUMENTS Indexes ===");
db.knowledge_hub_documents.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== KNOWLEDGE_HUB_DOCUMENT_METADATA Indexes ===");
db.knowledge_hub_document_metadata.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== LINQ_LLM_MODELS Indexes ===");
db.linq_llm_models.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== LINQ_WORKFLOW_EXECUTIONS Indexes ===");
db.linq_workflow_executions.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== LINQ_WORKFLOW_VERSIONS Indexes ===");
db.linq_workflow_versions.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== LLM_PRICING_SNAPSHOTS Indexes ===");
db.llm_pricing_snapshots.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== TEAM_MEMBERS Indexes ===");
db.team_members.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== TEAM_ROUTES Indexes ===");
db.team_routes.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== GRAPH_EXTRACTION_JOBS Indexes ===");
db.graph_extraction_jobs.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n=== COLLECTION_EXPORT_JOBS Indexes ===");
db.collection_export_jobs.getIndexes().forEach(function(index) {
  print("Index: " + index.name);
  printjson(index.key);
});

print("\n✅ Index creation completed for all collections!");

