# Quotes-Service RAG Workflow (Starting from Scratch)

## Initial Setup

### 1.1 Create Empty Milvus Collection
```bash
# Create collection for quotes (one-time setup)
POST /api/milvus/collections
{
  "collectionName": "famous_quotes_openai_text_embedding_3_small_1536",
  "schemaFields": [
    {"name": "id", "dtype": "INT64", "is_primary": true},
    {"name": "person_name", "dtype": "VARCHAR", "max_length": 100},
    {"name": "quote_text", "dtype": "VARCHAR", "max_length": 1000},
    {"name": "context", "dtype": "VARCHAR", "max_length": 500},
    {"name": "category", "dtype": "VARCHAR", "max_length": 50},
    {"name": "embedding", "dtype": "FLOAT_VECTOR", "dim": 1536}
  ],
  "description": "Famous quotes with embeddings for semantic search",
  "teamId": "67d0aeb17172416c411d419e"
}
```

## Linqra Workflow Integration

### 1.1 Basic Historical Saying Generator (Working Example)

**Part 1: Create the Workflow Definition**

First, create the workflow using the API:

```bash
POST https://localhost:7777/linq/workflows
Content-Type: application/json

{
    "name": "Quotes of Famous People",
    "description": "This is quotes application of famous people.",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
            "params": {
                "topic": "psychology"
            },
            "workflow": [
                {
                    "step": 1,
                    "target": "quotes-service",
                    "action": "fetch",
                    "intent": "/api/people/random",
                    "params": {},
                    "cacheConfig": {
                        "enabled": true,
                        "ttl": "86400",
                        "key": "historical_people_cache"
                    }
                },
                {
                    "step": 2,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}} about {{params.topic}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Output only a single inspirational saying by {{step1.result.fullName}} about {{params.topic}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.9,
                            "max_tokens": 200
                        }
                    }
                }
            ]
        }
    },
    "isPublic": false
}
```

**What Gets Saved to MongoDB:**

The system automatically adds several fields when saving the workflow. Here's the complete document structure that gets saved:

```json
{
    "id": "6857750f023fa3190a2d63a5",
    "name": "Quotes of Famous People",
    "description": "This is quotes application of famous people.",
    "teamId": "67d0aeb17172416c411d419e",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
            "workflowId": "6857750f023fa3190a2d63a5",
            "params": {
                "topic": "psychology",
                "teamId": "67d0aeb17172416c411d419e"
            },
            "payload": null,
            "toolConfig": null,
            "workflow": [
                {
                    "step": 1,
                    "target": "quotes-service",
                    "action": "fetch",
                    "intent": "/api/people/random",
                    "params": {},
                    "payload": null,
                    "toolConfig": null,
                    "async": null,
                    "cacheConfig": {
                        "enabled": true,
                        "ttl": "86400",
                        "key": "historical_people_cache"
                    }
                },
                {
                    "step": 2,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}} about {{params.topic}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Output only a single inspirational saying by {{step1.result.fullName}} about {{params.topic}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.9,
                            "max_tokens": 200
                        }
                    },
                    "async": null,
                    "cacheConfig": null
                }
            ]
        },
        "executedBy": null
    },
    "createdAt": [
        2025,
        6,
        21,
        22,
        14,
        23,
        776137000
    ],
    "updatedAt": [
        2025,
        6,
        21,
        22,
        14,
        23,
        776263000
    ],
    "createdBy": "timursen",
    "updatedBy": "timursen",
    "version": 1,
    "public": false
}
```

**Key Changes Made by the System:**

1. **`id`**: Auto-generated MongoDB ObjectId
2. **`teamId`**: Automatically set from the current user's team context
3. **`workflowId`**: Added to the query (same as the document ID)
4. **`params.teamId`**: Automatically added to the query parameters for team context
5. **`createdAt`/`updatedAt`**: Timestamps in MongoDB date format
6. **`createdBy`/`updatedBy`**: Username of the user who created/updated the workflow
7. **`version`**: Starts at 1 for new workflows
8. **`public`**: Boolean flag for workflow visibility
9. **`executedBy`**: Set to null initially (will be populated during execution)

