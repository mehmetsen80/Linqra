![Build Status](https://github.com/mehmetsen80/Linqra/actions/workflows/ci.yml/badge.svg) ![Java 21](https://img.shields.io/badge/Java-21-blue) ![Version](https://img.shields.io/badge/version-0.6-brightgreen)

# What is Linqra?

<div align="center">
<a href="assets/linqrawithbg.png"> <img alt="Linqra" src="assets/linqrawithbg.png"></a>
</div>
<br/>

Linqra introduces a new paradigm for AI deployment: an Enterprise AI App Store that lets organizations discover, deploy, and manage AI applications with unprecedented ease. Built on our battle-tested gateway infrastructure, it ensures enterprise-grade security, scalability, and reliability for all AI services. This unique combination allows companies to innovate with AI while maintaining the robust security and performance standards they expect from traditional enterprise software.

🌐 **[Visit Linqra.com](https://linqra.com)** for more information and live demos.

## Key Features

### 🏪 AI Agents
Discover and deploy AI applications with enterprise-grade security and management capabilities. Our curated marketplace ensures quality and compatibility while providing seamless integration options.

### 🔐 Enterprise Gateway
Built with a security-first approach, Linqra provides comprehensive authentication, rate limiting, and load balancing features that protect and optimize your API infrastructure.

### 📡 Linq Protocol
Our unified protocol simplifies how your applications communicate with AI services. Instead of managing multiple API formats, use one consistent approach for all AI interactions.

```json
{
  "link": {
    "target": "ai-workflow",
    "action": "create"
  },
  "query": {
    "intent": "workflow/execute",
    "payload": {
      "workflow": {
        "steps": [
          {
            "id": "analysis",
            "service": "chat-service",
            "action": "completion"
          }
        ]
      }
    }
  }
}
```

## Deployment Options

- **Enterprise On-Premise**: Full control over data and security
- **Private Cloud**: AWS, Azure, or GCP with Kubernetes support
- **Startup Quick-Start**: Docker Compose for rapid development

## Documentation

For detailed information about installation, configuration, and usage, visit our [comprehensive documentation](https://docs.linqra.com/introduction).

## License

Linqra is distributed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. For commercial use, please contact us at msen@dipme.app.

See the [LICENSE](./LICENSE) file for details.

## Support

- [Issue Tracker](https://github.com/mehmetsen80/Linqra/issues)
- [Documentation](https://docs.linqra.com/introduction)
- [Community Discussion](https://github.com/mehmetsen80/Linqra/discussions)


# Quotes-Service RAG Workflow (Starting from Scratch)

## Initial Setup

### 1.1 Create Empty Milvus Collection
```bash
# Create collection for quotes (one-time setup)
POST /api/milvus/collections
Content-Type: application/json
```

```json
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
x-api-key: {api key}
x-api-key-name: {api key name}
Authorization: Bearer {Token}
```

```json
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
x-api-key: {api key}
x-api-key-name: {api key name}
Authorization: Bearer {Token}
```

```json
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
```

```json
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
      "name": "personname",
      "dtype": "VARCHAR",
      "max_length": 255
    },
    {
      "name": "quotetext",
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
      "name": "createdat",
      "dtype": "INT64"
    },
    {
      "name": "teamid",
      "dtype": "VARCHAR",
      "max_length": 100
    },
    {
      "name": "language",
      "dtype": "VARCHAR",
      "max_length": 100
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

- **`personname`**: Name of the person who said/wrote the quote
- **`quotetext`**: The actual quote text (this field will be used for embedding generation)
- **`context`**: Additional context about when/where the quote was said
- **`category`**: Topic category (e.g., "creativity", "leadership", "love")
- **`source`**: Whether the quote was "generated" by AI or "retrieved" from existing sources
- **`createdat`**: Timestamp when the quote was added to the collection
- **`createdby`**: Username or system identifier that created the quote (is handled internally for every milvus collection)
- **`teamid`**: Team identifier for multi-tenant isolation
- **`language`**: Language of the quote (e.g., "en", "es", "fr")
- **`tags`**: Comma-separated tags for additional categorization

**Verify the Collection:**

After creating the collection, you can verify it works by testing the verification endpoint:

```bash
POST /api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/verify
Content-Type: application/json
```

```json
{
    "textField": "quotetext",
    "text": "Creativity is intelligence having fun.",
    "teamId": "67d0aeb17172416c411d419e",
    "targetTool": "openai-embed",
    "modelType": "text-embedding-3-small"
}
```

This should return a response indicating whether similar quotes already exist in the collection. 

### 2.3 Smart Historical Saying Generator (5-Step RAG Workflow)

This advanced workflow demonstrates a complete RAG (Retrieval-Augmented Generation) system with 5 steps:

1. **Step 1**: Fetch random historical figure data
2. **Step 2**: Search for existing quotes in the Milvus collection
3. **Step 3**: Generate or retrieve a quote using AI analysis
4. **Step 4**: Detect the language of the quote
5. **Step 5**: Store the quote in the Milvus collection

**Part 1: Create the Workflow Definition**

```bash
POST https://localhost:7777/linq/workflows
Content-Type: application/json
x-api-key: {api key}
x-api-key-name: {api key name}
Authorization: Bearer {Token}
```

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
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/search",
                    "payload": {
                        "textField": "quotetext",
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
                    "payload": [
                        {
                            "role": "system",
                            "content": "You are a quote generator and analyzer. Your job is to either return an existing relevant quote from the search results (if it's from someone other than the randomly selected person) or generate a new quote by the randomly selected person."
                        },
                        {
                            "role": "user",
                            "content": "Random person: {{step1.result.fullName}}\nTopic: {{params.topic}}\nSearch results: {{step2.result.results}}\nTotal found: {{step2.result.total_results}}\n\nIf the search results contain relevant quotes from people other than {{step1.result.fullName}}, return the most relevant one. If no relevant quotes found from other people, generate a new inspirational quote by {{step1.result.fullName}} about {{params.topic}}. Return only the quote text, no explanations."
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
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "payload": [
                        {
                            "role": "system",
                            "content": "You are a language detection expert. Your task is to detect the language of the given text and return ONLY the ISO 639-1 language code (e.g., 'en', 'es', 'fr', 'de', 'it', 'pt', 'ru', 'ja', 'ko', 'zh'). Do not include any explanations, punctuation, or additional text."
                        },
                        {
                            "role": "user",
                            "content": "Detect the language of this quote: {{step3.result.choices[0].message.content}}"
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.1,
                            "max_tokens": 10
                        }
                    }
                },
                {
                    "step": 5,
                    "target": "api-gateway",
                    "action": "create",
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/records",
                    "payload": {
                        "record": {
                            "personname": "{{step1.result.fullName}}",
                            "quotetext": "{{step3.result.choices[0].message.content}}",
                            "context": "Generated quote about {{params.topic}}",
                            "category": "{{params.topic}}",
                            "source": "generated",
                            "language": "{{step4.result.choices[0].message.content}}",
                            "teamid": "{{params.teamId}}",
                            "tags": "{{params.topic}},{{step4.result.choices[0].message.content}},generated,inspirational"
                        },
                        "targetTool": "openai-embed",
                        "modelType": "text-embedding-3-small",
                        "textField": "quotetext",
                        "teamId": "{{params.teamId}}"
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
    "id": "685cbc51bb8e9e0f1c8d0487",
    "name": "Smart Historical Saying Generator (RAG)",
    "description": "Generates inspirational sayings from historical figures using RAG for diversity and quality",
    "teamId": "67d0aeb17172416c411d419e",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_smart_historical_saying",
            "workflowId": "685cbc51bb8e9e0f1c8d0487",
            "params": {
                "topic": "relationships",
                "teamId": "67d0aeb17172416c411d419e",
                "userId": "timursen"
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
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/search",
                    "params": null,
                    "payload": {
                        "textField": "quotetext",
                        "text": "{{params.topic}}",
                        "teamId": "{{params.teamId}}",
                        "targetTool": "openai-embed",
                        "modelType": "text-embedding-3-small"
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
                    "params": null,
                    "payload": [
                        {
                            "role": "system",
                            "content": "You are a quote generator and analyzer. Your job is to either return an existing relevant quote from the search results (if it's from someone other than the randomly selected person) or generate a new quote by the randomly selected person."
                        },
                        {
                            "role": "user",
                            "content": "Random person: {{step1.result.fullName}}\nTopic: {{params.topic}}\nSearch results: {{step2.result.results}}\nTotal found: {{step2.result.total_results}}\n\nIf the search results contain relevant quotes from people other than {{step1.result.fullName}}, return the most relevant one. If no relevant quotes found from other people, generate a new inspirational quote by {{step1.result.fullName}} about {{params.topic}}. Return only the quote text, no explanations."
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.8,
                            "max_tokens": 200
                        }
                    },
                    "async": null,
                    "cacheConfig": null
                },
                {
                    "step": 4,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": null,
                    "payload": [
                        {
                            "role": "system",
                            "content": "You are a language detection expert. Your task is to detect the language of the given text and return ONLY the ISO 639-1 language code (e.g., 'en', 'es', 'fr', 'de', 'it', 'pt', 'ru', 'ja', 'ko', 'zh'). Do not include any explanations, punctuation, or additional text."
                        },
                        {
                            "role": "user",
                            "content": "Detect the language of this quote: {{step3.result.choices[0].message.content}}"
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.1,
                            "max_tokens": 10
                        }
                    },
                    "async": null,
                    "cacheConfig": null
                },
                {
                    "step": 5,
                    "target": "api-gateway",
                    "action": "create",
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/records",
                    "params": null,
                    "payload": {
                        "record": {
                            "personname": "{{step1.result.fullName}}",
                            "quotetext": "{{step3.result.choices[0].message.content}}",
                            "context": "Generated quote about {{params.topic}}",
                            "category": "{{params.topic}}",
                            "source": "generated",
                            "language": "{{step4.result.choices[0].message.content}}",
                            "teamid": "{{params.teamId}}",
                            "tags": "{{params.topic}},{{step4.result.choices[0].message.content}},generated,inspirational"
                        },
                        "targetTool": "openai-embed",
                        "modelType": "text-embedding-3-small",
                        "textField": "quotetext",
                        "teamId": "{{params.teamId}}"
                    },
                    "toolConfig": null,
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
        25,
        22,
        19,
        45,
        529697000
    ],
    "updatedAt": [
        2025,
        6,
        25,
        22,
        19,
        45,
        529700000
    ],
    "createdBy": "timursen",
    "updatedBy": "timursen",
    "version": 1,
    "public": false
}
```

**Part 2: Execute the Workflow**

```bash
POST https://localhost:7777/linq/workflows/685cbc51bb8e9e0f1c8d0487/execute
Content-Type: application/json
x-api-key: {api key}
x-api-key-name: {api key name}
Authorization: Bearer {Token}
```

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
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/search",
                    "payload": {
                        "textField": "quotetext",
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
                    "payload": [
                        {
                            "role": "system",
                            "content": "You are a quote generator and analyzer. Your job is to either return an existing relevant quote from the search results (if it's from someone other than the randomly selected person) or generate a new quote by the randomly selected person."
                        },
                        {
                            "role": "user",
                            "content": "Random person: {{step1.result.fullName}}\nTopic: {{params.topic}}\nSearch results: {{step2.result.results}}\nTotal found: {{step2.result.total_results}}\n\nIf the search results contain relevant quotes from people other than {{step1.result.fullName}}, return the most relevant one. If no relevant quotes found from other people, generate a new inspirational quote by {{step1.result.fullName}} about {{params.topic}}. Return only the quote text, no explanations."
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
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "payload": [
                        {
                            "role": "system",
                            "content": "You are a language detection expert. Your task is to detect the language of the given text and return ONLY the ISO 639-1 language code (e.g., 'en', 'es', 'fr', 'de', 'it', 'pt', 'ru', 'ja', 'ko', 'zh'). Do not include any explanations, punctuation, or additional text."
                        },
                        {
                            "role": "user",
                            "content": "Detect the language of this quote: {{step3.result.choices[0].message.content}}"
                        }
                    ],
                    "toolConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.1,
                            "max_tokens": 10
                        }
                    }
                },
                {
                    "step": 5,
                    "target": "api-gateway",
                    "action": "create",
                    "intent": "/api/milvus/collections/famous_quotes_openai_text_embedding_3_small_1536/records",
                    "payload": {
                        "record": {
                            "personname": "{{step1.result.fullName}}",
                            "quotetext": "{{step3.result.choices[0].message.content}}",
                            "context": "Generated quote about {{params.topic}}",
                            "category": "{{params.topic}}",
                            "source": "generated",
                            "language": "{{step4.result.choices[0].message.content}}",
                            "teamid": "{{params.teamId}}",
                            "tags": "{{params.topic}},{{step4.result.choices[0].message.content}},generated,inspirational"
                        },
                        "targetTool": "openai-embed",
                        "modelType": "text-embedding-3-small",
                        "textField": "quotetext",
                        "teamId": "{{params.teamId}}"
                    }
                }
            ]
        }
    },
    "isPublic": false
}
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
                    "fullName": "Atticus Finch",
                    "knownAs": "The Moral Lawyer",
                    "birthYear": 1920,
                    "deathYear": null,
                    "nationality": "American",
                    "description": "Fictional character known for his wisdom and moral guidance in To Kill a Mockingbird",
                    "category": "Characters"
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
                    "found": true,
                    "message": "Found 1 relevant records",
                    "results": [
                        {
                            "quotetext": "\"The relationships we form are the true reflection of our social conditions; they are both the chains that bind us and the forces that set us free.\" - Karl Marx",
                            "distance": 0.0,
                            "match_type": "exact",
                            "id": 1750824666915
                        }
                    ],
                    "search_text": "relationships",
                    "total_results": 1
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
                    "id": "chatcmpl-BmXdd3zg4gPhiUPLdtAzmQhvHjOQ7",
                    "object": "chat.completion",
                    "created": 1750908749,
                    "model": "gpt-4o-2024-08-06",
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": "\"The relationships we form are the true reflection of our social conditions; they are both the chains that bind us and the forces that set us free.\" - Karl Marx",
                                "refusal": null,
                                "annotations": []
                            },
                            "logprobs": null,
                            "finish_reason": "stop"
                        }
                    ],
                    "usage": {
                        "prompt_tokens": 184,
                        "completion_tokens": 32,
                        "total_tokens": 216,
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
            },
            {
                "step": 4,
                "target": "openai",
                "result": {
                    "id": "chatcmpl-BmXde2jQoljYUe5wkO68nTxkvoXKt",
                    "object": "chat.completion",
                    "created": 1750908750,
                    "model": "gpt-4o-2024-08-06",
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": "en",
                                "refusal": null,
                                "annotations": []
                            },
                            "logprobs": null,
                            "finish_reason": "stop"
                        }
                    ],
                    "usage": {
                        "prompt_tokens": 126,
                        "completion_tokens": 1,
                        "total_tokens": 127,
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
                    "system_fingerprint": "fp_07871e2ad8"
                },
                "params": null,
                "action": null,
                "intent": null,
                "executionId": null,
                "async": false
            },
            {
                "step": 5,
                "target": "api-gateway",
                "result": {
                    "message": "Record stored successfully in collection famous_quotes_openai_text_embedding_3_small_1536"
                },
                "params": null,
                "action": null,
                "intent": null,
                "executionId": null,
                "async": false
            }
        ],
        "finalResult": "{message=Record stored successfully in collection famous_quotes_openai_text_embedding_3_small_1536}",
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
                "durationMs": 62,
                "target": "quotes-service",
                "executedAt": [
                    2025,
                    6,
                    25,
                    22,
                    32,
                    27,
                    335161000
                ],
                "tokenUsage": null,
                "model": null,
                "async": false
            },
            {
                "step": 2,
                "status": "success",
                "durationMs": 1345,
                "target": "api-gateway",
                "executedAt": [
                    2025,
                    6,
                    25,
                    22,
                    32,
                    28,
                    680892000
                ],
                "tokenUsage": null,
                "model": null,
                "async": false
            },
            {
                "step": 3,
                "status": "success",
                "durationMs": 1119,
                "target": "openai",
                "executedAt": [
                    2025,
                    6,
                    25,
                    22,
                    32,
                    29,
                    800727000
                ],
                "tokenUsage": {
                    "promptTokens": 184,
                    "completionTokens": 32,
                    "totalTokens": 216
                },
                "model": "gpt-4o-2024-08-06",
                "async": false
            },
            {
                "step": 4,
                "status": "success",
                "durationMs": 1055,
                "target": "openai",
                "executedAt": [
                    2025,
                    6,
                    25,
                    22,
                    32,
                    30,
                    856447000
                ],
                "tokenUsage": {
                    "promptTokens": 126,
                    "completionTokens": 1,
                    "totalTokens": 127
                },
                "model": "gpt-4o-2024-08-06",
                "async": false
            },
            {
                "step": 5,
                "status": "success",
                "durationMs": 443,
                "target": "api-gateway",
                "executedAt": [
                    2025,
                    6,
                    25,
                    22,
                    32,
                    31,
                    300080000
                ],
                "tokenUsage": null,
                "model": null,
                "async": false
            }
        ],
        "asyncSteps": null
    }
}
```

**5-Step Workflow Analysis:**

1. **Step 1 - Data Retrieval**: Fetches Atticus Finch's biographical data from quotes-service
2. **Step 2 - RAG Search**: Searches existing quotes in Milvus collection and finds a relevant quote by Karl Marx
3. **Step 3 - Quote Analysis**: AI analyzes search results and returns the existing Karl Marx quote (since it's from someone other than Atticus Finch)
4. **Step 4 - Language Detection**: AI detects the quote is in English ("en")
5. **Step 5 - Storage**: Stores the quote in Milvus collection with proper metadata

**Key Features of 5-Step Workflow:**

- ✅ **Smart RAG**: Searches existing quotes before generating new ones
- ✅ **Quote Reuse**: Returns existing relevant quotes from other people
- ✅ **Language Detection**: Automatically detects and tags quote language
- ✅ **Data Persistence**: Stores quotes with comprehensive metadata
- ✅ **Team Isolation**: Maintains multi-tenant data separation
- ✅ **Performance Optimization**: Uses caching and efficient search

**RAG Benefits Demonstrated:**

- **Knowledge Reuse**: Avoids generating duplicate quotes by checking existing database
- **Diversity**: Returns quotes from different historical figures
- **Quality Control**: Uses AI to analyze relevance and choose best quotes
- **Metadata Enrichment**: Automatically adds language detection and categorization
- **Scalable Knowledge Base**: Continuously builds a searchable quote database

This 5-step workflow represents a complete RAG system that intelligently combines retrieval, analysis, and storage in a single orchestrated process. 

