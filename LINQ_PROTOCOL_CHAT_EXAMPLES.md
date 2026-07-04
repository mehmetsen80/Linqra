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

## Other Examples

### Summarize Long Text - LOCAL LLM - Ollama

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "ollama_summarize_content",
    "params": {
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "textToSummarize": "The history of the Internet has its origin in the efforts of wide area networking that originated in several computer science laboratories in the United States, United Kingdom, and France. The U.S. Department of Defense awarded contracts as early as the 1960s, including for the development of the ARPANET project, directed by Robert Taylor and managed by Lawrence Roberts. The first message was sent over the ARPANET in 1969 from computer science professor Leonard Kleinrock's laboratory at the University of California, Los Angeles (UCLA) to the second network node at Stanford Research Institute (SRI). Packet switching, a fundamental concept for data transfer, was proposed by Paul Baran in the early 1960s and independently by Donald Davies in 1965. This method allowed data to be broken into smaller blocks, sent independently, and reassembled at the destination, which was far more efficient than the circuit-switching methods used by telephone networks. Access to the ARPANET was expanded in 1981 when the National Science Foundation (NSF) funded the Computer Science Network (CSNET). In 1982, the Internet Protocol Suite (TCP/IP) was standardized, which permitted disparate networks to interconnect. NSFNet access provided connection to supercomputer sites in the United States from research and education organizations. Commercial internet service providers (ISPs) began to emerge in the very late 1980s and 1990s. The ARPANET was decommissioned in 1990. The Internet was commercialized in 1995 when NSFNet was decommissioned, removing the last restrictions on the use of the Internet to carry commercial traffic. The Internet rapidly expanded in Europe and Australia in the mid to late 1990s and to Asia. The culture of the Internet is distinct because it is not owned or controlled by any single entity."
    },
    "workflow": [
      {
        "step": 1,
        "target": "ollama-chat",
        "action": "generate",
        "intent": "generate",
        "description": "Summarize the provided text using the local Ollama model.",
        "params": null,
        "payload": [
          {
            "role": "system",
            "content": "You are a helpful assistant specialized in summarizing text. Provide a concise summary of the following content."
          },
          {
            "role": "user",
            "content": "{{params.textToSummarize}}"
          }
        ],
        "llmConfig": {
          "model": "llama3",
          "settings": {
            "max.tokens": 500,
            "temperature": 0.7
          }
        },
        "async": null,
        "cacheConfig": null
      }
    ]
  }
}
```
  
### Read and Write F1 Questions Document - Ollama

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "visa_questions_and_answers_intent",
    "params": {
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen"
    },
    "workflow": [
      {
        "step": 1,
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/milvus/collections/visa_files_ollama_nomic_embed_text_768/search",
        "payload": {
          "textField": "text",
          "text": "{{params.question}}",
          "teamId": "{{params.teamId}}",
          "modelCategory": "ollama-embed",
          "modelName": "nomic-embed-text",
          "nResults": 10
        },
        "description": "Retrieve the most relevant knowledge snippets for the user’s question."
      },
      {
        "step": 2,
        "target": "ollama-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are an expert F1 Visa and US Immigration assistant. Your role is to provide specific, actionable answers to questions about F1 Visa rules, work authorization (OPT/CPT), business ownership, and immigration compliance. When provided with knowledge snippets from documents, use them as your primary source."
          },
          {
            "role": "user",
            "content": "Question: {{params.question}}\n\nContext snippets from knowledge base:\n{{step1.result.results}}\n\n**CRITICAL INSTRUCTIONS:**\n\n1. **Primary Goal**: Provide a SPECIFIC, DETAILED answer to the question above based on the provided context.\n\n2. **When context snippets are available and relevant**:\n   - Use them as the primary source\n   - Quote specific passages from the 'F1 Visa FAQ' or other documents if applicable\n\n3. **When context is insufficient**:\n   - State clearly that the provided documents do not fully answer the question, but provide general guidance based on standard F1 Visa regulations (e.g., prohibition on self-employment without OPT/CPT, passive investment rules).\n\n4. **Response Structure**:\n   **Direct Answer**: [Provide a clear Yes/No/Maybe answer with conditions]\n   \n   **Specific Details**: [Explain the rules, such as the difference between passive ownership and active management, or OPT requirements]\n   \n   **Action Items**: [Provide concrete steps, e.g., 'Consult DSO', 'Apply for OPT', 'Hire a manager']\n   \n   **Important Note**: [Disclaimer about legal advice and checking latest USCIS/SEVP guidelines]"
          }
        ],
        "llmConfig": {
          "model": "llama3",
          "settings": {
            "temperature": 0.3,
            "max.tokens": 1000
          }
        },
        "description": "Generate a comprehensive answer based on the search results."
      }
    ]
  }
}
```
### USCIS Form Sync Workflow - Multi-Step with Conditionals

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_form_sync",
    "params": {
      "resourceCategory": "uscis-sentinel",
      "resourceId": "I-485",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "collectionId": "69ac77018626a22133fff877",
      "agentTaskId": "69aa53798626a22133fff865"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "I-485 Version Check",
        "description": "Checking USCIS for latest form edition, mandatory dates, and instruction updates",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/sync/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "targetStep": 6
        }
      },
      {
        "step": 2,
        "summary": "Form PDF Ingestion",
        "description": "Ingressing latest primary Form PDF into the Knowledge Hub",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.resourceUrl}}",
          "fileName": "{{params.resourceId}}_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 3,
        "summary": "Instructions Ingestion",
        "description": "Ingressing latest Instructions PDF into the Knowledge Hub",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.instructionsUrl}}",
          "fileName": "{{params.resourceId}}_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 4,
        "summary": "Delta Extraction",
        "description": "Extracting targeted content delta between versions",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "newDocumentId": "{{step2.result.documentId}}",
          "resourceId": "{{params.resourceId}}",
          "resourceCategory": "{{params.resourceCategory}}",
          "categories": [
            "Filing Fees",
            "Addresses",
            "Evidence",
            "Signatures"
          ]
        }
      },
      {
        "step": 5,
        "summary": "AI Edition Analysis",
        "description": "Generating Precise Edition Analysis via AI Assistant",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a USCIS Form Analyst. Compare document versions and identify changes.\n\nRULES:\n1. Output MUST be valid JSON.\n2. Do NOT speculate. If no evidence found, use status 'NO_CHANGE' and set \"changeDetected\": false.\n3. Cite exact changed passages if possible.\n4. details must be concise and based only on explicit textual differences.\n5. Output MUST follow this schema:\n{\n  \"resourceId\": \"{{params.resourceId}}\",\n  \"changeDetected\": true,\n  \"categories\": [\n    { \"name\": \"Filing Fees\", \"status\": \"NO_CHANGE\", \"details\": \"\" },\n    { \"name\": \"Evidence\", \"status\": \"CHANGED\", \"details\": \"New wording added regarding...\" }\n  ],\n  \"summary\": \"...\"\n}"
          },
          {
            "role": "user",
            "content": "Old Content:\n{{step4.result.oldText}}\n\nNew Content:\n{{step4.result.newText}}"
          }
        ]
      },
      {
        "step": 6,
        "summary": "I-485 State Commit",
        "description": "Committing sync state and analysis to sovereign database",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/sync/commit",
        "payload": {
          "resourceId": "{{params.resourceId}}",
          "resourceCategory": "{{params.resourceCategory}}",
          "version": "{{step1.result.newVersion}}",
          "effectiveDate": "{{step1.result.effectiveDate}}",
          "hash": "{{step1.result.currentHash}}",
          "instructionsHash": "{{step1.result.instructionsHash}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "instructionsUrl": "{{step1.result.instructionsUrl}}",
          "documentId": "{{step2.result.documentId??step1.result.oldDocumentId}}",
          "instructionsDocumentId": "{{step3.result.documentId??step1.result.oldInstructionsDocumentId}}",
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "oldInstructionsDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "changeType": "EDITION_UPDATE",
          "agentTaskId": "{{params.agentTaskId}}",
          "changeDetected": "{{step5.result.changeDetected??false}}",
          "summary": "{{step5.result.summary??USCIS Form I-485 scan completed - No changes detected to document content.}}",
          "analysis": "{{step5.result??{ \"status\": \"NO_CHANGE\", \"changeDetected\": false, \"details\": \"The scan was completed and no changes were detected. The form and instructions are identical to the previous version verified.\" }}}"
        },
        "jump": {
          "condition": "{{step6.result.changeDetected}} == false",
          "targetStep": 0
        }
      },
      {
        "step": 7,
        "summary": "Dispatch Notification",
        "description": "Dispatching high-severity edition update notifications",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "resourceCategory": "{{params.resourceCategory}}",
          "resourceId": "{{params.resourceId}}",
          "type": "EDITION_UPDATE",
          "severity": "HIGH",
          "summary": "USCIS Form {{params.resourceId}} Updated to Edition {{step6.result.version}}",
          "details": "{{step6.result.summary}}"
        }
      }
    ]
  }
}
```

### Example from uscis_form_n400_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_form_sync",
    "params": {
      "domain": "uscis-sentinel",
      "category": "forms",
      "resourceId": "N-400",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "collectionId": "69ac77018626a22133fff877",
      "agentTaskId": "69c41f31aa54fc27fe60a542"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "N-400 Edition Check",
        "description": "Monitoring USCIS for updates to the Application for Naturalization, including the G-1151 supplemental document and fee changes",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/sync/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 9
        }
      },
      {
        "step": 2,
        "summary": "Form N-400 Ingestion",
        "description": "Ingressing primary N-400 PDF into Knowledge Hub",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.resourceUrl}}",
          "fileName": "{{params.resourceId}}_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 3,
        "summary": "Instruction Ingestion",
        "description": "Ingressing N-400 Instructions for updated residency, physical presence, and moral character guidance",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.instructionsUrl}}",
          "fileName": "{{params.resourceId}}_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 4,
        "summary": "G-1151 Supplemental Ingestion",
        "description": "Ingressing G-1151 Notification of Acceptance for naturalization filing fee confirmation",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.g1151.url}}",
          "fileName": "G-1151_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 5,
        "summary": "Main Form Delta Extraction",
        "description": "Extracting content shifts for the primary N-400 Form PDF",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "newDocumentId": "{{step2.result.documentId}}",
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Naturalization Eligibility Checkboxes",
            "Continuous Residence & Physical Presence Data",
            "Good Moral Character (GMC) Disclosures"
          ]
        }
      },
      {
        "step": 6,
        "summary": "Instructions Delta Extraction",
        "description": "Extracting shifts in the N-400 Instructions regarding citizenship protocols",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "newDocumentId": "{{step3.result.documentId}}",
          "resourceId": "{{params.resourceId}}_instructions",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Residency Calculation Rules",
            "English & Civics Test Exemptions",
            "Filing Locations & Fees"
          ]
        }
      },
      {
        "step": 7,
        "summary": "G-1151 Delta Extraction",
        "description": "Extracting shifts in the fee notification supplemental document",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.supplementalResources.g1151.oldDocumentId}}",
          "newDocumentId": "{{step4.result.documentId}}",
          "resourceId": "G-1151",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Fee Acceptance Protocols",
            "Electronic Notification Procedures"
          ]
        }
      },
      {
        "step": 8,
        "summary": "AI Suite-Wide Naturalization Analysis",
        "description": "Synthesizing shifts across the full N-400 naturalization suite",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a High-Sensitivity USCIS Senior Specialist in Naturalization. Your mission is to detect ANY deviation in policy, procedures, or text within the ENTIRE Form N-400 suite.\n\nRULES:\n1. Output MUST be valid JSON.\n2. Be extremely sensitive: Any difference in text, even minor, MUST be reported with \"changeDetected\": true.\n3. For every category marked as 'CHANGED', you MUST provide a high-fidelity delta in the 'details' field following this format: '[Location: <Document Name>, Page X, Section Y] <Change Type>: <Context>'. \n4. If the inputs are empty (Initial Discovery), summarize the current version's key citizenship/residency protocols IF text is provided. IF the input text is empty for all documents, set \"changeDetected\": true, set status to \"INITIAL_DISCOVERY\", and in details state: \"Baseline established; waiting for full content ingestion for detailed summary.\"\n5. STRICT RULE: Do NOT invent Page numbers or specific policy changes if the input text is empty. Hallucination is UNACCEPTABLE.\n6. If ANY category status is 'CHANGED' or 'INITIAL_DISCOVERY', the top-level \"changeDetected\" MUST be true.\n7. The 'summary' field MUST be a comprehensive, human-readable synthesis of all detected shifts across the main form, instructions, and G-1151.\n8. Output MUST follow this schema:\n{\n  \"resourceId\": \"{{params.resourceId}}\",\n  \"changeDetected\": true,\n  \"categories\": [\n    { \"name\": \"Category Name\", \"status\": \"INITIAL_DISCOVERY\", \"details\": \"[Location: Document Name] Baseline summary of current protocol...\" }\n  ],\n  \"summary\": \"[Detailed synthesis...]\"\n}"
          },
          {
            "role": "user",
            "content": "### MAIN FORM DELTAS\nOld: {{step5.result.oldText}}\nNew: {{step5.result.newText}}\n\n### INSTRUCTIONS DELTAS\nOld: {{step6.result.oldText}}\nNew: {{step6.result.newText}}\n\n### G-1151 SUPPLEMENTAL DELTAS\nOld: {{step7.result.oldText}}\nNew: {{step7.result.newText}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-o4-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 9,
        "summary": "N-400 State Commit",
        "description": "Finalizing N-400 state into the Sovereign Knowledge Hub DB",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/sync/commit",
        "payload": {
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "displayName": "{{step1.result.displayName}}",
          "version": "{{step1.result.newVersion}}",
          "effectiveDate": "{{step1.result.effectiveDate}}",
          "hash": "{{step1.result.newHash}}",
          "instructionsHash": "{{step1.result.instructionsHash}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "instructionsUrl": "{{step1.result.instructionsUrl}}",
          "supplementalResources": {
            "g1151": {
              "name": "G-1151 Notification",
              "url": "{{step1.result.supplementalResources.g1151.url}}",
              "hash": "{{step1.result.supplementalResources.g1151.hash}}",
              "documentId": "{{step4.result.documentId??step1.result.supplementalResources.g1151.oldDocumentId}}"
            }
          },
          "documentId": "{{step2.result.documentId??step1.result.oldDocumentId}}",
          "instructionsDocumentId": "{{step3.result.documentId??step1.result.oldInstructionsDocumentId}}",
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "oldInstructionsDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "changeType": "EDITION_UPDATE",
          "agentTaskId": "{{params.agentTaskId}}",
          "changeDetected": "{{step8.result.changeDetected??false}}",
          "summary": "{{step8.result.summary??USCIS Form N-400 scan completed - No residency or eligibility changes detected.}}",
          "analysis": "{{step8.result??{ \"status\": \"NO_CHANGE\", \"changeDetected\": false, \"details\": \"The surveillance check for Form N-400 was completed. No document content updates were found during this cycle.\" }}}"
        },
        "jump": {
          "condition": "{{step9.result.changeDetected}} == false",
          "conditionDesc": "Naturalization Up-to-Date",
          "targetStep": 0
        }
      },
      {
        "step": 10,
        "summary": "Dispatch Notification",
        "description": "Notifying priority subscribers of N-400 naturalization policy shifts",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "resourceId": "{{params.resourceId}}",
          "type": "EDITION_UPDATE",
          "reportUrl": "https://komunas.com",
          "severity": "HIGH",
          "summary": "USCIS Form {{params.resourceId}} Updated to Edition {{step9.result.version}}",
          "details": "{{step9.result.summary}}",
          "delta": "{{step1.result.delta}}"
        }
      }
    ]
  }
}

```