**Part 2: Execute the Workflow**

Now that the workflow is saved, you can execute it using the workflow ID:

```bash
POST https://localhost:7777/linq/workflows/6857750f023fa3190a2d63a5/execute
Content-Type: application/json
x-api-key: {Api Key}
x-api-key-name: {Api Key Name}
Authorization: Bearer {Token}

# Empty payload - the topic parameter is already defined in the workflow
{}
```

**Response:**
```json
{
    "result": {
        "steps": [
            {
                "step": 1,
                "target": "quotes-service",
                "result": {
                    "fullName": "Jean Piaget",
                    "knownAs": "The Child Development Theorist",
                    "birthYear": 1896,
                    "deathYear": 1980,
                    "nationality": "Swiss",
                    "description": "Swiss psychologist known for cognitive development",
                    "category": "Psychologists"
                },
                "params": null,
                "action": null,
                "intent": null,
                "executionId": null,
                "async": false
            },
            {
                "step": 2,
                "target": "openai",
                "result": {
                    "id": "chatcmpl-Bl5pQSJZOmm7ItiIUfAHZIl8sXHJv",
                    "object": "chat.completion",
                    "created": 1750563520,
                    "model": "gpt-4o-2024-08-06",
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": "Understanding is based on the ability to invent and reinvent.",
                                "refusal": null,
                                "annotations": []
                            },
                            "logprobs": null,
                            "finish_reason": "stop"
                        }
                    ],
                    "usage": {
                        "prompt_tokens": 42,
                        "completion_tokens": 11,
                        "total_tokens": 53,
                        "prompt_tokens_details": {
                            "cached_tokens": 0,
                            "audio_tokens": 0
                        },
                        "completion_tokens_details": {
                            "reasoning_tokens": 0,
                            "audio_tokens": 0,
                            "accepted_prediction_tokens": 0,
                            "rejected_prediction_tokens": 0
                        }
                    },
                    "service_tier": "default",
                    "system_fingerprint": "fp_a288987b44"
                },
                "params": null,
                "action": null,
                "intent": null,
                "executionId": null,
                "async": false
            }
        ],
        "finalResult": "Understanding is based on the ability to invent and reinvent.",
        "pendingAsyncSteps": null
    },
    "metadata": {
        "source": null,
        "status": "success",
        "teamId": "67d0aeb17172416c411d419e",
        "cacheHit": false,
        "workflowMetadata": [
            {
                "step": 1,
                "status": "success",
                "durationMs": 216,
                "target": "quotes-service",
                "executedAt": [
                    2025,
                    6,
                    21,
                    22,
                    38,
                    38,
                    663066000
                ],
                "tokenUsage": null,
                "model": null,
                "async": false
            },
            {
                "step": 2,
                "status": "success",
                "durationMs": 2186,
                "target": "openai",
                "executedAt": [
                    2025,
                    6,
                    21,
                    22,
                    38,
                    40,
                    849728000
                ],
                "tokenUsage": {
                    "promptTokens": 42,
                    "completionTokens": 11,
                    "totalTokens": 53
                },
                "model": "gpt-4o-2024-08-06",
                "async": false
            }
        ],
        "asyncSteps": null
    }
}
```

**Execution Response Analysis:**

1. **Step Results**: Each step shows the actual result from the service
   - **Step 1**: Returns Jean Piaget's biographical data from quotes-service
   - **Step 2**: Returns the generated quote from OpenAI

2. **Final Result**: The clean quote text extracted from the OpenAI response

3. **Metadata**: Detailed execution information including:
   - **Duration**: Each step's execution time in milliseconds
   - **Token Usage**: OpenAI API token consumption
   - **Timestamps**: When each step was executed
   - **Status**: Success/failure status for each step

4. **Team Context**: The `teamId` is maintained throughout the execution

