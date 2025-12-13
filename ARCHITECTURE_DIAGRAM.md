## Linqra Agentic AI Framework - Architecture Diagram

### Visual Architecture Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT APPLICATIONS                          │
│                                                                     │
│  • Linqra Web Console (Teams, Agents, AI Assistants, RAG, etc.)     │
│  • Public Assistant Widgets (embeddable JS/iframe, API key based)   │
│  • External API Clients / Integrations                              │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTPS Requests
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                         🌐 API GATEWAY                              │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  • Authentication & Authorization (Keycloak SSO)            │    │
│  │  • Team / Tenant Context Injection                          │    │
│  │  • Rate Limiting & Throttling                               │    │
│  │  • Request Routing & Load Balancing                         │    │
│  │  • API Key Management (including Public Widget API Keys)    │    │
│  │  • SSL/TLS Termination                                      │    │
│  │  • WebSocket Upgrade (STOMP over /ws for chat streaming)    │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Authenticated Requests
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      📊 WORKFLOW ENGINE                             │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  • Workflow Definition & Execution                          │    │
│  │  • Step Orchestration (Sequential / Parallel)               │    │
│  │  • Dynamic Step Resolution                                  │    │
│  │  • Variable Interpolation ({{step1.result}})                │    │
│  │  • Async Step Processing with Queues                        │    │
│  │  • Workflow Version Control                                 │    │
│  │  • RAG Steps (Milvus search, Knowledge Hub document fetch)  │    │
│  │  • LLM Invocation (OpenAI, Gemini, Claude, etc.)            │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ↓               ↓               ↓
      ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  ┌─────────────────┐
      │   🤖 AI AGENTS  │  │  ⏰ SCHEDULER    │  │  💬 AI ASSISTANTS       │  │  📱 APPS        │
      └─────────────────┘  └─────────────────┘  └─────────────────────────┘  └─────────────────┘
                    │               │               │
                    └───────────────┼───────────────┘
                                    │
                                    ↓

