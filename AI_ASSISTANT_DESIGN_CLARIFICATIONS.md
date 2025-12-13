# AI Assistant Design Clarifications

## Overview
This document clarifies key design decisions around:
1. Agent Task response handling
2. Multi-agent task orchestration
3. AI Assistant default model assignment
4. Chat history context management

---

## 1. Agent Task Response → AI Assistant Usage

### **The Flow**

```
User Query
    ↓
AI Assistant (Default Model)
    ├── **MVP**: Execute ALL selected Agent Tasks in parallel
    ├── **Future Phase 2**: Intent Classification + Intelligent Task Selection
    └── **MVP**: Pass user query as parameter to each task
    ↓
Agent Task Execution (Task's Own Model) - Parallel Execution
    ├── Task 1: Executes its workflow (e.g., Milvus search + OpenAI)
    ├── Task 2: Executes its workflow (if multiple tasks selected)
    └── Task N: Executes its workflow
    └── Returns: Map<taskId, result> with all task results
    ↓
AI Assistant (Default Model) - Response Synthesis
    ├── Takes aggregated Agent Task results (all tasks)
    ├── Incorporates conversation context
    ├── Generates natural language response
    ├── **New**: Streams response word-by-word via WebSocket
    ├── **New**: Allows user to cancel generation mid-stream
    └── Returns: "Based on the information available, Form I-485 is..."
```

**MVP Implementation Note**: 
- Currently executes ALL enabled selected tasks in parallel
- No intent classification to select specific tasks (planned for Phase 2)
- User query is passed directly as a parameter to each task
- All task results are aggregated and sent to the LLM for synthesis

### **Key Points**

1. **MVP Implementation (Current)**:
   - **Stage 1**: AI Assistant executes ALL selected Agent Tasks in parallel
   - **Stage 2**: Each Agent Task executes using its own model/workflow (parallel execution)
   - **Stage 3**: AI Assistant (default model) synthesizes ALL task results into natural response
   - **Stage 4**: Response is streamed word-by-word via WebSocket (ChatGPT-like experience)
   - **Stage 5**: User can cancel generation mid-stream

2. **Future Phase 2 Enhancement** (Not Yet Implemented):
   - **Stage 1**: AI Assistant (default model) analyzes user query and intelligently routes to specific Agent Tasks
   - **Stage 2**: Only selected Agent Tasks execute (not all tasks)
   - **Stage 3**: AI Assistant synthesizes results from selected tasks

2. **Agent Task Result Format**:
   - Agent Tasks return structured results (JSON)
   - Results can include:
     - `finalResult`: The main answer/text
     - `steps`: Array of workflow step results
     - `metadata`: Execution metadata (tokens, duration, etc.)
   - AI Assistant needs to extract relevant information from this structure

3. **Result Processing**:
   ```javascript
   // Example: Agent Task Result
   {
     "finalResult": "Form I-485 is an application to register permanent residence...",
     "steps": [
       { "step": 1, "result": {...} },  // Milvus search results
       { "step": 2, "result": {...} }   // OpenAI response
     ],
     "metadata": { "status": "SUCCESS", "durationMs": 2500 }
   }
   
   // AI Assistant synthesizes:
   // "Based on the latest USCIS documentation, Form I-485 is an application 
   //  to register permanent residence or adjust status. This form is used 
   //  for individuals who are already in the United States..."
   ```

---

## 2. Multi-Agent Task Handling

### **Scenario: User Query Requires Multiple Tasks**

```
User: "Check my eligibility for Form I-485 and tell me what documents I need"

AI Assistant Analysis:
    ├── Intent 1: Eligibility check → "Eligibility Assessment" Task
    └── Intent 2: Document requirements → "Document Checklist" Task

Execution Strategy Options:
    A. Sequential (if tasks depend on each other)
    B. Parallel (if tasks are independent)
    C. Conditional (if Task 2 depends on Task 1 result)
```

### **Execution Patterns**

#### **A. Sequential Execution**
```
Task 1: Eligibility Assessment
    ↓ (if eligible)
Task 2: Document Checklist
    ↓
Aggregate Results
    ↓
Synthesize Response
```

#### **B. Parallel Execution**
```
Task 1: Eligibility Assessment ┐
Task 2: Document Checklist      ├──→ Aggregate Results
Task 3: Fee Calculator          ┘
    ↓
Synthesize Combined Response
```