**Key Features:**
- ✅ **Predefined Workflow**: Workflow is created once and executed by ID
- ✅ **Global Parameters**: Uses `{{params.topic}}` to access the topic from the request
- ✅ **Step Results**: Uses `{{step1.result.fullName}}` to access the random person's name
- ✅ **Caching**: Step 1 uses caching to avoid repeated API calls for historical people
- ✅ **Clean Output**: Generates only the quote text without extra formatting

**Usage Pattern:**
1. **Create workflow once** with the workflow definition
2. **Execute workflow multiple times** with different parameters
3. **Get clean results** with step-by-step execution details

### 2.1 Historical Figure Data Storage Workflow (RAG Preparation)

This workflow demonstrates how to store historical figure data in Milvus for future RAG (Retrieval-Augmented Generation) operations. It has 3 steps:

1. **Step 1**: Fetch random historical figure data
2. **Step 2**: Store the figure data in Milvus collection (RAG database)
3. **Step 3**: Generate a quote using the figure data

**Part 1: Create the Workflow Definition**

```bash
POST https://localhost:7777/linq/workflows
Content-Type: application/json

{
    "name": "Quotes of Famous People",
    "description": "This is quotes application of famous people.",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
            "params": {
                "topic": "relationships"
            },
            "workflow": [
                {
                    "step": 1,
                    "target": "quotes-service",
                    "action": "fetch",
                    "intent": "/api/people/random",
                    "params": {},
                    "cacheConfig": {
                        "enabled": false,
                        "ttl": "86400",
                        "key": "historical_people_cache"
                    }
                },
                {
                    "step": 2,
                    "target": "api-gateway",
                    "action": "create",
                    "intent": "/api/milvus/collections/historical_figures_openai_text_embedding_3_small_1536/records",
                    "payload": {
                        "record": {
                            "fullName": "{{step1.result.fullName}}",
                            "knownAs": "{{step1.result.knownAs}}",
                            "birthYear": "{{step1.result.birthYear}}",
                            "deathYear": "{{step1.result.deathYear}}",
                            "nationality": "{{step1.result.nationality}}",
                            "description": "{{step1.result.description}}",
                            "category": "{{step1.result.category}}"
                        },
                        "targetTool": "openai-embed",
                        "modelType": "text-embedding-3-small",
                        "textField": "description",
                        "teamId": "{{params.teamId}}"
                    }
                },
                {
                    "step": 3,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Output only a single inspirational saying by {{step1.result.fullName}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.9,
                            "max_tokens": 200
                        }
                    }
                }
            ]
        }
    },
    "isPublic": false
}
```

**What Gets Saved to MongoDB:**

```json
{
    "id": "6857784d023fa3190a2d63a7",
    "name": "Quotes of Famous People",
    "description": "This is quotes application of famous people.",
    "teamId": "67d0aeb17172416c411d419e",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
            "workflowId": "6857784d023fa3190a2d63a7",
            "params": {
                "topic": "relationships",
                "teamId": "67d0aeb17172416c411d419e"
            },
            "payload": null,
            "toolConfig": null,
            "workflow": [
                {
                    "step": 1,
                    "target": "quotes-service",
                    "action": "fetch",
                    "intent": "/api/people/random",
                    "params": {},
                    "payload": null,
                    "toolConfig": null,
                    "async": null,
                    "cacheConfig": {
                        "enabled": false,
                        "ttl": "86400",
                        "key": "historical_people_cache"
                    }
                },
                {
                    "step": 2,
                    "target": "api-gateway",
                    "action": "create",
                    "intent": "/api/milvus/collections/historical_figures_openai_text_embedding_3_small_1536/records",
                    "params": null,
                    "payload": {
                        "record": {
                            "fullName": "{{step1.result.fullName}}",
                            "knownAs": "{{step1.result.knownAs}}",
                            "birthYear": "{{step1.result.birthYear}}",
                            "deathYear": "{{step1.result.deathYear}}",
                            "nationality": "{{step1.result.nationality}}",
                            "description": "{{step1.result.description}}",
                            "category": "{{step1.result.category}}"
                        },
                        "targetTool": "openai-embed",
                        "modelType": "text-embedding-3-small",
                        "textField": "description",
                        "teamId": "{{params.teamId}}"
                    },
                    "toolConfig": null,
                    "async": null,
                    "cacheConfig": null
                },
                {
                    "step": 3,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Output only a single inspirational saying by {{step1.result.fullName}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.9,
                            "max_tokens": 200
                        }
                    },
                    "async": null,
                    "cacheConfig": null
                }
            ]
        },
        "executedBy": null
    },
    "createdAt": [
        2025,
        6,
        21,
        22,
        28,
        13,
        178585000
    ],
    "updatedAt": [
        2025,
        6,
        21,
        22,
        28,
        13,
        178720000
    ],
    "createdBy": "timursen",
    "updatedBy": "timursen",
    "version": 1,
    "public": false
}
```

