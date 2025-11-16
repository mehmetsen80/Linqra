# AI Assistant MVP Design

## Overview
This document outlines the MVP (Minimum Viable Product) design for AI Assistants based on the finalized architecture decisions. The MVP focuses on core functionality while keeping complexity manageable.

---

## MVP Scope

### ✅ **Included in MVP**

1. **Assistant Management**
   - Create, edit, delete assistants (team-scoped)
   - Configure assistant name, description, system prompt
   - Assign default model (LLM configuration)
   - Select existing Agent Tasks (no task creation)

2. **Conversation Interface**
   - Chat UI for user interactions
   - Real-time message streaming via WebSocket (ChatGPT-like word-by-word streaming)
   - Conversation history sidebar (last 1 year)
   - Multi-turn conversations with context
   - Cancel generation functionality during streaming

3. **Task Execution**
   - Parallel execution of all selected Agent Tasks (MVP approach)
   - **Note**: Intent-based routing to select specific tasks is planned for Phase 2
   - Currently executes ALL enabled selected tasks in parallel for each query
   - Graceful failure handling with fallback
   - User query passed as parameter to task execution

4. **Response Synthesis**
   - Natural language response generation
   - Context-aware responses (conversation history)
   - Token-efficient context management

5. **Access Control**
   - Configurable access (Private or Public)
   - Team-private assistants (default)
   - Public assistants with widget deployment
   - Super Admin access
   - Role-based permissions (view, manage, admin)

6. **Widget Deployment** (Public Assistants)
   - JavaScript widget script for embedding
   - Iframe embedding option
   - Customizable appearance (theme, colors, size)
   - Public API key generation
   - Domain whitelist (optional)
   - CORS configuration for cross-origin embedding

7. **Data Management**
   - Conversation storage (1 year retention)
   - Configurable retention policy
   - Export functionality

8. **Guardrails**
   - Basic PII detection (**Not yet implemented - TODO**)
   - Sensitive data handling (**Not yet implemented - TODO**)
   - Audit logging (**Not yet implemented - TODO**)

### ❌ **Excluded from MVP**

1. **Intent-based task routing** (MVP executes all selected tasks in parallel; intelligent routing planned for Phase 2)
2. **Sequential/conditional task execution** (only parallel execution supported in MVP)
3. **File uploads**
4. **Assistant Marketplace** (templates/pre-configured assistants)
5. **Dynamic task creation**
6. **Rate limiting** (not yet implemented - TODO)
7. **PII detection** (not yet implemented - TODO)
8. **Collaboration features** (multi-user conversations)
9. **Advanced analytics dashboard**

---

## Core Entities

### **1. AI Assistant**

```javascript
{
  "id": "assistant-123",
  "name": "USCIS Immigration Assistant",
  "description": "Helps with USCIS forms and immigration questions",
  "teamId": "team-456",
  
  // Model Configuration
  "defaultModel": {
    "provider": "openai",
    "modelName": "gpt-4o",
    "modelCategory": "openai-chat",
    "settings": {
      "temperature": 0.7,
      "max_tokens": 2000,
      "top_p": 0.9
    }
  },
  
  // System Prompt / Personality
  "systemPrompt": "You are a helpful immigration assistant...",
  
  // Selected Tasks (existing Agent Tasks only)
  "selectedTasks": [
    {
      "taskId": "task-789",
      "taskName": "USCIS Q&A Task",
      "priority": 1,
      "enabled": true,
      "paramsMapping": {
        // How to map user query to task params
        "question": "{{userQuery}}",
        "teamId": "{{teamId}}",
        "userId": "{{userId}}"
      }
    }
  ],
  
  // Context Management
  "contextManagement": {
    "strategy": "sliding_window",
    "maxRecentMessages": 10,
    "maxTotalTokens": 4000
  },
  
  // Guardrails
  "guardrails": {
    "piiDetectionEnabled": true,
    "redactionEnabled": false,  // Log but don't redact in MVP
    "auditLoggingEnabled": true
  },
  
  // Retention Policy
  "retentionPolicy": {
    "durationDays": 365,
    "autoDelete": true,
    "exportBeforeDelete": false
  },
  
  // Access Control
  "accessControl": {
    "type": "PRIVATE",  // PRIVATE | PUBLIC
    "publicApiKey": null,  // Generated when type is PUBLIC
    "allowedDomains": [],  // Optional: Domain whitelist for widget embedding
    "allowAnonymousAccess": false  // Only relevant for PUBLIC
  },
  
  // Widget Deployment (for PUBLIC assistants)
  "widgetConfig": {
    "enabled": false,  // Only for PUBLIC assistants
    "theme": {
      "primaryColor": "#007bff",
      "secondaryColor": "#6c757d",
      "backgroundColor": "#ffffff",
      "textColor": "#212529"
    },
    "position": "bottom-right",  // bottom-right | bottom-left | top-right | top-left
    "size": {
      "width": "400px",
      "height": "600px"
    },
    "headerText": "Chat with Assistant",
    "welcomeMessage": "Hello! How can I help you today?",
    "embedScriptUrl": null  // Generated widget script URL
  },
  
  // Status
  "status": "ACTIVE",  // ACTIVE, INACTIVE, DRAFT
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z",
  "createdBy": "user-123"
}
```