### Example from uscis_form_i130_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_form_sync",
    "params": {
      "domain": "uscis-sentinel",
      "category": "forms",
      "resourceId": "I-130",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "collectionId": "69ac77018626a22133fff877",
      "agentTaskId": "69b224d79b8c45139dc7ed8a"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "I-130 Edition Check",
        "description": "Monitoring USCIS for updates to the Petition for Alien Relative, including the I-130A supplemental for spouses",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/sync/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 9
        }
      },
      {
        "step": 2,
        "summary": "Form I-130 Ingestion",
        "description": "Ingressing primary I-130 PDF into Knowledge Hub",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.resourceUrl}}",
          "fileName": "{{params.resourceId}}_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 3,
        "summary": "Instruction Ingestion",
        "description": "Ingressing I-130 Instructions for updated relative evidence and filing location policies",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.instructionsUrl}}",
          "fileName": "{{params.resourceId}}_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 4,
        "summary": "I-130A Supplemental Ingestion",
        "description": "Ingressing I-130A Supplemental Information for Spouse Beneficiary",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.i130a.url}}",
          "fileName": "I-130A_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 5,
        "summary": "Main Form Delta Extraction",
        "description": "Extracting content shifts for the primary I-130 Form PDF",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "newDocumentId": "{{step2.result.documentId}}",
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Eligibility & Priority Dates",
            "Petitioner & Beneficiary Info",
            "Mailing Addresses"
          ]
        }
      },
      {
        "step": 6,
        "summary": "Instructions Delta Extraction",
        "description": "Extracting shifts in the I-130 Instructions regarding relative evidence",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "newDocumentId": "{{step3.result.documentId}}",
          "resourceId": "{{params.resourceId}}_instructions",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Proof of Qualifying Relationship",
            "Filing Locations & Jurisdictions",
            "Evidentiary Standards"
          ]
        }
      },
      {
        "step": 7,
        "summary": "I-130A Delta Extraction",
        "description": "Extracting shifts in the spouse supplemental form",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.supplementalResources.i130a.oldDocumentId}}",
          "newDocumentId": "{{step4.result.documentId}}",
          "resourceId": "I-130A",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Beneficiary Biographical Data",
            "Spouse History Protocols"
          ]
        }
      },
      {
        "step": 8,
        "summary": "AI Suite-Wide Family Petition Analysis",
        "description": "Synthesizing shifts across the full I-130 family suite",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a High-Sensitivity USCIS Senior Specialist in Family-Based Petitions. Your mission is to detect ANY deviation in policy, procedures, or text within the ENTIRE Form I-130 suite.\n\nRULES:\n1. Output MUST be valid JSON.\n2. Be extremely sensitive: Any difference in text, even minor, MUST be reported with \"changeDetected\": true.\n3. For every category marked as 'CHANGED', you MUST provide a high-fidelity delta in the 'details' field following this format: '[Location: <Document Name>, Page X, Section Y] <Change Type>: <Context>'. \n4. If the inputs are empty (Initial Discovery), summarize the current version's key marriage/relationship protocols IF text is provided. IF the input text is empty for all documents, set \"changeDetected\": true, set status to \"INITIAL_DISCOVERY\", and in details state: \"Baseline established; waiting for full content ingestion for detailed summary.\"\n5. STRICT RULE: Do NOT invent Page numbers or specific policy changes if the input text is empty. Hallucination is UNACCEPTABLE.\n6. If ANY category status is 'CHANGED' or 'INITIAL_DISCOVERY', the top-level \"changeDetected\" MUST be true.\n7. The 'summary' field MUST be a comprehensive, human-readable synthesis of all detected shifts across the main form, instructions, and I-130A.\n8. Output MUST follow this schema:\n{\n  \"resourceId\": \"{{params.resourceId}}\",\n  \"changeDetected\": true,\n  \"categories\": [\n    { \"name\": \"Category Name\", \"status\": \"INITIAL_DISCOVERY\", \"details\": \"[Location: Document Name] Baseline summary of current protocol...\" }\n  ],\n  \"summary\": \"[Detailed synthesis...]\"\n}"
          },
          {
            "role": "user",
            "content": "### MAIN FORM DELTAS\nOld: {{step5.result.oldText}}\nNew: {{step5.result.newText}}\n\n### INSTRUCTIONS DELTAS\nOld: {{step6.result.oldText}}\nNew: {{step6.result.newText}}\n\n### I-130A SUPPLEMENTAL DELTAS\nOld: {{step7.result.oldText}}\nNew: {{step7.result.newText}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-o4-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 9,
        "summary": "I-130 State Commit",
        "description": "Finalizing I-130 state into the Sovereign Knowledge Hub DB",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/sync/commit",
        "payload": {
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "displayName": "{{step1.result.displayName}}",
          "version": "{{step1.result.newVersion}}",
          "effectiveDate": "{{step1.result.effectiveDate}}",
          "hash": "{{step1.result.newHash}}",
          "instructionsHash": "{{step1.result.instructionsHash}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "instructionsUrl": "{{step1.result.instructionsUrl}}",
          "supplementalResources": {
            "i130a": {
              "name": "I-130A Supplemental",
              "url": "{{step1.result.supplementalResources.i130a.url}}",
              "hash": "{{step1.result.supplementalResources.i130a.hash}}",
              "documentId": "{{step4.result.documentId??step1.result.supplementalResources.i130a.oldDocumentId}}"
            }
          },
          "documentId": "{{step2.result.documentId??step1.result.oldDocumentId}}",
          "instructionsDocumentId": "{{step3.result.documentId??step1.result.oldInstructionsDocumentId}}",
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "oldInstructionsDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "changeType": "EDITION_UPDATE",
          "agentTaskId": "{{params.agentTaskId}}",
          "changeDetected": "{{step8.result.changeDetected??false}}",
          "summary": "{{step8.result.summary??USCIS Form I-130 scan completed - No version changes found for Relative Petition.}}",
          "analysis": "{{step8.result??{ \"status\": \"NO_CHANGE\", \"changeDetected\": false, \"details\": \"The surveillance check for Form I-130 was completed. No document content updates were found during this cycle.\" }}}"
        },
        "jump": {
          "condition": "{{step9.result.changeDetected}} == false",
          "conditionDesc": "No Changes Found",
          "targetStep": 0
        }
      },
      {
        "step": 10,
        "summary": "Dispatch Notification",
        "description": "Notifying priority subscribers of I-130 edition updates or priority date shifts",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "resourceId": "{{params.resourceId}}",
          "type": "EDITION_UPDATE",
          "reportUrl": "https://komunas.com",
          "severity": "HIGH",
          "summary": "USCIS Form {{params.resourceId}} Updated to Edition {{step9.result.version}}",
          "details": "{{step9.result.summary}}",
          "delta": "{{step1.result.delta}}"
        }
      }
    ]
  }
}
```


### Example from uscis_form_i485_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_form_sync",
    "params": {
      "domain": "uscis-sentinel",
      "category": "forms",
      "resourceId": "I-485",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "collectionId": "69ac77018626a22133fff877",
      "agentTaskId": "69aa53798626a22133fff865"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "I-485 Version Check",
        "description": "Checking USCIS for latest form edition, mandatory dates, and instruction updates for Adjustment of Status applications",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/sync/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 7
        }
      },
      {
        "step": 2,
        "summary": "Form I-485 Ingestion",
        "description": "Ingressing primary I-485 Form PDF into Knowledge Hub",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.resourceUrl}}",
          "fileName": "{{params.resourceId}}_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 3,
        "summary": "Instruction Ingestion",
        "description": "Ingressing I-485 Instructions for updated public charge rules and filing policies",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.instructionsUrl}}",
          "fileName": "{{params.resourceId}}_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 4,
        "summary": "Main Form Delta Extraction",
        "description": "Extracting content shifts for the primary I-485 Form PDF",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "newDocumentId": "{{step2.result.documentId}}",
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Eligibility Basis Checkboxes",
            "Public Charge Disclosures",
            "Biographical Information Protocols"
          ]
        }
      },
      {
        "step": 5,
        "summary": "Instructions Delta Extraction",
        "description": "Extracting shifts in the I-485 Instructions regarding public charge and evidence",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "newDocumentId": "{{step3.result.documentId}}",
          "resourceId": "{{params.resourceId}}_instructions",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Public Charge Definition Shifts",
            "Filing Fees & Biometric Requirements",
            "Filing Locations & Lockbox Policies"
          ]
        }
      },
      {
        "step": 6,
        "summary": "AI Suite-Wide Adjustment Analysis",
        "description": "Synthesizing shifts across the full I-485 adjustment suite",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a High-Sensitivity USCIS Senior Specialist in Adjustment of Status (INA 245). Your mission is to detect ANY deviation in policy, procedures, or text within the ENTIRE Form I-485 suite.\n\nRULES:\n1. Output MUST be valid JSON.\n2. Be extremely sensitive: Any difference in text, even minor, MUST be reported with \"changeDetected\": true.\n3. For every category marked as 'CHANGED', you MUST provide a high-fidelity delta in the 'details' field following this format: '[Location: <Document Name>, Page X, Section Y] <Change Type>: <Context>'. \n4. If the inputs are empty (Initial Discovery), summarize the current version's key adjustment/public-charge protocols IF text is provided. IF the input text is empty for all documents, set \"changeDetected\": true, set status to \"INITIAL_DISCOVERY\", and in details state: \"Baseline established; waiting for full content ingestion for detailed summary.\"\n5. STRICT RULE: Do NOT invent Page numbers or specific policy changes if the input text is empty. Hallucination is UNACCEPTABLE.\n6. If ANY category status is 'CHANGED' or 'INITIAL_DISCOVERY', the top-level \"changeDetected\" MUST be true.\n7. The 'summary' field MUST be a comprehensive, human-readable synthesis of all detected shifts across the main form and instructions.\n8. Output MUST follow this schema:\n{\n  \"resourceId\": \"{{params.resourceId}}\",\n  \"changeDetected\": true,\n  \"categories\": [\n    { \"name\": \"Category Name\", \"status\": \"INITIAL_DISCOVERY\", \"details\": \"[Location: Document Name] Baseline summary of current protocol...\" }\n  ],\n  \"summary\": \"[Detailed synthesis...]\"\n}"
          },
          {
            "role": "user",
            "content": "### MAIN FORM DELTAS\nOld: {{step4.result.oldText}}\nNew: {{step4.result.newText}}\n\n### INSTRUCTIONS DELTAS\nOld: {{step5.result.oldText}}\nNew: {{step5.result.newText}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-o4-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 7,
        "summary": "I-485 State Commit",
        "description": "Finalizing I-485 state into the Sovereign Knowledge Hub DB",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/sync/commit",
        "payload": {
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "displayName": "{{step1.result.displayName}}",
          "version": "{{step1.result.newVersion}}",
          "effectiveDate": "{{step1.result.effectiveDate}}",
          "hash": "{{step1.result.newHash}}",
          "instructionsHash": "{{step1.result.instructionsHash}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "instructionsUrl": "{{step1.result.instructionsUrl}}",
          "documentId": "{{step2.result.documentId??step1.result.oldDocumentId}}",
          "instructionsDocumentId": "{{step3.result.documentId??step1.result.oldInstructionsDocumentId}}",
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "oldInstructionsDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "changeType": "EDITION_UPDATE",
          "agentTaskId": "{{params.agentTaskId}}",
          "changeDetected": "{{step6.result.changeDetected??false}}",
          "summary": "{{step6.result.summary??No material changes detected for Form {{params.resourceId}}. Monitoring is active and up-to-date.}}",
          "analysis": "{{step6.result??{ \"status\": \"NO_CHANGE\", \"changeDetected\": false, \"details\": \"The surveillance check for Form {{params.resourceId}} was successful. No document content updates were found during this cycle.\" }}}"
        },
        "jump": {
          "condition": "{{step7.result.changeDetected}} == false",
          "conditionDesc": "No Changes Found",
          "targetStep": 0
        }
      },
      {
        "step": 8,
        "summary": "Dispatch Notification",
        "description": "Notifying priority subscribers of I-485 adjustment policy shifts or fee updates",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "resourceId": "{{params.resourceId}}",
          "type": "EDITION_UPDATE",
          "reportUrl": "https://komunas.com",
          "severity": "HIGH",
          "summary": "USCIS Form {{params.resourceId}} Updated to Edition {{step7.result.version}}",
          "details": "{{step7.result.summary}}",
          "delta": "{{step1.result.delta}}"
        }
      }
    ]
  }
}

```