**Workflow Steps Explanation:**

1. **Step 1 - Data Retrieval**: Fetches random historical figure data from the quotes-service microservice
2. **Step 2 - RAG Storage**: Stores the historical figure data in Milvus collection for future semantic search operations. This step creates embeddings of the figure's description for later retrieval.
3. **Step 3 - Quote Generation**: Generates an inspirational quote using the figure's name from Step 1

**Key Features:**
- ✅ **RAG Preparation**: Stores historical figure data with embeddings for future semantic search
- ✅ **Data Persistence**: Each execution adds a new historical figure to the Milvus collection
- ✅ **Quote Generation**: Still generates quotes as the final output
- ✅ **Team Context**: Automatically includes teamId for multi-tenant data isolation

**Purpose:**
This workflow builds a knowledge base of historical figures in Milvus. Over time, as you run this workflow multiple times, you'll accumulate a database of historical figures that can be used for semantic search in more advanced RAG workflows.

**Part 2: Execute the Workflow**

Now that the workflow is saved, you can execute it using the workflow ID:

```bash
POST https://localhost:7777/linq/workflows/6857784d023fa3190a2d63a7/execute
Content-Type: application/json
x-api-key: {api key}
x-api-key-name: {api key name}
Authorization: Bearer {Token}

# Empty payload - the topic parameter is already defined in the workflow
{}
```

**Response:**
```json
{
    "result": {
        "steps": [
            {
                "step": 1,
                "target": "quotes-service",
                "result": {
                    "fullName": "Nazım Hikmet",
                    "knownAs": "The Revolutionary Poet",
                    "birthYear": 1902,
                    "deathYear": 1963,
                    "nationality": "Turkish",
                    "description": "Turkish poet known for his political and romantic poetry",
                    "category": "Writers"
                },
                "params": null,
                "action": null,
                "intent": null,
                "executionId": null,
                "async": false
            },
            {
                "step": 2,
                "target": "api-gateway",
                "result": {
                    "message": "Record stored successfully in collection historical_figures_openai_text_embedding_3_small_1536"
                },
                "params": null,
                "action": null,
                "intent": null,
                "executionId": null,
                "async": false
            },
            {
                "step": 3,
                "target": "openai",
                "result": {
                    "id": "chatcmpl-Bl62EkldILXI5ooBRcWfYDfmddf4y",
                    "object": "chat.completion",
                    "created": 1750564314,
                    "model": "gpt-4o-2024-08-06",
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": "To live like a tree, single and at liberty and brotherly like the trees of a forest.",
                                "refusal": null,
                                "annotations": []
                            },
                            "logprobs": null,
                            "finish_reason": "stop"
                        }
                    ],
                    "usage": {
                        "prompt_tokens": 41,
                        "completion_tokens": 20,
                        "total_tokens": 61,
                        "prompt_tokens_details": {
                            "cached_tokens": 0,
                            "audio_tokens": 0
                        },
                        "completion_tokens_details": {
                            "reasoning_tokens": 0,
                            "audio_tokens": 0,
                            "accepted_prediction_tokens": 0,
                            "rejected_prediction_tokens": 0
                        }
                    },
                    "service_tier": "default",
                    "system_fingerprint": "fp_a288987b44"
                },
                "params": null,
                "action": null,
                "intent": null,
                "executionId": null,
                "async": false
            }
        ],
        "finalResult": "To live like a tree, single and at liberty and brotherly like the trees of a forest.",
        "pendingAsyncSteps": null
    },
    "metadata": {
        "source": null,
        "status": "success",
        "teamId": "67d0aeb17172416c411d419e",
        "cacheHit": false,
        "workflowMetadata": [
            {
                "step": 1,
                "status": "success",
                "durationMs": 69,
                "target": "quotes-service",
                "executedAt": [
                    2025,
                    6,
                    21,
                    22,
                    51,
                    53,
                    665571000
                ],
                "tokenUsage": null,
                "model": null,
                "async": false
            },
            {
                "step": 2,
                "status": "success",
                "durationMs": 810,
                "target": "api-gateway",
                "executedAt": [
                    2025,
                    6,
                    21,
                    22,
                    51,
                    54,
                    476162000
                ],
                "tokenUsage": null,
                "model": null,
                "async": false
            },
            {
                "step": 3,
                "status": "success",
                "durationMs": 790,
                "target": "openai",
                "executedAt": [
                    2025,
                    6,
                    21,
                    22,
                    51,
                    55,
                    266534000
                ],
                "tokenUsage": {
                    "promptTokens": 41,
                    "completionTokens": 20,
                    "totalTokens": 61
                },
                "model": "gpt-4o-2024-08-06",
                "async": false
            }
        ],
        "asyncSteps": null
    }
}
```