### **2. Conversation**

```javascript
{
  "id": "conversation-123",
  "assistantId": "assistant-123",
  "teamId": "team-456",
  "userId": "user-789",  // null for public/anonymous conversations
  "guestId": null,  // For public conversations: browser fingerprint or session ID
  "isPublic": false,  // true if from public assistant/widget
  "source": "web_app",  // web_app | widget | api
  
  "title": "USCIS Form I-485 Questions",  // Auto-generated from first message
  "status": "ACTIVE",  // ACTIVE, ARCHIVED, DELETED
  
  // Widget metadata (for public conversations)
  "widgetMetadata": {
    "domain": null,  // Source domain if from widget
    "userAgent": null,
    "ipAddress": null  // Hashed IP for privacy
  },
  
  // Timestamps
  "startedAt": "2024-01-01T00:00:00Z",
  "lastMessageAt": "2024-01-01T00:05:00Z",
  "messageCount": 12,
  
  // Metadata
  "metadata": {
    "totalTokens": 5000,
    "totalCost": 0.15,
    "taskExecutions": 8,
    "successfulTasks": 7,
    "failedTasks": 1
  }
}
```

### **3. Conversation Message**

```javascript
{
  "id": "message-123",
  "conversationId": "conversation-123",
  "role": "USER",  // USER, ASSISTANT, SYSTEM
  "content": "What documents do I need for Form I-485?",
  "sequenceNumber": 1,
  "timestamp": "2024-01-01T00:00:00Z",
  
  // Metadata
  "metadata": {
    "tokens": 10,
    "executedTasks": ["task-789"],
    "taskResults": {
      "task-789": {
        "status": "SUCCESS",
        "result": { "documents": [...] }
      }
    },
    "piiDetected": false,
    "moderationFlags": []
  }
}
```

### **4. Conversation Context Cache**

```javascript
{
  "conversationId": "conversation-123",
  "contextData": {
    "extractedEntities": {
      "forms": ["I-485", "I-864"],
      "topics": ["document_requirements", "eligibility"]
    },
    "intentHistory": [
      { "intent": "document_requirements", "timestamp": "..." }
    ],
    "taskResultsCache": {
      // Cached task results for reference
    }
  },
  "updatedAt": "2024-01-01T00:05:00Z"
}
```

---

## MVP Workflow

### **User Sends Message**

