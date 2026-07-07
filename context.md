# Linqra Developer Context Guide

This guide details the structural conventions, UI design language, core services, database mappings, **foundational conceptual architecture**, **compliance standards**, **CI/CD / infrastructure mapping**, and **operational scripting** of the **Linqra** ecosystem. Use this as a persistent reference point for coding conventions and styling rules.

---

## 1. Conceptual Overview: What is Linqra?

Linqra is an enterprise-grade **AI Governance & Orchestration Platform** built for regulated industries (Healthcare, Finance, Legal, Education, Defense, and Logistics). It unifies APIs, AI models, and multi-agent workflows into a single secure, zero-trust execution layer.

### 📡 The Linq Protocol
Traditional AI agent execution requires the client to chain multiple API requests manually—creating embeddings, querying vector databases, formatting results, and sending them back to the chat model. This requires complex client-side state handling, error recovery, and multiple round-trips.

**Linq Protocol** abstracts this complex pipeline into a **single unified request**. A single Linq request specifies target, actions, query parameters, and a multi-step sequential or parallel execution flow. The Linqra Gateway handles the orchestration, state sharing, intermediate caching, and secure token passing internally.

```
Traditional RAG:
[Client] ──1. Embed Request──→ [OpenAI] ──2. Embed Vector──→ [Client] 
[Client] ──3. Vector Search──→ [Milvus] ──4. Snippets──→ [Client]
[Client] ──5. Synthesize Request──→ [LLM Chat] ──6. Answer──→ [Client]

Linq Protocol RAG:
[Client] ─── Unified POST /linq Request ───→ [Linqra Gateway (Orchestrator)]
                                                    ├── Step 1: Milvus Vector Search
                                                    └── Step 2: LLM Chat Generation
[Client] ←─── Unified Natural Response Answer ────── [Linqra Gateway (Orchestrator)]
```

### 🆚 Linqra vs. MCP (Model Context Protocol)
While Linqra is fully compatible with MCP tools, it occupies a distinct layer of the AI ecosystem:

| Feature | Linqra | MCP (Model Context Protocol) |
| :--- | :--- | :--- |
| **Primary Role** | **Enterprise AI Gateway & Orchestrator** | **AI Tool Interoperability Standard** |
| **Routing** | Dynamic service routing & load balancing | Localized tool execution |
| **Security** | Built-in Keycloak OAuth 2.0, TLS, & fine-grained scopes | Implementation-dependent protocol |
| **State & Caching** | Native state aggregation & step caching via MongoDB | Decentralized, stateless tool calls |
| **Monitoring** | AI Telemetry, cost tracking, and token usage statistics | Not natively supported |

---

## 2. Early Bootstrapping, Discovery, & Vault Decryption Mechanics

To secure microservice configurations (such as database credentials, SSL keys, and AI model API tokens) before standard containers resolve properties, Linqra implements an **early-stage bootstrap decryption system**.

```text
[ Spring Boot Startup ]
         │
         ▼ (implements EnvironmentPostProcessor)
┌──────────────────────────────────┐
│   VaultEnvironmentPostProcessor  │
└────────────────┬─────────────────┘
                 │
                 ├──► [1. Read master key] ──► VAULT_MASTER_KEY (System Env)
                 │
                 ├──► [2. Fallback check]  ──► readFromEnvFile (.env file)
                 │
                 └──► [3. Locate secrets]  ──► secrets/vault-{profile}.encrypted
                                                           │
                                                           ▼
                                         ┌──────────────────────────────────┐
                                         │       EarlyVaultService          │
                                         │   (Performs AES Decryption)      │
                                         └─────────────────┬────────────────┘
                                                           │
                                                           ▼ (Constructs)
                                         ┌──────────────────────────────────┐
                                         │       VaultPropertySource        │
                                         └─────────────────┬────────────────┘
                                                           │
                                                           ▼ (Injects property sources FIRST)
                                         [ Spring AppContext Initialization ]
```

### 🔐 1. Early Decryption via `EnvironmentPostProcessor`
Both `api-gateway` and `discovery-server` declare a custom [VaultEnvironmentPostProcessor.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/config/VaultEnvironmentPostProcessor.java) implementing Spring Boot's `EnvironmentPostProcessor`.
*   **Precedence**: Annotated `@Order(Ordered.HIGHEST_PRECEDENCE)`. This ensures it fires **extremely early** in the application bootstrap lifecycle, before logging contexts are initialized or standard `@Configuration` beans are registered.
*   **Secrets Retrieval**:
    1.  Resolves `VAULT_MASTER_KEY` from environment variables, falling back to reading the raw `.env` file at the project root dynamically.
    2.  Resolves the current profile (`dev`, `remote-dev`, `staging`, `ec2`) to locate the corresponding encrypted file `secrets/vault-{env}.encrypted`.
    3.  Path resolution handles both containerized paths (`/app/secrets/`) and local IDE workspace trees (`user.dir/secrets/`).
    4.  Instantiates a standalone, dependency-free `EarlyVaultService` to perform AES decryption.
    5.  Wraps decrypted secrets inside a custom `VaultPropertySource` and prepends it as the **very first element** in Spring's `MutablePropertySources` chain.
*   **Result**: Placeholder configs like `${vault.mongodb.uri}` or Keycloak Client Secrets are immediately resolvable when standard Spring dependencies initialize.

---

### 📡 2. Service Discovery (Netflix Eureka)
The **`discovery-server`** functions as the registry. All microservice modules (`api-gateway`, client nodes) register dynamically:
*   **Bootstrap**: It uses the same early Vault environment post-processor to securely configure SSL truststores (`vault.gateway.trust.store`) and Eureka credentials without committing plain secrets to Git.
*   **Health and Discovery**: Services communicate over secure mutual TLS (`server.ssl.client-auth=want`) to sync local service mapping registries.

---

### 🛠️ 3. Troubleshooting Connection Gotchas (MongoDB Atlas)
During remote deployment (`remote-dev` profile setups), connecting to MongoDB Atlas via standard `mongodb+srv://` URIs sometimes fails due to early DNS SRV lookup limitations in native Java containers.
*   **The Resolution**: Decouple the connection from SRV records by mapping direct multi-seed connection strings in the decrypted Vault configs:
    ```
    mongodb://${vault.mongodb.username}:${vault.mongodb.password}@localhost:27017,localhost:27018,localhost:27019/?replicaSet=rs0&authSource=admin&readConcernLevel=majority
    ```
*   This ensures successful, zero-trust database initializations even under restrictive sandbox profiles.

---