**Execution Response Analysis:**

1. **Step 1 - Data Retrieval**: Returns Nazım Hikmet's biographical data from quotes-service
2. **Step 2 - RAG Storage**: Successfully stores the historical figure data in Milvus collection with confirmation message
3. **Step 3 - Quote Generation**: Generates an inspirational quote using the figure's name

**Key Differences from Basic Workflow:**

- **Step 2 Result**: Shows successful storage confirmation instead of just data retrieval
- **RAG Integration**: The figure data is now stored in Milvus for future semantic search
- **Database Building**: Each execution contributes to building the knowledge base
- **Embedding Creation**: The figure's description is automatically converted to embeddings for future retrieval

**What Happens Behind the Scenes:**

1. **Data Storage**: Nazım Hikmet's information is stored in the `historical_figures_openai_text_embedding_3_small_1536` collection
2. **Embedding Generation**: The description "Turkish poet known for his political and romantic poetry" is converted to a 1536-dimensional vector
3. **Indexing**: The vector is indexed for fast semantic search operations
4. **Team Isolation**: The data is stored with the team's context for multi-tenant security

**RAG Benefits Demonstrated:**

- **Knowledge Accumulation**: Each execution adds a new historical figure to the database
- **Semantic Search Ready**: The stored data can be searched using natural language queries
- **Scalable Foundation**: As the database grows, more sophisticated RAG workflows become possible

### 2.2 Create Quotes Collection (Prerequisite)

Before using the Enhanced Historical Saying Generator Workflow, you need to create the quotes collection in Milvus:

