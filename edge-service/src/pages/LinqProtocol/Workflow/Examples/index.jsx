import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import './styles.css';

const WorkflowExamples = () => {
    return (
        <div className="workflow-examples">
            <h2>Workflow Examples</h2>

            <div className="workflow-section">
                <h3>Historical Saying Generator</h3>
                <p>This example demonstrates how to create a workflow that generates inspirational sayings from historical figures using different AI models.</p>

                <div className="example-tabs">
                    <div className="tab">
                        <h4>OpenAI Version</h4>
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
}`}
                            </SyntaxHighlighter>
                        </div>
                    </div>

                    <div className="tab">
                        <h4>Gemini Version</h4>
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
                    "target": "gemini-chat",
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
}`}
                            </SyntaxHighlighter>
                        </div>
                    </div>
                </div>
            </div>

            <div className="workflow-section">
                <h3>Image Analysis Pipeline</h3>
                <p>This example shows how to create a workflow that analyzes images using multiple AI models in sequence.</p>

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
    "name": "Image Analysis Pipeline",
    "description": "Analyzes images using multiple AI models",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "analyze_image",
            "workflow": [
                {
                    "step": 1,
                    "target": "gpt4-vision",
                    "action": "analyze",
                    "intent": "analyze",
                    "params": {
                        "image": "{{input.image}}",
                        "prompt": "Describe this image in detail, focusing on objects, people, and actions."
                    }
                },
                {
                    "step": 2,
                    "target": "gemini-vision",
                    "action": "analyze",
                    "intent": "analyze",
                    "params": {
                        "image": "{{input.image}}",
                        "prompt": "Based on the image description from step 1, identify any potential safety concerns or inappropriate content."
                    }
                }
            ]
        }
    },
    "isPublic": false
}`}
                    </SyntaxHighlighter>
                </div>
            </div>

            <div className="workflow-section">
                <h3>Multi-Model Text Analysis</h3>
                <p>This example demonstrates how to use different AI models for various aspects of text analysis.</p>

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
    "name": "Multi-Model Text Analysis",
    "description": "Analyzes text using different AI models for various aspects",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "analyze_text",
            "workflow": [
                {
                    "step": 1,
                    "target": "openai-chat",
                    "action": "analyze",
                    "intent": "sentiment",
                    "params": {
                        "text": "{{input.text}}",
                        "prompt": "Analyze the sentiment of this text and provide a score from -1 to 1."
                    }
                },
                {
                    "step": 2,
                    "target": "gemini-chat",
                    "action": "analyze",
                    "intent": "topics",
                    "params": {
                        "text": "{{input.text}}",
                        "prompt": "Extract the main topics from this text, considering the sentiment analysis from step 1."
                    }
                }
            ]
        }
    },
    "isPublic": false
}`}
                    </SyntaxHighlighter>
                </div>
            </div>
        </div>
    );
};

export default WorkflowExamples; 