```
1. User sends message: "What documents do I need for I-485?"
   ↓
2. Context Building
   ├── Load conversation history (last 10 messages, configurable)
   └── Build context window (sliding window approach)
   ↓
3. Task Execution (MVP: Execute ALL Selected Tasks in Parallel)
   ├── Check: Assistant's selectedTasks configuration
   ├── Filter: Only execute enabled tasks
   ├── Execute: All enabled selected tasks in parallel
   │   ├── Task 1: Execute with user message as parameter
   │   ├── Task 2: Execute with user message as parameter
   │   └── Task N: Execute with user message as parameter
   ├── Wait: For all tasks to complete (timeout: 30s per task)
   └── Aggregate: Collect all task results (Map<taskId, result>)
   ↓
   **Note**: Intent-based routing to select specific tasks is NOT in MVP.
   MVP executes ALL selected tasks in parallel. Future Phase 2 will add
   intelligent intent classification to route to specific tasks.
   ↓
4. Failure Handling (if tasks fail)
   ├── Check: Task failure reasons
   ├── Continue: With successful task results
   ├── Aggregate: Partial results from successful tasks
   └── Note: Failed tasks are logged but don't block response
   ↓
5. Response Synthesis (AI Assistant Default Model)
   ├── If tasks succeeded:
   │   ├── Prompt: "Generate response using task results + history"
   │   │   - Include all task results in prompt
   │   │   - Incorporate conversation context
   │   └── Generate: Natural language response
   ├── If tasks failed:
   │   ├── Prompt: "Explain failure + provide general answer"
   │   └── Generate: Apology + fallback response using general knowledge
   └── Include: Follow-up suggestions
   ↓
6. WebSocket Streaming (Real-time to User)
   ├── Publish: LLM_RESPONSE_STREAMING_STARTED
   ├── Stream: Response chunks word-by-word (30ms delay per word)
   ├── Publish: LLM_RESPONSE_CHUNK (for each word)
   ├── Allow: User to cancel generation mid-stream
   └── Publish: LLM_RESPONSE_STREAMING_COMPLETE
   ↓
7. PII Detection & Guardrails (**TODO - Not yet implemented**)
   ├── Scan: User message and assistant response
   ├── Detect: PII patterns (SSN, phone, email, etc.)
   └── Log: PII detection events (audit log)
   ↓
8. Save & Update
   ├── Save: User message to database
   ├── Save: Assistant response to database
   ├── Update: Conversation metadata (message count, last message time, token usage, cost)
   ├── Update: Conversation history in UI (sidebar refresh)
   └── Return: Complete response to user
```

---

## Failure Handling Strategy

### **Task Execution Failures**

```javascript
// Scenario 1: Task Timeout
if (taskExecution.status === "TIMEOUT") {
  // Generate fallback response using AI Assistant model
  response = await generateFallbackResponse({
    userQuery: userMessage,
    conversationHistory: history,
    failureReason: "Task execution timed out",
    suggestion: "Please try rephrasing your question or try again later"
  });
}

// Scenario 2: Task Error
if (taskExecution.status === "FAILED") {
  // Provide helpful error message + general answer
  response = await generateErrorResponse({
    userQuery: userMessage,
    conversationHistory: history,
    errorMessage: taskExecution.error,
    fallbackToGeneralKnowledge: true
  });
}

// Scenario 3: Partial Success
if (taskExecution.status === "PARTIAL_SUCCESS") {
  // Use partial results + explain what's missing
  response = await generatePartialResponse({
    userQuery: userMessage,
    taskResults: taskExecution.partialResults,
    missingData: taskExecution.missingParts,
    conversationHistory: history
  });
}
```

### **Fallback Response Generation**

```javascript
// Fallback prompt to AI Assistant model
const fallbackPrompt = `
The Agent Task execution failed with the following error: ${error}

User's question: ${userQuery}

Conversation history:
${conversationHistory}

Please provide a helpful response that:
1. Acknowledges the technical issue
2. Explains what happened in simple terms
3. Provides a general answer based on your knowledge (if applicable)
4. Suggests next steps (retry, rephrase, contact support)

Be empathetic and helpful, not technical.
`;
```

---

## WebSocket Streaming (MVP) ✅ **Implemented**

### **Real-Time Streaming Architecture**

**Status**: Fully implemented with ChatGPT-like word-by-word streaming experience.

**Implementation Details**:

1. **WebSocket Infrastructure**:
   - WebSocket endpoint: `/ws-linqra`
   - STOMP protocol support
   - Topic: `/topic/chat` for chat updates
   - Topics: `/topic/health`, `/topic/execution` for other updates

2. **Streaming Flow**:
   ```
   User sends message
       ↓
   Agent Tasks execute (parallel)
       ↓
   LLM generates complete response
       ↓
   Backend splits response into word chunks
       ↓
   Stream chunks via WebSocket (30ms delay per word)
       ↓
   Frontend displays chunks in real-time (ChatGPT-like)
       ↓
   User can cancel generation mid-stream
   ```

3. **WebSocket Events**:
   - `CONVERSATION_STARTED`: When a conversation is created/loaded
   - `USER_MESSAGE_SENT`: When a user sends a message
   - `AGENT_TASKS_EXECUTING`: When agent tasks start executing
   - `AGENT_TASKS_COMPLETED`: When agent tasks complete successfully
   - `AGENT_TASKS_FAILED`: When agent tasks fail
   - `LLM_RESPONSE_STREAMING_STARTED`: When streaming begins
   - `LLM_RESPONSE_CHUNK`: Each word chunk with accumulated content
   - `LLM_RESPONSE_STREAMING_COMPLETE`: When streaming finishes
   - `LLM_RESPONSE_STREAMING_CANCELLED`: When user cancels generation
   - `LLM_RESPONSE_RECEIVED`: Final response (fallback if WebSocket not used)
   - `MESSAGE_SAVED`: When user/assistant messages are saved