```bash
POST /api/milvus/collections
Content-Type: application/json

{
  "collectionName": "famous_quotes_openai_text_embedding_3_small_1536",
  "schemaFields": [
    {
      "name": "id",
      "dtype": "INT64",
      "is_primary": true
    },
    {
      "name": "embedding",
      "dtype": "FLOAT_VECTOR",
      "dim": 1536
    },
    {
      "name": "person_name",
      "dtype": "VARCHAR",
      "max_length": 255
    },
    {
      "name": "quote_text",
      "dtype": "VARCHAR",
      "max_length": 1000
    },
    {
      "name": "context",
      "dtype": "VARCHAR",
      "max_length": 500
    },
    {
      "name": "category",
      "dtype": "VARCHAR",
      "max_length": 100
    },
    {
      "name": "source",
      "dtype": "VARCHAR",
      "max_length": 50
    },
    {
      "name": "quality_score",
      "dtype": "FLOAT"
    },
    {
      "name": "created_at",
      "dtype": "INT64"
    },
    {
      "name": "created_by",
      "dtype": "VARCHAR",
      "max_length": 100
    },
    {
      "name": "team_id",
      "dtype": "VARCHAR",
      "max_length": 100
    },
    {
      "name": "language",
      "dtype": "VARCHAR",
      "max_length": 10
    },
    {
      "name": "tags",
      "dtype": "VARCHAR",
      "max_length": 500
    }
  ],
  "description": "Collection for storing famous quotes with embeddings for semantic search",
  "teamId": "67d0aeb17172416c411d419e"
}
```

**Collection Schema Explanation:**

- **`person_name`**: Name of the person who said/wrote the quote
- **`quote_text`**: The actual quote text (this field will be used for embedding generation)
- **`context`**: Additional context about when/where the quote was said
- **`category`**: Topic category (e.g., "creativity", "leadership", "love")
- **`source`**: Whether the quote was "generated" by AI or "retrieved" from existing sources
- **`quality_score`**: Optional quality rating for the quote (0.0 to 1.0)
- **`created_at`**: Timestamp when the quote was added to the collection
- **`created_by`**: Username or system identifier that created the quote
- **`team_id`**: Team identifier for multi-tenant isolation
- **`language`**: Language of the quote (e.g., "en", "es", "fr")
- **`tags`**: Comma-separated tags for additional categorization

**Verify the Collection:**

After creating the collection, you can verify it works by testing the verification endpoint:

```bash
POST /api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/verify
Content-Type: application/json

{
    "textField": "quote_text",
    "text": "Creativity is intelligence having fun.",
    "teamId": "67d0aeb17172416c411d419e",
    "targetTool": "openai-embed",
    "modelType": "text-embedding-3-small"
}
```

This should return a response indicating whether similar quotes already exist in the collection.

### 2.3 Enhanced Historical Saying Generator Workflow

```json
{
    "name": "Smart Historical Saying Generator (RAG)",
    "description": "Generates inspirational sayings from historical figures using RAG for diversity and quality",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_smart_historical_saying",
            "params": {
                "topic": "{{user_query}}",
            },
            "workflow": [
                {
                    "step": 1,
                    "target": "quotes-service",
                    "action": "fetch",
                    "intent": "/api/people/random",
                    "params": {}
                },
                {
                    "step": 2,
                    "target": "api-gateway",
                    "action": "create",
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/verify",
                    "payload": {
                        "textField": "quote_text",
                        "text": "{{params.topic}}",
                        "teamId": "{{params.teamId}}",
                        "targetTool": "openai-embed",
                        "modelType": "text-embedding-3-small"
                    }
                },
                {
                    "step": 3,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "You are a quote generator and analyzer. Based on the search results, either return an existing relevant quote from someone other than the randomly selected person, or generate a new quote by the randomly selected person."
                    },
                    "payload": [
                        {
                            "role": "system",
                            "content": "You are a quote generator and analyzer. Your job is to either return an existing relevant quote from the search results (if it's from someone other than the randomly selected person) or generate a new quote by the randomly selected person."
                        },
                        {
                            "role": "user",
                            "content": "Random person: {{step1.result.fullName}}\nTopic: {{params.topic}}\nSearch results: {{step2.result}}\n\nIf the search results contain relevant quotes from people other than {{step1.result.fullName}}, return the most relevant one. If no relevant quotes found from other people, generate a new inspirational quote by {{step1.result.fullName}} about {{params.topic}}. Return only the quote text, no explanations."
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.8,
                            "max_tokens": 200
                        }
                    }
                },
                {
                    "step": 4,
                    "target": "api-gateway",
                    "action": "create",
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/records",
                    "payload": {
                        "record": {
                            "person_name": "{{step1.result.fullName}}",
                            "quote_text": "{{step3.result}}",
                            "context": "Generated quote about {{params.topic}}",
                            "category": "{{params.topic}}"
                        },
                        "targetTool": "openai-embed",
                        "modelType": "text-embedding-3-small",
                        "textField": "quote_text",
                        "teamId": "{{params.teamId}}"
                    }
                }
            ]
        }
    },
    "isPublic": false
}
```