### Example from uscis_form_i90_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_form_sync",
    "params": {
      "domain": "uscis-sentinel",
      "category": "forms",
      "resourceId": "I-90",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "collectionId": "69ac77018626a22133fff877",
      "agentTaskId": "69c37951aa54fc27fe60a36d"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "I-90 Edition Check",
        "description": "Monitoring USCIS for updates to the Application to Replace Permanent Resident Card, including edition date, filing fees, and biometric requirements",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/sync/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 7
        }
      },
      {
        "step": 2,
        "summary": "Form I-90 Ingestion",
        "description": "Ingressing primary I-90 PDF into Knowledge Hub",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.resourceUrl}}",
          "fileName": "{{params.resourceId}}_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 3,
        "summary": "Instruction Ingestion",
        "description": "Ingressing I-90 Instructions for updated evidence requirements for lost, stolen, or damaged cards",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.instructionsUrl}}",
          "fileName": "{{params.resourceId}}_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 4,
        "summary": "Main Form Delta Extraction",
        "description": "Extracting content shifts for the primary I-90 Form PDF",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "newDocumentId": "{{step2.result.documentId}}",
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Card Renewal Reason Codes",
            "Biometric Appointment Logic",
            "Mailing Address Formats"
          ]
        }
      },
      {
        "step": 5,
        "summary": "Instructions Delta Extraction",
        "description": "Extracting shifts in the I-90 Instructions regarding evidence and fee protocols",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "newDocumentId": "{{step3.result.documentId}}",
          "resourceId": "{{params.resourceId}}_instructions",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Filing Fees & Biometric Requirements",
            "Evidence for Replacement Cards",
            "Filing Locations & Online Eligibility"
          ]
        }
      },
      {
        "step": 6,
        "summary": "AI Suite-Wide Green Card Analysis",
        "description": "Synthesizing shifts across the full I-90 replacement suite",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a High-Sensitivity USCIS Senior Specialist in Green Card Renewals. Your mission is to detect ANY deviation in policy, procedures, or text within the ENTIRE Form I-90 suite.\n\nRULES:\n1. Output MUST be valid JSON.\n2. Be extremely sensitive: Any difference in text, even minor, MUST be reported with \"changeDetected\": true.\n3. For every category marked as 'CHANGED', you MUST provide a high-fidelity delta in the 'details' field following this format: '[Location: <Document Name>, Page X, Section Y] <Change Type>: <Context>'. \n4. If the inputs are empty (Initial Discovery), summarize the current version's key renewal/evidence protocols IF text is provided. IF the input text is empty for all documents, set \"changeDetected\": true, set status to \"INITIAL_DISCOVERY\", and in details state: \"Baseline established; waiting for full content ingestion for detailed summary.\"\n5. STRICT RULE: Do NOT invent Page numbers or specific policy changes if the input text is empty. Hallucination is UNACCEPTABLE.\n6. If ANY category status is 'CHANGED' or 'INITIAL_DISCOVERY', the top-level \"changeDetected\" MUST be true.\n7. The 'summary' field MUST be a comprehensive, human-readable synthesis of all detected shifts across the main form and instructions.\n8. Output MUST follow this schema:\n{\n  \"resourceId\": \"{{params.resourceId}}\",\n  \"changeDetected\": true,\n  \"categories\": [\n    { \"name\": \"Category Name\", \"status\": \"INITIAL_DISCOVERY\", \"details\": \"[Location: Document Name] Baseline summary of current protocol...\" }\n  ],\n  \"summary\": \"[Detailed synthesis...]\"\n}"
          },
          {
            "role": "user",
            "content": "### MAIN FORM DELTAS\nOld: {{step4.result.oldText}}\nNew: {{step4.result.newText}}\n\n### INSTRUCTIONS DELTAS\nOld: {{step5.result.oldText}}\nNew: {{step5.result.newText}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-o4-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 7,
        "summary": "I-90 State Commit",
        "description": "Finalizing I-90 state into the Sovereign Knowledge Hub DB",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/sync/commit",
        "payload": {
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "displayName": "{{step1.result.displayName}}",
          "version": "{{step1.result.newVersion}}",
          "effectiveDate": "{{step1.result.effectiveDate}}",
          "hash": "{{step1.result.newHash}}",
          "instructionsHash": "{{step1.result.instructionsHash}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "instructionsUrl": "{{step1.result.instructionsUrl}}",
          "documentId": "{{step2.result.documentId??step1.result.oldDocumentId}}",
          "instructionsDocumentId": "{{step3.result.documentId??step1.result.oldInstructionsDocumentId}}",
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "oldInstructionsDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "changeType": "EDITION_UPDATE",
          "agentTaskId": "{{params.agentTaskId}}",
          "changeDetected": "{{step6.result.changeDetected??false}}",
          "summary": "{{step6.result.summary??USCIS Form I-90 scan completed - No version changes found for Green Card Replacement.}}",
          "analysis": "{{step6.result??{ \"status\": \"NO_CHANGE\", \"changeDetected\": false, \"details\": \"The surveillance check for Form I-90 was completed. No document content updates were found during this cycle.\" }}}"
        },
        "jump": {
          "condition": "{{step7.result.changeDetected}} == false",
          "conditionDesc": "No Changes Found",
          "targetStep": 0
        }
      },
      {
        "step": 8,
        "summary": "Dispatch Notification",
        "description": "Notifying priority subscribers of I-90 edition updates or fee shifts",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "resourceId": "{{params.resourceId}}",
          "type": "EDITION_UPDATE",
          "reportUrl": "https://komunas.com",
          "severity": "HIGH",
          "summary": "USCIS Form {{params.resourceId}} Updated to Edition {{step7.result.version}}",
          "details": "{{step7.result.summary}}",
          "delta": "{{step1.result.delta}}"
        }
      }
    ]
  }
}
```


### Example from uscis_form_i765_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_form_sync",
    "params": {
      "domain": "uscis-sentinel",
      "category": "forms",
      "resourceId": "I-765",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "collectionId": "69ac77018626a22133fff877",
      "agentTaskId": "69c35d61aa54fc27fe609cb1"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "I-765 Edition Check",
        "description": "Monitoring USCIS for updates to the Application for Employment Authorization, including the I-765WS worksheet and category fee logic",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/sync/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 9
        }
      },
      {
        "step": 2,
        "summary": "Form I-765 Ingestion",
        "description": "Ingressing primary I-765 PDF into Knowledge Hub",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.resourceUrl}}",
          "fileName": "{{params.resourceId}}_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 3,
        "summary": "Instruction Ingestion",
        "description": "Ingressing I-765 Instructions for updated category codes and document requirements",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.instructionsUrl}}",
          "fileName": "{{params.resourceId}}_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 4,
        "summary": "I-765WS Worksheet Ingestion",
        "description": "Ingressing Form I-765 Worksheet (I-765WS) for category-specific eligibility data",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.i765ws.url}}",
          "fileName": "I-765WS_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 5,
        "summary": "Main Form Delta Extraction",
        "description": "Extracting content shifts for the primary I-765 Form PDF",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "newDocumentId": "{{step2.result.documentId}}",
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Eligibility Category Basis",
            "Mailing Address Protocols",
            "Interpreter & Preparer Sections"
          ]
        }
      },
      {
        "step": 6,
        "summary": "Instructions Delta Extraction",
        "description": "Extracting shifts in the I-765 Instructions regarding category codes",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "newDocumentId": "{{step3.result.documentId}}",
          "resourceId": "{{params.resourceId}}_instructions",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Eligibility Category Codes (OPT, STEM, DACA, etc.)",
            "Filing Fees & Biometric Requirements",
            "Filing Locations & Online Eligibility"
          ]
        }
      },
      {
        "step": 7,
        "summary": "I-765WS Delta Extraction",
        "description": "Extracting shifts in the employment authorization worksheet",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.supplementalResources.i765ws.oldDocumentId}}",
          "newDocumentId": "{{step4.result.documentId}}",
          "resourceId": "I-765WS",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Financial Necessity Definitions",
            "Expense/Income Documentation Rules"
          ]
        }
      },
      {
        "step": 8,
        "summary": "AI Suite-Wide Employment Auth Analysis",
        "description": "Synthesizing shifts across the full I-765 suite",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a High-Sensitivity USCIS Senior Specialist in Employment Authorization. Your mission is to detect ANY deviation in policy, procedures, or text within the ENTIRE Form I-765 suite.\n\nRULES:\n1. Output MUST be valid JSON.\n2. Be extremely sensitive: Any difference in text, even minor, MUST be reported with \"changeDetected\": true.\n3. For every category marked as 'CHANGED', you MUST provide a high-fidelity delta in the 'details' field following this format: '[Location: <Document Name>, Page X, Section Y] <Change Type>: <Context>'. \n4. If the inputs are empty (Initial Discovery), summarize the current version's key category eligibility and fee protocols IF text is provided. IF the input text is empty for all documents, set \"changeDetected\": true, set status to \"INITIAL_DISCOVERY\", and in details state: \"Baseline established; waiting for full content ingestion for detailed summary.\"\n5. STRICT RULE: Do NOT invent Page numbers or specific policy changes if the input text is empty. Hallucination is UNACCEPTABLE.\n6. If ANY category status is 'CHANGED' or 'INITIAL_DISCOVERY', the top-level \"changeDetected\" MUST be true.\n7. The 'summary' field MUST be a comprehensive, human-readable synthesis of all detected shifts across the main form, instructions, and I-765WS.\n8. Output MUST follow this schema:\n{\n  \"resourceId\": \"{{params.resourceId}}\",\n  \"changeDetected\": true,\n  \"categories\": [\n    { \"name\": \"Category Name\", \"status\": \"INITIAL_DISCOVERY\", \"details\": \"[Location: Document Name] Baseline summary of current protocol...\" }\n  ],\n  \"summary\": \"[Detailed synthesis...]\"\n}"
          },
          {
            "role": "user",
            "content": "### MAIN FORM DELTAS\nOld: {{step5.result.oldText}}\nNew: {{step5.result.newText}}\n\n### INSTRUCTIONS DELTAS\nOld: {{step6.result.oldText}}\nNew: {{step6.result.newText}}\n\n### I-765WS WORKSHEET DELTAS\nOld: {{step7.result.oldText}}\nNew: {{step7.result.newText}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-o4-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 9,
        "summary": "I-765 State Commit",
        "description": "Finalizing I-765 state into the Sovereign Knowledge Hub DB",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/sync/commit",
        "payload": {
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "displayName": "{{step1.result.displayName}}",
          "version": "{{step1.result.newVersion}}",
          "effectiveDate": "{{step1.result.effectiveDate}}",
          "hash": "{{step1.result.newHash}}",
          "instructionsHash": "{{step1.result.instructionsHash}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "instructionsUrl": "{{step1.result.instructionsUrl}}",
          "supplementalResources": {
            "i765ws": {
              "name": "I-765 Worksheet",
              "url": "{{step1.result.supplementalResources.i765ws.url}}",
              "hash": "{{step1.result.supplementalResources.i765ws.hash}}",
              "documentId": "{{step4.result.documentId??step1.result.supplementalResources.i765ws.oldDocumentId}}"
            }
          },
          "documentId": "{{step2.result.documentId??step1.result.oldDocumentId}}",
          "instructionsDocumentId": "{{step3.result.documentId??step1.result.oldInstructionsDocumentId}}",
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "oldInstructionsDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "changeType": "EDITION_UPDATE",
          "agentTaskId": "{{params.agentTaskId}}",
          "changeDetected": "{{step8.result.changeDetected??false}}",
          "summary": "{{step8.result.summary??USCIS Form I-765 scan completed - No version changes found for Employment Authorization.}}",
          "analysis": "{{step8.result??{ \"status\": \"NO_CHANGE\", \"changeDetected\": false, \"details\": \"The surveillance check for Form I-765 was completed. No document content updates were found during this cycle.\" }}}"
        },
        "jump": {
          "condition": "{{step9.result.changeDetected}} == false",
          "conditionDesc": "No Changes Found",
          "targetStep": 0
        }
      },
      {
        "step": 10,
        "summary": "Dispatch Notification",
        "description": "Notifying priority subscribers of I-765 edition updates or fee shifts",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "resourceId": "{{params.resourceId}}",
          "type": "EDITION_UPDATE",
          "reportUrl": "https://komunas.com",
          "severity": "HIGH",
          "summary": "USCIS Form {{params.resourceId}} Updated to Edition {{step9.result.version}}",
          "details": "{{step9.result.summary}}",
          "delta": "{{step1.result.delta}}"
        }
      }
    ]
  }
}

```


