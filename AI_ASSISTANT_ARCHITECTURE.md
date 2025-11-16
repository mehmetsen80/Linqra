# AI Assistant Architecture - High-Level Design

## Overview
AI Assistants provide a conversational interface that intelligently routes user queries to relevant Agent Tasks and synthesizes responses. They act as an orchestration layer between users and the agent task execution system.

---

## Core Concepts

### 1. **AI Assistant**
- **Definition**: A team-owned conversational AI that can intelligently execute Agent Tasks based on user queries
- **Ownership**: Each assistant belongs to a team
- **Configuration**: 
  - Name, description, avatar/icon
  - Personality/system prompt
  - Selected Agent Tasks (which tasks it can execute)
  - Routing/intent matching rules
  - Response synthesis preferences

### 2. **Agent Task Selection**
- AI Assistant can be configured with multiple Agent Tasks
- Tasks can be:
  - **Required** (always executed when certain conditions are met)
  - **Optional** (suggested/executed based on query analysis)
  - **Sequential** (tasks execute in a specific order)
  - **Parallel** (multiple tasks execute simultaneously)
  - **Conditional** (tasks execute based on previous task results)

### 3. **Conversation Flow**
- User sends a message
- AI Assistant analyzes the query (intent, entities, context)
- Determines which Agent Task(s) to execute
- Executes task(s) and waits for results
- Synthesizes a natural language response
- Presents response to user with optional follow-up actions

---

## High-Level Architecture

### **Layer 1: AI Assistant Management**
```
Team
 └── AI Assistants (1:N)
      ├── Assistant Metadata
      │   ├── Name, Description, Avatar
      │   ├── System Prompt/Personality
      │   └── Status (Active/Inactive/Draft)
      │
      └── Configuration
          ├── Selected Agent Tasks (M:N relationship)
          ├── Routing Rules
          ├── Response Templates
          └── Settings (temperature, max tokens, etc.)
```

### **Layer 2: Conversation & Routing**
```
User Query
    ↓
MVP: Execute ALL Selected Tasks in Parallel
Future Phase 2: Intent Analysis (LLM-based classification)
    ↓
MVP: All Selected Tasks Execute
Future Phase 2: Agent Task Selection (match intents to tasks)
    ↓
Task Execution (MVP: parallel only; Future: sequential/parallel/conditional)
    ↓
Result Aggregation (all task results combined)
    ↓
Response Synthesis (LLM generates natural language)
    ↓
WebSocket Streaming (word-by-word, ChatGPT-like)
    ↓
User Response (with cancel option)
```

**Current MVP Status**:
- ✅ Parallel task execution (all selected tasks)
- ✅ Result aggregation
- ✅ Response synthesis
- ✅ WebSocket streaming
- ✅ Cancel functionality
- ❌ Intent-based task selection (planned for Phase 2)
- ❌ Sequential/conditional execution (planned for Phase 2)

### **Layer 3: Execution & Orchestration**
```
AI Assistant Chat Request
    ↓
Query Understanding Layer
    │
    ├── Extract Intent
    ├── Extract Entities/Parameters
    ├── Determine Context (conversation history)
    └── Select Relevant Agent Tasks
         ↓
Agent Task Execution Layer
    │
    ├── Execute Task 1 (with extracted params)
    ├── Execute Task 2 (if needed)
    └── Execute Task N (sequential or parallel)
         ↓
Result Processing Layer
    │
    ├── Aggregate Results
    ├── Handle Errors/Partial Failures
    └── Format for Response Synthesis
         ↓
Response Generation Layer
    │
    ├── Synthesize Natural Language
    ├── Include Task Results
    ├── Add Follow-up Suggestions
    └── Maintain Conversation Context
```

---

## Key Components

### **1. AI Assistant Entity**
- Core metadata (name, description, team association)
- Configuration (selected tasks, routing rules, system prompt)
- State management (active/inactive, conversation limits)

### **2. Conversation Manager**
- Manages conversation sessions/threads
- Maintains context history
- Handles multi-turn conversations
- Tracks conversation metadata (start time, message count, etc.)

### **3. Intent Router** ⚠️ **Phase 2 Enhancement (Not Yet Implemented)**
- **MVP**: Not implemented - all selected tasks execute in parallel
- **Planned**: Analyzes user queries using LLM
- **Planned**: Maps intents to Agent Tasks
- **Planned**: Handles ambiguous queries (asks for clarification)
- **Planned**: Supports intent prioritization and conflict resolution