### 2.2 Alternative Simplified Workflow (Conditional Logic)

```json
{
    "name": "Conditional Quote Generator",
    "description": "Generates quotes with smart retrieval and storage",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_conditional_quote",
            "params": {
                "topic": "{{user_query}}",
                "teamId": "67d0aeb17172416c411d419e"
            },
            "workflow": [
                {
                    "step": 1,
                    "target": "quotes-service",
                    "action": "fetch",
                    "intent": "/api/people/random",
                    "params": {}
                },
                {
                    "step": 2,
                    "target": "api-gateway",
                    "action": "fetch",
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/query",
                    "params": {
                        "embedding": "{{embedding_for_topic}}",
                        "nResults": 5,
                        "outputFields": ["person_name", "quote_text"],
                        "teamId": "{{step1.params.teamId}}"
                    }
                },
                {
                    "step": 3,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Generate a quote by {{step1.result.fullName}} about {{step1.params.topic}}. If search results contain relevant quotes from other people, use one of those instead."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Person: {{step1.result.fullName}}\nTopic: {{step1.params.topic}}\nExisting quotes: {{step2.result}}\n\nGenerate a relevant quote or select from existing ones."
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.8,
                            "max_tokens": 200
                        }
                    }
                }
            ]
        }
    }
}
```

## Dynamic Quote Generation and Storage Workflow

### 2.1 User Request Flow
```
User Request: "Give me a quote about creativity"
```

### 2.2 Service Logic (First Request)
```java
// 1. Get random famous person
Person randomPerson = getRandomFamousPerson();

// 2. Generate embedding for user's query
List<Float> queryEmbedding = getEmbedding("creativity", "openai-embed", "text-embedding-3-small", teamId);

// 3. Search for similar quotes in Milvus (will be empty initially)
Map<String, Object> searchResults = queryRecords(
    "famous_quotes_openai_text_embedding_3_small_1536",
    queryEmbedding,
    5,
    new String[]{"id", "person_name", "quote_text", "context"},
    teamId
);

// 4. If no quotes found, generate new quote with LLM
if (searchResults.get("documents").isEmpty()) {
    Quote generatedQuote = generateQuoteWithLLM(randomPerson, "creativity");
    
    // 5. Store the new quote in Milvus for future use
    Map<String, Object> quoteRecord = new HashMap<>();
    quoteRecord.put("person_name", randomPerson.getName());
    quoteRecord.put("quote_text", generatedQuote.getText());
    quoteRecord.put("context", "Generated quote about creativity");
    quoteRecord.put("category", "creativity");
    
    storeRecord(
        "famous_quotes_openai_text_embedding_3_small_1536",
        quoteRecord,
        "openai-embed",
        "text-embedding-3-small",
        "quote_text",
        teamId
    );
    
    return new QuoteResponse(randomPerson, generatedQuote, "generated");
}
```