4. **Cancel Functionality**:
   - User can click cancel button during streaming
   - Cancellation sent via WebSocket to `/app/chat-cancel`
   - Backend stops streaming thread and cleans up
   - Frontend removes streaming message from UI

5. **Frontend Integration**:
   - `chatWebSocketService.jsx`: WebSocket client for chat updates
   - Subscribes to `/topic/chat` for conversation-specific updates
   - Handles streaming chunks and updates UI in real-time
   - Manages streaming state (streaming, completed, cancelled)

**Performance**:
- Streaming speed: ~30ms per word (configurable)
- Smooth user experience with ChatGPT-like behavior
- Auto-scrolling to follow message generation

---

## Context Management (MVP)

### **Sliding Window Strategy** ✅ **Implemented**

```javascript
// MVP: Simple sliding window
function buildContext(conversation, currentQuery) {
  const maxTokens = 4000;
  const reservedTokens = 2500; // System prompt + task results
  const availableTokens = maxTokens - reservedTokens;
  
  // Always include last N messages (up to token limit)
  const recentMessages = conversation.messages
    .slice(-20)  // Last 20 messages max
    .filter(msg => {
      // Estimate tokens and keep within budget
      const msgTokens = estimateTokens(msg.content);
      return msgTokens <= availableTokens;
    });
  
  return {
    messages: recentMessages,
    totalTokens: estimateTokens(recentMessages),
    strategy: "sliding_window"
  };
}
```

### **Token Estimation**

```javascript
// Simple token estimation (1 token ≈ 4 characters)
function estimateTokens(text) {
  return Math.ceil(text.length / 4);
}

// More accurate: Use actual tokenizer if available
function estimateTokensAccurate(text) {
  // Use tiktoken or similar library
  return tokenizer.encode(text).length;
}
```

---

## Access Control

### **Assistant Types**

1. **Private Assistants** (Default)
   - Only accessible to team users and Super Admin
   - Requires authentication (team membership)
   - All conversations tied to authenticated users

2. **Public Assistants**
   - Can be embedded as widgets on external websites
   - Accessible without authentication (anonymous access)
   - Conversations tracked anonymously (guest users)
   - Requires public API key for widget access
   - Optional domain whitelist for security

### **Permission Levels**

1. **View** (Team Member)
   - Can chat with assistants in their team
   - Can view their own conversations
   - Cannot modify assistants

2. **Manage** (Team Admin)
   - Can create/edit/delete assistants
   - Can configure assistant settings (including making public)
   - Can generate public API keys
   - Can view all team conversations

3. **Admin** (Super Admin)
   - Full access to all assistants across all teams
   - Can override team settings
   - Can access all conversations

### **Implementation**

```javascript
// Access control check (private assistants)
function canAccessAssistant(user, assistant) {
  // Public assistants are accessible to anyone (via widget)
  if (assistant.accessControl.type === "PUBLIC") {
    // Public access requires API key validation (handled separately)
    return true;  // Public access handled via widget authentication
  }
  
  // Super Admin has full access to private assistants
  if (user.role === "SUPER_ADMIN") {
    return true;
  }
  
  // Team members can access private assistants in their team
  if (user && user.teamId === assistant.teamId) {
    return true;
  }
  
  return false;
}

// Widget access check (public assistants)
function canAccessWidget(publicApiKey, domain) {
  const assistant = await authenticateWidget(publicApiKey, domain);
  return assistant !== null;
}

function canModifyAssistant(user, assistant) {
  // Super Admin can modify any assistant
  if (user.role === "SUPER_ADMIN") {
    return true;
  }
  
  // Team Admin can modify assistants in their team
  if (user.role === "TEAM_ADMIN" && user.teamId === assistant.teamId) {
    return true;
  }
  
  return false;
}

function canMakePublic(user, assistant) {
  // Only Team Admin and Super Admin can make assistants public
  return canModifyAssistant(user, assistant);
}
```

---

## Guardrails (MVP)

### **PII Detection** ⚠️ **Not Yet Implemented - TODO**

