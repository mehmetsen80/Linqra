# Linq Protocol - Chat Conversation Examples

This document shows how AI Assistant chat conversations are compatible with the Linq Protocol structure.

---

## Linq Protocol Structure

The Linq Protocol supports multiple query types:
- **`workflow`**: For workflow execution (existing)
- **`chat`**: For AI Assistant conversations (new)

---

## Example 1: Simple Chat Request

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
      "message": "What documents do I need for Form I-485?",
      "conversationId": null,
      "history": [],
      "context": {}
    }
  },
  "executedBy": "timursen"
}
```

## Example 2: Multi-Turn Conversation (with History)

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
          "timestamp": "2024-01-01T10:00:00Z",
          "metadata": {
            "intent": "document_requirements",
            "executedTasks": ["task-789"]
          }
        },
        {
          "role": "assistant",
          "content": "Based on the USCIS requirements, you'll need the following documents for Form I-485...",
          "timestamp": "2024-01-01T10:00:05Z",
          "metadata": {
            "executedTasks": ["task-789"],
            "modelCategory": "openai-chat",
            "modelName": "gpt-4o",
            "tokenUsage": {
              "totalTokens": 500,
              "costUsd": 0.01
            }
          }
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

## Example 3: Chat Request with Agent Task Execution

When the AI Assistant determines it needs to execute an Agent Task, the internal flow would be:

```json
{
  "link": {
    "target": "assistant",
    "action": "chat"
  },
  "query": {
    "intent": "uscis_marriage_based_qna",
    "params": {
      "question": "What documents do I need for Form I-485?",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen"
    },
    "chat": {
      "assistantId": "6917a47bf50d951760a1c6e1",
      "message": "What documents do I need for Form I-485?",
      "conversationId": null,
      "history": [],
      "context": {
        "selectedTask": "6913bde0c8ba393945dcd39b",
        "taskParams": {
          "question": "What documents do I need for Form I-485?"
        }
      }
    }
  },
  "executedBy": "timursen"
}
```

**Internal Flow:**
1. AI Assistant receives chat request
2. Analyzes intent: "document_requirements"
3. Selects Agent Task: "6913bde0c8ba393945dcd39b" (USCIS Marriage-Based Q&A Task)
4. Executes Agent Task using workflow protocol:
   ```json
   {
     "link": {
       "target": "workflow",
       "action": "execute"
     },
     "query": {
       "intent": "uscis_marriage_based_qna",
       "params": {
         "question": "What documents do I need for Form I-485?",
         "teamId": "67d0aeb17172416c411d419e",
         "userId": "timursen"
       },
       "workflow": [
         {
           "step": 1,
           "target": "api-gateway",
           "action": "create",
           "intent": "/api/milvus/collections/uscis_marriage_based_files_openai_text_embedding_3_small_1536/search",
           "payload": {
             "textField": "text",
             "text": "{{params.question}}",
             "teamId": "{{params.teamId}}",
             "modelCategory": "openai-embed",
             "modelName": "text-embedding-3-small",
             "nResults": 10
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
               "content": "You are Linqra's immigration orchestration assistant..."
             },
             {
               "role": "user",
              "content": "Question: {{params.question}}\n\nUse the following knowledge snippets from USCIS documents as your primary source. If they are relevant, ground your answer in them and reference specific sections, items, or pages when possible.\n\nContext snippets:\n{{#each step1.result.results}}\n- [{{this.title}}] (pages {{this.pageNumbers}}): {{this.text}}\n{{/each}}"
             }
           ],
           "llmConfig": {
             "model": "gpt-4o",
             "settings": {
               "temperature": 0.3,
               "max_tokens": 800
             }
           }
         }
       ]
     }
   }
   ```
5. Receives task result
6. Synthesizes response using AI Assistant's default model
7. Returns chat response

---

## Chat Response Structure

```json
{
  "result": {
    "conversationId": "fb2d56b4-1e4e-40c6-af4e-5b8936700775",
    "assistantId": "6917a47bf50d951760a1c6e1",
    "message": "Based on the USCIS requirements, you'll need the following documents for Form I-485:\n\n1. Form I-485 (Application to Register Permanent Residence)\n2. Form I-864 (Affidavit of Support)\n3. Birth certificate (certified copy)\n...",
    "intent": "document_requirements",
    "modelCategory": "openai-chat",
    "modelName": "gpt-4o",
    "executedTasks": [
      "6913bde0c8ba393945dcd39b"
    ],
    "taskResults": {
      "6913bde0c8ba393945dcd39b": {
        "answer": "To obtain Form I-485, you can visit the official USCIS website. The form is titled \"Application to Register Permanent Residence or Adjust Status.\" Here are some key points about the form and the process:\n\n- **Form I-485** is used by individuals in the United States to apply for lawful permanent resident status (a green card) without having to return to their home country to complete visa processing.\n\n- **Supporting Documents**: When filing Form I-485, you must include various supporting documents, such as a government-issued identity document with a photograph, a birth certificate, and evidence of inspection and admission or parole into the United States. (Source: Form I-485 Instructions, pages 11-13)\n\n- **Medical Examination**: You may also need to submit a Form I-693, Report of Immigration Medical Examination and Vaccination Record, completed by a civil surgeon. This is to ensure you meet health-related admissibility requirements. (Source: Form I-485 Instructions, page 14)\n\n**Recommendations:**\n1. **Download Form I-485**: Visit https://www.uscis.gov/i-485 to download the form and its instructions.\n2. **Gather Required Documents**: Ensure you have all necessary documents, such as identity documents, birth certificates, and any required medical examination records.\n3. **Consult USCIS Resources**: For detailed guidance, refer to the instructions provided with the form or consult an immigration attorney if you have specific questions about your eligibility or the application process.",
        "documents": [
          {
            "documentId": "55515527-f464-4b56-96f4-535af1ff7a19",
            "title": "Form I-485, Instructions for Application to Register Permanent Residence or Adjust Status",
            "fileName": "i-485_instructions.pdf",
            "pageNumbers": "14,15",
            "collectionType": "KNOWLEDGE_HUB",
            "teamId": "67d0aeb17172416c411d419e",
            "collectionId": "690693fdcd90d04617697736",
            "rank": 1,
            "distance": 0.6016468
          },
          {
            "documentId": "55515527-f464-4b56-96f4-535af1ff7a19",
            "title": "Form I-485, Instructions for Application to Register Permanent Residence or Adjust Status",
            "fileName": "i-485_instructions.pdf",
            "pageNumbers": "9,10",
            "collectionType": "KNOWLEDGE_HUB",
            "teamId": "67d0aeb17172416c411d419e",
            "collectionId": "690693fdcd90d04617697736",
            "rank": 2,
            "distance": 0.6016468
          }
          // ... more document hits omitted for brevity ...
        ]
      }
    },
    "tokenUsage": {
      "promptTokens": 9443,
      "completionTokens": 79,
      "totalTokens": 9522,
      "costUsd": 0.0243975
    },
    "metadata": {
      "extractedEntities": {
        "forms": ["I-485", "I-864"]
      },
      "contextWindow": {
        "messagesIncluded": 10,
        "totalTokens": 4000
      }
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

## Compatibility Notes

### Link Target Values
- `"workflow"`: For workflow execution (existing)
- `"assistant"`: For AI Assistant chat conversations (new)

### Link Action Values
- `"execute"`: For workflow execution
- `"chat"`: For chat conversations

### Query Structure
- **Workflow**: Uses `query.workflow` (array of WorkflowStep)
- **Chat**: Uses `query.chat` (ChatConversation object)
- Both can coexist in the same Query object, but typically only one is used

### Backward Compatibility
- Existing workflow requests remain unchanged
- New chat requests follow the same Linq Protocol structure
- Both use the same `LinqRequest` and `LinqResponse` DTOs

---

## Real MongoDB Examples (USCIS Marriage-Based Assistant)

The following examples show how the AI Assistant, conversations, and messages are persisted in MongoDB.

### `ai_assistants` – USCIS Marriage-Based Green Card Assistant

```json
{
  "_id": { "$oid": "6917a47bf50d951760a1c6e1" },
  "name": "USCIS Marriage-Based Green Card Assistant",
  "description": "Specialized AI assistant for helping with marriage-based green card applications. Provides guidance on USCIS forms, required documents, eligibility requirements, and application procedures based on your uploaded Knowledge Hub documents.",
  "teamId": "67d0aeb17172416c411d419e",
  "status": "ACTIVE",
  "defaultModel": {
    "provider": "openai",
    "modelName": "gpt-4o",
    "modelCategory": "openai-chat",
    "settings": {
      "temperature": 0.7,
      "max_tokens": 2000
    }
  },
  "systemPrompt": "You are Linqra's immigration orchestration assistant specializing in marriage-based green card applications. Your role is to provide specific, actionable answers to USCIS-related questions.\n\nWhen provided with knowledge snippets from documents, use them as the primary source. When context is insufficient or no relevant snippets are found, draw upon your comprehensive training data about USCIS forms, procedures, and immigration law to provide specific, detailed answers.\n\nAlways prioritize accuracy and provide concrete information rather than generic advice. Cite specific form sections, item numbers, fees, deadlines, and requirements when possible.\n\nFormat your responses clearly with:\n- Direct Answer\n- Specific Details (form sections, fees, deadlines)\n- Action Items (step-by-step guidance)\n- Important Notes (warnings, caveats)\n\nBe professional, empathetic, and thorough in your assistance.",
  "selectedTasks": [
    {
      "taskId": "6913bde0c8ba393945dcd39b",
      "taskName": "Initial Evidence Intake & Eligibility Assessment (USCIS Marriage-Based Greencard Application Agent)"
    }
  ],
  "contextManagement": {
    "strategy": "sliding_window",
    "maxRecentMessages": 10,
    "maxTotalTokens": 4000
  },
  "accessControl": {
    "type": "PRIVATE",
    "allowedDomains": []
  },
  "guardrails": {
    "piiDetectionEnabled": true,
    "auditLoggingEnabled": true
  },
  "createdAt": { "$date": "2025-11-14T21:51:54.991Z" },
  "updatedAt": { "$date": "2025-11-15T02:58:55.327Z" },
  "createdBy": "timursen",
  "updatedBy": "timursen",
  "_class": "org.lite.gateway.entity.AIAssistant"
}
```

### `conversations` – Example Conversation Record

```json
{
  "_id": "fb2d56b4-1e4e-40c6-af4e-5b8936700775",
  "assistantId": "6917a47bf50d951760a1c6e1",
  "teamId": "67d0aeb17172416c411d419e",
  "username": "timursen",
  "isPublic": false,
  "source": "web_app",
  "title": "Why do I need the I-130 Form for?",
  "status": "ACTIVE",
  "startedAt": { "$date": "2025-11-15T03:04:49.981Z" },
  "lastMessageAt": { "$date": "2025-11-15T23:22:13.874Z" },
  "messageCount": 4,
  "metadata": {
    "totalTokens": { "$numberLong": "0" },
    "totalCost": 0,
    "taskExecutions": 0,
    "successfulTasks": 0,
    "failedTasks": 0
  },
  "updatedAt": { "$date": "2025-11-15T23:22:13.922Z" },
  "_class": "org.lite.gateway.entity.Conversation"
}
```

### `conversation_messages` – Assistant Reply with Structured Task Results

```json
{
  "_id": "a642f8d7-4374-48e0-99ad-db6c151b49f6",
  "conversationId": "b35383a5-4ec2-4204-ae47-2fdd79b3c889",
  "sequenceNumber": 8,
  "role": "ASSISTANT",
  "content": "You can download Form I-485, Application to Register Permanent Residence or Adjust Status, from the official USCIS website. Visit https://www.uscis.gov/i-485 to get the form and its instructions. This will provide you with the most up-to-date version of the form and detailed guidance on how to fill it out correctly.",
  "timestamp": { "$date": "2025-11-16T05:17:36.742Z" },
  "metadata": {
    "executedTasks": [
      "6913bde0c8ba393945dcd39b"
    ],
    "taskResults": {
      "6913bde0c8ba393945dcd39b": {
        "answer": "To obtain Form I-485, you can visit the official USCIS website. The form is titled \"Application to Register Permanent Residence or Adjust Status.\" Here are some key points about the form and the process:\n\n- **Form I-485** is used by individuals in the United States to apply for lawful permanent resident status (a green card) without having to return to their home country to complete visa processing.\n\n- **Supporting Documents**: When filing Form I-485, you must include various supporting documents, such as a government-issued identity document with a photograph, a birth certificate, and evidence of inspection and admission or parole into the United States. (Source: Form I-485 Instructions, pages 11-13)\n\n- **Medical Examination**: You may also need to submit a Form I-693, Report of Immigration Medical Examination and Vaccination Record, completed by a civil surgeon. This is to ensure you meet health-related admissibility requirements. (Source: Form I-485 Instructions, page 14)\n\n**Recommendations:**\n1. **Download Form I-485**: Visit https://www.uscis.gov/i-485 to download the form and its instructions.\n2. **Gather Required Documents**: Ensure you have all necessary documents, such as identity documents, birth certificates, and any required medical examination records.\n3. **Consult USCIS Resources**: For detailed guidance, refer to the instructions provided with the form or consult an immigration attorney if you have specific questions about your eligibility or the application process.",
        "documents": [
          {
            "fileName": "i-485_instructions.pdf",
            "documentId": "55515527-f464-4b56-96f4-535af1ff7a19",
            "title": "Form I-485, Instructions for Application to Register Permanent Residence or Adjust Status",
            "pageNumbers": "14,15",
            "collectionType": "KNOWLEDGE_HUB",
            "teamId": "67d0aeb17172416c411d419e",
            "collectionId": "690693fdcd90d04617697736",
            "rank": 1,
            "distance": 0.6016468
          }
          // ... additional document hits omitted for brevity ...
        ]
      }
    },
    "intent": "user_query",
    "modelCategory": "openai-chat",
    "modelName": "gpt-4o",
    "tokenUsage": {
      "promptTokens": { "$numberLong": "9443" },
      "completionTokens": { "$numberLong": "79" },
      "totalTokens": { "$numberLong": "9522" },
      "costUsd": 0.0243975
    },
    "additionalData": {}
  },
  "_class": "org.lite.gateway.entity.ConversationMessage"
}
```

These real examples match the Linq Protocol structures described above and illustrate how:

- **Agent Task results** are stored as structured `answer` + `documents` objects.
- **Token usage and cost** are tracked per message.
- **Conversation metadata** links assistants, teams, and users (via `username`).

---

## MongoDB Collections

### `ai_assistants`
Stores AI Assistant configurations (similar to `linq_workflows`)

### `conversations`
Stores conversation metadata and context

### `conversation_messages`
Stores individual messages within conversations

---

## Integration with Agent Tasks

When an AI Assistant needs to execute an Agent Task:

1. **Intent Classification**: AI Assistant analyzes user query
2. **Task Selection**: Matches intent to configured Agent Task
3. **Parameter Extraction**: Extracts parameters from user query
4. **Task Execution**: Executes Agent Task using workflow protocol
5. **Result Synthesis**: AI Assistant synthesizes task result into natural language response

The Agent Task execution uses the existing workflow protocol, while the AI Assistant orchestrates the conversation using the chat protocol.