### Example from uscis_form_i131_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_form_sync",
    "params": {
      "domain": "uscis-sentinel",
      "category": "forms",
      "resourceId": "I-131",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "collectionId": "69ac77018626a22133fff877",
      "agentTaskId": "69c086f708e1c76ac3e3d281"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "I-131 Version Check",
        "description": "Scanning USCIS for updates to Application for Travel Document, including Re-entry Permits and Advance Parole editions",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/sync/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 7
        }
      },
      {
        "step": 2,
        "summary": "Form I-131 Ingestion",
        "description": "Ingressing primary I-131 Form PDF into Knowledge Hub",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.resourceUrl}}",
          "fileName": "{{params.resourceId}}_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 3,
        "summary": "Instruction Ingestion",
        "description": "Ingressing I-131 Instructions for updated evidence and eligibility requirements",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.instructionsUrl}}",
          "fileName": "{{params.resourceId}}_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 4,
        "summary": "Main Form Delta Extraction",
        "description": "Extracting content shifts for the primary I-131 Form PDF",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "newDocumentId": "{{step2.result.documentId}}",
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Advance Parole Eligibility",
            "Re-entry Permit Requirements",
            "Refugee Travel Document Rules"
          ]
        }
      },
      {
        "step": 5,
        "summary": "Instructions Delta Extraction",
        "description": "Extracting shifts in the I-131 Instructions regarding evidence and fee protocols",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "newDocumentId": "{{step3.result.documentId}}",
          "resourceId": "{{params.resourceId}}_instructions",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Evidentiary Standards",
            "Filing Fees & Biometric Protocols",
            "Filing Locations"
          ]
        }
      },
      {
        "step": 6,
        "summary": "AI Suite-Wide Travel Document Analysis",
        "description": "Synthesizing shifts across the full I-131 travel document suite",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a High-Sensitivity USCIS Senior Specialist in Travel Documents. Your mission is to detect ANY deviation in policy, procedures, or text within the ENTIRE Form I-131 suite.\n\nRULES:\n1. Output MUST be valid JSON.\n2. Be extremely sensitive: Any difference in text, even minor, MUST be reported with \"changeDetected\": true.\n3. For every category marked as 'CHANGED', you MUST provide a high-fidelity delta in the 'details' field following this format: '[Location: <Document Name>, Page X, Section Y] <Change Type>: <Context>'. \n4. If the inputs are empty (Initial Discovery), summarize the current version's key travel/advance-parole protocols IF text is provided. IF the input text is empty for all documents, set \"changeDetected\": true, set status to \"INITIAL_DISCOVERY\", and in details state: \"Baseline established; waiting for full content ingestion for detailed summary.\"\n5. STRICT RULE: Do NOT invent Page numbers or specific policy changes if the input text is empty. Hallucination is UNACCEPTABLE.\n6. If ANY category status is 'CHANGED' or 'INITIAL_DISCOVERY', the top-level \"changeDetected\" MUST be true.\n7. The 'summary' field MUST be a comprehensive, human-readable synthesis of all detected shifts across the main form and instructions.\n8. Output MUST follow this schema:\n{\n  \"resourceId\": \"{{params.resourceId}}\",\n  \"changeDetected\": true,\n  \"categories\": [\n    { \"name\": \"Category Name\", \"status\": \"INITIAL_DISCOVERY\", \"details\": \"[Location: Document Name] Baseline summary of current protocol...\" }\n  ],\n  \"summary\": \"[Detailed synthesis...]\"\n}"
          },
          {
            "role": "user",
            "content": "### MAIN FORM DELTAS\nOld: {{step4.result.oldText}}\nNew: {{step4.result.newText}}\n\n### INSTRUCTIONS DELTAS\nOld: {{step5.result.oldText}}\nNew: {{step5.result.newText}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-o4-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 7,
        "summary": "I-131 State Commit",
        "description": "Finalizing I-131 state into the Sovereign Knowledge Hub DB",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/sync/commit",
        "payload": {
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "displayName": "{{step1.result.displayName}}",
          "version": "{{step1.result.newVersion}}",
          "effectiveDate": "{{step1.result.effectiveDate}}",
          "hash": "{{step1.result.newHash}}",
          "instructionsHash": "{{step1.result.instructionsHash}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "instructionsUrl": "{{step1.result.instructionsUrl}}",
          "documentId": "{{step2.result.documentId??step1.result.oldDocumentId}}",
          "instructionsDocumentId": "{{step3.result.documentId??step1.result.oldInstructionsDocumentId}}",
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "oldInstructionsDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "changeType": "EDITION_UPDATE",
          "agentTaskId": "{{params.agentTaskId}}",
          "changeDetected": "{{step6.result.changeDetected??false}}",
          "summary": "{{step6.result.summary??USCIS Form {{params.resourceId}} scan completed - No version changes found for Travel Documents.}}",
          "analysis": "{{step6.result??{ \"status\": \"NO_CHANGE\", \"changeDetected\": false, \"details\": \"The surveillance check for Form I-131 was completed. No document content updates were found during this cycle.\" }}}"
        },
        "jump": {
          "condition": "{{step7.result.changeDetected}} == false",
          "conditionDesc": "No Changes Found",
          "targetStep": 0
        }
      },
      {
        "step": 8,
        "summary": "Dispatch Notification",
        "description": "Notifying priority subscribers of I-131 travel policy shifts or fee updates",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "resourceId": "{{params.resourceId}}",
          "type": "EDITION_UPDATE",
          "reportUrl": "https://komunas.com",
          "severity": "HIGH",
          "summary": "USCIS Form {{params.resourceId}} Updated to Edition {{step7.result.version}}",
          "details": "{{step7.result.summary}}",
          "delta": "{{step1.result.delta}}"
        }
      }
    ]
  }
}

