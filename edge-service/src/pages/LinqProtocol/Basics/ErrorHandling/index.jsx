import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import './styles.css';

const ErrorHandling = () => {
  const authErrorCode = `{
  "error": {
    "code": "AUTH_ERROR",
    "message": "Invalid API key",
    "details": {
      "reason": "API key expired"
    }
  }
}`;

  const validationErrorCode = `{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request parameters",
    "details": {
      "fields": {
        "price": "Must be a positive number",
        "name": "Required field"
      }
    }
  }
}`;

  const serviceErrorCode = `{
  "error": {
    "code": "SERVICE_ERROR",
    "message": "Service unavailable",
    "details": {
      "service": "inventory-service",
      "reason": "Connection timeout"
    }
  }
}`;

  const errorHandlingCode = `try {
  const response = await fetch('/linq', {
    method: 'POST',
    body: JSON.stringify(request)
  });
  
  const data = await response.json();
  
  if (data.error) {
    switch (data.error.code) {
      case 'AUTH_ERROR':
        // Handle authentication errors
        break;
      case 'VALIDATION_ERROR':
        // Handle validation errors
        break;
      case 'SERVICE_ERROR':
        // Handle service errors
        break;
      default:
        // Handle unexpected errors
    }
  }
} catch (error) {
  // Handle network or parsing errors
}`;

  return (
    <div className="basics-container">
      <h2>Error Handling</h2>
      
      <div className="error-overview">
        <p>
          The Linq Protocol provides a standardized way to handle and communicate errors across services.
          All errors follow a consistent format and include helpful information for debugging.
        </p>
      </div>

      <div className="error-types">
        <h3>Common Error Types</h3>
        
        <div className="error-section">
          <h4>Authentication Errors</h4>
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
            {authErrorCode}
          </SyntaxHighlighter>
          <p>Occurs when there are issues with authentication or authorization.</p>
        </div>

        <div className="error-section">
          <h4>Validation Errors</h4>
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
            {validationErrorCode}
          </SyntaxHighlighter>
          <p>Occurs when request parameters or payload don't meet validation requirements.</p>
        </div>

        <div className="error-section">
          <h4>Service Errors</h4>
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
            {serviceErrorCode}
          </SyntaxHighlighter>
          <p>Occurs when there are issues with the target service.</p>
        </div>
      </div>

      <div className="error-handling-tips">
        <h3>Best Practices</h3>
        <ul>
          <li>Always check the <code>error.code</code> for programmatic error handling</li>
          <li>Use <code>error.message</code> for user-friendly error messages</li>
          <li>Check <code>error.details</code> for additional context and debugging information</li>
          <li>Implement proper error logging using the metadata provided</li>
          <li>Handle both expected and unexpected errors gracefully</li>
        </ul>
      </div>

      <div className="error-example">
        <h3>Example Error Handling</h3>
        <SyntaxHighlighter 
          language="javascript" 
          style={vscDarkPlus}
          showLineNumbers={true}
          customStyle={{
            margin: '1.5rem 0',
            borderRadius: '8px',
            fontSize: '0.95rem',
            lineHeight: '1.6'
          }}
        >
          {errorHandlingCode}
        </SyntaxHighlighter>
      </div>
    </div>
  );
};

export default ErrorHandling; 