#### **C. Conditional Execution**
```
Task 1: Eligibility Assessment
    ↓
Decision Point:
    ├── If eligible → Task 2: Document Checklist
    └── If not eligible → Skip Task 2, explain why
    ↓
Synthesize Response
```

### **Result Aggregation Strategy**

```javascript
// Example: Multiple Task Results
const taskResults = [
  {
    taskId: "eligibility-task",
    taskName: "Eligibility Assessment",
    result: { "eligible": true, "reason": "Meets marriage-based criteria" },
    status: "SUCCESS"
  },
  {
    taskId: "documents-task",
    taskName: "Document Checklist",
    result: { "documents": ["Form I-485", "Form I-864", "Birth certificate"] },
    status: "SUCCESS"
  }
];

// AI Assistant synthesizes:
// "Good news! Based on your profile, you appear eligible for a marriage-based 
//  green card through Form I-485. Here are the documents you'll need:
//  1. Form I-485 (Application to Register Permanent Residence)
//  2. Form I-864 (Affidavit of Support)
//  3. Birth certificate (certified copy)
//  ..."
```

### **Partial Failure Handling**

```javascript
// Example: One task succeeds, one fails
const taskResults = [
  { taskId: "task-1", status: "SUCCESS", result: {...} },
  { taskId: "task-2", status: "FAILED", error: "Timeout" }
];

// AI Assistant response:
// "I was able to check your eligibility (you appear eligible), but I 
//  encountered an issue retrieving your document checklist. Here's what 
//  I found about eligibility: [...]. For document requirements, please 
//  try asking again or consult the USCIS website."
```

---

## 3. AI Assistant Default Model

### **Why a Default Model is Essential**

1. **Intent Classification**: Need a model to understand user queries and route to tasks
2. **Response Synthesis**: Need a model to convert task results into natural language
3. **Multi-Turn Conversations**: Need a model to maintain context across messages
4. **Query Enhancement**: Need a model to extract parameters from user queries

### **Model Configuration**

```javascript
// AI Assistant Configuration
{
  "id": "assistant-123",
  "name": "USCIS Immigration Assistant",
  "defaultModel": {
    "provider": "openai",
    "modelName": "gpt-4o",
    "modelCategory": "openai-chat",
    "settings": {
      "temperature": 0.7,      // More creative for synthesis
      "max_tokens": 2000,      // Sufficient for long responses
      "top_p": 0.9
    }
  },
  "selectedTasks": [
    { "taskId": "task-1", "priority": 1, "executionType": "REQUIRED" },
    { "taskId": "task-2", "priority": 2, "executionType": "OPTIONAL" }
  ]
}
```

### **Model Usage Points**

1. **Query Understanding** (Default Model):
   ```
   User: "What do I need for I-485?"
   
   Prompt to Default Model:
   "Analyze this user query and extract:
    - Intent: document_requirements
    - Entities: form=I-485
    - Parameters: question='What documents are needed for Form I-485?'
    - Relevant Tasks: [document-checklist-task]"
   ```

2. **Response Synthesis** (Default Model):
   ```
   Task Results: { "documents": [...] }
   
   Prompt to Default Model:
   "Based on the following Agent Task results, generate a natural, 
    conversational response that:
    - Answers the user's question directly
    - Incorporates the task results naturally
    - Maintains conversation context (previous messages)
    - Provides actionable next steps
    
    Task Results:
    {taskResults}
    
    Conversation History:
    {chatHistory}
    
    User's Question:
    {userQuery}"
   ```

### **Model Separation**

- **AI Assistant Model**: Orchestrates conversation, routes queries, synthesizes responses
- **Agent Task Models**: Execute specific workflows (e.g., RAG search + OpenAI chat)
- **Benefits**: 
  - Each model optimized for its role
  - Flexibility (can use different models for different assistants)
  - Cost optimization (can use cheaper model for routing, expensive model for synthesis)

---

## 4. Chat History Context Management

### **The Challenge**

Sending full chat history to the model can:
- Exceed token limits (especially for long conversations)
- Increase costs (more tokens = higher cost)
- Slow down responses (more context to process)
- Include irrelevant information (old context may not be relevant)

### **Solution: Intelligent Context Management**

#### **A. Sliding Window Approach**
```
Last N Messages Strategy:
- Keep only the most recent N messages in context
- Example: Last 10 messages (or last 50 messages, configurable)
- Older messages are dropped from context
```

