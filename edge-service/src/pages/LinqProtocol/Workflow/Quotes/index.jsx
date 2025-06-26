import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { FaExternalLinkAlt, FaBook, FaCode } from 'react-icons/fa';
import './styles.css';

const WorkflowQuotes = () => {
    return (
        <div className="workflow-quotes">
            <h2>Quotes Service Workflow</h2>
            
            <div className="workflow-section">
                <h3>Overview</h3>
                <p>
                    The Quotes Service Workflow demonstrates advanced workflow patterns including basic quote generation, 
                    RAG (Retrieval-Augmented Generation), Milvus vector database integration, multi-step workflow orchestration, 
                    and language detection with metadata tagging.
                </p>
            </div>

            <div className="workflow-section">
                <h3>Features</h3>
                <div className="features-grid">
                    <div className="feature-item">
                        <FaCode className="feature-icon" />
                        <h4>Basic Quote Generation</h4>
                        <p>Simple workflow that generates inspirational quotes from historical figures</p>
                    </div>
                    <div className="feature-item">
                        <FaCode className="feature-icon" />
                        <h4>RAG System</h4>
                        <p>Advanced workflow using retrieval-augmented generation for better quality quotes</p>
                    </div>
                    <div className="feature-item">
                        <FaCode className="feature-icon" />
                        <h4>Vector Database</h4>
                        <p>Milvus integration for storing and searching quote embeddings</p>
                    </div>
                    <div className="feature-item">
                        <FaCode className="feature-icon" />
                        <h4>Multi-Step Orchestration</h4>
                        <p>Complex workflows with multiple steps and conditional logic</p>
                    </div>
                    <div className="feature-item">
                        <FaCode className="feature-icon" />
                        <h4>Language Detection</h4>
                        <p>Automatic language detection and metadata tagging for quotes</p>
                    </div>
                    <div className="feature-item">
                        <FaCode className="feature-icon" />
                        <h4>Data Storage</h4>
                        <p>Workflow for building and maintaining quote databases with vector embeddings</p>
                    </div>
                </div>
            </div>

            <div className="workflow-section">
                <h3>Documentation</h3>
                <p>
                    Explore the comprehensive guide covering workflow creation, execution, and advanced RAG patterns 
                    in our complete documentation.
                </p>
                <div className="documentation-link">
                    <a 
                        href="https://docs.linqra.com/linq-protocol/introduction" 
                        target="_blank" 
                        rel="noopener noreferrer"
                        className="quotes-docs-button"
                    >
                        <FaBook className="button-icon" />
                        <span>View Full Documentation</span>
                        <FaExternalLinkAlt className="external-icon" />
                    </a>
                </div>
            </div>

            <div className="workflow-section">
                <h3>Quick Examples</h3>
                <div className="example-tabs">
                    <div className="tab">
                        <h4>Basic Quote Generator</h4>
                        <p>Simple workflow that generates inspirational quotes from historical figures</p>
                        <div className="code-example">
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
    "name": "Basic Quote Generator",
    "description": "Generates inspirational quotes from historical figures",
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
                        "prompt": "Generate an inspirational quote by {{step1.result.fullName}}"
                    }
                }
            ]
        }
    }
}`}
                            </SyntaxHighlighter>
                        </div>
                    </div>
                    
                    <div className="tab">
                        <h4>RAG Quote System</h4>
                        <p>Advanced workflow using retrieval-augmented generation for better quality quotes</p>
                        <div className="code-example">
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
    "name": "RAG Quote System",
    "description": "Advanced quote generation with RAG",
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_smart_historical_saying",
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
                    "intent": "/api/milvus/collections/quotes/search",
                    "payload": {
                        "textField": "quotetext",
                        "text": "{{params.topic}}"
                    }
                },
                {
                    "step": 3,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Use search results or generate new quote by {{step1.result.fullName}}"
                    }
                }
            ]
        }
    }
}`}
                            </SyntaxHighlighter>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default WorkflowQuotes;