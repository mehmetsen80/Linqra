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
