import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import './styles.css';

function WorkflowExecute() {
    return (
        <div className="workflow-execute">
            <h2>Executing Workflows</h2>
            
            <section className="workflow-section">
                <h3>Workflow Execution Endpoint</h3>
                <p>
                    To execute a workflow, send a POST request to the workflow execution endpoint with the workflow ID:
                </p>
                <div className="endpoint-box">
                    <code>POST https://localhost:7777/linq/workflows/{'{workflowId}'}/execute</code>
                </div>
            </section>

            <section className="workflow-section">
                <h3>Request Structure</h3>
                <p>
                    The request body should contain the following fields:
                </p>
                <ul className="structure-list">
                    <li>
                        <strong>link:</strong> Contains target and action information
                        <ul>
                            <li>target: "workflow"</li>
                            <li>action: "execute"</li>
                        </ul>
                    </li>
                    <li>
                        <strong>query:</strong> Contains execution parameters
                        <ul>
                            <li>intent: The workflow's intent</li>
                            <li>params: Optional parameters for the workflow</li>
                            <li>payload: Optional payload data</li>
                        </ul>
                    </li>
                </ul>
            </section>

            <section className="workflow-section">
                <h3>Example: Executing a Workflow</h3>
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
                        {`// Request
POST https://localhost:7777/linq/workflows/684124f5a02e4e19d35b62db/execute


// Response
HTTP/1.1 200 OK`}
                    </SyntaxHighlighter>
                </div>
            </section>

            <section className="workflow-section">
                <h3>Response</h3>
                <p>
                    The endpoint returns a 200 OK status code upon successful execution of the workflow.
                </p>
            </section>
        </div>
    );
}

export default WorkflowExecute; 