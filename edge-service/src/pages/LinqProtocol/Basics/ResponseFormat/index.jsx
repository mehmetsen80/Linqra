import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import './styles.css';

const ResponseFormat = () => {
  const successResponseCode = `{
  "result": {
    // The actual response data from the service
    "id": 123,
    "name": "Product Name",
    "price": 99.99
  },
  "metadata": {
    "source": "service-name",
    "status": "success",
    "team": "team-id",
    "cacheHit": false,
    "executionTime": 150
  }
}`;

  const errorResponseCode = `{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": {
      // Additional error details if available
    }
  },
  "metadata": {
    "source": "service-name",
    "status": "error",
    "team": "team-id",
    "executionTime": 50
  }
}`;

  return (
    <div className="basics-container">
      <h2>Response Format</h2>
      
      <div className="response-overview">
        <p>
          The Linq Protocol provides a consistent response format that includes both the result data
          and metadata about the request execution.
        </p>
      </div>

      <div className="response-sections">
        <div className="section">
          <h3>Success Response</h3>
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
            {successResponseCode}
          </SyntaxHighlighter>
          <ul>
            <li><strong>result:</strong> Contains the actual response data from the service</li>
            <li><strong>metadata:</strong> Contains information about the request execution</li>
          </ul>
        </div>

        <div className="section">
          <h3>Error Response</h3>
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
            {errorResponseCode}
          </SyntaxHighlighter>
          <ul>
            <li><strong>error:</strong> Contains error information when the request fails</li>
            <li><strong>metadata:</strong> Still included in error responses for debugging</li>
          </ul>
        </div>
      </div>

      <div className="metadata-section">
        <h3>Metadata Fields</h3>
        <ul>
          <li><strong>source:</strong> The identifier of the service that processed the request</li>
          <li><strong>status:</strong> The status of the request (success or error)</li>
          <li><strong>team:</strong> The team identifier associated with the request</li>
          <li><strong>cacheHit:</strong> Indicates if the response was served from cache</li>
          <li><strong>executionTime:</strong> The time taken to process the request in milliseconds</li>
        </ul>
      </div>
    </div>
  );
};

export default ResponseFormat; 