**Current MVP Behavior**: Executes ALL enabled selected tasks in parallel for every query.

### **4. Task Orchestrator**
- ✅ Executes selected Agent Tasks (MVP: ALL selected tasks in parallel)
- ✅ Parallel execution (MVP: all tasks execute simultaneously)
- ❌ Sequential execution (planned for Phase 2)
- ❌ Conditional execution (planned for Phase 2: if Task A succeeds, run Task B)
- ✅ Aggregates results from multiple tasks
- ✅ Handles errors gracefully (partial failures - successful tasks continue, failed tasks logged)

**MVP Implementation**: All enabled selected tasks execute in parallel. Results are aggregated regardless of individual task success/failure.

### **5. Response Synthesizer**
- ✅ Takes task execution results (aggregated from all tasks)
- ✅ Generates natural language responses using LLM
- ✅ Incorporates conversation context (sliding window approach)
- ✅ Provides follow-up suggestions (via system prompt)
- ✅ Formats structured data (tables, lists, code) appropriately (via Markdown)
- ✅ **New**: Streams responses word-by-word via WebSocket
- ✅ **New**: Supports cancellation during generation

### **6. Conversation History**
- ✅ Stores chat messages (user queries, assistant responses)
- ✅ Maintains conversation context for multi-turn interactions (sliding window)
- ✅ Enables conversation continuity (context window management)
- ✅ **New**: Conversation history sidebar in UI (list of past conversations)
- ✅ **New**: Click to load previous conversations
- ✅ **New**: Start new conversation functionality
- ❌ Conversation export/analytics (planned for Phase 4)

---

## Workflow: User Interaction

### **Scenario 1: Simple Query → Single Task**
```
User: "What documents do I need for Form I-485?"
    ↓
Intent Router: Matches to "USCIS Marriage-Based Q&A" Agent Task
    ↓
Task Orchestrator: Executes the task with question parameter
    ↓
Response Synthesizer: Formats task result into natural response
    ↓
Response: "Based on your marriage-based green card application, 
          you'll need the following documents for Form I-485..."
```

### **Scenario 2: Complex Query → Multiple Tasks**
```
User: "Check my eligibility and tell me what documents I need"
    ↓
Intent Router: 
    - Intent 1: Eligibility check → "Eligibility Assessment" Task
    - Intent 2: Document requirements → "Document Checklist" Task
    ↓
Task Orchestrator: 
    - Execute Task 1 first (eligibility)
    - If eligible, execute Task 2 (documents)
    - If not eligible, skip Task 2
    ↓
Response Synthesizer: 
    - Combines results from both tasks
    - Creates cohesive narrative response
    ↓
Response: "I've checked your eligibility status. You appear to 
          meet the requirements for a marriage-based green card. 
          Here are the documents you'll need..."
```

### **Scenario 3: Ambiguous Query → Clarification**
```
User: "Tell me about forms"
    ↓
Intent Router: Detects ambiguity (which forms? which context?)
    ↓
Response Synthesizer: Generates clarification question
    ↓
Response: "I can help you with several types of forms. Are you 
          asking about:
          - USCIS immigration forms?
          - Employment verification forms?
          - Tax-related forms?
          
          Which one would you like information about?"
```

---

## Data Model (High-Level)

### **AI Assistant**
- `id`, `name`, `description`, `teamId`
- `systemPrompt` (personality/instructions)
- `status` (ACTIVE, INACTIVE, DRAFT)
- `configuration` (JSON: task selections, routing rules, settings)
- `createdAt`, `updatedAt`, `createdBy`

### **Assistant Agent Task (Junction Table)**
- `assistantId`, `agentTaskId`
- `priority` (order of execution)
- `executionType` (REQUIRED, OPTIONAL, CONDITIONAL)
- `conditions` (JSON: when to execute)
- `paramsMapping` (JSON: how to map user query to task params)

### **Conversation**
- `id`, `assistantId`, `teamId`, `userId`
- `title` (auto-generated from first message)
- `status` (ACTIVE, ARCHIVED, DELETED)
- `startedAt`, `lastMessageAt`
- `messageCount`

### **Conversation Message**
- `id`, `conversationId`
- `role` (USER, ASSISTANT, SYSTEM)
- `content` (message text)
- `metadata` (JSON: task executions, intents detected, etc.)
- `timestamp`
- `sequenceNumber`

