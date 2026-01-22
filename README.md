![Build Status](https://github.com/mehmetsen80/Linqra/actions/workflows/ci.yml/badge.svg) ![Java 21](https://img.shields.io/badge/Java-21-blue) ![Version](https://img.shields.io/badge/version-0.8-brightgreen)

# What is Linqra?

<div align="center">
<a href="assets/color_logo_with_background.png"> <img alt="Linqra" src="assets/color_logo_with_background.png"></a>
</div>
<br/>

Linqra is an enterprise-grade **AI Orchestration Platform** that unifies multi-model access, workflow execution, and governance. It provides a secure, standardized layer for enterprises to build, manage, and scale complex AI applications. By abstracting away provider-specific complexities, Linqra enables organizations to orchestrate multi-agent workflows, managing everything from authentication and rate limiting to state sharing and failover.

🌐 **[Visit Linqra.com](https://linqra.com)** for more information and live demos.

## Key Features


### 🧠 Intelligent Orchestration
Orchestrate complex, multi-step AI workflows with ease. Linqra's engine handles the execution of AI agents, parallel processing, and fallback strategies, ensuring resilient and efficient automation pipelines.

### 🔐 Enterprise Gateway
Built with a security-first approach, Linqra provides comprehensive authentication, rate limiting, and load balancing features that protect and optimize your API infrastructure.

### 📡 Linq Protocol
Linq Protocol simplifies complex AI workflows into a single request. Instead of making multiple API calls, orchestrate your entire AI pipeline in one unified protocol.

#### Traditional Way vs. Linq Protocol

**Traditional Way**
Manually chaining requests requires client-side state management, multiple round-trips, and complex error handling.

```bash
// Step 1: Generate embedding
POST https://api.openai.com/v1/embeddings
Authorization: Bearer sk-...
Content-Type: application/json

{
  "model": "text-embedding-3-small",
  "input": "What is machine learning?"
}

// Step 2: Search vector database
POST https://your-milvus-api/search
Authorization: Bearer your-api-key
Content-Type: application/json

{
  "collection": "knowledge_base",
  "vector": [0.123, -0.456, ...],
  "top_k": 5
}

// Step 3: Generate LLM response
POST https://api.openai.com/v1/chat/completions
Authorization: Bearer sk-...
Content-Type: application/json

{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "system",
      "content": "Answer using the provided context..."
    },
    {
      "role": "user",
      "content": "Question: What is machine learning?\n\nContext: [parsed search results]"
    }
  ],
  "temperature": 0.3,
  "max_tokens": 500
}
```

**Linq Protocol**
Define your workflow and execute it in a single request. Linqra handles the orchestration, security, and context passing.

```http
POST /linq
X-API-Key: lm_...
Content-Type: application/json

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
            "content": "Answer using the provided context..."
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
            "max.tokens": 500
          }
        }
      }
    ]
  }
}
```

### 🆚 What's Different: Linqra vs. MCP

| Feature | Linqra Server (Linq Protocol) | MCP Server (Modal Context Protocol) |
| :--- | :--- | :--- |
| **Gateway/API Layer** | **Yes** - Centralized gateway with dynamic routing, resiliency, and unified API management | **No** - Protocol for AI-tool interactions, not a gateway layer |
| **Workflow Orchestration** | Seamless API conversion to Linq Protocol. Sequential/parallel steps coming via SDK, with visual designer planned | No (focus on tool interoperability) - Orchestration handled by external platforms (Zapier, n8n) or custom implementations |
| **Security** | Enterprise-grade with built-in OAuth 2.0, TLS, scopes, and Keycloak integration | Standardized security (OAuth 2.0, TLS, scopes) - implementation depends on server configuration |
| **Dynamic Routing** | **Yes** - Built-in dynamic routing with rule-based routing and service discovery for scalable AI app ecosystems | **No** - Routing handled by external gateways or platforms (e.g., Higress MCP in Go/Envoy) |
| **Analytics/Monitoring** | **Yes** - Built-in analytics with request latency, error rates, and AI model performance metrics | **No** - Monitoring requires external tools or custom implementation |
| **Tool Integrations** | Built-in support for external APIs, AI models, and SaaS tools (expanding integration ecosystem in development) | Extensive integrations with tools like Google Drive, Excel, Dropbox, Slack, and more via standardized MCP servers and automation platforms (e.g., Zapier, n8n, Composio) |
| **Developer SDK** | **Coming soon** - SDK development in progress, leveraging Linq Protocol for seamless API integration and workflow management | **Yes** - Composio SDK (JavaScript/TypeScript, Python) with support for 200+ tools, plus additional SDKs for specific MCP servers |
| **Context Management** | Advanced context management with multi-step workflow orchestration and state persistence | Advanced context management with standardized protocol for multi-modal AI applications |
| **Interoperability** | High (via gateway/protocol) | High (between AI models/services) |
| **Use Case Focus** | AI Agent deployment & workflow simplification | Multi-modal AI context/state handling |
| **Architecture** | Centralized logic with unified protocol (Linq Protocol) that orchestrates workflows through sequential steps | Distributed and decentralized logic across multiple MCP servers, each handling specific tool interactions independently |
| **Deployment Model** | Hybrid SaaS - Run on Linqra.com or self-hosted in your own network | Self-hosted only - Requires setting up MCP servers in your infrastructure |
| **Platform Capabilities** | AI Agents with team/organization management, unified API Routes for both traditional APIs and Linq Protocol endpoints | Protocol for tool interactions - No built-in app store or team management |
| **RAG Support** | **Yes** - Native RAG customization in development with planned configurable retrieval pipelines, embedding models, and centralized orchestration | **Yes** - Indirect support via standardized data retrieval and context-retrieval tools; relies on external RAG frameworks (e.g., LangChain) and distributed MCP servers |