```


### Example from uscis_form_i129_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_form_sync",
    "params": {
      "domain": "uscis-sentinel",
      "category": "forms",
      "resourceId": "I-129",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen",
      "collectionId": "69ac77018626a22133fff877",
      "agentTaskId": "69c5a07794816c41cec7f40e"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "I-129 Edition Check",
        "description": "Monitoring USCIS for updates to Petition for Nonimmigrant Worker, including H2A, H-1B, L-1, and R-1 supplemental protocols",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/sync/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 16
        }
      },
      {
        "step": 2,
        "summary": "Form I-129 Ingestion",
        "description": "Ingressing primary I-129 PDF for nonimmigrant worker petition analysis",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.resourceUrl}}",
          "fileName": "{{params.resourceId}}_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 3,
        "summary": "Instruction Ingestion",
        "description": "Ingressing I-129 Instructions for updated filing fees and classification criteria",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.instructionsUrl}}",
          "fileName": "{{params.resourceId}}_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 4,
        "summary": "I-129H2A Ingestion",
        "description": "Ingressing Form I-129H2A for agricultural worker petitions",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.i129h2a.url}}",
          "fileName": "I-129H2A_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 5,
        "summary": "I-129H2A Instructions Ingestion",
        "description": "Ingressing Instructions for Form I-129H2A",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.i129h2ainstr.url}}",
          "fileName": "I-129H2A_instructions_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 6,
        "summary": "H-1B Checklist Ingestion",
        "description": "Ingressing M-735 Optional Checklist for H-1B filings",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.m735.url}}",
          "fileName": "M-735_H1B_Checklist_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 7,
        "summary": "H-2A Checklist Ingestion",
        "description": "Ingressing M-1097 Optional Checklist for H-2A filings",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.m1097.url}}",
          "fileName": "M-1097_H2A_Checklist_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 8,
        "summary": "H-2B Checklist Ingestion",
        "description": "Ingressing M-1087 Optional Checklist for H-2B filings",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.m1087.url}}",
          "fileName": "M-1087_H2B_Checklist_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 9,
        "summary": "R-1 Checklist Ingestion",
        "description": "Ingressing M-736 Optional Checklist for Religious Workers (R-1)",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.m736.url}}",
          "fileName": "M-736_R1_Checklist_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 10,
        "summary": "DOT Codes Ingestion",
        "description": "Ingressing M-746 Dictionary of Occupational Titles (DOT) Codes",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/ingression/url",
        "payload": {
          "url": "{{step1.result.supplementalResources.m746.url}}",
          "fileName": "M-746_DOT_Codes_{{step1.result.newVersion}}.pdf",
          "collectionId": "{{params.collectionId}}",
          "teamId": "{{params.teamId}}",
          "contentType": "application/pdf"
        }
      },
      {
        "step": 11,
        "summary": "Main Form Delta Extraction",
        "description": "Extracting content shifts for the primary I-129 Form PDF",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "newDocumentId": "{{step2.result.documentId}}",
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Temporary Employment Classifications (H, L, O, P)",
            "Religious Worker Eligibility (R-1)",
            "Petitioner Evidentiary Requirements"
          ]
        }
      },
      {
        "step": 12,
        "summary": "Instructions Delta Extraction",
        "description": "Extracting shifts in the I-129 Instructions regarding filing fees and classification criteria",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "newDocumentId": "{{step3.result.documentId}}",
          "resourceId": "{{params.resourceId}}_instructions",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Classification Criteria",
            "Filing Fee Schedules",
            "Residency Requirements"
          ]
        }
      },
      {
        "step": 13,
        "summary": "H-2A Suite Delta Extraction",
        "description": "Extracting shifts in the H-2A supplemental form and instructions",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.supplementalResources.i129h2a.oldDocumentId}}",
          "newDocumentId": "{{step4.result.documentId}}",
          "resourceId": "I-129H2A",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Agricultural Labor Standards",
            "H-2A Classification Specifics"
          ]
        }
      },
      {
        "step": 14,
        "summary": "Checklists & DOT Delta Extraction",
        "description": "Extracting shifts across M-735, M-736, M-1097, M-1087, and M-746 Dictionary of Occupational Titles",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/kh/sync/delta-content",
        "payload": {
          "oldDocumentId": "{{step1.result.supplementalResources.m735.oldDocumentId}}",
          "newDocumentId": "{{step6.result.documentId}}",
          "resourceId": "I-129_Checklists",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "categories": [
            "Evidence Checklists (H1B, R1, H2A, H2B)",
            "DOT Code Mapping Shifts"
          ]
        }
      },
      {
        "step": 15,
        "summary": "AI Suite-Wide Labor Analysis",
        "description": "Synthesizing shifts across the full I-129 labor petition suite",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a High-Sensitivity USCIS Senior Specialist in Nonimmigrant Worker Petitions. Your mission is to detect ANY deviation in policy, procedures, or text within the ENTIRE Form I-129 suite.\n\nRULES:\n1. Output MUST be valid JSON.\n2. Be extremely sensitive: Any difference in text, even minor, MUST be reported with \"changeDetected\": true.\n3. For every category marked as 'CHANGED', you MUST provide a high-fidelity delta in the 'details' field following this format: '[Location: <Document Name>, Page X, Section Y] <Change Type>: <Context>'. \n4. If the inputs are empty (Initial Discovery), summarize the current version's key classifications/checklists IF text is provided. IF the input text is empty for all documents, set \"changeDetected\": true, set status to \"INITIAL_DISCOVERY\", and in details state: \"Baseline established; waiting for full content ingestion for detailed summary.\"\n5. STRICT RULE: Do NOT invent Page numbers or specific policy changes if the input text is empty. Hallucination is UNACCEPTABLE.\n6. If ANY category status is 'CHANGED' or 'INITIAL_DISCOVERY', the top-level \"changeDetected\" MUST be true.\n7. The 'summary' field MUST be a comprehensive, human-readable synthesis of all detected shifts across the main form, instructions, H-2A suite, and specialized checklists.\n8. Output MUST follow this schema:\n{\n  \"resourceId\": \"{{params.resourceId}}\",\n  \"changeDetected\": true,\n  \"categories\": [\n    { \"name\": \"Category Name\", \"status\": \"INITIAL_DISCOVERY\", \"details\": \"[Location: Document Name] Baseline summary of current protocol...\" }\n  ],\n  \"summary\": \"[Detailed synthesis...]\"\n}"
          },
          {
            "role": "user",
            "content": "### MAIN FORM DELTAS\nOld: {{step11.result.oldText}}\nNew: {{step11.result.newText}}\n\n### INSTRUCTIONS DELTAS\nOld: {{step12.result.oldText}}\nNew: {{step12.result.newText}}\n\n### H-2A SUITE DELTAS\nOld: {{step13.result.oldText}}\nNew: {{step13.result.newText}}\n\n### CHECKLISTS & DOT DELTAS\nOld: {{step14.result.oldText}}\nNew: {{step14.result.newText}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-o4-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 16,
        "summary": "I-129 State Commit",
        "description": "Finalizing I-129 state (including specialized supplements) into the Sovereign Knowledge Hub DB",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/sync/commit",
        "payload": {
          "resourceId": "{{params.resourceId}}",
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "displayName": "{{step1.result.displayName}}",
          "version": "{{step1.result.newVersion}}",
          "effectiveDate": "{{step1.result.effectiveDate}}",
          "hash": "{{step1.result.newHash}}",
          "instructionsHash": "{{step1.result.instructionsHash}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "instructionsUrl": "{{step1.result.instructionsUrl}}",
          "supplementalResources": {
            "i129h2a": {
              "name": "Form I-129H2A",
              "url": "{{step1.result.supplementalResources.i129h2a.url}}",
              "hash": "{{step1.result.supplementalResources.i129h2a.hash}}",
              "documentId": "{{step4.result.documentId??step1.result.supplementalResources.i129h2a.oldDocumentId}}"
            },
            "i129h2ainstr": {
              "name": "Instructions I-129H2A",
              "url": "{{step1.result.supplementalResources.i129h2ainstr.url}}",
              "hash": "{{step1.result.supplementalResources.i129h2ainstr.hash}}",
              "documentId": "{{step5.result.documentId??step1.result.supplementalResources.i129h2ainstr.oldDocumentId}}"
            },
            "m735": {
              "name": "H-1B Checklist",
              "url": "{{step1.result.supplementalResources.m735.url}}",
              "hash": "{{step1.result.supplementalResources.m735.hash}}",
              "documentId": "{{step6.result.documentId??step1.result.supplementalResources.m735.oldDocumentId}}"
            },
            "m1097": {
              "name": "H-2A Checklist",
              "url": "{{step1.result.supplementalResources.m1097.url}}",
              "hash": "{{step1.result.supplementalResources.m1097.hash}}",
              "documentId": "{{step7.result.documentId??step1.result.supplementalResources.m1097.oldDocumentId}}"
            },
            "m1087": {
              "name": "H-2B Checklist",
              "url": "{{step1.result.supplementalResources.m1087.url}}",
              "hash": "{{step1.result.supplementalResources.m1087.hash}}",
              "documentId": "{{step8.result.documentId??step1.result.supplementalResources.m1087.oldDocumentId}}"
            },
            "m736": {
              "name": "R-1 Checklist",
              "url": "{{step1.result.supplementalResources.m736.url}}",
              "hash": "{{step1.result.supplementalResources.m736.hash}}",
              "documentId": "{{step9.result.documentId??step1.result.supplementalResources.m736.oldDocumentId}}"
            },
            "m746": {
              "name": "DOT Codes Dictionary",
              "url": "{{step1.result.supplementalResources.m746.url}}",
              "hash": "{{step1.result.supplementalResources.m746.hash}}",
              "documentId": "{{step10.result.documentId??step1.result.supplementalResources.m746.oldDocumentId}}"
            }
          },
          "documentId": "{{step2.result.documentId??step1.result.oldDocumentId}}",
          "instructionsDocumentId": "{{step3.result.documentId??step1.result.oldInstructionsDocumentId}}",
          "oldDocumentId": "{{step1.result.oldDocumentId}}",
          "oldInstructionsDocumentId": "{{step1.result.oldInstructionsDocumentId}}",
          "changeType": "EDITION_UPDATE",
          "agentTaskId": "{{params.agentTaskId}}",
          "changeDetected": "{{step15.result.changeDetected??false}}",
          "summary": "{{step15.result.summary??USCIS Form I-129 suite scan completed - No nonimmigrant classification shifts detected.}}",
          "analysis": "{{step15.result??{ \"status\": \"NO_CHANGE\", \"changeDetected\": false, \"details\": \"The surveillance check for Form I-129 was completed. No document content updates were found during this cycle.\" }}}"
        },
        "jump": {
          "condition": "{{step16.result.changeDetected}} == false",
          "conditionDesc": "No Classification Changes",
          "targetStep": 0
        }
      },
      {
        "step": 17,
        "summary": "Dispatch Notification",
        "description": "Notifying priority subscribers of Form I-129 edition updates or classification shifts",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "{{params.domain}}",
          "category": "{{params.category}}",
          "resourceId": "{{params.resourceId}}",
          "type": "EDITION_UPDATE",
          "reportUrl": "https://komunas.com",
          "severity": "HIGH",
          "summary": "USCIS Form {{params.resourceId}} Updated to Edition {{step16.result.version}}",
          "details": "{{step16.result.summary}}",
          "delta": "{{step1.result.delta}}"
        }
      }
    ]
  }
}

```