```javascript
// Basic PII patterns (planned for MVP)
const piiPatterns = {
  ssn: /\b\d{3}-\d{2}-\d{4}\b/g,
  phone: /\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/g,
  email: /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/g,
  creditCard: /\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b/g
};

function detectPII(text) {
  const detected = [];
  
  for (const [type, pattern] of Object.entries(piiPatterns)) {
    const matches = text.match(pattern);
    if (matches) {
      detected.push({ type, matches, count: matches.length });
    }
  }
  
  return detected;
}

// Scan user message and response
function scanForPII(userMessage, assistantResponse) {
  const userPII = detectPII(userMessage);
  const responsePII = detectPII(assistantResponse);
  
  // Log to audit log
  if (userPII.length > 0 || responsePII.length > 0) {
    auditLog({
      event: "PII_DETECTED",
      conversationId: conversation.id,
      userPII: userPII,
      responsePII: responsePII,
      timestamp: new Date()
    });
  }
  
  return { userPII, responsePII };
}
```

**Status**: PII detection is planned but not yet implemented. The `ConversationMessage` entity has a `piiDetected` field in metadata, but the detection logic is not yet implemented.

### **Audit Logging** ⚠️ **Not Yet Implemented - TODO**

```javascript
// Audit log structure (planned)
{
  "id": "audit-123",
  "event": "PII_DETECTED",
  "conversationId": "conversation-123",
  "assistantId": "assistant-123",
  "teamId": "team-456",
  "userId": "user-789",
  "details": {
    "piiTypes": ["ssn", "email"],
    "messageId": "message-123"
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

**Status**: Audit logging infrastructure is not yet implemented. Planned for future implementation.

---

## Rate Limiting (MVP - Basic) ⚠️ **Not Yet Implemented - TODO**

### **Simple Rate Limits** (Planned)

```javascript
// MVP: Basic rate limiting (not yet implemented)
const rateLimits = {
  perUser: {
    messagesPerMinute: 30,
    messagesPerHour: 200,
    messagesPerDay: 1000
  },
  perAssistant: {
    messagesPerMinute: 100,
    messagesPerHour: 1000
  },
  perTeam: {
    messagesPerDay: 10000,
    tokensPerDay: 1000000  // ~$10-20/day depending on model
  }
};
```

**Status**: Rate limiting is planned but not yet implemented. Consider implementing before production deployment.

// Simple in-memory rate limiter (MVP)
class SimpleRateLimiter {
  constructor() {
    this.counts = new Map(); // key -> count
  }
  
  checkLimit(key, limit, windowMs) {
    const now = Date.now();
    const windowKey = `${key}_${Math.floor(now / windowMs)}`;
    
    const current = this.counts.get(windowKey) || 0;
    if (current >= limit) {
      return { allowed: false, remaining: 0 };
    }
    
    this.counts.set(windowKey, current + 1);
    return { allowed: true, remaining: limit - current - 1 };
  }
}

// Rate limit check
function checkRateLimit(user, assistant, team) {
  const limiter = new SimpleRateLimiter();
  
  // Check user limit
  const userCheck = limiter.checkLimit(
    `user_${user.id}`,
    rateLimits.perUser.messagesPerMinute,
    60 * 1000
  );
  if (!userCheck.allowed) {
    throw new Error("Rate limit exceeded: Too many messages per minute");
  }
  
  // Check assistant limit
  const assistantCheck = limiter.checkLimit(
    `assistant_${assistant.id}`,
    rateLimits.perAssistant.messagesPerMinute,
    60 * 1000
  );
  if (!assistantCheck.allowed) {
    throw new Error("Rate limit exceeded: Assistant is busy, please try again");
  }
  
  // Check team limit
  const teamCheck = limiter.checkLimit(
    `team_${team.id}`,
    rateLimits.perTeam.messagesPerDay,
    24 * 60 * 60 * 1000
  );
  if (!teamCheck.allowed) {
    throw new Error("Rate limit exceeded: Team daily limit reached");
  }
  
  return true;
}
```

---

## Data Retention

### **Retention Policy**