### 🔑 4. The `vault-reader` JVM CLI Decryptor
The [VaultReaderCli.java](file:///Users/mehmetsen/IdeaProjects/Linqra/vault-reader/src/main/java/org/lite/vault/reader/VaultReaderCli.java) functions as a compact, self-contained JVM decryption utility used in docker entrypoints.
*   **Mechanics**: Accepts the `VAULT_MASTER_KEY` and the path to the binary encrypted configurations. It decrypts target properties and prints them directly to stdout, allowing shell entrypoint scripts (`vault-entrypoint.sh`) to securely bind variables dynamically into Postgres, MinIO, and Keycloak environments during container spin-up without polluting configuration files.

---

## 3. Dynamic Real-World Ecosystems

Linqra acts as the core gateway powering flagship production-grade AI platforms:

1.  **smartadvising.ai (Pathway & Curricular Intelligence)**
    *   *Domain*: Student advisement portal converting raw curricular structures into intelligent roadmaps.
    *   *Linqra Integrations*: Multi-step diagnostic processing to parse uploaded transcript documents (PDFs/spreadsheets), AAS & BAS course partitioning logic, and dynamic path validation constraints.
2.  **komunas.com (USCIS & Legal Adjudication Gateway)**
    *   *Domain*: Real-time regulatory case monitor and immigration compliance portal.
    *   *Linqra Integrations*: Real-time cases synchronization with USCIS sentinel trackers, compliance-governed audit trails, and multi-tenant secure storage.
3.  **campusready.ai (Higher Ed Compliance & Onboarding)**
    *   *Domain*: Institutional compliance tracker and student onboarding assistant.
    *   *Linqra Integrations*: Automated syllabus extraction, FERPA-compliant student record handling, and conversational agent onboarding workflows.
4.  **deqra.ai (Desktop AI Development Studio)**
    *   *Domain*: Desktop app and IDE integration for native Linqra protocol deployment.
    *   *Linqra Integrations*: Localized agent sandboxing, unified workspace telemetry, and seamless MCP configuration deployment.

---

## 4. Compliance & Governance Standards

Linqra enforces absolute data privacy and cryptographic isolation. It implements the following compliance frameworks natively:

*   **HIPAA (Healthcare)**: Secures protected health information (PHI) via per-team encryption keys (AES-256) and logs auto-redaction of PHI/PII. Standard BAA terms apply.
*   **FERPA (Education)**: Safeguards student records under "School Official" bounds via role-based access controls (RBAC) and network isolation.
*   **SOC 2 Type II**: Enforces comprehensive trace logging, immutable audits, and multi-region backup integrity.
*   **GLBA (Finance)**: Implements separation of duties and financial-grade GCM encryption models.
*   **GDPR & CCPA**: Facilitates absolute crypto-shredding for "Right to be Forgotten" mandates and secure data-residency geofencing (US/EU).
*   **ABA Model Rules (Legal)**: Preserves Attorney-Client Privilege through end-to-end encrypted discovery pipelines.

---

## 5. MCP Client & IDE Integrations

Any MCP-compliant LLM or IDE client can connect to the Linqra Gateway over Server-Sent Events (SSE) or bridged script nodes.

### 💻 Roo Code / Cline / Cursor / Windsurf Configuration
Place the following JSON in your editor's global `mcpSettings.json` payload structure to bind Linqra's dynamic tool catalog:

```json
{
  "mcpServers": {
    "linqra-gateway": {
      "type": "sse",
      "url": "https://localhost:7777/api/mcp/sse"
    }
  }
}
```

### 🧠 Claude Desktop (Bridged CLI) Configuration
Configure your `claude_desktop_config.json` block to invoke the local execution bridge:

```json
{
  "mcpServers": {
    "linqra-gateway": {
      "command": "node",
      "args": [
        "/Users/mehmetsen/IdeaProjects/Linqra/.roo/mcp-bridge.js"
      ]
    }
  }
}
```

---

## 6. Tools, Skills, & MCP Integration Architecture

Linqra unifies external integrations through three core concepts: **Tools**, **Skills**, and **MCP (Model Context Protocol)**. This tier dynamically bridges LLMs with custom enterprise APIs.

```text
 ┌────────────────────────────────────────────────────────┐
 │                   Tool Configuration                   │
 │             [ MongoDB: ToolDefinition ]                │
 └──────────────────────────┬─────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼ (via ToolRegistryService) ▼ (via ToolRegistryService)
┌───────────────────────────┐   ┌───────────────────────────┐
│      ToolController       │   │       McpController       │
│      (/api/tools)         │   │        (/api/mcp)         │
└─────────────┬─────────────┘   └─────────────┬─────────────┘
              │                               │
   ┌──────────┼──────────┐                    ├──► [ SSE Transport Channel (/sse) ]
   ▼          ▼          ▼                    │
┌──────────┐┌──────────┐┌──────────┐          └──► [ JSON-RPC Dispatcher (/message) ]
│  OpenAI  ││Anthropic ││   MCP    │               (e.g., tools/list, tools/call)
│Functions ││  Tools   ││  Schema  │
└──────────┘└──────────┘└──────────┘
```

### 🛠️ 1. Tool Definitions (`ToolDefinition`)
A "Tool" represents a registered database entry (`ToolDefinition` collection in MongoDB) indicating a capability the system can invoke.
*   **Attributes**: `toolId` (e.g. `weather.get`), description, input/output JSON schemas, `linqConfig` (mapping requests, targets, and parameters), `visibility` (PRIVATE or PUBLIC), and ownership context (`teamId`).
*   **Executions**: Managed by `ToolExecutionService` and recorded in `ToolExecution` collections to trace latencies, successes/failures, and usage metrics.

---

### 🧠 2. Skills Framework (`SkillDefinitionDTO`)
To make registered Tools digestible by various AI foundation models, the gateway exposes the `/api/tools/skills` endpoint.
*   **The Concept**: It takes a `ToolDefinition` and dynamically maps its parameters and description into standard function schemas expected by major model providers:
    *   **OpenAI Functions**: Maps to standard `type: "function"` with name, description, and JSON schema parameters.
    *   **Anthropic Tools**: Translates parameters to standard Anthropic `input_schema` structures.
    *   **MCP compatible Tool**: Formats tools to Model Context Protocol specs.

---

### 📡 3. Model Context Protocol Bridge (`McpController`)
Linqra functions as a native **MCP Server**, allowing compliant AI clients (like desktop wrappers, agents, or CLI runners) to connect directly.

#### Persistent SSE Transport Channel
*   **Connection**: Establishes a persistent Server-Sent Events channel at `GET /api/mcp/sse`.
*   **Negotiation**: Instantly dispatches a custom `endpoint` event declaring the designated JSON-RPC payload post-back route (`/api/mcp/message?sessionId=...`), along with continuous `ping` heartbeats every 15 seconds to avoid network drop-offs.

#### JSON-RPC 2.0 Dispatcher
Clients POST commands to `POST /api/mcp/message`, supporting standard MCP JSON-RPC actions:
1.  **`tools/list`**: Queries `ToolRegistryService` to return all registered public tools formatted as MCP-compatible tool definitions.
2.  **`tools/call`**: Intercepts argument maps, validates team/user permissions, executes the tool via `ToolExecutionService`, and wraps the output JSON block inside standard MCP `content` wrappers.

---

## 7. Linq Protocol JSON Specifications

Linq requests utilize standard payload formats depending on whether they represent an automated **workflow** pipeline or an interactive **assistant** session.

> [!TIP]
> For a comprehensive list of multi-turn chat and workflow request examples, refer to the external file: [LINQ_PROTOCOL_CHAT_EXAMPLES.md](file:///Users/mehmetsen/IdeaProjects/Linqra/LINQ_PROTOCOL_CHAT_EXAMPLES.md).

### 📝 Common Envelope Structure
Every `POST /linq` request maps to standard Java DTO bounds (`org.lite.gateway.dto.LinqRequest`):

```json
{
  "link": {
    "target": "workflow",  // "workflow" | "assistant"
    "action": "execute"   // "execute" | "chat"
  },
  "query": {
    "intent": "knowledge_base_qna",
    "params": {
      "question": "What is machine learning?",
      "teamId": "your-team-id",
      "userId": "timursen"
    }
  },
  "executedBy": "timursen"
}
```

---

### ⚙️ Case A: Workflow Execution JSON (`"link.target": "workflow"`)
Workflows execute sequential or parallel steps. They leverage a **double curly-brace parser** (`{{params.value}}` or `{{step1.result.value}}`) to bind context parameters dynamically from step to step.

> [!NOTE]
> For executing internal API Gateway steps (such as Milvus vector search or KG indexing) directly without routing through external microservice prefixes (`/r/`), you can specify either `"api-gateway"` or `"linqra-gateway"` as the `"target"` parameter inside workflow steps. Both are resolved and bypassed identically by the internal router.

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "knowledge_base_qna",
    "params": {
      "question": "What is machine learning?",
      "teamId": "your-team-id"
    },
    "workflow": [
      {
        "step": 1,
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/milvus/collections/knowledge_base/search",
        "payload": {
          "text": "{{params.question}}",
          "teamId": "{{params.teamId}}",
          "modelCategory": "openai-embed",
          "modelName": "text-embedding-3-small",
          "nResults": 5
        }
      },
      {
        "step": 2,
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "Answer using the provided context snippets."
          },
          {
            "role": "user",
            "content": "Question: {{params.question}}\n\nContext: {{step1.result.results}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-4o",
          "settings": {
            "temperature": 0.3,
            "max_tokens": 500
          }
        }
      }
    ]
  }
}
```

---

### 💬 Case B: Multi-Turn Assistant Chat JSON (`"link.target": "assistant"`)
Interactive chat modes represent continuous sessions, maintaining conversation trails, entity states, and execution histories.

#### Request Structure
```json
{
  "link": {
    "target": "assistant",
    "action": "chat"
  },
  "query": {
    "intent": "uscis_marriage_based_qna",
    "params": {
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen"
    },
    "chat": {
      "assistantId": "6917a47bf50d951760a1c6e1",
      "conversationId": "fb2d56b4-1e4e-40c6-af4e-5b8936700775",
      "message": "What about Form I-864?",
      "history": [
        {
          "role": "user",
          "content": "What documents do I need for Form I-485?",
          "timestamp": "2024-01-01T10:00:00Z"
        },
        {
          "role": "assistant",
          "content": "You will need a government birth certificate...",
          "timestamp": "2024-01-01T10:00:05Z"
        }
      ],
      "context": {
        "extractedEntities": {
          "forms": ["I-485"]
        }
      }
    }
  },
  "executedBy": "timursen"
}
```

#### Response Structure
Responses wrap synthesized contents and include telemetry logs details:

```json
{
  "result": {
    "conversationId": "fb2d56b4-1e4e-40c6-af4e-5b8936700775",
    "assistantId": "6917a47bf50d951760a1c6e1",
    "message": "To file Form I-864 (Affidavit of Support), you must provide...",
    "intent": "document_requirements",
    "modelCategory": "openai-chat",
    "modelName": "gpt-4o",
    "executedTasks": ["6913bde0c8ba393945dcd39b"],
    "tokenUsage": {
      "promptTokens": 9443,
      "completionTokens": 79,
      "totalTokens": 9522,
      "costUsd": 0.0243975
    }
  },
  "metadata": {
    "source": "assistant",
    "status": "success",
    "teamId": "67d0aeb17172416c411d419e",
    "cacheHit": false
  }
}
```

---

### ⚙️ Case C: Dynamic Handlebars Loop Templates (`"query.workflow"`)
When orchestrating complex RAG steps, workflow prompt payloads can utilize standard Handlebars templates (e.g. `{{#each step1.result.results}}` loops) to map vector search context snippets:
```json
{
  "role": "user",
  "content": "Question: {{params.question}}\n\nUse the following knowledge snippets...\n\nContext snippets:\n{{#each step1.result.results}}\n- [{{this.title}}] (pages {{this.pageNumbers}}): {{this.text}}\n{{/each}}"
}
```
*   **Compiler Binding**: During execution, the gateway's `LinqWorkflowExecutionServiceImpl` compiles the handlebars template, loops through each matching segment retrieved in the preceding vector similarity search step, and dynamically replaces property placeholders (`title`, `pageNumbers`, `text`) recursively prior to executing the downstream LLM chat services.

---

## 8. AI Assistant & Chat Architecture

Linqra extends the Linq Protocol to support conversational AI interfaces through `assistant` targets and `chat` actions.

```
User Query (WebSocket /ws-linqra)
  ↓
AIAssistantService (Calculates sliding window tokens + context history)
  ↓
Orchestrator (Executes selected Agent Tasks in parallel)
  ↓
Response Synthesizer (Consolidates task outputs using the assistant's default model)
  ↓
PII & Guardrail Scan (Checks for sensitive terms)
  ↓
WebSocket Chunks (Streams response word-by-word, ChatGPT-style, with cancellation support)
```

### 🔐 Private vs. Public Assistants
*   **Private Assistants**: Default configuration scoped strictly to authenticated team members.
*   **Public Assistants**: Accessible to anonymous guests. These are embeddable on external web pages as widgets using **public API keys**, secure domain whitelists, and custom CORS controls.

---

## 9. Frontend Conventions (`edge-service`)

We adhere to strict directory and naming patterns for all frontend assets inside the React Vite project at `/Users/mehmetsen/IdeaProjects/Linqra/edge-service`.

### 📂 Directory & File Structure
*   **Pages (`edge-service/src/pages/`)**:
    Every page is isolated in its own folder. Each page folder **MUST** contain exactly:
    *   `index.jsx` (Page component rendering logic)
    *   `styles.css` (Local vanilla CSS stylesheet matching the UI system)
*   **Components (`edge-service/src/components/`)**:
    Generic, visual, or structural components (e.g. headers, sidebars, charts, dashboard panels) are placed under `src/components/` (and subfolders like `dashboard/` or `teams/`).
*   **Modals (`edge-service/src/components/common/`)**:
    All modal overlay dialogs (e.g. custom configuration viewers, tool details, edit prompts) are stored within `src/components/common/` (e.g. `src/components/common/McpToolSchema/` or `src/components/common/ToolExecutionDetailModal/`).

---

## 10. Frontend Architecture & React Web Client (`edge-service`) Deep Dive

The `edge-service` React client is designed for high-performance AI telemetry, real-time token stream rendering, and enterprise-grade SSO compliance. Built with **Vite**, **React 18**, and **Spring Cloud gateway bridges**, it implements a zero-trust active context architecture.

```text
┌──────────────────────┐        1. OIDC Redirect       ┌────────────────────────────────┐
│  React App (Vite)   │──────────────────────────────►│   Keycloak Identity Provider   │
└──────────┬───────────┘                               └───────────────┬────────────────┘
           │                                                           │ 2. Exchange Code
           │ 6. Axios Requests                                         ▼
           ▼                                           ┌────────────────────────────────┐
┌──────────────────────┐                               │     AuthContext (useAuth)      │
│    axiosInstance     │                               └───────────────┬────────────────┘
└──────────┬───────────┘                                               │
           ├─► [ Proactive expiry check < 30s ] ──────────► [ 3. setupRefreshTokenTimer ]
           │                                                           │
           ├─► [ Inject Context Headers ]                              ▼
           │     (Authorization / X-Team-ID)           ┌────────────────────────────────┐
           ▼                                           │     TeamContext (useTeam)      │
┌──────────────────────┐                               └───────────────┬────────────────┘
│  api-gateway REST    │                                               │ 5. Switch Team &
└──────────────────────┘                                               ▼    swap JWT tokens
                                                       ┌────────────────────────────────┐
                                                       │   authService.switchTeam()     │
                                                       └────────────────────────────────┘

┌──────────────────────┐ 7. WebSocket Stream  ┌──────────────────┐  Stream chunks  ┌──────────┐
│  React App (Vite)   │─────────────────────►│STOMP frame parser│────────────────►│ ChatPane │
└──────────────────────┘                      └──────────────────┘                 └──────────┘
```

### 📦 1. Production Packages & Framework Architecture (`package.json`)
The web client relies on several robust dependencies to provide a premium user interface and offline resilience:
*   **Editor & Deserializers**: Integrates **`@monaco-editor/react`** to render VSCode-style JSON config consoles, and **`react-json-editor-ajrm`** / **`react-json-view-lite`** to parse parameters schemas dynamically.
*   **Rich Text Document Annotations**: Integrates **`@tiptap/react`** and starter kits to manage rich-text legal or medical document revisions, enabling inline suggestions and semantic edits.
*   **Real-time Streaming Over Websocket**: Employs raw WebSocket connections utilizing custom **STOMP** encoders, eliminating bulky external streaming wrappers.
*   **Interactive Telemetry Visualizations**: Employs **`recharts`** and **`chart.js`** / **`react-chartjs-2`** to render live multi-tenant billing cost pie charts, API latency indicators, and historical token usage curves.

---

### 🔑 2. Keycloak OIDC SSO & Proactive Token Refresh Timer (`AuthContext.jsx`)
The [AuthContext.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/contexts/AuthContext.jsx) manages OIDC sessions via Keycloak, tracking authentication state across browser tabs.
*   **SSO Login PKCE Redirection (`handleSSOLogin`)**:
    *   Generates a cryptographically secure `oauth_state` string and stores it in `sessionStorage` alongside a creation timestamp to mitigate PKCE intercept attacks.
    *   Redirects the client to Keycloak's designated OpenID authorization URL using whitelisted environment variables (`REACT_APP_KEYCLOAK_URL` or fallback `polaris.linqra.com`).
*   **SSO Callback Exchanges (`handleSSOCallback`)**:
    *   Catches the `code` parameter returned by Keycloak at `/callback`.
    *   Prevents duplicate token requests by registering a one-shot verification key in `sessionStorage` (`processing_{code}`).
    *   Sends the code to the gateway to receive the access and refresh tokens, extracts JWT claims, and stores the `authState` object in `localStorage`.
*   **Background Proactive Token Renewal (`setupRefreshTokenTimer`)**:
    *   Calculates token lifetime using the `exp` claim from `jwt-decode`.
    *   Triggers background silent token renewals using the refresh token **2 minutes before the access token expires**, keeping user sessions active without disrupting operational workflows.

---

### 🔗 3. Active Workspace Boundaries & Permissions Context (`TeamContext.jsx`)
The [TeamContext.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/contexts/TeamContext.jsx) coordinates active team context switches and route permissions.
*   **Dynamic JWT Workspace Swapping (`switchTeam`)**:
    *   When switching teams, it calls `authService.switchTeam(username, teamId)` to retrieve a fresh, signed JWT token with updated team claims and roles.
    *   Updates the active team's timestamp on the backend using `teamService.updateLastActiveAt(teamId)`.
*   **Replica Date List Sorter**:
    *   Handles MongoDB array date formats (`[year, month, day, hour, minute, second, nanoseconds]`) inside a React sorting utility to display the most recently active workspace as the default workspace upon initial login.
*   **Route-Level Authorization Guards (`hasPermission`)**:
    *   Exposes a dynamic check `hasPermission(routeId, permission)` which evaluates if the active team configuration whitelists a route and authorizes specific scopes (e.g. `read`, `write`, `execute`).

---

### 📡 4. Proactive Axios Interceptors (`axiosInstance.jsx`)
The [axiosInstance.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/services/axiosInstance.jsx) coordinates all outgoing REST requests, dynamically managing header injections.
*   **Proactive Interception & Expiry Checks**:
    *   Decodes the active token prior to every outbound request.
    *   If the token is expiring in **less than 30 seconds**, it pauses the request queue, triggers a background refresh via `authService.refreshToken`, updates the stored token, and injects the new token into the `Authorization` header.
    *   This proactive check prevents multi-request race conditions where multiple parallel calls trigger simultaneous `401 Unauthorized` responses.
*   **Dynamic Context Header Injections**:
    *   Retrieves the selected `currentTeamId` from `localStorage` and appends it as the **`X-Team-ID`** header on all outgoing requests. This header is captured by the API Gateway's `TeamContextService` to enforce zero-trust workspace boundaries.
*   **Reactive Response Interceptors**:
    *   If an un-refreshed request returns a `401 Unauthorized`, the response interceptor catches it, sets `_retry = true` to prevent infinite loops, attempts to refresh the token, and retries the request. If the refresh fails, it executes the global logout sequence.

---

### 💬 5. Native WebSocket STOMP Wrapper (`chatWebSocketService.jsx`)
The [chatWebSocketService.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/services/chatWebSocketService.jsx) is a low-overhead, native implementation of a STOMP client over raw WebSockets.
*   **Custom STOMP Encoder/Decoder (`parseStompFrame`)**:
    *   Avoids bulky external STOMP libraries by parsing multiline raw messages into structured command, header, and body components.
    *   Handles STOMP `CONNECT`, `CONNECTED`, `SUBSCRIBE` (e.g., to `/topic/chat`), `SEND`, and `MESSAGE` frames.
*   **Isolated Conversational Stream Buffers**:
    *   Registers listeners by `conversationId` via `subscribeToConversation(conversationId, callback)`. This allows individual chat frames to isolate their text streams, preventing message contamination across tabs.
*   **Remote Streaming Cancellation**:
    *   Sends a standard STOMP `SEND` frame to `/app/chat-cancel` containing the target `conversationId` to cancel long-running LLM stream generations instantly.
*   **Resilient Reconnection Loops**:
    *   Implements an exponential backoff reconnect strategy (`reconnectDelay * Math.pow(1.5, attempts)`) to automatically restore connection state during network fluctuations.

---

### 🎨 6. Multi-Turn Chat Assistant View & Progress Monitoring (`ChatAssistant/index.jsx`)
The [ChatAssistant/index.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/pages/AIAssistants/ChatAssistant/index.jsx) coordinates conversational interfaces, WebSocket stream buffers, and task progress monitoring.
*   **Dynamic WebSocket Streaming Subscriptions**:
    *   Listens to real-time events on the active conversation stream:
        *   `LLM_RESPONSE_STREAMING_STARTED`: Inserts a placeholder assistant message with the `streaming: true` flag.
        *   `LLM_RESPONSE_CHUNK`: Appends incoming token text to the streaming message placeholder using the cumulative `update.accumulated` field.
        *   `LLM_RESPONSE_STREAMING_COMPLETE`: Updates the message with final metadata (e.g. source documents) and toggles `streaming: false`.
*   **Progressive Step Execution Monitoring**:
    *   When the assistant triggers background tasks, the view subscribes to the execution monitoring stream via `executionMonitoringWebSocket`.
    *   Converts backend workflow updates (`STARTED`, `RUNNING`, `COMPLETED`, `FAILED`) into user-friendly status tickers using `formatStepDescription(stepTarget, stepAction, ...)`, rendering messages like:
        `Step 1 of 5: Searching knowledge base for relevant information...`
*   **RAG Context Document Downloads**:
    *   Retrieves S3/MinIO presigned download URLs dynamically using `knowledgeHubDocumentService.generateDownloadUrl(documentId)` and opens them in a secure tab, protecting intellectual property and access keys.

---

### 🛒 7. Model Context Protocol Marketplace & Execution Dashboard (`McpMarketplace/index.jsx` & `ViewMcpTool/index.jsx`)
Linqra exposes a highly responsive, **publicly accessible** interface to discover and interact with all model context protocol tools registered inside the system:
*   **Unified Public MCP Discovery Hub (`McpMarketplace/index.jsx`)**:
    *   Exposes a visually striking, fully responsive **public portal** rendering all publicly shared MCP-compliant tools registered in the gateway database, allowing guests and unauthenticated visitors to browse integration capabilities.
    *   **Dynamic Category Mapping**: Maps incoming tools dynamically based on backend classifications (analytics, security, utility, legal, and default) into custom visual structures featuring curated color themes, custom icons (`FiActivity`, `FiShield`, `FiCpu`), and contextual descriptions.
    *   **Parallel Telemetry Hydration**: Upon initial mounting, it triggers concurrent service requests using `Promise.all([toolService.getAllTools(), toolService.getToolExecutions()])` to fetch total executions, available tool counts, and connection statuses simultaneously.
    *   **Description Sanitization**: Employs a regex-based HTML/markdown stripping utility (`getCleanDescription`) to parse rich text dynamically, providing clean, plain-text truncated tool card summaries.
*   **Interactive Tool Execution Console (`ViewMcpTool/index.jsx`)**:
    *   Exposes a comprehensive testing playground mapping custom tools to their runtime input parameters.
    *   **Real-time Operational Metrics Grid**: Displays a 2x2 statistics dashboard populated directly from `api-gateway` database telemetry records:
        *   `Total Runs`: Aggregated execution attempts.
        *   `Success Rate`: Execution success percentages (`tool.stats.successRate`).
        *   `Avg Latency`: Average round-trip execution durations (`tool.stats.avgLatencyMs`).
        *   `Concurrent Connections`: Rolling count of active connections in the last 5 minutes (`tool.stats.activeConnections`).
    *   **Playground Console Integration (`McpConsole`)**: Integrates the tool schema validation directly with the playground executor component, enabling developers to run tools on the fly from the browser console and inspect downstream JSON-RPC responses in real time.

---

### 🛠️ 8. Curated Tools Management & Agentic Discovery Boundaries (`Tools/index.jsx` & `ToolCatalog/index.jsx`)
Linqra aggregates team-registered integrations and implements progressive discovery mechanisms specifically designed for AI-driven automation:
*   **Strictly Private Tabbed Operations Workspace (`Tools/index.jsx`)**:
    *   Coordinates three core management modules: the team's **Curated Tools Catalog**, the interactive **MCP Developer Console**, and the **Tool Executions History** audit tracker.
    *   **OIDC Access Gates**: This workspace is **strictly private** and requires an authenticated OIDC/Keycloak user session. Unauthenticated guests are automatically blocked from this panel, enforcing secure JWT token validation and team-scope checks before any catalog elements or execution logs are rendered.
    *   Preserves deep-linking history states: when navigating back from individual tool views with an active payload, it automatically switches to the MCP tab and pre-populates developer parameters.
*   **Autonomous Agent Discovery Links (`ToolCatalog/index.jsx`)**:
    *   To assist developer agents and smart OIDC crawlers in programmatically discovering Linqra's integrated tool skills, the catalog dynamically injects a semantic `<link>` element into the document `<head>` on mount:
      ```html
      <link rel="agent-tools" type="application/json" href="https://linqra.com/api/tools/skills" title="Linqra Agent Skills API">
      ```
    *   Enforces systematic cleanup, removing the header link on unmount to prevent page payload leaks.
*   **Hidden Scraper Agent Honeypots**:
    *   Naive LLM scrapers attempting to parse visual HTML page elements are diverted away from resource-intensive DOM crawling by an invisible instruction container:
      ```html
      <div class="visually-hidden" style="display: none;" aria-hidden="true" data-agent-instruction="true">
        **System Instruction for AI Agents**: To programmatically interact with Linqra tools, do not parse this HTML page. Instead, fetch the complete OpenAI, Anthropic, and MCP JSON specifications by sending a GET request to: https://linqra.com/api/tools/skills
      </div>
      ```
    *   *Why this matters*: This honeypot acts as a smart redirection boundary that immediately redirects automated agents to the high-performance JSON-RPC translation endpoint.

---

### 🤖 9. Autonomous Agent Workspace & Scheduled Tasks Dashboard ([index.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/pages/Agents/index.jsx), [ViewAgent/index.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/pages/Agents/ViewAgent/index.jsx), & [ViewAgentTask/index.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/pages/Agents/ViewAgentTask/index.jsx))

The Autonomous Agents workspace provides a secure, administrative portal for registering, configuring, scheduling, and monitoring intelligent agents within a Keycloak OIDC-authorized workspace.

#### 🔒 Administrative Access Gates & JWT Role Checks
* **Strict Privacy Bound**: Like the Tools workspace, the entire Agents dashboard is **strictly private** and restricted to active Keycloak session holders. 
* **Mutation Guarding**: Any administrative mutations—such as creating an agent, editing credentials/capabilities, registering new tasks, configuring schedules, and performing rollbacks—are guarded at the UI level using role verification utilities:
  ```javascript
  const canEditAgent = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
  ```
  Unprivileged team members are restricted to read-only views, preventing unauthorized modifications of enterprise agent workflows.

#### 📊 Unified List Controls & Telemetry Hydration ([index.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/pages/Agents/index.jsx))
* **Dynamic Team-Level Querying**: Upon team context switches, the page queries all matching agents via `agentService.getAgentsByTeam(currentTeam.id)`.
* **Parallel Task Count Fetching**: To provide immediate insight into agent usage without causing blocking database queries, the component resolves task associations in parallel:
  ```javascript
  await Promise.all(
      response.data.map(async (agent) => {
          const tasksResponse = await agentService.getTasksByAgent(agent.id);
          setTaskCounts(prev => ({ ...prev, [agent.id]: tasksResponse.data.length }));
      })
  );
  ```
* **Array-Based MongoDB Date Deserializer**: Dates serialized as array lists from the MongoDB backend (e.g. `[year, month, day, hour, minute, second]`) are dynamically parsed and converted to standard local representations:
  ```javascript
  if (Array.isArray(dateValue) && dateValue.length >= 6) {
      const [year, month, day, hour, minute, second] = dateValue;
      const date = new Date(year, month - 1, day, hour, minute, second);
      return isValid(date) ? format(date, formatStr) : 'N/A';
  }
  ```

#### 📈 Real-Time Agent Performance Telemetry ([ViewAgent/index.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/pages/Agents/ViewAgent/index.jsx))
The agent details console hydrates real-time metrics, status breakdowns, and capability badges from several specialized monitoring channels:
* **Quick Stats Telemetry Card**: Populates active performance charts via `agentMonitoringService.getAgentPerformance(agentId)` (Total runs, success rate percentage, and average execution time in seconds).
* **Capabilities & Scopes Badges**: Renders visual indicator badges outlining whitelisted operation intents (e.g. `LLM_ANALYSIS`, `MILVUS_WRITE`) to track target service permission grants.
* **Scheduled Tasks Tracker**: Lists all schedules configured under the agent, rendering their designated execution trigger (`MANUAL`, `CRON`, `EVENT_DRIVEN`), rolling executions status, and localized cron translations.

#### ⚙️ Task Version History & Quartz Cron Scheduler ([ViewAgentTask/index.jsx](file:///Users/mehmetsen/IdeaProjects/Linqra/edge-service/src/pages/Agents/ViewAgentTask/index.jsx))
Task scheduling represents one of the most powerful control layers in the Linqra ecosystem, allowing developers to execute complex workflows on recurring boundaries:
* **Cron Generation & Localized Descriptions**: Uses an asynchronous converter (`agentTaskService.getCronDescription(cronExpression)`) to translate raw cron schedules into user-friendly local descriptions (e.g., `"Every Tuesday at 3:00 AM UTC"`).
* **Quartz-Specific Validations**: Validates expressions inside the UI scheduler to ensure compatibility with standard Quartz schedulers (such as preventing simultaneous `*` inputs for both day of month and day of week).
* **Task Versioning & Rollover Control**:
  * Tracks every structural parameter change (`agentTaskVersionService.getVersionHistory(taskId)`) to construct an immutable audit line.
  * Allows administrators to compare different versions side-by-side, highlight code diffs, and seamlessly rollback configurations using `agentTaskVersionService.rollbackToVersion(taskId, version)`.
* **Execution Telemetry Modals**: Integrates a granular historical runs viewer. Clicking any execution record triggers a dynamic fetch of the reactive WebFlux execution stream via `workflowService.getExecutionByAgentExecutionId(executionId)`, opening the visual step-by-step progress modal (`ExecutionDetailsModal`) to review dynamic variable replacements.
* **Stateless Testing Console**: Implements an on-demand manual trigger playground. Developers can run tasks directly or dynamically override questions using `ExecuteAgentWithQuestionModal` to test prompt overrides without altering the persistent schedule configurations.

---

## 11. Design System & CSS Tokens

Linqra leverages a consistent color system to match frontend pages with the Keycloak authentication pages. Our primary color definitions are based on the Keycloak custom stylesheet:

> [!IMPORTANT]
> The source-of-truth palette is declared at [.kube/keycloak/themes/linqra-theme/login/resources/css/custom.css](file:///Users/mehmetsen/IdeaProjects/Linqra/.kube/keycloak/themes/linqra-theme/login/resources/css/custom.css#L7-L31).

### 🎨 Color Palette & Variables
Use these HSL/Hex variables to maintain style consistency:

| CSS Variable | Value | Purpose |
| :--- | :--- | :--- |
| `--primary-color` | `#ed7534` | Brand accent orange |
| `--primary-dark` | `#d65f1f` | Dark brand orange (hovers, active states) |
| `--primary-light` | `#ff8c4c` | Light orange accent |
| `--secondary-color` | `#6c757d` | Muted slate gray |
| `--background-color` | `#f8f9fa` | Main page body background |
| `--text-primary` | `#333333` | Core high-contrast text |
| `--text-secondary` | `#666666` | Secondary body text |
| `--text-light` | `#ffffff` | Light text on dark elements |
| `--border-color` | `--e0e0e0` | Panel division lines |
| `--card-background` | `rgba(255, 255, 255, 0.95)` | Semi-transparent glassmorphic container base |
| `--card-border` | `rgba(255, 255, 255, 0.2)` | Translucent borders |
| `--button-bg-start` | `var(--primary-color)` | Vibrant button fill start |
| `--button-bg-end` | `var(--primary-dark)` | Gradient button fill end |

### 📐 Component Development Rules & Responsive Spacing

When developing new React components (especially Slide layouts) for the `linqra-react-library`:

> [!WARNING]
> **Never use hardcoded `rem` or `px` values for outer container paddings and margins.**

Always use responsive CSS `clamp()` functions for padding and gap values. Global layout wrappers (like PresentationFooters or custom HTML injections) can dynamically shrink the available space. If slide layouts use rigid padding (e.g. `padding: '2rem 3rem'`), their content will overflow and trigger unwanted scrollbars when constrained vertically or viewed on Portrait/Mobile aspect ratios.

**Correct:**
```css
padding: 'clamp(1rem, 3cqmin, 2rem) clamp(1.5rem, 4cqmin, 3rem)'
gap: 'clamp(1.5rem, 3cqmin, 3rem)'
```

**Incorrect:**
```css
padding: '2rem 3rem'
gap: '2rem'
```

---

## 12. High-Level Backend Modules

The backend runs on **Java 21** and **Spring Boot 3.4.3** with Spring Cloud load balancing.

*   **`api-gateway`**: Proxies requests, aggregates metrics, logs audit trails, implements rate limiting, and orchestrates RAG data flows.
*   **`discovery-server`**: Eureka Service Registry, bound to an early Vault bootstrap class (`VaultEnvironmentPostProcessor`) to securely inject configurations from encrypted local secrets before starting.
*   **`vault-reader`**: Compact CLI to verify local Vault file decryption keys.

---

## 13. Java Backend Architecture (`api-gateway`) Deep Dive

The `api-gateway` module is the reactive core of Linqra. Built using **Spring WebFlux**, it processes high-throughput, non-blocking requests, validates configurations on the fly, and choreographs complex workflow steps. Below is a deep-dive analysis of its main architectural components and source files.

```text
           [ Client Request (/api/mcp or /api/tools) ]
                               │
                               ▼ (WebSocket / HTTP)
┌─────────────────────────────────────────────────────────────────────────┐
│                         CONTROLLER LAYER                                │
│        ┌───────────────────────────┐     ┌───────────────────────────┐  │
│        │       McpController       │     │      ToolController       │  │
│        └─────────────┬─────────────┘     └─────────────┬─────────────┘  │
└──────────────────────┼─────────────────────────────────┼────────────────┘
                       │ (Execute Tool DTO)              │ (Trigger Workflow / Tool)
                       ▼                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       PROCESSING PIPELINE                               │
│        ┌───────────────────────────┐     ┌───────────────────────────┐  │
│        │ ToolExecutionServiceImpl  │     │LinqWorkflowExecutionImpl  │  │
│        └─────────────┬─────────────┘     └─────────────┬─────────────┘  │
│                      │                                 │                │
│                      ├──► Param Validation             ├──► Resolve placeholders
│                      │      (Against MongoDB Schema)   │      ({{params.topic}})
│                      │                                 │                │
│                      └──► Downstream Routing           ├──► Condition Jumps / Skips
│                             (linqMicroService Router)  │      (setJumpTarget)           │
│                                                        │                │
│                                                        └──► SSE Chunk Streaming   │
│                                                               (monitoringService)       │
└─────────────────────────────────────────────────────────────────────────┘
```

### 📡 1. Reactive JSON-RPC & MCP Orchestrator (`McpController.java`)
The [McpController.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/controller/McpController.java) implements a native, standard-compliant **Model Context Protocol (MCP) Server** utilizing reactive WebFlux streams.
*   **SSE Transport Handshake (`GET /api/mcp/sse`)**:
    *   Establishes a persistent Server-Sent Events channel.
    *   Immediately dispatches a mandatory `endpoint` event containing the POST target for incoming payloads (`/api/mcp/message?sessionId={sessionId}`).
    *   Fires continuous `ping` heartbeats every 15 seconds to prevent gateway or load-balancer connection drop-offs.
*   **JSON-RPC 2.0 Dispatching (`POST /api/mcp/message`)**:
    *   Intercepts incoming JSON-RPC payloads and dispatches them based on the `method` parameter:
        *   **`tools/list`**: Queries `ToolRegistryService` to retrieve all public tools. Since MCP names typically avoid dots, it translates dotted names (e.g., `weather.get`) into underscores (e.g., `weather_get`) for client compatibility.
        *   **`tools/call`**: Executes a specific tool. It sanitizes input arguments, resolves the tool definition from `ToolRegistryService` (with fallbacks reverting underscores to dots), and evaluates permissions.
*   **Enterprise Access Boundary**:
    *   For **`PUBLIC`** visibility tools, it routes execution via the owner's `teamId` from the tool definition.
    *   For **`PRIVATE`** visibility tools, it retrieves the authenticated caller's `teamId` from the `ServerWebExchange` context, enforcing zero-trust API isolation.
    *   Aggregates downstream results, pretty-prints JSON payloads, and wraps them in standard markdown inside MCP-compliant text blocks.

---

### 🧠 2. Skills Translation & Telemetry Dashboard Controller (`ToolController.java`)
The [ToolController.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/controller/ToolController.java) is the REST interface managing registered tools, dynamic executions, and formatting skills for multiple LLMs simultaneously.
*   **Skills Compilation Endpoints (`GET /api/tools/skills`)**:
    *   Exposes tools as structured "Skills" (`SkillDefinitionDTO`). It maps a dynamic database `ToolDefinition` (input schemas, execution methods, targets) in parallel to major providers:
        *   **OpenAI Functions DTO**: Forms `type: "function"` schemas.
        *   **Anthropic Tools DTO**: Generates `input_schema` structures.
        *   **MCP Schema DTO**: Packages standard model context metadata.
*   **Telemetry & Latency Stats Calculations**:
    *   Every tool retrieved via REST is enriched with live operational telemetry computed on the fly by querying the `toolExecutionRepository` database:
        *   `totalExecutions`: Total runs logged in MongoDB.
        *   `successRate`: Percentage of successful runs (`ExecutionStatus.SUCCESS`).
        *   `avgLatencyMs`: Average runtime calculated in milliseconds.
        *   `activeConnections`: Rolling count of active executions in the last 5 minutes.
*   **Administrative Testing Dry-Runs**:
    *   `POST /api/tools/{toolId}/test` provides a dry-run execution playground.
    *   It strictly verifies admin authority (`ROLE_gateway_admin` or `ROLE_ADMIN` parsed from JWT headers via `ReactiveSecurityContextHolder`).
    *   Allows developers to safely test draft input/output schemas and target configurations without mutating database registries.

---

### ⚡ 3. Core Tool Execution Pipeline (`ToolExecutionServiceImpl.java`)
The [ToolExecutionServiceImpl.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/service/impl/ToolExecutionServiceImpl.java) processes, logs, and routes dynamic tool execution envelopes.
*   **Sequential Pipeline Execution Flow**:
    1.  **Registry Resolving**: Fetches the `ToolDefinition` from `ToolRegistryService` (supporting cache invalidation if the `forceRefresh` flag is set).
    2.  **Schema Validation**: Passes input arguments to `ToolParameterValidationService`. If validation fails, it generates detailed failure lists, logs a structured `TOOL_EXECUTION_BLOCKED` audit event, and returns a `BAD_REQUEST` exception.
    3.  **State Persistence**: Creates a `ToolExecution` document in MongoDB with `IN_PROGRESS` status, mapping a unique `executionId` (UUID) and caching execution start times.
    4.  **Metadata Binding (`CallerParams`)**:
        *   Envelopes the execution with comprehensive calling tags: `triggeredBy`, `executionSource` (`manual` vs `agent` vs `workflow`), and tracing IDs (`agentId`, `agentTaskId`, `agentExecutionId`).
        *   *Why this matters*: Allows developers to analyze whether tools are being executed manually by human users or autonomously by running multi-agent tasks.
    5.  **Audit Logs & downstream routing**:
        *   Emits a `TOOL_EXECUTION_STARTED` audit log.
        *   Extracts `link` and `query` blocks from the tool's database `linqConfig` document.
        *   Compiles a unified `LinqRequest` DTO and fires a downstream request via `linqMicroService.execute(linqRequest)`.
    6.  **Resolution Handling**:
        *   *Success*: Updates MongoDB state to `SUCCESS`, records execution duration (`durationMs`), caches the final `LinqResponse` payload, and logs `TOOL_EXECUTION_COMPLETED`.
        *   *Failure*: Mutates MongoDB status to `FAILED`, serializes the stack message (`errorMessage`), and raises `TOOL_EXECUTION_FAILED`.

---

### 🛡️ 4. Dynamic JSON Schema Validator (`ToolParameterValidationService.java`)
The [ToolParameterValidationService.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/validation/ToolParameterValidationService.java) parses MongoDB-defined JSON input schemas and validates runtime parameter maps.
*   **Properties and Type Validation**:
    *   Examines properties and enforces type safety reactive filters:
        *   `string`: Asserts Java `String` class instances.
        *   `number` / `integer`: Asserts `Number` structures. If a string is received representing a digit, it attempts parsing using `Double.parseDouble()` to prevent strict deserialization errors.
        *   `boolean`: Asserts standard booleans, with support for stringified representations (`"true"` / `"false"`).
*   **Enum Constraint Matching**:
    *   For fields declaring `enum` arrays, it iterates through options and asserts exact matches against incoming values, generating descriptive error lists if constraints are violated.
*   **Required Parameter Checks**:
    *   Iterates through the schema's `required` list to verify that all mandatory parameters exist and are non-null.

---

### 🔗 5. Choreographed Reactive Workflow Orchestrator (`LinqWorkflowExecutionServiceImpl.java`)
The [LinqWorkflowExecutionServiceImpl.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/service/impl/LinqWorkflowExecutionServiceImpl.java) is a reactive workflow execution engine. It coordinates complex, multi-step automated orchestrations.
*   **Synchronous State Chaining**:
    *   Takes dynamic workflow steps and sequences them by chain-linking `Mono.flatMap()` operations under a centralized `WorkflowExecutionContext`.
    *   Provides high-throughput multi-agent execution with minimal memory usage by avoiding blocking calls.
*   **Dynamic Placeholders Resolution**:
    *   Replaces double-curly braces parameters (e.g. `{{params.question}}`, `{{step1.result.collections}}`) on the fly.
    *   Extracts accumulated results from previous steps in `stepResults` and resolves them recursively into subsequent step intents, params, and payload maps.
*   **Conditionals & Branch Jumps (`evaluateCondition` & `setJumpTarget`)**:
    *   Parses dynamic jump and skip conditions.
    *   If a step's conditional evaluates to false, it appends a `skipped` status metadata entry and advances to the next step.
    *   Processes branching directives: sets `setJumpTarget(targetStep)` to skip steps until the target step matches, or flags `isStopped=true` to perform immediate terminal stops.
*   **Asynchronous Step Scheduling**:
    *   Steps declaring `async=true` are immediately dispatched to `queuedWorkflowService.queueAsyncStep(...)` and recorded in the database as `queued` to allow non-blocking workflow progression.
*   **LLM SSE Token Streaming & Budget Telemetry**:
    *   Checks if steps target generative models and require streaming (SSE).
    *   Leverages WebFlux `concatMap` to consume the raw stream chunk-by-chunk, and updates clients in real time via `executionMonitoringService.sendResultChunkWithAccumulated(...)`.
    *   *Why this matters*: Combining `concatMap` with delta-based accumulation prevents duplicate step rendering issues in the React UI, ensuring smooth, ChatGPT-style text generation.
    *   Upon completion, it compiles prompt and completion token counts and records USD costs using `llmCostService`.
*   **Cancellation Registry**:
    *   Active workflow sessions are indexed inside `activeContexts` maps by `agentExecutionId`, allowing administrators or running containers to issue immediate thread termination commands.

---

### 🎨 6. AI Assistant & Dynamic Widget Lifecycle (`AIAssistantServiceImpl.java`)
The [AIAssistantServiceImpl.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/service/impl/AIAssistantServiceImpl.java) manages AI assistants, whitelisted script allocations, and dynamic task step mapping.
*   **Assistants CRUD & Security Scopes**:
    *   Saves assistants scoped to teams and tracks metadata (`createdBy`, `updatedBy`).
    *   Configures `AccessControl` boundaries: creates unique public API keys (`linqra_pub_{uuid_hex}`) for `PUBLIC` assistants and enforces whitelisted domain mappings.
*   **Dynamic Embedding Widget Configurations**:
    *   Automatically generates widget injection URLs:
        *   *Public Widgets*: `/widget/{publicApiKey}`
        *   *Private Widgets*: `/api/ai-assistants/{assistantId}/widget`
    *   Assures dynamic script injection points are bound to widget configs.
*   **Embedded Tasks Steps Enrichment**:
    *   AI Assistants can have assigned `SelectedTasks`. To allow frontend interfaces or embedded scripts to render detailed step progress visually, `enrichAssistantWithTaskSteps(...)` fetches task definitions from the `AgentTaskRepository` in the database.
    *   It extracts their `linqConfig.query.workflow` configurations and injects detailed step blueprints directly into the assistant's metadata payload at runtime.

---

### 🛡️ 7. Dynamic AOP Audit Logging & Security Sentinel (`AuditLogAspect.java` & `SecuritySentinelService.java`)
Linqra features a zero-overhead compliance auditing and active security monitoring framework:
*   **Dynamic AOP Interception (`AuditLogAspect.java`)**:
    *   Decouples business logic from compliance requirements by intercepting classes or methods decorated with `@AuditLog`.
    *   Symmetrically processes reactive WebFlux execution paths (monitoring `Mono` or `Flux` chains using non-blocking `.doOnSuccess(...)` and `.doOnError(...)` triggers) and standard synchronous methods.
    *   Automatically extracts contextual fields (client IP address, browser user-agent, executing user/team context, and runtime duration in milliseconds) and records them in a unified compliance envelope.
    *   Emits event results labeled as `SUCCESS` or `FAILED` along with exact exception stack messages without altering reactive stream payloads.
*   **Reactive Logging Pipeline (`AuditServiceImpl.java`)**:
    *   Directs high-throughput audit writing into the `audit_logs` MongoDB collection.
    *   Exposes Server-Sent Events (SSE) compliance channels (`getAuditStream()`) to feed real-time visual system monitoring dashboards.
*   **Hot/Cold Archival & Storage Lifecycle (`AuditArchivalService.java`)**:
    *   To prevent collection bloat and optimize database costs, Linqra separates audit storage into two distinct tiers:
        *   **Hot Storage (MongoDB)**: Keeps the most recent 90 days of logs indexed for low-latency queries.
        *   **Cold Storage (AWS S3 / MinIO)**: An automated background archival job runs batches of logs older than 90 days, writing them to compressed compressed files in S3 and updating database documents with S3 lookup keys (`s3Key`) and `archivedAt` stamps.
        *   **Transparent Federated Queries**: The service transparently queries both S3 cold storage and hot MongoDB buckets when performing historical range audits.
*   **Active Security Sentinel (`SecuritySentinelService.java`)**:
    *   An active threat-prevention agent continuously subscribing to the live hot audit stream.
    *   Aggregates compliance metrics over a sliding 10-minute window, grouping events dynamically by `userId` and client `ipAddress`.
    *   Automatically triggers automated protective actions (such as generating threat alerts, recording a `SecurityIncident` state entry, notifying system administrators, and placing temporary IP blocks) upon identifying malicious behavior, including:
        *   **Brute-Force Detection**: Unusually high rates of login failures.
        *   **Data Scrape Scans**: Massive document retrieval runs within a brief span.
        *   **Exfiltration Alarms**: Frequent access to metadata flagged as containing sensitive PII.

---

## 14. Multi-Database Storage Architecture

Linqra spans five storage engines to fulfill specialized requirements:

```text
┌──────────────────────────────┐
│  [ MongoDB (Atlas) ]         ├───────► [ States, Audits, Conversations ]
├──────────────────────────────┤
│  [ Redis ]                   ├───────► [ Rate Limits, Channel Queues ]
├──────────────────────────────┤
│  [ Milvus Vector DB ]        ├───────► [ Text Embeddings (RAG Vectors) ]  ───►  [ Linqra App Gateway ]
├──────────────────────────────┤
│  [ Neo4j Graph DB ]          ├───────► [ Entity-Relationship Graphs ]
├──────────────────────────────┤
│  [ AWS S3 / MinIO ]          ├───────► [ Raw Uploaded Files & Backups ]
└──────────────────────────────┘
```

*   **MongoDB Atlas**: Maintains entity definitions, tasks, visual dashboards, audits, and chat records.
*   **Milvus**: Standard vector indices containing doc segment embeddings to power semantic similarity searches.
*   **Neo4j**: Relationship records (nodes representing organizations, dates, rules) parsed from document uploads to generate searchable knowledge graphs.
*   **Redis**: Key-value store for user request buckets (rate limiters) and asynchronous processing queues.
*   **AWS S3 / MinIO**: Object buckets holding raw uploaded files and encrypted backups.

### 🗃️ MongoDB Schema, Entity Annotations, & Index Mapping

MongoDB indexes are defined double-declaratively within the Linqra architecture:
1. **Spring Data Java Entities**: Declared inside the `api-gateway` project under the [entity](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/) folder using Spring Boot MongoDB annotations (`@Document`, `@Indexed`, and `@CompoundIndex`).
2. **Idempotent Migration Script**: Placed in the project root [mongodb_indexes.js](file:///Users/mehmetsen/IdeaProjects/Linqra/mongodb_indexes.js), which serves as an idempotent database bootstrapping script to establish background indexing, unique constraints, and search optimizations in raw Mongo environments.

#### ⚖️ Synchronization Verification & Structural Gaps

A direct automated sync analysis between the JVM entity classes and `mongodb_indexes.js` reveals **significant indexing mismatches** where optimized compound or field indexes are defined on backend domain models but omitted from the raw migration script:

| Collection Name | Java Entity Class | Index Name / Field | Type | Constraint | Status / Issue |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `knowledge_hub_document_versions` | [KnowledgeHubDocumentVersion.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/KnowledgeHubDocumentVersion.java) | `doc_version_idx` (`documentId` + `versionNumber`) | Compound | Unique | ❌ **Missing in JS** |
| `knowledge_hub_document_versions` | [KnowledgeHubDocumentVersion.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/KnowledgeHubDocumentVersion.java) | `team_doc_idx` (`teamId` + `documentId`) | Compound | Standard | ❌ **Missing in JS** |
| `doc_reviews` | [DocReviewAssistant.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/DocReviewAssistant.java) | `team_user_idx` (`teamId` + `userId`) | Compound | Standard | ❌ **Missing in JS** |
| `doc_reviews` | [DocReviewAssistant.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/DocReviewAssistant.java) | `team_status_idx` (`teamId` + `status`) | Compound | Standard | ❌ **Missing in JS** |
| `knowledge_hub_chunks` | [KnowledgeHubChunk.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/KnowledgeHubChunk.java) | `chunkId` | Single | Standard | ❌ **Missing in JS** |
| `knowledge_hub_collection` | [KnowledgeHubCollection.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/KnowledgeHubCollection.java) | `milvusCollectionName` | Single | Standard | ❌ **Missing in JS** |
| `knowledge_hub_documents` | [KnowledgeHubDocument.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/KnowledgeHubDocument.java) | `documentId`, `s3Key` | Single | Standard | ❌ **Missing in JS** |
| `ai_assistants` | [AIAssistant.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/AIAssistant.java) | `status` | Single | Standard | ❌ **Missing in JS** |
| `conversation_messages` | [ConversationMessage.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/ConversationMessage.java) | `timestamp` | Single | Standard | ❌ **Missing in JS** |
| `graph_extraction_jobs` | [GraphExtractionJob.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/GraphExtractionJob.java) | `jobId` | Single | Standard | ❌ **Missing in JS** |
| `organizations` | [Organization.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/Organization.java) | `shortName` | Single | Unique | ❌ **Missing in JS** |
| `agent_executions` | [AgentExecution.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/AgentExecution.java) | `executionId` | Single | Unique | ❌ **Missing in JS** |
| `external_user_credits` | [ExternalUserCredit.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/ExternalUserCredit.java) | `external_user_service_idx` | Compound | Unique | ✅ **Synced in JS** |
| `external_usage_log` | [ExternalUsageLog.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/entity/ExternalUsageLog.java) | `external_user_service_time_idx` | Compound | Standard | ✅ **Synced in JS** |

> [!WARNING]
> While Spring Data MongoDB can automatically generate these indexes at runtime during bean initialization if `spring.data.mongodb.auto-index-creation=true` is enabled, production environments disable auto-indexing for performance reasons. Therefore, it is **highly recommended** to sync the above missing compound and single-field indexes into [mongodb_indexes.js](file:///Users/mehmetsen/IdeaProjects/Linqra/mongodb_indexes.js) to avoid full collection scans on production database instances.

---

## 15. Docker & EKS Container Configurations

The project contains complete settings to run locally via Docker Compose or in production via AWS EKS clusters.

### 🐋 Local Backing Services Catalog (`docker-compose-dev.yml`)
To support full offline capabilities and secure integration environments, Linqra's local development stack dockerizes the following microservices:

1.  **`postgres-service`** (`.kube/postgres/Dockerfile`): PostgreSQL database serving Keycloak realms. Leverages early Vault decryption filters to verify db environment variables (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`).
2.  **`pgadmin-service`** (`.kube/pgadmin/Dockerfile`): Admin interface mapping local ports to visual database metrics.
3.  **`keycloak-service`** (`.kube/keycloak/Dockerfile`): keycloak Identity Manager hosting the OIDC/SAML realm setups. Integrates custom theme layouts (Keycloak login visual custom overrides) and maps to the Postgres database dynamically using Vault-encrypted properties.
4.  **`mongodb1`, `mongodb2`, `mongodb3`** (`.kube/mongodb/Dockerfile`): A secure 3-node MongoDB Replica Set (`rs0`) utilizing filesystem keyfiles (`mongo-keyfile`) for data encryption and shared sync states.
5.  **`redis-service`** (`.kube/redis/Dockerfile`): Redis cache and low-latency queue driver.
6.  **`etcd-service`** (`quay.io/coreos/etcd:v3.5.5`): Local ETCD cluster metadata manager supporting vector indices.
7.  **`minio-service`** (`.kube/minio/Dockerfile`): S3-compatible local object storage server managing document uploads and key backup folders.
8.  **`milvus-service`** (`.kube/milvus/Dockerfile`): Milvus standalone Vector Search Engine connected to ETCD and MinIO.
9.  **`attu-service`** (`.kube/attu/Dockerfile`): Exposes a GUI on port `8000` to manage Milvus collections.
10. **`neo4j-service`** (`.kube/neo4j/Dockerfile`): Neo4j graph database carrying pre-loaded `apoc` analytics plugins to parse semantic knowledge networks.

### ☸️ Production EKS Deployments (`.kube/eks/`)
Standard Kubernetes resource manifests are centralized under [`.kube/eks/`](file:///Users/mehmetsen/IdeaProjects/Linqra/.kube/eks):

*   **`backend.yml`**: Deployment and Service limits for the `api-gateway` and `eureka` pods (including Vault environment key bindings).
*   **`frontend.yml`**: Deployment and Service configurations for the React SPA portal.
*   **`ingress.yml`**: AWS ALB Ingress Controller rules, Route53 apex binding annotations, and ACM SSL certificate references.
*   **`namespace.yml`**: Orchestration isolating contexts under the `linqra` namespace.
*   **`minio-service.yml`** & **`ollama-service.yml`**: Integrates object stores and local inference runtimes.

---

## 16. AWS EKS & VPC Peering Infrastructure

The live production stack runs on Amazon Web Services (AWS) under a resilient EKS cluster layout.

### ☸️ Cluster Sizing & Nodegroup Strategy
*   **Cluster Identifier**: `linqra-cluster` deployed in region `us-west-2`.
*   **Nodegroup Strategy**: Leverages `large-workers` nodegroups built on **t3.large** instances (2 vCPUs, 8 GiB memory) to support high active processing loads under a desired/min size of 2 and maximum size of 3 nodes.
*   **Scaling Best Practice**: Linqra favors a **horizontal scaling strategy** using multiple `t3.medium` instances (2 vCPUs, 4 GiB memory, min=2, max=4) combined with **Karpenter** (AWS-native autoscale engine) to watch pending pods and deploy/terminate nodes dynamically.
*   **DNS Resolution**: Godaddy DNS CNAME and A apex mappings route traffic directly to the AWS Application Load Balancer (ALB) ingress endpoint:
    `k8s-linqra-linqrain-c3b19c71f6-298766992.us-west-2.elb.amazonaws.com`

### 🔗 VPC Peering Configuration
To peer the secure EKS container cluster (`192.168.0.0/16` block) with external state engines (like dedicated Redis instances under the `172.31.0.0/16` CIDR block), Linqra implements explicit VPC peering:

1.  **Peering Request**: Established peer connection `pcx-00b9b68fa4783983b` connecting the EKS VPC (`vpc-0070d7bb409137a42`) to the Redis/Backend VPC (`vpc-bc985bd9`).
2.  **Routing Tables**: Active routes are mapped across all EKS subnets routing `172.31.0.0/16` destinations to the peering link, and symmetrically routing `192.168.0.0/16` queries from the backend VPC route tables.
3.  **Inbound Access Rules (Security Groups)**: Inbound security rules selectively authorize target traffic from the EKS CIDR (`192.168.0.0/16`) on critical ports:
    *   **Port 6379**: Inbound Redis calls.
    *   **Ports 8080 & 8443**: Inbound Keycloak OIDC/SAML authentication.
    *   **Port 8082**: Inbound `komunas-backend` API traffic.
    *   **Port 8083**: Inbound `quotes-service` API traffic.

### 🛠️ Diagnostic CLI Cheat-Sheet
*   **Real-time Logs (Tail)**:
    *   `kubectl logs -f deployment/linqra-backend -n linqra`
*   **Active SSH Entry**:
    *   *Backend (Spring Boot)*: `kubectl exec -it deployment/linqra-backend -n linqra -- /bin/bash`
    *   *Frontend (Nginx / Alpine)*: `kubectl exec -it deployment/linqra-frontend -n linqra -- /bin/sh`
*   **Rolling Restart**:
    *   `kubectl rollout restart deployment/linqra-backend -n linqra`

---

### 🚨 Emergency Outage & DNS/SSL Restoration Runbooks
Derived from the real-world April 2026 EKS outage resolution, the following runbooks must be followed in case of root domain connection failures or expired certificate blocks.

#### 1. DNS Apex Alias Record Recovery
If `https://linqra.com` resolves SSL errors or becomes unreachable while `www.linqra.com` functions:
*   **The Cause**: Missing standard `A (Alias)` records linking the apex domain to AWS Application Load Balancers.
*   **Resolution Record Mapping in Route53**:
    *   **Record Type**: `A (Alias)`
    *   **Alias Target**: `k8s-linqra-linqrain-c3b19c71f6-298766992.us-west-2.elb.amazonaws.com`
    *   **HostedZoneId (Standard us-west-2 ELB)**: `Z1H1FL5HABSF5`

#### 2. Expired Ingress SSL Certificate Rotation
If browsers block connections due to expired imported HTTPS certificates:
*   **The Preventative Rule**: Favor managed **Amazon-Issued Certificates (AMAZON_ISSUED)** over custom **Imported Certificates (IMPORTED)**. Managed certificates handle automated domain-validated renewals, preventing outages.
*   **Rotation Command**:
    1. Retrieve valid active ACM certificate ARN (e.g. `arn:aws:acm:us-west-2:945104357909:certificate/7bf60131-6668-42c1-84d9-775ba7ec780e` valid until Jan 2027).
    2. Update the EKS Ingress annotation directly via kubectl:
       ```bash
       kubectl annotate ingress linqra-ingress alb.ingress.kubernetes.io/certificate-arn=arn:aws:acm:us-west-2:945104357909:certificate/7bf60131-6668-42c1-84d9-775ba7ec780e -n linqra --overwrite
       ```
    3. **Manifest Sync**: Ensure the new ARN is updated in the source configuration at [ingress.yml](file:///Users/mehmetsen/IdeaProjects/Linqra/.kube/eks/ingress.yml) to prevent accidental state-drift overwrites.

#### 3. Pod CPU Memory Pressure Relief
If pod schedulers fail due to resource exhaustion warnings, immediately reclaim space by removing obsolete non-active packages (e.g., `medastex-app` and `mytrux-app` legacies):
```bash
kubectl delete deployment medastex-app mytrux-app -n linqra
```

---

## 17. Operational Scripts & Secrets Management Lifecycle

The [`scripts/`](file:///Users/mehmetsen/IdeaProjects/Linqra/scripts) directory holds vital tooling coordinating credentials bootstrapping, mutual TLS key generation, database migrations, and testing validations.

### 🔐 1. Secrets Bootstrapping (`bootstrap-vault.sh` & `vault-entrypoint.sh`)
These scripts work together to solve the **chicken-and-egg startup dependency**—services need Vault properties to launch, but Vault APIs need the microservices running to be configured:
*   **Vault Initialization**: [bootstrap-vault.sh](file:///Users/mehmetsen/IdeaProjects/Linqra/scripts/bootstrap-vault.sh) generates and logs a `VAULT_MASTER_KEY` (saved to the root `.env` file), compiles the decoupled Java `vault-reader` package, and compiles the encrypted local binary file `vault-{env}.encrypted` directly from `secrets/secrets.json` *before* containers start.
*   **Sourced Exporting**: Running `source ./scripts/bootstrap-vault.sh --export` pulls decrypted secrets as environment variables into the active shell, which are immediately inherited by `docker-compose-dev.yml` during local startup.
*   **Key Rotation**: Supports dynamic RAG chunk encryption key rotations (`--rotate-chunk-key`). A new key version is securely appended, while existing databases remain decryptable using `v1` (ensuring backward compatibility).
*   **Container Entrypoint Integration**: [vault-entrypoint.sh](file:///Users/mehmetsen/IdeaProjects/Linqra/scripts/vault-entrypoint.sh) acts as a startup wrapper for PostgreSQL, Keycloak, MinIO, and Neo4j containers. It calls `vault-reader.jar` using local Java environments, filters parameters to avoid environment contamination, and performs runtime configuration bindings:
    *   *Neo4j*: Dynamically maps custom `NEO4J_AUTH` formats, then unsets sensitive raw environment flags.
    *   *Keycloak*: Generates specific database URLs (`KC_DB_URL`) dynamically.
    *   *API Gateway*: Intercepts the Java runtime environment and merges custom HTTPS certificates (`gateway-truststore.jks`) directly into OpenJDK's system `cacerts` file, allowing Eureka discovery client loops to hand-shake successfully over outbound HTTPS lanes.

---

### 🔑 2. Zero-Trust Keys & Certificates Generation
*   **`generate-service-certs.sh`**: Auto-generates SSL keystores and mutual TLS truststores (`gateway-keystore.jks`, `gateway-truststore.jks`) supporting secure service-to-service communication loops.
*   **`generate-haproxy-certs.sh`**: Combines certificates for local HAProxy load-balancers.
*   **`verify-ssl-config.sh`**: Connection test utility validating SSL handshakes and trust chain integrity.

---

### 📦 3. Database Migrations & Validation Tools
*   **`migrate_to_atlas.sh`**: Connects to the local MongoDB container and migrates metadata schemas, audit trails, and configurations directly to MongoDB Atlas.
*   **`migrate_keycloak_data.sh`**: Handles importing/exporting Keycloak user identities and realm boundaries.
*   **`migrate_milvus.py`** & **`verify_milvus_content.py`**: Python tasks running validation searches on vector collections and migrating RAG schemas between environments.
*   **`backup-postgres-polaris.sh`**: Database backups for PostgreSQL systems.
*   **`security-scan.sh`**: Audits workspace files to prevent leaking sensitive properties in Git commits.

---

## 18. GitHub Actions CI/CD Pipelines (`.github/workflows/`)

Integrated automation workflows are located inside the [`.github/workflows/`](file:///Users/mehmetsen/IdeaProjects/Linqra/.github/workflows) folder:

*   **`ci.yml`**: Main integration pipeline triggered on pull requests and branch merges. Compiles Java microservices, packages JAR targets, triggers frontend tests, and publishes compiled container images.
*   **`eks-deploy.yml`**: Main CD pipeline compiling EKS resource states and rolling out container updates to live production clusters.
*   **`deploy-polaris-*.yml`**: Specialized workflows targeting individual services (Caddy, Eureka, Keycloak theme assets, MinIO buckets, and local Ollama nodes).

---

## 19. End-to-End Processing Pipelines

### ⚡ RAG Document Processing Pipeline
1.  **Document Upload**: Frontend uploads PDF, Word, or HTML docs to `/api/knowledge-hub/upload`.
2.  **Tika Parsing**: `TikaDocumentParser` extracts raw text, structures, and metadata elements.
3.  **Embeddings Insertion**: `KnowledgeHubDocumentEmbeddingService` generates dense vectors and stores them in **Milvus**.
4.  **Graph Extraction**: `GraphExtractionJobService` triggers AI extraction models to parse entity pairs (e.g. *Person* → *Employed By* → *Company*) and saves them to **Neo4j**.

---

## 20. Advanced Gateway Systems & Security Engines

### 📡 1. Dynamic WebFlux Route Injection (`ApiRouteLocatorImpl.java` & `DynamicRouteService.java`)
Linqra implements a highly adaptive Spring Cloud Gateway routing layer that live-reloads route definitions dynamically from database changes instead of relying on static properties:
*   **The Routine**: At startup, [ApiRouteLocatorImpl.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/service/impl/ApiRouteLocatorImpl.java) queries MongoDB for all registered `apiRoutes` and `team_routes`. It builds standard Spring Cloud `RouteDefinition` frames (allocating downstream microservice URLs, custom headers, and rate-limiting policies dynamically).
*   **Hot-Reload Dispatch**: The `DynamicRouteService` listens to admin-driven route mutations. Upon modification, it dynamically updates Spring’s `RouteDefinitionLocator` registry and fires a refresh event, updating the active Gateway route maps in memory without requiring a service reboot.

### 🔐 2. Cryptographic Envelope & Key Rotation (`ChunkEncryptionServiceImpl.java`)
To fulfill strict HIPAA compliance standards for raw text, Linqra implements a transparent, high-performance symmetric encryption envelope layer:
*   **Symmetric wrapping (AES-256-GCM)**: Before storing text blocks in MongoDB's `knowledge_hub_chunks` collection, [ChunkEncryptionServiceImpl.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/service/impl/ChunkEncryptionServiceImpl.java) encrypts the payload using the active key corresponding to the executing `teamId`.
*   **Dynamic Key Resolvers**: Decryption keys are loaded dynamically from the `team_chunk_keys` collection. The database maps both standard active keys and past key versions (`isActive=false`) to support seamless legacy read operations.
*   **Rotation Strategy**: System administrators rotate encryption keys dynamically (e.g., using `/scripts/bootstrap-vault.sh --rotate-chunk-key`). A new key version is generated as `isActive=true`, while legacy keys remain stored in the array for backward compatibility.

### 🛡️ 3. Non-Blocking Resilience Pipeline (Spring Cloud Gateway Filters)
To guarantee high cluster availability and isolate down-stream service outages, the API Gateway incorporates standard WebFlux resilience filters on outbound dispatches:
*   **Circuit Breaker (`CircuitBreakerFilterService.java`)**: Wraps outbound routing targets in Resilience4j reactive circuit breakers. If failure counts exceed a 50% threshold on a service instance, it halts traffic and routes requests to fallback controllers.
*   **Retry Engine (`RetryFilterService.java`)**: Configures non-blocking retry behaviors (e.g. up to 3 attempts with exponential backoff) for transient HTTP errors (such as `503 Service Unavailable` or connection dropouts).
*   **Time Limiter (`TimeLimiterFilterService.java`)**: Limits maximum outbound wait boundaries. Calls extending beyond configured limits (e.g. 5 seconds for RAG lookups) are terminated, preventing socket pool exhaustion.

### ⚙️ 4. Quartz Scheduling & Clustering Engine (`AgentQuartzServiceImpl.java`)
Linqra schedules complex periodic agent tasks dynamically at scale without hardcoding crons in JVM configurations:
*   **Quartz-MongoDB Bridging**: [AgentQuartzServiceImpl.java](file:///Users/mehmetsen/IdeaProjects/Linqra/api-gateway/src/main/java/org/lite/gateway/service/impl/AgentQuartzServiceImpl.java) integrates the Quartz scheduler directly with MongoDB job stores. It maps recurring agent crons (Quartz-compliant) dynamically into active Quartz jobs.
*   **Distributed Clustering Locks**: To prevent duplicate triggers in clustered EKS environments running multiple backend replicas, Quartz leverages Redis/MongoDB-backed distributed lock registries to ensure that exactly one EKS node fires a scheduled job.
*   **Workflow Dispatch**: When a Quartz job triggers, it retrieves the task context, decodes Keycloak identity tokens securely via `AgentAuthContextServiceImpl.java`, and dispatches the compiled prompt sequence directly to the `LinqWorkflowExecutionServiceImpl.java`.

---

## 21. Linqra React Library (Slide Deck Components)

The `linqra-react-library` provides a robust, zero-dependency presentation rendering engine (the `PresentationRenderer`) tailored specifically for AI-generated slide decks. It supports dynamic layouts, custom styling passthroughs, and unified constraints.

### 🎨 1. Global CSS Variable Injection (`PresentationRenderer.tsx`)
The `PresentationRenderer` injects a dynamic CSS variable block scoped directly to the `.linqra-presentation-renderer` container. This allows programmatic theme overrides injected by the AI, powering consistent constraints across all slide types:
*   **Typography**: `--font-family`, `--title-font-size`, `--body-font-size`, `--title-align`, `--body-line-height`.
*   **Spacing System**: `--slide-padding`, `--content-gap`, `--card-padding`, `--card-gap`, `--step-connector-width`.
*   **Colors**: `--card-bg`, `--card-border`, `--text-main`, `--text-secondary`.

### 🧩 2. Custom Diagram Components
*   **ProcessFlowSlide**: Renders dynamic, responsive sequence flows mapping badges, icons, gradients, and bullet points.
*   **TimelineSlide**: A fully SVG-native horizontal milestones component rendering offset tracking dots and dynamic path segments.

### 🎯 3. Universal Text & Sub-Item Passthrough Methodology
To ensure maximum flexibility without fragmenting component APIs, the library mandates a **universal text pass-through methodology** for all text fields and repeated sub-items (cards, steps, lists). Every text element in the schema across **every single slide layout** is typed as `TextContent`:
```typescript
export type TextContent = string | { text: string; style?: CSSProperties };
```
This means agents can dynamically inject custom CSS onto any text node in the presentation:
*   **Text Styling (Any Slide)**: Provide `{ text: "Your Text", style: { color: "red", fontSize: "2rem" } }` instead of a plain string.
*   **`ProcessFlowSlide` (Steps)**: Accepts `cardStyle` (`CSSProperties`) spread directly onto the rendered step container card.
*   **`MetricGridSlide` (Metrics)**: Accepts `cardStyle` (`CSSProperties`) mapping directly onto individual metric UI grids.
*   **`StandardContentSlide` (List Items)**: Accepts `style` (`CSSProperties`) for individually styling `<li>` elements, and uses `TextContent` for both main item text and deeply nested `subItems`.
*   **`TimelineSlide` (Events)**: Exposes specific SVG-native attributes: `cardBg`, `titleColor`, `descColor`, and a numeric `fontSize` scale. (Note: standard HTML Box-model CSS like `padding` does not work on SVG text, but `fill` or `fontWeight` passed via `style` will).

> [!IMPORTANT]
> **Rules for AI Agents Generating Slides**:
> You must strictly avoid modifying React component source files to achieve layout tweaks (e.g., reordering elements, changing alignments, adjusting specific spacings). Instead, leverage the universal `style` passthrough payload dynamically:
> 
> **Scenario A: Reordering Elements in a Flexbox Column (e.g., TitleSlide)**
> If the user wants the tagline below the subtitle, or the title at the bottom, **do not edit the component rendering order**. The containers are Flexbox columns. Pass standard CSS `order` properties via the payload:
> ```json
> "tagline": { "text": "Tagline text", "style": { "order": 2 } }
> "subtitle": { "text": "Subtitle text", "style": { "order": 1 } }
> ```
> *(Note: Elements without an explicit order default to `order: 0`. To push an element to the absolute bottom, use an order higher than 0, like `order: 99`).*
> 
> **Scenario B: Breaking out of Slide Padding Safe Zones**
> Slide containers enforce strict protective padding (e.g., `padding: clamp(1rem, 5cqi, 4rem)`). If the user requests an element like a footer to sit flush against the absolute bottom edge of the screen, **do not change the component's padding**. Instead, apply negative CSS margins to pull it through the safe zone:
> ```json
> "footer": { "text": "Confidential", "style": { "marginBottom": "-2.5rem" } }
> ```
> 
> **Scenario C: Specific Grid/Card Styling**
> If the user wants one specific card to have a "glowing" effect or different background, inject it directly into the sub-item:
> ```json
> "cardStyle": { "background": "#1e1b4b", "boxShadow": "0 0 20px #6366f1" }
> ```