### Example from uscis_newsroom_alerts_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_newsroom_sync",
    "params": {
      "resourceCategory": "announcements",
      "resourceId": "newsroom-alerts",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "Newsroom Alert Check",
        "description": "Monitoring USCIS Newsroom Alerts for new announcements and updates",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/newsroom/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 0
        }
      },
      {
        "step": 2,
        "summary": "AI Announcement Digest",
        "description": "Synthesizing a high-fidelity intelligence digest of recent USCIS announcements.",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a USCIS Policy Analyst. Review the provided JSON of the latest USCIS announcements. Provide a high-level summary of the overall activity, and then provide a SUBSTANTIVE summary for EACH alert. Explain the 'WHY' behind the alert and its 'PRACTICAL IMPACT' on applicants or practitioners. Avoid generic filler. You MUST preserve the exact 'url', 'title', and 'date' from the input for each alert. Output MUST follow this exact JSON schema: { \"title\": \"Short 3-6 word catchy title summarizing the most important update\", \"summary\": \"A high-fidelity 2-3 sentence overview of all recent USCIS activity covered in this digest.\", \"alerts\": [ { \"title\": \"Original Title\", \"date\": \"Original Date\", \"summary\": \"A detailed 3-5 sentence intelligence summary explaining the substantive changes and practical impact of this specific alert.\", \"url\": \"Original URL\" } ] }. IMPORTANT: Do NOT truncate or use ellipsis (...) in any field. Write complete sentences. Ensure your generated summaries use standard spaces only."
          },
          {
            "role": "user",
            "content": "Latest Announcements Payload:\n{{step1.result.payload}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-4o-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 3,
        "summary": "Commit Resource Metadata",
        "description": "Committing detected changes and AI analysis to the central resource repository",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/newsroom/commit",
        "payload": {
          "domain": "uscis-sentinel",
          "category": "announcements",
          "resourceId": "newsroom-alerts",
          "version": "{{step1.result.newVersion}}",
          "hash": "{{step1.result.newHash}}",
          "changeType": "ANNOUNCEMENT_UPDATE",
          "changeDetected": "{{step1.result.changed}}",
          "summary": "New announcements: {{step2.result.output.title}} ({{step1.result.newVersion}})",
          "analysis": "{{step2.result}}",
          "payload": "{{step2.result.output}}"
        }
      },
      {
        "step": 4,
        "summary": "Dispatch Notification",
        "description": "Dispatching high-severity notifications for new USCIS announcements",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "uscis-sentinel",
          "category": "announcements",
          "resourceId": "newsroom-alerts",
          "type": "NEWS_UPDATE",
          "reportUrl": "{{step1.result.resourceUrl}}",
          "severity": "HIGH",
          "summary": "USCIS Alert: {{step2.result.output.title}} ({{step1.result.newVersion}})",
          "details": "{{step2.result.output.summary}}",
          "delta": {
            "alerts": "{{step2.result.output.alerts}}"
          }
        }
      }
    ]
  }
}