```javascript
// Conversation retention
const retentionPolicy = {
  defaultDays: 365,
  configurable: true,
  autoDelete: true,
  exportBeforeDelete: false  // MVP: users can export manually
};

// Scheduled job to delete old conversations
async function cleanupOldConversations() {
  const cutoffDate = new Date();
  cutoffDate.setDate(cutoffDate.getDate() - retentionPolicy.defaultDays);
  
  // Find conversations older than retention period
  const oldConversations = await db.conversations.find({
    startedAt: { $lt: cutoffDate },
    status: { $ne: "ARCHIVED" }  // Don't delete archived conversations
  });
  
  // Delete conversations and their messages
  for (const conversation of oldConversations) {
    await db.messages.deleteMany({ conversationId: conversation.id });
    await db.conversations.deleteOne({ id: conversation.id });
    
    // Log deletion
    auditLog({
      event: "CONVERSATION_DELETED",
      conversationId: conversation.id,
      reason: "retention_policy",
      deletedAt: new Date()
    });
  }
}

// Run cleanup job daily
cron.schedule("0 2 * * *", cleanupOldConversations); // 2 AM daily
```

---

## MVP API Endpoints

### **Assistant Management**

```
POST   /api/assistants                           # Create assistant
GET    /api/assistants                           # List team assistants
GET    /api/assistants/:id                       # Get assistant details
PUT    /api/assistants/:id                       # Update assistant
DELETE /api/assistants/:id                       # Delete assistant
PUT    /api/assistants/:id/access-control        # Update access control (private/public)
POST   /api/assistants/:id/generate-api-key      # Generate public API key for widget
```

### **Widget Deployment (Public Assistants)**

```
GET    /api/assistants/:id/widget-script         # Get embeddable widget script
GET    /api/assistants/:id/widget-config         # Get widget configuration (for customization)
PUT    /api/assistants/:id/widget-config         # Update widget configuration
GET    /widget/:publicApiKey                     # Widget iframe endpoint (for embedding)
POST   /widget/:publicApiKey/conversations       # Start conversation via widget (public)
```

### **Conversation Management**

```
POST   /api/assistants/:id/conversations         # Start conversation (private)
POST   /widget/:publicApiKey/conversations       # Start conversation (public/widget)
GET    /api/conversations                        # List user conversations
GET    /api/conversations/:id                    # Get conversation details
DELETE /api/conversations/:id                    # Delete conversation
GET    /api/conversations/:id/export             # Export conversation
```

### **Chat**

```
POST   /api/conversations/:id/messages           # Send message (private)
POST   /widget/conversations/:id/messages        # Send message (public/widget)
GET    /api/conversations/:id/messages           # Get messages (pagination)
WS     /ws/conversations/:id                     # WebSocket for streaming
WS     /ws/widget/:publicApiKey/conversations/:id # WebSocket for widget streaming
```

---

## Widget Deployment & Embedding

### **Widget Embedding Methods**

#### **Method 1: JavaScript Widget Script (Recommended)**

```html
<!-- Simple embed script -->
<script src="https://linqra.com/widget/assistant-123/script.js" async></script>

<!-- Or with configuration -->
<script>
  window.LinqraWidget = {
    assistantId: 'assistant-123',
    publicApiKey: 'pk_live_abc123...',
    theme: {
      primaryColor: '#007bff',
      position: 'bottom-right'
    }
  };
</script>
<script src="https://linqra.com/widget/script.js" async></script>
```

#### **Method 2: Iframe Embedding**

```html
<!-- Iframe embedding -->
<iframe 
  src="https://linqra.com/widget/pk_live_abc123"
  width="400"
  height="600"
  frameborder="0"
  style="position: fixed; bottom: 20px; right: 20px; z-index: 9999;">
</iframe>
```

### **Widget Script Implementation**