**Configuration:**
```javascript
{
  "contextWindow": {
    "strategy": "sliding_window",
    "maxMessages": 20,           // Last 20 messages
    "maxTokens": 4000,           // Or limit by tokens
    "includeSystemMessages": true
  }
}
```

#### **B. Summarization Approach**
```
Old History Summary Strategy:
- Keep recent messages (e.g., last 5-10) in full
- Summarize older messages into a concise summary
- Include summary + recent messages in context
```

**Implementation:**
```javascript
// Example Context Structure
{
  "recentMessages": [
    { "role": "user", "content": "What is Form I-485?" },
    { "role": "assistant", "content": "Form I-485 is..." },
    { "role": "user", "content": "What documents do I need?" }
  ],
  "summary": "User is asking about Form I-485 immigration form. 
              Previously discussed eligibility requirements. User 
              is preparing a marriage-based green card application."
}
```

#### **C. Relevance-Based Retrieval**
```
Smart Context Selection:
- Analyze current user query
- Retrieve only relevant past messages based on semantic similarity
- Include task execution results from relevant past interactions
```

**Implementation:**
```javascript
// Example: Retrieve relevant history using embedding similarity
const relevantHistory = await retrieveRelevantHistory(
  currentQuery,
  conversationHistory,
  maxMessages: 10
);

// Only send relevant messages to model
```

#### **D. Hybrid Approach (Recommended)**
```
Combination Strategy:
1. Always include last N messages (e.g., last 5)
2. Summarize messages beyond that window
3. Include summary + recent messages
4. Optional: Retrieve semantically relevant older messages if needed
```

### **Token Budget Management**

```javascript
// Token Budget Allocation
{
  "totalBudget": 8000,              // Model's context window
  "reservedForTaskResults": 2000,   // Space for task execution results
  "reservedForSystemPrompt": 500,   // System prompt + instructions
  "availableForHistory": 5500,      // Remaining for chat history
  
  "historyStrategy": {
    "recentMessages": 10,           // Always include last 10 messages
    "summaryTokens": 500,           // Summary of older messages
    "relevanceRetrieval": 5         // Optional: relevant older messages
  }
}
```

### **Implementation Example**

```javascript
// Pseudocode for Context Building
function buildContext(conversation, currentQuery) {
  const totalBudget = 8000;
  const reservedTokens = 2500; // System + task results
  const availableTokens = totalBudget - reservedTokens;
  
  // 1. Always include recent messages (last 10)
  const recentMessages = conversation.messages.slice(-10);
  let contextTokens = estimateTokens(recentMessages);
  
  // 2. If room available, add summary of older messages
  if (contextTokens < availableTokens - 500) {
    const summary = await summarizeOlderMessages(
      conversation.messages.slice(0, -10)
    );
    contextTokens += estimateTokens(summary);
  }
  
  // 3. If still room, retrieve relevant older messages
  if (contextTokens < availableTokens - 200) {
    const relevantMessages = await retrieveRelevantMessages(
      currentQuery,
      conversation.messages.slice(0, -10),
      limit: 5
    );
    contextTokens += estimateTokens(relevantMessages);
  }
  
  return {
    recentMessages,
    summary,
    relevantMessages
  };
}
```

### **Configuration Options**

```javascript
// AI Assistant Context Configuration
{
  "assistantId": "assistant-123",
  "contextManagement": {
    "strategy": "hybrid",              // sliding_window | summarization | relevance | hybrid
    "maxRecentMessages": 10,           // Always include last N messages
    "maxTotalTokens": 4000,            // Max tokens for history
    "summarizationEnabled": true,      // Summarize old messages
    "relevanceRetrievalEnabled": true, // Retrieve relevant old messages
    "summaryMaxTokens": 500,           // Max tokens for summary
    "relevanceMaxMessages": 5          // Max relevant messages to retrieve
  }
}
```

### **When to Use Each Strategy**

1. **Sliding Window**: 
   - Simple conversations
   - Cost-sensitive scenarios
   - Fast response requirements

2. **Summarization**:
   - Long conversations
   - Need to maintain key context
   - Moderate cost sensitivity

3. **Relevance Retrieval**:
   - Complex conversations
   - Need specific historical context
   - Can afford embedding similarity search

4. **Hybrid** (Recommended):
   - Production-ready
   - Balances context quality with cost
   - Handles most scenarios well

---

## Complete Flow Example

### **User: "What documents do I need for I-485?"**