```


### Example from uscis_news_releases_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_newsroom_sync",
    "params": {
      "resourceCategory": "announcements",
      "resourceId": "news-releases",
      "teamId": "67d0aeb17172416c411d419e",
      "userId": "timursen"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "News Releases Check",
        "description": "Checking USCIS Newsroom for latest press releases and official news updates",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/newsroom/check/{{params.resourceId}}",
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 0
        }
      },
      {
        "step": 2,
        "summary": "AI News Release Digest",
        "description": "Synthesizing a high-fidelity digest of recent USCIS news releases with AI analysis.",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "payload": [
          {
            "role": "system",
            "content": "You are a USCIS Media Liaison. Review the provided JSON of the latest USCIS news releases. Provide a high-level summary of the recent news activity, and then provide a DETAILED summary for EACH release. Focus on why this news matters and what it signals for future policy or processing. You MUST preserve the exact 'url', 'title', and 'date' from the input for each item. Output MUST follow this exact JSON schema: { \"title\": \"Short 3-6 word catchy title for this news cycle\", \"summary\": \"A high-fidelity 2-3 sentence overview of all recent news releases covered in this digest.\", \"alerts\": [ { \"title\": \"Original Title\", \"date\": \"Original Date\", \"summary\": \"A descriptive 3-5 sentence intelligence summary providing full context and significance for this news release.\", \"url\": \"Original URL\" } ] }. IMPORTANT: Do NOT truncate or use ellipsis (...) in any field. Ensure your generated summaries use standard spaces only."
          },
          {
            "role": "user",
            "content": "Latest News Releases Payload:\n{{step1.result.payload}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-4o-mini",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 4096
          }
        }
      },
      {
        "step": 3,
        "summary": "Commit News Metadata",
        "description": "Committing news releases and AI analysis to the central repository",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/newsroom/commit",
        "payload": {
          "domain": "uscis-sentinel",
          "category": "announcements",
          "resourceId": "news-releases",
          "version": "{{step1.result.newVersion}}",
          "hash": "{{step1.result.newHash}}",
          "changeType": "ANNOUNCEMENT_UPDATE",
          "changeDetected": "{{step1.result.changed}}",
          "summary": "New news releases: {{step2.result.output.title}} ({{step1.result.newVersion}})",
          "analysis": "{{step2.result}}",
          "payload": "{{step2.result.output}}"
        }
      },
      {
        "step": 4,
        "summary": "Dispatch Notification",
        "description": "Dispatching high-severity notifications for new USCIS news releases",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "payload": {
          "domain": "uscis-sentinel",
          "category": "announcements",
          "resourceId": "news-releases",
          "type": "NEWS_UPDATE",
          "reportUrl": "{{step1.result.resourceUrl}}",
          "severity": "HIGH",
          "summary": "USCIS News: {{step2.result.output.title}} ({{step1.result.newVersion}})",
          "details": "{{step2.result.output.summary}}",
          "delta": {
            "alerts": "{{step2.result.output.alerts}}"
          }
        }
      }
    ]
  }
}

```