```javascript
// Widget script (public-facing JavaScript)
(function() {
  const config = window.LinqraWidget || {};
  const assistantId = config.assistantId || window.location.search.match(/assistant=([^&]+)/)?.[1];
  const publicApiKey = config.publicApiKey || window.location.search.match(/key=([^&]+)/)?.[1];
  
  // Create widget container
  const widgetContainer = document.createElement('div');
  widgetContainer.id = 'linqra-widget-container';
  widgetContainer.style.cssText = `
    position: fixed;
    bottom: 20px;
    right: 20px;
    width: 400px;
    height: 600px;
    z-index: 9999;
    display: none;
  `;
  
  // Create widget iframe
  const widgetIframe = document.createElement('iframe');
  widgetIframe.src = `https://linqra.com/widget/${publicApiKey}`;
  widgetIframe.style.cssText = 'width: 100%; height: 100%; border: none; border-radius: 8px;';
  widgetContainer.appendChild(widgetIframe);
  
  // Create toggle button
  const toggleButton = document.createElement('button');
  toggleButton.innerHTML = '💬';
  toggleButton.style.cssText = `
    position: fixed;
    bottom: 20px;
    right: 20px;
    width: 60px;
    height: 60px;
    border-radius: 50%;
    background-color: ${config.theme?.primaryColor || '#007bff'};
    color: white;
    border: none;
    cursor: pointer;
    z-index: 10000;
    box-shadow: 0 2px 10px rgba(0,0,0,0.2);
  `;
  
  toggleButton.onclick = () => {
    const isVisible = widgetContainer.style.display !== 'none';
    widgetContainer.style.display = isVisible ? 'none' : 'block';
    toggleButton.style.display = isVisible ? 'block' : 'none';
  };
  
  document.body.appendChild(toggleButton);
  document.body.appendChild(widgetContainer);
})();
```

### **Widget Security**

```javascript
// Widget authentication via public API key
function authenticateWidget(publicApiKey, domain) {
  // 1. Validate API key format
  if (!publicApiKey || !publicApiKey.startsWith('pk_live_') || !publicApiKey.startsWith('pk_test_')) {
    throw new Error('Invalid API key');
  }
  
  // 2. Lookup assistant by API key
  const assistant = await db.assistants.findOne({ 
    'accessControl.publicApiKey': publicApiKey,
    'accessControl.type': 'PUBLIC',
    'status': 'ACTIVE'
  });
  
  if (!assistant) {
    throw new Error('Assistant not found or not public');
  }
  
  // 3. Check domain whitelist (if configured)
  if (assistant.accessControl.allowedDomains && 
      assistant.accessControl.allowedDomains.length > 0) {
    const origin = new URL(domain).hostname;
    if (!assistant.accessControl.allowedDomains.includes(origin)) {
      throw new Error('Domain not allowed');
    }
  }
  
  // 4. Check CORS
  // (CORS headers should be set to allow widget embedding)
  
  return assistant;
}
```

### **Widget Rate Limiting**

```javascript
// Rate limiting for public widgets (more restrictive)
const widgetRateLimits = {
  perIP: {
    messagesPerMinute: 10,  // Lower than private
    messagesPerHour: 50,
    messagesPerDay: 200
  },
  perAssistant: {
    messagesPerMinute: 100,
    messagesPerHour: 1000,
    totalConversationsPerDay: 1000
  }
};

// IP-based rate limiting for widgets
function checkWidgetRateLimit(ipAddress, assistantId) {
  const limiter = new SimpleRateLimiter();
  
  // Hash IP for privacy
  const hashedIP = hashIP(ipAddress);
  
  // Check per-IP limit
  const ipCheck = limiter.checkLimit(
    `widget_ip_${hashedIP}`,
    widgetRateLimits.perIP.messagesPerMinute,
    60 * 1000
  );
  
  if (!ipCheck.allowed) {
    throw new Error('Rate limit exceeded. Please try again later.');
  }
  
  // Check per-assistant limit
  const assistantCheck = limiter.checkLimit(
    `widget_assistant_${assistantId}`,
    widgetRateLimits.perAssistant.messagesPerMinute,
    60 * 1000
  );
  
  if (!assistantCheck.allowed) {
    throw new Error('Assistant is busy. Please try again later.');
  }
  
  return true;
}
```

### **Widget CORS Configuration**

```javascript
// CORS headers for widget endpoints
app.use('/widget/*', (req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');  // Or specific domains
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.header('Access-Control-Allow-Credentials', 'false');
  
  if (req.method === 'OPTIONS') {
    res.sendStatus(200);
  } else {
    next();
  }
});
```

### **Public API Key Generation**

```javascript
// Generate public API key for widget access
async function generatePublicApiKey(assistantId, teamId) {
  // Validate assistant belongs to team
  const assistant = await db.assistants.findOne({
    id: assistantId,
    teamId: teamId
  });
  
  if (!assistant) {
    throw new Error('Assistant not found');
  }
  
  // Generate secure API key
  const apiKey = `pk_live_${generateSecureToken(32)}`;
  
  // Update assistant with API key
  await db.assistants.updateOne(
    { id: assistantId },
    {
      $set: {
        'accessControl.publicApiKey': apiKey,
        'accessControl.type': 'PUBLIC',
        'accessControl.allowAnonymousAccess': true,
        'widgetConfig.enabled': true,
        'widgetConfig.embedScriptUrl': `https://linqra.com/widget/${assistantId}/script.js`
      }
    }
  );
  
  return apiKey;
}
```

### **Widget Embedding Documentation**

```markdown
# Embedding AI Assistant Widget