┌─────────────────────────────────────────────────────────────────────┐
│                      CORE EXECUTION LAYER                           │
│                                                                     │
│  ┌──────────────────────┐      ┌──────────────────────┐             │
│  │   🤖 AI AGENTS       │ ←──→ │   ⏰ SCHEDULER       │              │
│  ├──────────────────────┤      ├──────────────────────┤             │
│  │ • Task Management    │      │ • Cron Jobs (Quartz) │             │
│  │ • Task Versioning    │      │ • Event Triggers     │             │
│  │ • Execution Tracking │      │ • Workflow Triggers  │             │
│  │ • Multi-Task Support │      │ • Manual Execution   │             │
│  │ • Workflow Embedding │      │ • Timezone Support   │             │
│  │ • Retry Logic        │      │ • Startup Scheduling │             │
│  └──────────────────────┘      └──────────────────────┘             │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │   💬 AI ASSISTANT CHAT ORCHESTRATOR                           │  │
│  ├───────────────────────────────────────────────────────────────┤  │
│  │ • Conversation & History Management                           │  │
│  │ • AI Assistant Config (model, tasks, guardrails, access)      │  │
│  │ • Parallel Execution of Selected Agent Tasks (MVP)            │  │
│  │ • RAG Orchestration (Milvus + Knowledge Hub)                  │  │
│  │ • LLM Prompt Construction & Response Synthesis                │  │
│  │ • WebSocket Streaming (word-by-word / token updates)          │  │
│  │ • Cancel In-Flight Generation                                 │  │
│  │ • Token Usage Extraction & LLM Cost Tracking                  │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ All Operations
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                    SUPPORT SERVICES LAYER                           │
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────┐  │
│  │ 📈 OBSERV.   │  │ 🛡️ SECURITY   │  │ 🔄 RESILIENCY│  │ ⚙️ LLM  │  │
│  ├──────────────┤  ├──────────────┤  ├──────────────┤  ├─────────┤  │
│  │• Metrics     │  │• Keycloak SSO│  │• Circuit     │  │• Model   │ │
│  │• Monitoring  │  │• RBAC/ABAC   │  │  Breaker     │  │  Registry│ │
│  │• Execution   │  │• API Keys    │  │• Retry Logic │  │• Dynamic │ │
│  │  History     │  │• Team Auth   │  │• Failover    │  │  Pricing │ │
│  │• Analytics   │  │• Token Mgmt  │  │• Timeouts    │  │• Cost    │ │
│  │• Dashboards  │  │• Encryption  │  │• Bulkhead    │  │  Tracking│ │
│  │• Alerts      │  │• Audit Logs  │  │• Rate Limit  │  │• Provider│ │
│  │              │  │              │  │              │  │  Routing │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Persistent Storage
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        DATA PERSISTENCE LAYER                       │
│                                                                     │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────────┐   │
│  │  MongoDB   │  │  Milvus    │  │   Redis    │  │  Kafka       │   │
│  │  (Primary: │  │  (Vectors  │  │  (Cache,   │  │  (Events,    │   │
│  │   Agents,  │  │   for RAG) │  │   Queues)  │  │   Streaming) │   │
│  │   Workflows│  │            │  │            │  │              │   │
│  │   Assistants│ │            │  │            │  │              │   │
│  └────────────┘  └────────────┘  └────────────┘  └──────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Interactions

### 1. **API Gateway → Workflows & AI Assistants**
- Client requests enter through the API Gateway
- Gateway authenticates users via Keycloak SSO
- Validated requests are routed to:
  - **Workflow Engine** for traditional workflow execution
  - **AI Assistant Chat Orchestrator** for chat (`link.target = "assistant"`, `link.action = "chat"`)
- API keys and team context are injected into requests
- WebSocket connections are upgraded and routed to chat topics for streaming responses

### 2. **Workflows → AI Agents + Scheduler**
- Workflows orchestrate multi-step processes
- Can trigger AI Agents for intelligent task execution
- Can be scheduled via the Scheduler for automated runs
- Steps can call external APIs, LLMs, databases, etc.
 - RAG steps can:
   - Query Milvus collections
   - Fetch Knowledge Hub documents and metadata

### 3. **AI Agents ↔ Scheduler**
- Agents can have multiple execution triggers:
  - **CRON**: Scheduled execution (Quartz)
  - **EVENT_DRIVEN**: Triggered by Kafka events
  - **MANUAL**: On-demand execution
  - **WORKFLOW_TRIGGER**: Triggered by another workflow
- Scheduler manages task execution lifecycle
- Supports timezone-aware scheduling (UTC storage)

### 4. **AI Assistants → Agents + RAG + LLMs**
- AI Assistants orchestrate:
  - Parallel execution of configured Agent Tasks (MVP)
  - Retrieval of relevant context from Milvus + Knowledge Hub
  - Construction of grounded LLM prompts with context snippets
  - Conversation history (sliding window) as additional context
- Responses are:
  - Streamed via WebSocket as word-by-word updates
  - Persisted as `Conversation` and `ConversationMessage` documents
  - Enriched with token usage, cost, and task result metadata (including Knowledge Hub documents)

### 5. **All Components → Support Services**
- **Observability**: 
  - Tracks execution history, metrics, and analytics
  - Provides real-time monitoring and dashboards
  - Stores performance data for optimization
  
- **Security**:
  - Keycloak SSO for authentication
  - Team-based authorization (RBAC)
  - API key management
  - Audit logging for compliance
  
- **Resiliency**:
  - Circuit breakers prevent cascade failures
  - Retry logic with exponential backoff
  - Timeout management
  - Bulkhead pattern for resource isolation
  
- **Applications / Data Services**:
  - MongoDB for persistent data (agents, workflows, assistants, conversations, pricing snapshots, etc.)
  - Milvus for vector search (embeddings / RAG collections)
  - Redis for caching and internal queues
  - Kafka for event streaming and async execution
  - LLM integrations (OpenAI, Gemini, Claude, Cohere, etc.)
  - Dynamic LLM pricing and cost tracking (per model, per provider, per team)

## Key Features

### **Workflow Capabilities**
- **Embedded Workflows**: Steps defined inline within agent tasks
- **Triggered Workflows**: Reference existing workflow IDs
- **Async Execution**: Queue-based processing for long-running steps
- **Step Chaining**: Use outputs from previous steps ({{step1.result}})
- **Caching**: TTL-based caching for expensive operations
- **Version Control**: Track changes and rollback if needed

### **Agent Task Types**
- `WORKFLOW_EMBEDDED`: Contains workflow steps inline
- `WORKFLOW_EMBEDDED_ADHOC`: Dynamic workflow creation
- `WORKFLOW_TRIGGER`: References external workflow
- `API_CALL`: Direct API invocation
- `DATA_PROCESSING`: Data transformation tasks

### **Execution Triggers**
- `CRON`: Time-based (supports 6-part Quartz expressions)
- `EVENT_DRIVEN`: Event-based (Kafka, webhooks)
- `MANUAL`: User-initiated
- `WORKFLOW_TRIGGER`: Workflow-initiated

### **Security Model**
- Multi-tenant architecture with team isolation
- Row-level security based on teamId
- JWT token validation
- API key authentication
- Role-based access control (gateway_admin, etc.)

### **Observability Features**
- Real-time execution monitoring
- Historical analytics and trends
- Step-level performance metrics
- Success/failure rate tracking
- Result distribution analysis
- Execution timeline visualization

## Data Flow Example 1: AI Quote Generation

1. User Request
   ↓
2. API Gateway (Auth check)
   ↓
3. Workflow Engine (Load workflow)
   ↓
4. Step 1: Call Quotes Service (Get random person)
   ↓
5. Step 2: Vector Search in Milvus (Find similar quotes)
   ↓
6. Step 3: OpenAI Generation (Create new quote)
   ↓
7. Step 4: Gemini Language Detection (Detect language)
   ↓
8. Step 5: Store in Milvus (Save with embedding)
   ↓
9. Observability (Track metrics, execution time)
   ↓
10. Return Response to Client

## Data Flow Example 2: AI Assistant Chat with RAG & Streaming

1. User types a message in the Linqra console or public widget  
2. API Gateway authenticates (or validates public API key) and routes to Chat Orchestrator  
3. Chat Orchestrator:
   - Loads AI Assistant config (model, selected tasks, guardrails, access control)
   - Gets / creates `Conversation` and recent `ConversationMessage` history
4. Agent Tasks execute in parallel (via Workflow Engine) using the user’s question:
   - Step 1: Milvus search on the relevant Knowledge Hub RAG collection(s)
   - Step 2: LLM call (e.g., OpenAI `gpt-4o`) using retrieved context snippets
   - Workflow response is normalized into `answer` + `documents[]`  
5. Chat Orchestrator builds final prompt (system + history + task results) and calls default LLM  
6. LLM response is streamed back over WebSocket (word-by-word / chunked) to the client  
7. On completion, the full assistant reply, token usage, cost, and structured taskResults are stored in MongoDB  
8. Observability records execution metrics and LLM cost snapshots for analytics and billing

## Scaling & Performance

- **Horizontal Scaling**: API Gateway and workflow executors can scale independently
- **Queue-Based Processing**: Async steps use Kafka/Redis for buffering
- **Caching Strategy**: Redis for hot data, Milvus for vector similarity
- **Database Optimization**: MongoDB indexes for fast queries
- **Circuit Breakers**: Prevent overload on downstream services
- **Rate Limiting**: Protect against abuse and ensure fair usage

---

**This architecture enables:**
- 🤖 **Intelligent Automation**: AI agents that learn and adapt
- 🔄 **Complex Workflows**: Multi-step orchestration with dependencies
- 📊 **Full Observability**: Real-time monitoring and analytics
- 🛡️ **Enterprise Security**: SSO, RBAC, audit trails
- ⚡ **High Resilience**: Fault tolerance and graceful degradation
- 🌐 **Multi-Tenant**: Team-based isolation and security

