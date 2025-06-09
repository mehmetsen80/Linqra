import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import './styles.css';

const RequestStructure = () => {
  const linkObjectCode = `{
  "link": {
    "target": "service-name",
    "action": "fetch|create|update|patch|delete|options|head|generate|execute"
  }
}`;

  const queryObjectCode = `{
  "query": {
    "intent": "api/endpoint",
    "params": {
      "param1": "value1",
      "param2": "value2"
    },
    "payload": {
      "field1": "value1",
      "field2": "value2"
    }
  }
}`;

  const completeExampleCode = `POST /linq
{
  "link": {
    "target": "inventory-service",
    "action": "create"
  },
  "query": {
    "intent": "api/products",
    "params": {
      "category": "electronics"
    },
    "payload": {
      "name": "New Product",
      "price": 99.99,
      "stock": 100
    }
  }
}`;

  return (
    <div className="basics-container">
      <h2>Request Structure</h2>
      
      <div className="request-overview">
        <p>
          The Linq Protocol uses a standardized request format that consists of two main sections:
          the <code>link</code> object and the <code>query</code> object.
        </p>
      </div>

      <div className="request-sections">
        <div className="section">
          <h3>Link Object</h3>
          <p>The <code>link</code> object defines the target service and the action to perform:</p>
          <SyntaxHighlighter 
            language="json" 
            style={vscDarkPlus}
            showLineNumbers={true}
            customStyle={{
              margin: '1.5rem 0',
              borderRadius: '8px',
              fontSize: '0.95rem',
              lineHeight: '1.6'
            }}
          >
            {linkObjectCode}
          </SyntaxHighlighter>
          <div className="link-properties">
            <ul>
              <li><strong>target:</strong> The identifier of the service you want to interact with</li>
              <li><strong>action:</strong> The operation to perform:</li>
            </ul>
            <table className="action-table">
              <thead>
                <tr>
                  <th>Action</th>
                  <th>Description</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td><code>fetch</code></td>
                  <td>Retrieve data (get)</td>
                </tr>
                <tr>
                  <td><code>create</code></td>
                  <td>Create new resources (post)</td>
                </tr>
                <tr>
                  <td><code>update</code></td>
                  <td>Update existing resources</td>
                </tr>
                <tr>
                  <td><code>patch</code></td>
                  <td>Partially update resources</td>
                </tr>
                <tr>
                  <td><code>delete</code></td>
                  <td>Remove resources</td>
                </tr>
                <tr>
                  <td><code>options</code></td>
                  <td>Get available options</td>
                </tr>
                <tr>
                  <td><code>head</code></td>
                  <td>Get headers only</td>
                </tr>
                <tr>
                  <td><code>generate</code></td>
                  <td>Generate content or resources</td>
                </tr>
                <tr>
                  <td><code>execute</code></td>
                  <td>Execute specific operations</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div className="section">
          <h3>Query Object</h3>
          <p>The <code>query</code> object contains the specific details of your request:</p>
          <SyntaxHighlighter 
            language="json" 
            style={vscDarkPlus}
            showLineNumbers={true}
            customStyle={{
              margin: '1.5rem 0',
              borderRadius: '8px',
              fontSize: '0.95rem',
              lineHeight: '1.6'
            }}
          >
            {queryObjectCode}
          </SyntaxHighlighter>
          <div className="query-properties">
            <ul>
              <li><strong>intent:</strong> The specific endpoint or operation within the service</li>
              <li><strong>params:</strong> Query parameters and path variables</li>
              <li><strong>payload:</strong> The request body (for POST, PUT requests)</li>
            </ul>
          </div>
        </div>
      </div>

      <div className="example-section">
        <h3>Complete Example</h3>
        <SyntaxHighlighter 
          language="json" 
          style={vscDarkPlus}
          showLineNumbers={true}
          customStyle={{
            margin: '1.5rem 0',
            borderRadius: '8px',
            fontSize: '0.95rem',
            lineHeight: '1.6'
          }}
        >
          {completeExampleCode}
        </SyntaxHighlighter>
      </div>
    </div>
  );
};

export default RequestStructure; 