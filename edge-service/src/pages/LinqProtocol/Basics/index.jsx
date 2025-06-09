import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import './styles.css';

const Basics = () => {
  const traditionalApiCode = `{
  "method": "GET",
  "url": "https://localhost:7777/r/inventory-service/api/inventory"
}`;

  const linqProtocolCode = `{
  "method": "POST",
  "url": "https://localhost:7777/linq",
  "body": {
    "link": {
      "target": "inventory-service",
      "action": "fetch"
    },
    "query": {
      "intent": "api/inventory",
      "params": {}
    }
  }
}`;

  return (
    <div className="basics-container">
      <h2>Understanding Linq Protocol</h2>
      
      <div className="comparison-section">
        <div className="traditional-api">
          <h3>Traditional API Approach</h3>
          <p>
            With traditional APIs, you need to know the exact endpoint for each service. However, Linqra simplifies this by providing a unified gateway architecture where all API requests go through a standardized endpoint pattern: <code>/r/servicename</code>.
          </p>
          <p>
            For example, if you have services like inventory-service, chatbot, or reportapp, you can access them through:
          </p>
          <ul className="api-examples">
            <li><code>/r/inventory-service</code> - For inventory management</li>
            <li><code>/r/chatbot</code> - For chatbot interactions</li>
            <li><code>/r/reportapp</code> - For report generation</li>
          </ul>
          <p>
            These are defined as API Routes in the system, allowing any API interaction through a single gateway endpoint while maintaining the original API structure.
          </p>
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
            {traditionalApiCode}
          </SyntaxHighlighter>
          <p className="note">
            Each service might have different endpoints, protocols, and authentication methods, but Linqra's gateway architecture provides a unified way to access them through the <code>/r/servicename</code> pattern.
          </p>
        </div>

        <div className="linq-protocol">
          <h3>Linq Protocol Approach</h3>
          <p>
            Linq Protocol provides a higher-level abstraction for service communication. While the gateway architecture uses <code>/r/servicename</code> endpoints, Linq Protocol unifies all interactions through a single <code>POST /linq</code> endpoint with a standardized schema.
          </p>
          <p>
            For example, when you make a Linq Protocol request, it internally routes through the appropriate <code>/r/servicename</code> endpoint while providing a consistent interface for all services.
          </p>
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
            {linqProtocolCode}
          </SyntaxHighlighter>
          <p className="note">
            This is just the beginning of what Linq Protocol can do. In advanced workflows, you can chain multiple service calls together, creating complex sequences of operations while maintaining the same simple interface. We'll explore these advanced capabilities in detail in later sections.
          </p>
        </div>
      </div>

      <div className="benefits-section">
        <h3>Key Benefits of Linq Protocol</h3>
        <div className="benefits-grid">
          <div className="benefit-item">
            <strong>Consistency</strong>
            <span>Single endpoint for all service communications</span>
          </div>
          <div className="benefit-item">
            <strong>Simplicity</strong>
            <span>Standardized request format across all services</span>
          </div>
          <div className="benefit-item">
            <strong>Maintainability</strong>
            <span>Easier to manage and update service interactions</span>
          </div>
          <div className="benefit-item">
            <strong>Scalability</strong>
            <span>Simplified integration of new services</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Basics;