### 2.3 Service Logic (Subsequent Requests)
```java
// 1. Get random famous person
Person randomPerson = getRandomFamousPerson();

// 2. Generate embedding for user's query
List<Float> queryEmbedding = getEmbedding("creativity", "openai-embed", "text-embedding-3-small", teamId);

// 3. Search for similar quotes in Milvus
Map<String, Object> searchResults = queryRecords(
    "famous_quotes_openai_text_embedding_3_small_1536",
    queryEmbedding,
    10, // Get more results to filter from
    new String[]{"id", "person_name", "quote_text", "context"},
    teamId
);

// 4. Filter results to avoid the randomly selected person
List<Quote> relevantQuotes = filterQuotesByPerson(searchResults, randomPerson.getName());

// 5. Decision logic
if (relevantQuotes.isEmpty()) {
    // No relevant quotes from other people, generate new one
    Quote generatedQuote = generateQuoteWithLLM(randomPerson, "creativity");
    
    // Store the new quote
    Map<String, Object> quoteRecord = new HashMap<>();
    quoteRecord.put("person_name", randomPerson.getName());
    quoteRecord.put("quote_text", generatedQuote.getText());
    quoteRecord.put("context", "Generated quote about creativity");
    quoteRecord.put("category", "creativity");
    
    storeRecord(
        "famous_quotes_openai_text_embedding_3_small_1536",
        quoteRecord,
        "openai-embed",
        "text-embedding-3-small",
        "quote_text",
        teamId
    );
    
    return new QuoteResponse(randomPerson, generatedQuote, "generated");
} else {
    // Return the most relevant quote from another person
    Quote bestQuote = relevantQuotes.get(0);
    return new QuoteResponse(randomPerson, bestQuote, "retrieved");
}
```

## Smart Storage Strategy

### 3.1 When to Store Quotes
```java
// Store quotes in these scenarios:
// 1. First time generating a quote for a topic
// 2. When no relevant quotes exist for the query
// 3. When the generated quote is high quality (optional quality check)

private boolean shouldStoreQuote(Quote quote, String query) {
    // Basic quality checks
    return quote.getText().length() > 10 && 
           quote.getText().length() < 500 &&
           !containsInappropriateContent(quote.getText());
}
```

### 3.2 Quote Deduplication
```java
// Before storing, check if similar quote already exists
private boolean isDuplicateQuote(String quoteText, String teamId) {
    try {
        Map<String, Object> verification = verifyRecord(
            "famous_quotes_openai_text_embedding_3_small_1536",
            "quote_text",
            quoteText,
            teamId,
            "openai-embed",
            "text-embedding-3-small"
        );
        
        // If verification returns a match with high similarity, it's a duplicate
        return verification.containsKey("id") && 
               (Double) verification.get("distance") < 0.1; // High similarity threshold
    } catch (Exception e) {
        return false; // If verification fails, assume it's not a duplicate
    }
}
```

## Benefits of This Approach

### ✅ **Gradual Learning**: 
- System learns from each generated quote
- Builds a knowledge base over time

### ✅ **Diversity**: 
- Avoids generating the same quote repeatedly
- Reuses high-quality quotes from previous requests

### ✅ **Cost Optimization**: 
- Reduces LLM API calls as database grows
- Balances between retrieval and generation

### ✅ **Quality Improvement**: 
- Only stores quotes that pass quality checks
- Can implement feedback mechanisms

## Implementation Strategy

### Step 1: Initial Setup
1. Create empty Milvus collection
2. Implement quote generation with LLM
3. Add storage logic for new quotes

### Step 2: Smart Retrieval
1. Implement semantic search for existing quotes
2. Add filtering logic to avoid person conflicts
3. Implement similarity thresholds

### Step 3: Quality Control
1. Add quote quality validation
2. Implement deduplication checks
3. Add feedback mechanisms for quote quality

## Example API Response

```json
{
  "person": {
    "name": "Albert Einstein",
    "biography": "German-born theoretical physicist..."
  },
  "quote": {
    "text": "Creativity is intelligence having fun.",
    "context": "Generated quote about creativity",
    "category": "creativity"
  },
  "source": "generated", // or "retrieved"
  "similarity_score": null, // only for retrieved quotes
  "search_query": "creativity",
  "database_size": 15 // number of quotes in Milvus
}
```

## Performance Considerations

### Storage Optimization
- **Batch storage**: Store multiple quotes at once if generating many
- **Indexing**: Ensure Milvus collection is properly indexed
- **Cleanup**: Periodically remove low-quality or duplicate quotes

### Query Optimization
- **Caching**: Cache frequently requested quotes
- **Pre-filtering**: Use metadata filters before vector search
- **Result limiting**: Limit search results to improve performance 