### Example from uscis_processing_times_i130_sync_workflow.json

```json
{
  "link": {
    "target": "workflow",
    "action": "execute"
  },
  "query": {
    "intent": "uscis_processing_times_i130_sync",
    "params": {
      "resourceCategory": "processing-times",
      "formId": "I-130",
      "teamId": "681a97eaec715f343bcb1ecf",
      "userId": "timursen"
    },
    "workflow": [
      {
        "step": 1,
        "summary": "Processing Times Fetch",
        "description": "Continuous batch monitoring of the USCIS Processing Times API specifically for Form I-130 (Petition for Alien Relative) across all preference categories (Immediate Relatives, F1-F4) and offices (NBC, SCD, FOD).",
        "target": "komunas-app",
        "action": "fetch",
        "intent": "/api/uscis/processing-times/check/form/{{params.formId}}",
        "params": {},
        "payload": null,
        "llmConfig": null,
        "async": null,
        "jump": {
          "condition": "{{step1.result.shouldSync}} == false",
          "conditionDesc": "Up-to-Date",
          "targetStep": 0
        }
      },
      {
        "step": 2,
        "summary": "AI Processing Times Digest",
        "description": "Analyzing I-130 processing times API response to determine 80th percentile shifts, Visa Bulletin constraints, and prioritization notes.",
        "target": "openai-chat",
        "action": "generate",
        "intent": "generate",
        "params": null,
        "payload": [
          {
            "role": "system",
            "content": "You are an expert USCIS Immigration Intelligence Analyst. Your task is to analyze the latest batch of processing times specifically for Form I-130 (Petition for Alien Relative). You will receive a JSON payload containing processing data across multiple service centers (NBC, SCD) and Field Offices (FOD) for various family preference categories. INSTRUCTIONS: 1. Identify Trends: Compare shifts across offices and categories. 2. Group by Impact: Highlight differences between Immediate Relatives (IR) vs. Family Preference categories (F11, F21, F31, F41). 3. Interpret 'See notes': If an estimated time is 'See notes', read the accompanying text and explain that USCIS is prioritizing cases based on Department of State Visa Bulletin constraints rather than fixed timelines. Output MUST follow this exact JSON schema: { \"title\": \"Catchy 4-7 word title summarizing I-130 processing times\", \"summary\": \"A high-fidelity 1-2 sentence overview of family-based petition backlogs.\", \"formId\": \"I-130\", \"percentile80\": \"e.g. 17.5-261.5 Months depending on office/category\", \"trend\": \"e.g. Increasing/Decreasing/Stable\", \"analysis\": \"Substantive impact analysis across the different field offices and categories, specifically addressing Visa Bulletin constraints when applicable.\" }. Ensure summaries are professional, objective, and avoid filler."
          },
          {
            "role": "user",
            "content": "Latest Processing Times Payload:\n{{step1.result.payload}}"
          }
        ],
        "llmConfig": {
          "model": "gpt-4o",
          "settings": {
            "temperature": 0.1,
            "max.tokens": 2048
          }
        },
        "async": null,
        "cacheConfig": null
      },
      {
        "step": 3,
        "summary": "Commit Processing Times Metadata",
        "description": "Persisting structured processing times data and version hashes into the Sovereign DB via Komunas.",
        "target": "komunas-app",
        "action": "create",
        "intent": "/api/uscis/processing-times/commit",
        "params": null,
        "payload": {
          "domain": "uscis-sentinel",
          "category": "processing-times",
          "resourceId": "{{params.formId}}",
          "resourceUrl": "{{step1.result.resourceUrl}}",
          "version": "{{step1.result.newVersion}}",
          "hash": "{{step1.result.newHash}}",
          "changeDetected": "{{step1.result.changed}}",
          "summary": "{{step2.result.output.summary}}",
          "analysis": "{{step2.result}}",
          "payload": {
            "title": "{{step2.result.output.title}}",
            "summary": "{{step2.result.output.summary}}",
            "formId": "{{step2.result.output.formId}}",
            "percentile80": "{{step2.result.output.percentile80}}",
            "trend": "{{step2.result.output.trend}}",
            "analysis": "{{step2.result.output.analysis}}",
            "combinations": "{{step1.result.payload}}"
          }
        },
        "llmConfig": null,
        "async": null
      },
      {
        "step": 4,
        "summary": "Dispatch Notification",
        "description": "Routing the formatted processing times update to the Notification Service for email broadcast.",
        "target": "api-gateway",
        "action": "create",
        "intent": "/api/notifications/dispatch",
        "params": null,
        "payload": {
          "domain": "uscis-sentinel",
          "category": "processing-times",
          "resourceId": "{{params.formId}}",
          "type": "PROCESSING_TIMES_UPDATE",
          "reportUrl": "{{step1.result.resourceUrl}}",
          "severity": "MEDIUM",
          "summary": "Processing Times Alert: {{step2.result.output.title}}",
          "details": "{{step2.result.output.summary}}",
          "delta": {
            "percentile80": "{{step2.result.output.percentile80}}",
            "trend": "{{step2.result.output.trend}}",
            "analysis": "{{step2.result.output.analysis}}",
            "injectedHtml": "{{step1.result.payload.injectedHtml}}"
          }
        },
        "llmConfig": null,
        "async": false
      }
    ]
  }
}

```

