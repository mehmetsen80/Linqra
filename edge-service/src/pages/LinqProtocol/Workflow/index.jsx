import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import './styles.css';

function WorkflowOverview() {
    const stepReferenceExample = `{
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
                    "target": "openai-chat",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by " +
                                 "{{step1.result.fullName}}. Do not include any " +
                                 "other text, explanation, or formatting. Do not " +
                                 "use quotation marks. Only the saying."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Output only a single inspirational saying by " +
                                     "{{step1.result.fullName}}. Do not include any " +
                                     "other text, explanation, or formatting. Do not " +
                                     "use quotation marks. Only the saying."
                        }
                    ],
                    "llmConfig": {
                        "model": "gpt-4",
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

    return (
        <div className="workflow-overview">
            <h2>Linq Protocol Workflows</h2>
            
            <section className="workflow-section">
                <h3>Introduction</h3>
                <p>
                    Linq Protocol Workflows are a powerful feature that allows you to chain multiple service calls together
                    in a sequential manner. Each workflow consists of a series of steps, where each step can interact with
                    different services, including microservices and AI models like OpenAI or Gemini.
                </p>
            </section>

            <section className="workflow-section">
                <h3>Key Features</h3>
                <ul className="feature-list">
                    <li>
                        <strong>Step-by-Step Execution</strong>
                        Define a sequence of operations that execute in order
                    </li>
                    <li>
                        <strong>Service Integration</strong>
                        Seamlessly connect with various services and AI models
                    </li>
                    <li>
                        <strong>Data Flow</strong>
                        Pass data between steps using step references
                        <div style={{ 
                            overflowX: 'auto', 
                            maxWidth: '100%',
                            backgroundColor: '#1E1E1E',
                            borderRadius: '6px',
                            margin: '0.5rem 0 0 0'
                        }}>
                            <SyntaxHighlighter 
                                language="json" 
                                style={vscDarkPlus}
                                showLineNumbers={true}
                                customStyle={{
                                    margin: 0,
                                    padding: '1rem',
                                    fontSize: '0.9rem',
                                    lineHeight: '1.4'
                                }}
                            >
                                {stepReferenceExample}
                            </SyntaxHighlighter>
                        </div>
                    </li>
                    <li>
                        <strong>Flexible Configuration</strong>
                        Customize each step with specific parameters and settings
                    </li>
                </ul>
            </section>

            <section className="workflow-section">
                <h3>Workflow Structure</h3>
                <p>
                    A workflow consists of two main parts:
                </p>
                <ul className="structure-list">
                    <li>
                        <strong>Workflow Definition</strong>
                        Contains metadata and the complete workflow configuration
                        <ul>
                            <li>Name and description</li>
                            <li>Visibility settings (public/private)</li>
                            <li>Team association</li>
                        </ul>
                    </li>
                    <li>
                        <strong>Workflow Steps</strong>
                        Individual operations that make up the workflow
                        <ul>
                            <li>Step number and order</li>
                            <li>Target service</li>
                            <li>Action to perform</li>
                            <li>Parameters and configuration</li>
                        </ul>
                    </li>
                </ul>
            </section>

            <section className="workflow-section">
                <h3>Common Use Cases</h3>
                <ul className="use-cases-list">
                    <li>
                        <strong>AI-Enhanced Data Processing</strong>
                        Combine data fetching with AI model processing
                    </li>
                    <li>
                        <strong>Multi-Service Orchestration</strong>
                        Coordinate operations across multiple microservices
                    </li>
                    <li>
                        <strong>Data Transformation Pipelines</strong>
                        Transform and process data through multiple steps
                    </li>
                    <li>
                        <strong>AI Model Chaining</strong>
                        Use multiple AI models in sequence for complex tasks
                    </li>
                </ul>
            </section>

            <section className="workflow-section">
                <h3>Getting Started</h3>
                <p>
                    To start using workflows, you'll need to:
                </p>
                <ol className="getting-started-list">
                    <li>Create a new workflow using the workflow creation endpoint</li>
                    <li>Define your workflow steps and their configurations</li>
                    <li>Save the workflow to get a workflow ID</li>
                    <li>Execute the workflow using the workflow ID</li>
                </ol>
                <p>
                    Check out the <a href="/linq-protocol/workflow/examples">Examples</a> section for practical implementations
                    using different AI models and services.
                </p>
            </section>
        </div>
    );
}

export default WorkflowOverview; 