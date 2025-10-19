import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import './styles.css';

const WorkflowCreate = () => {
  const openaiExample = `{
    "name": "Historical Saying Generator",
    "description": "Generates inspirational sayings from historical figures",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
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
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}}. " +
                                "Do not include any other text, explanation, or formatting. " +
                                "Do not use quotation marks. Only the saying."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Output only a single inspirational saying by {{step1.result.fullName}}. " +
                                     "Do not include any other text, explanation, or formatting. " +
                                     "Do not use quotation marks. Only the saying."
                        }
                    ],
                    "llmConfig": {
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
}`;

  const geminiExample = `{
    "name": "Historical Saying Generator",
    "description": "Generates inspirational sayings from historical figures",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
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
                    "target": "gemini",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}}. " +
                                "Do not include any other text, explanation, or formatting. " +
                                "Do not use quotation marks. Only the saying."
                    },
                    "llmConfig": {
                        "model": "gemini-2.0-flash",
                        "settings": {
                            "temperature": 0.9,
                            "maxOutputTokens": 200
                        }
                    }
                }
            ]
        }
    },
    "isPublic": false
}`;

  const responseExample = `{
    "id": "68412401a02e4e19d35b62d9",
    "name": "Historical Saying Generator",
    "description": "Generates inspirational sayings from historical figures",
    "team": "681a97cdec715f343bcb1ece",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
            "workflowId": null,
            "params": null,
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
                    "llmConfig": null
                },
                {
                    "step": 2,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}}. " +
                                "Do not include any other text, explanation, or formatting. " +
                                "Do not use quotation marks. Only the saying."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Output only a single inspirational saying by {{step1.result.fullName}}. " +
                                     "Do not include any other text, explanation, or formatting. " +
                                     "Do not use quotation marks. Only the saying."
                        }
                    ],
                    "llmConfig": {
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
    "createdAt": [2025, 6, 5, 4, 58, 41, 367508506],
    "updatedAt": [2025, 6, 5, 4, 58, 41, 367517161],
    "createdBy": "johndoe",
    "updatedBy": "johndoe",
    "version": 1,
    "public": false
}`;

  return (
    <div className="workflow-create">
        <h2>Creating Workflows</h2>
      <div className="workflow-section">
        <p>
          Workflows are created by sending a POST request to the workflow creation endpoint.
          Each workflow defines a sequence of steps that can be executed using different AI models.
        </p>

        <h3>Endpoint</h3>
        <div className="endpoint-box">
          <code>POST https://localhost:7777/linq/workflows</code>
        </div>

        <h3>Request Structure</h3>
        <ul className="structure-list">
          <li>
            <strong>name</strong> - A unique identifier for the workflow
          </li>
          <li>
            <strong>description</strong> - A brief description of what the workflow does
          </li>
          <li>
            <strong>request</strong> - The workflow configuration object
            <ul>
              <li>link - Contains target and action information</li>
              <li>query - Contains the workflow steps and configuration</li>
              <li>Each step contains target, action, intent, and parameters</li>
            </ul>
          </li>
          <li>
            <strong>isPublic</strong> - Whether the workflow is publicly accessible
          </li>
        </ul>

        <h3>Example: Historical Saying Generator (OpenAI)</h3>
        <p>
          This example creates a workflow that generates historical sayings using OpenAI. It first fetches a random historical figure from the quotes service, then generates a saying in their style.
        </p>

        <div style={{ 
          overflowX: 'auto', 
          maxWidth: '100%',
          backgroundColor: '#000000',
          borderRadius: '6px',
          margin: '0.5rem 0 0 0'
        }}>
          <SyntaxHighlighter 
            language="json" 
            style={vscDarkPlus}
            showLineNumbers={true}
            wrapLines={true}
            customStyle={{
              margin: 0,
              padding: '1rem',
              fontSize: '0.9rem',
              lineHeight: '1.4',
              backgroundColor: '#000000'
            }}
          >
            {openaiExample}
          </SyntaxHighlighter>
        </div>

        <h3 class="mt-5">Response Format</h3>
        <p>
          The response includes the workflow ID, configuration details, and metadata about the workflow:
        </p>

        <div style={{ 
          overflowX: 'auto', 
          maxWidth: '100%',
          backgroundColor: '#000000',
          borderRadius: '6px',
          margin: '0.5rem 0 0 0'
        }}>
          <SyntaxHighlighter 
            language="json" 
            style={vscDarkPlus}
            showLineNumbers={true}
            wrapLines={true}
            customStyle={{
              margin: 0,
              padding: '1rem',
              fontSize: '0.9rem',
              lineHeight: '1.4',
              backgroundColor: '#000000'
            }}
          >
            {responseExample}
          </SyntaxHighlighter>
        </div>

        <h3 class="mt-5">Example: Historical Saying Generator (Gemini)</h3>
        <p>
          This example shows the same workflow using Gemini instead of OpenAI. The main differences are in the model configuration and the absence of a payload array.
        </p>

        <div style={{ 
          overflowX: 'auto', 
          maxWidth: '100%',
          backgroundColor: '#000000',
          borderRadius: '6px',
          margin: '0.5rem 0 0 0'
        }}>
          <SyntaxHighlighter 
            language="json" 
            style={vscDarkPlus}
            showLineNumbers={true}
            wrapLines={true}
            customStyle={{
              margin: 0,
              padding: '1rem',
              fontSize: '0.9rem',
              lineHeight: '1.4',
              backgroundColor: '#000000'
            }}
          >
            {geminiExample}
          </SyntaxHighlighter>
        </div>

        <h3 className="mt-5">Gemini Response Format</h3>
        <p>
          The response for a Gemini workflow is similar, but uses Gemini as the model in the workflow step:
        </p>
        <div style={{ 
          overflowX: 'auto', 
          maxWidth: '100%',
          backgroundColor: '#000000',
          borderRadius: '6px',
          margin: '0.5rem 0 0 0'
        }}>
          <SyntaxHighlighter 
            language="json" 
            style={vscDarkPlus}
            showLineNumbers={true}
            wrapLines={true}
            customStyle={{
              margin: 0,
              padding: '1rem',
              fontSize: '0.9rem',
              lineHeight: '1.4',
              backgroundColor: '#000000'
            }}
          >
            {`{
    "id": "684124f5a02e4e19d35b62db",
    "name": "Historical Saying Generator",
    "description": "Generates inspirational sayings from historical figures",
    "team": "681a97cdec715f343bcb1ece",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
            "workflowId": null,
            "params": null,
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
                    "llmConfig": null
                },
                {
                    "step": 2,
                    "target": "gemini",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}}. " +
                                "Do not include any other text, explanation, or formatting. " +
                                "Do not use quotation marks. Only the saying."
                    },
                    "payload": null,
                    "llmConfig": {
                        "model": "gemini-2.0-flash",
                        "settings": {
                            "temperature": 0.9,
                            "maxOutputTokens": 200
                        }
                    }
                }
            ]
        }
    },
    "createdAt": [
        2025,
        6,
        5,
        5,
        2,
        45,
        224545793
    ],
    "updatedAt": [
        2025,
        6,
        5,
        5,
        2,
        45,
        224547698
    ],
    "createdBy": "johndoe",
    "updatedBy": "johndoe",
    "version": 1,
    "public": false
}`}
          </SyntaxHighlighter>
        </div>

       
      </div>
    </div>
  );
};

export default WorkflowCreate; 