```
1. Context Building (AI Assistant Default Model)
   ├── Retrieve recent messages: Last 3 messages
   ├── Check if summary needed: No (conversation is short)
   └── Build context: Recent 3 messages only
   
2. Intent Classification (AI Assistant Default Model)
   ├── Prompt: "Analyze query with context: {recentMessages}"
   ├── Extract: Intent = "document_requirements", Entity = "I-485"
   └── Select Task: "USCIS Document Checklist" task
   
3. Task Execution (Agent Task's Model)
   ├── Execute: Document Checklist task
   ├── Extract: question = "What documents are needed for Form I-485?"
   └── Result: { "documents": ["Form I-485", "Form I-864", ...] }
   
4. Response Synthesis (AI Assistant Default Model)
   ├── Prompt: 
   │   "Generate response using:
   │    - Task results: {taskResults}
   │    - Conversation context: {recentMessages}
   │    - User query: {userQuery}"
   ├── Generate: Natural language response
   └── Return: "Based on the USCIS requirements, you'll need..."
   
5. Context Update
   ├── Save user message
   ├── Save assistant response
   └── Update conversation context cache
```

---

## Design Decisions Summary

### ✅ **Confirmed Design Choices**

1. **AI Assistant has a default model** - Used for intent classification and response synthesis
2. **Agent Tasks have their own models** - Execute specific workflows independently
3. **Chat history is managed intelligently** - Hybrid approach with sliding window + summarization
4. **Token budget is managed** - Reserved space for system prompts, task results, and history
5. **Multi-task execution is supported** - Sequential, parallel, and conditional patterns
6. **Partial failures are handled gracefully** - Assistant explains what worked and what didn't

### 📋 **Configuration Template**

```javascript
{
  "assistant": {
    "name": "USCIS Immigration Assistant",
    "defaultModel": {
      "provider": "openai",
      "modelName": "gpt-4o",
      "modelCategory": "openai-chat",
      "settings": { "temperature": 0.7, "max_tokens": 2000 }
    },
    "contextManagement": {
      "strategy": "hybrid",
      "maxRecentMessages": 10,
      "maxTotalTokens": 4000,
      "summarizationEnabled": true
    },
    "selectedTasks": [
      {
        "taskId": "task-1",
        "executionType": "REQUIRED",
        "priority": 1
      }
    ]
  }
}
```

---

## Implementation Status

### ✅ **Implemented Features**

1. **Multi-Task Parallel Execution**
   - All enabled selected tasks execute in parallel
   - Results aggregated and passed to response synthesis
   - Partial failures handled gracefully

2. **WebSocket Streaming**
   - Real-time word-by-word streaming via WebSocket
   - ChatGPT-like user experience
   - Cancel generation functionality

3. **Conversation History**
   - Conversation history sidebar in UI
   - Load previous conversations
   - Start new conversation
   - Sliding window context management (configurable maxRecentMessages)

4. **Token Usage & Cost Tracking**
   - Token usage extracted from LLM responses (OpenAI, Gemini, Claude, Cohere)
   - Cost calculation using LlmCostService
   - Stored in conversation metadata

5. **Response Synthesis**
   - Natural language response generation
   - Incorporates task results and conversation context
   - System prompt customization

### ❌ **Not Yet Implemented (TODO)**

1. **Intent Classification**
   - Currently returns hardcoded "user_query"
   - Intent-based task selection planned for Phase 2

2. **PII Detection**
   - Pattern-based detection not implemented
   - Audit logging not implemented

3. **Rate Limiting**
   - No rate limiting currently enforced
   - **Critical**: Should be implemented before production

4. **Data Retention**
   - Retention job not implemented
   - Conversations not automatically deleted

5. **Export Functionality**
   - Conversation export not implemented

## Open Questions

1. **Should summaries be generated on-the-fly or pre-computed?**
   - On-the-fly: More accurate but slower
   - Pre-computed: Faster but may miss recent context changes
   - **Status**: Not yet implemented - context uses simple sliding window

2. **How to handle task execution timeouts in multi-task scenarios?**
   - **Current**: Wait for all tasks, continue with successful results if some fail
   - **Future**: Configurable per assistant?

3. **Should there be a maximum conversation length?**
   - **Current**: No hard limit, uses sliding window
   - **Future**: Better summarization for very long conversations?

4. **How to handle sensitive information in chat history?**
   - **Status**: PII detection not yet implemented
   - **Future**: PII detection and redaction?
   - **Future**: Encryption at rest?
   - **Future**: Retention policies?