### **Conversation Context**
- `conversationId`
- `contextData` (JSON: extracted entities, intent history, task results cache)
- `updatedAt`

---

## Routing Strategies

### **1. Intent-Based Routing**
- Uses LLM to classify user intent
- Maps intents to Agent Task capabilities
- Supports fuzzy matching and confidence scoring

### **2. Keyword-Based Routing**
- Configurable keyword matching
- Fast and deterministic
- Good for simple, well-defined queries

### **3. Hybrid Routing**
- Combines intent analysis with keyword matching
- Fallback mechanisms
- Confidence thresholds for routing decisions

### **4. Learning-Based Routing**
- Analyzes past conversation success rates
- Adjusts routing based on user feedback
- Improves over time

---

## Response Generation Strategies

### **1. Direct Task Result**
- Simple pass-through of task execution result
- Minimal processing
- Best for single, straightforward queries

### **2. Synthesized Response**
- LLM processes task results
- Generates natural language narrative
- Incorporates conversation context
- Provides human-like explanations

### **3. Multi-Task Aggregation**
- Combines results from multiple tasks
- Creates cohesive narrative
- Handles contradictions or gaps
- Prioritizes most relevant information

### **4. Interactive Response**
- Provides follow-up questions
- Suggests related actions
- Offers clarifications
- Enables deeper exploration

---

## Advanced Features (Future Considerations)

### **1. Multi-Turn Conversations**
- Maintains context across multiple messages
- References previous task executions
- Handles follow-up questions naturally

### **2. Task Chaining**
- Automatically triggers related tasks
- Creates workflows dynamically
- Handles dependencies between tasks

### **3. Result Caching**
- Caches recent task execution results
- Reuses results for similar queries
- Reduces redundant task executions

### **4. Personalization**
- Learns user preferences
- Adapts responses to user's communication style
- Remembers user-specific context (past applications, documents uploaded)

### **5. Proactive Assistance**
- Suggests relevant information before user asks
- Alerts users to missing information
- Provides timely reminders (deadlines, document requirements)

### **6. Collaboration**
- Multiple users in same conversation
- Shared context across team members
- Conversation threads and mentions

### **7. Analytics & Insights**
- Track assistant usage and performance
- Analyze routing accuracy
- Monitor task execution success rates
- User satisfaction metrics

---

## Integration Points

### **Existing Systems**
- **Agents & Agent Tasks**: Source of executable capabilities
- **Knowledge Hub**: Can be queried for context
- **Workflow Execution**: Underlying execution engine
- **Team Management**: Access control and permissions

### **New Systems**
- ✅ **Conversation Storage**: MongoDB for conversations and messages (implemented)
- ✅ **Response Synthesis Service**: LLM-based response generation (implemented)
- ✅ **WebSocket**: Real-time message streaming (implemented with STOMP protocol)
- ✅ **Streaming Service**: Word-by-word streaming with cancellation support (implemented)
- ❌ **Intent Classification Service**: LLM-based intent analysis (planned for Phase 2)

---

## Security & Access Control

### **Team Isolation**
- AI Assistants are team-scoped
- Users can only access assistants in their teams
- Conversation data isolated by team

### **Permission Levels**
- **View**: Can chat with assistant
- **Manage**: Can configure assistant (select tasks, edit prompts)
- **Admin**: Can create/delete assistants

### **Data Privacy**
- Conversation history stored securely
- Compliance with data retention policies
- Export/deletion capabilities

---

## Performance Considerations

### **Response Time Targets**
- Simple queries: < 2 seconds
- Complex queries (multiple tasks): < 10 seconds
- Real-time streaming: Start streaming within 1 second