## Quick Start

1. **Make your assistant public**:
   - Go to assistant settings
   - Change access control to "Public"
   - Generate public API key

2. **Copy the embed script**:
   ```html
   <script src="https://linqra.com/widget/YOUR_PUBLIC_API_KEY/script.js" async></script>
   ```

3. **Paste into your website**:
   - Add the script to your HTML
   - Widget will automatically appear

## Customization

Configure widget appearance:
```html
<script>
  window.LinqraWidget = {
    publicApiKey: 'pk_live_abc123...',
    theme: {
      primaryColor: '#007bff',
      position: 'bottom-right'
    }
  };
</script>
```

## Security

- Use domain whitelist for production
- Keep API keys secure (don't commit to public repos)
- Monitor usage and rate limits
```

---

## Implementation Phases

### **Phase 1: Foundation (Weeks 1-2)**
- [x] Database schema (Assistant, Conversation, Message entities) - **Implemented**
- [x] Basic CRUD APIs for assistants - **Implemented**
- [x] Access control implementation - **Implemented (Private/Public)**
- [x] Basic chat UI (simple message input/output) - **Implemented with streaming**

### **Phase 2: Core Functionality (Weeks 3-4)**
- [x] Task routing and execution (parallel execution of all selected tasks)
- [x] Response synthesis
- [x] Conversation history storage
- [x] WebSocket streaming for real-time responses
- [x] Cancel generation functionality
- [ ] Intent classification (LLM-based) - **TODO: Planned for Phase 2 enhancement**
- [ ] Intelligent task selection based on intent - **TODO: Planned for Phase 2 enhancement**

### **Phase 3: Context & Guardrails (Weeks 5-6)**
- [x] Context management (sliding window) - **Implemented: Simple sliding window with configurable maxRecentMessages**
- [x] Failure handling with fallback - **Implemented: Graceful handling of task failures**
- [ ] PII detection - **TODO: Not yet implemented**
- [ ] Audit logging - **TODO: Not yet implemented**

### **Phase 4: Polish & Testing (Weeks 7-8)**
- [x] UI/UX improvements - **Implemented: Conversation history sidebar, streaming UI, cancel button**
- [ ] Rate limiting - **TODO: Not yet implemented - Critical before production**
- [ ] Data retention job - **TODO: Not yet implemented**
- [ ] Export functionality - **TODO: Not yet implemented**
- [ ] Testing and bug fixes - **Ongoing**

---

## Future Enhancements (Post-MVP)

1. **Multi-task execution** - Sequential, parallel, conditional
2. **Advanced context management** - Summarization, relevance retrieval
3. **File uploads** - Document analysis
4. **Assistant Marketplace** - Pre-configured assistant templates and sharing
5. **Collaboration** - Multi-user conversations (shared conversations)
6. **Advanced analytics** - Usage dashboards, insights, widget analytics
7. **Autonomous agents** - Dynamic task creation
8. **Advanced widget features** - Custom domains, white-label options, advanced customization
9. **Widget analytics** - Track widget usage, conversion rates, popular queries

---

## Key Constraints & Assumptions

1. **All selected tasks execute in parallel** - MVP executes ALL enabled selected tasks (not single task per query)
2. **No intent-based routing** - MVP limitation, intelligent routing in Phase 2
3. **No task creation** - Only execute existing tasks ✅
4. **Team-private or public** - Both supported ✅ (Public assistants with widget embedding available)
5. **No rate limiting** - Not yet implemented ⚠️ **TODO: Critical before production**
6. **1-year retention** - Planned but retention job not yet implemented
7. **No file uploads** - Text-only conversations ✅
8. **No PII detection** - Not yet implemented ⚠️ **TODO**
9. **No audit logging** - Not yet implemented ⚠️ **TODO**
10. **WebSocket streaming** - ✅ Implemented (ChatGPT-like word-by-word streaming)
11. **Cancel functionality** - ✅ Implemented (can cancel generation mid-stream)

---

## Success Metrics

1. **Functional Metrics**
   - Assistant creation and configuration works
   - Task routing accuracy > 80%
   - Response time < 5 seconds (p95)
   - Task execution success rate > 90%

2. **User Experience Metrics**
   - User satisfaction (surveys)
   - Conversation completion rate
   - Average messages per conversation
   - Error rate < 5%

3. **Technical Metrics**
   - API response time < 500ms (p95)
   - Database query performance
   - Token usage per conversation
   - Cost per conversation