### **Scaling**
- Stateless conversation processing (allows horizontal scaling)
- Async task execution (don't block chat interface)
- Result streaming for long-running tasks
- Caching frequently asked questions

### **Cost Optimization**
- Intent classification caching
- Batch similar queries
- Limit token usage in synthesis
- Use cheaper models for simple routing decisions

---

### **Chat Interface**
- ✅ Clean, modern chat UI (similar to ChatGPT/Claude)
- ✅ Message threading and context
- ✅ Markdown rendering for formatted responses (react-markdown)
- ✅ Code blocks, tables, lists support
- ✅ **New**: Real-time word-by-word streaming (ChatGPT-like)
- ✅ **New**: Cancel generation button during streaming
- ✅ **New**: Conversation history sidebar
- ✅ **New**: Loading indicators for task execution
- ❌ File attachments (planned for future)

### **Assistant Selection**
- Team can have multiple assistants
- Each assistant has distinct personality/capabilities
- Users switch between assistants seamlessly
- Assistant directory/explorer

### **Task Execution Feedback**
- ✅ Show when tasks are executing (WebSocket updates: AGENT_TASKS_EXECUTING)
- ✅ Real-time updates via WebSocket (AGENT_TASKS_COMPLETED, AGENT_TASKS_FAILED)
- ✅ Error handling with clear messages (task failures logged and included in response)
- ✅ Progress indicators for streaming responses
- ✅ Token usage and cost tracking displayed in metadata
- ❌ Ability to retry failed tasks (planned for future enhancement)

---

## Migration & Rollout Strategy

### **Phase 1: MVP**
- Basic assistant creation and configuration
- Simple intent routing (keyword-based)
- Single task execution per query
- Direct task result passthrough

### **Phase 2: Enhanced Routing**
- LLM-based intent classification
- Multi-task execution support
- Response synthesis
- Conversation history

### **Phase 3: Advanced Features**
- Multi-turn conversations
- Task chaining
- Personalization
- Analytics dashboard

### **Phase 4: Enterprise Features**
- Collaboration
- Proactive assistance
- Advanced analytics
- API access

---

## Design Decisions

### 1. **Assistant Access Control**
- **Decision**: Assistant access control is **configurable** (Private or Public)
- **Private Assistants**:
  - Default setting for new assistants
  - Only accessible to team users and Super Admin
  - Requires authentication (team membership)
  - All conversations tied to authenticated users
- **Public Assistants**:
  - Can be configured as public by team admins
  - Accessible without authentication (anonymous access)
  - Can be embedded as widgets on external websites
  - Widget deployment with embeddable script/iframe
  - Requires public API key or token for widget access
  - Rate limiting applies (per IP or per widget instance)
  - Conversations tracked anonymously (no user association)
  - Optional: Guest user identification for conversation continuity
- **Widget Deployment**:
  - Embeddable JavaScript widget for any website
  - Iframe-based embedding option
  - Customizable appearance (theme, colors, size)
  - CORS configuration for cross-origin embedding
  - Widget authentication via public API key
  - Optional: Domain whitelist for security

### 2. **Task Execution Failures**
- **Decision**: Assistants need **graceful fallback** when Agent Tasks fail
- **Strategy**:
  - Use existing Agent Task failure mechanisms (retry, timeout, error handling)
  - If task fails, AI Assistant should:
    - Explain the failure to the user
    - Suggest alternative actions (retry, try different query, etc.)
    - Provide partial results if available
    - Fallback to general knowledge response if task execution is critical
- **Future**: More sophisticated retry strategies and fallback chains

### 3. **Task Creation/Modification**
- **Decision**: Assistants **only execute existing Agent Tasks** (no dynamic creation)
- **Rationale**: Keep MVP simple, focus on orchestration rather than autonomous task creation
- **Future**: Autonomous agents that can create tasks on-the-fly will be implemented later

### 4. **Conversation Retention Policy**
- **Decision**: Conversations retained for **1 year** by default
- **Implementation**: Configurable per team via configuration settings
- **Storage**: Automatic archival/deletion after retention period
- **Export**: Users can export conversations before deletion

### 5. **Rate Limiting Strategy**
- **Decision**: **Different from API rate limiting** - needs separate strategy
- **Considerations**:
  - Per-user rate limits (e.g., X messages per minute/hour)
  - Per-assistant rate limits (e.g., prevent single assistant from being overwhelmed)
  - Per-team rate limits (e.g., team-level quotas)
  - Cost-based rate limiting (e.g., token usage limits per day)
- **Future**: Detailed rate limiting implementation to be designed based on usage patterns

### 6. **File Uploads**
- **Decision**: **Not supported in MVP**
- **Rationale**: Focus on core chat functionality first
- **Future**: File uploads for document analysis will be added in later phases

### 7. **Sensitive Data Handling**
- **Decision**: **Guardrails required** for PII and sensitive data
- **Implementation**:
  - PII detection in user queries and responses
  - Optional redaction/masking of sensitive information
  - Compliance with data privacy regulations
  - Audit logging for sensitive data access
- **Future**: Advanced data loss prevention (DLP) features

### 8. **Assistant Marketplace**
- **Decision**: **Not in MVP** - keep it simple
- **Future**: Marketplace for assistant templates and pre-configured assistants will be added later

