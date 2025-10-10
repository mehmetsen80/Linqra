import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import architectureDiagram from '/images/linqra-diagram-transparent.svg';
import ImageModal from '../../components/common/ImageModal';
import { useAuth } from '../../contexts/AuthContext';
import './styles.css';

function Home() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [showImageModal, setShowImageModal] = useState(false);

  const handleGetStarted = () => {
    if (isAuthenticated) {
      navigate('/dashboard');
    } else {
      navigate('/login');
    }
  };

  return (
    <div className="home-container">
      <nav className="top-nav">
        <div className="nav-links">
          <a href="#features">Features</a>
          <a href="https://docs.linqra.com" target="_blank" rel="noopener noreferrer">Docs</a>
          <a href="https://github.com/mehmetsen80/Linqra" target="_blank" rel="noopener noreferrer">GitHub</a>
          {isAuthenticated ? (
            <Link to="/dashboard" className="auth-link">Dashboard</Link>
          ) : (
            <div className="auth-links">
              <Link to="/login" className="auth-link">Login</Link>
              {/* TODO: Uncomment this when we decide for the registration */}
              {/* <Link to="/register" className="auth-link">Register</Link> */}
            </div>
          )}
        </div>
      </nav>

      <div className="hero-section">
        <div className="hero-content">
          <img 
            src="/images/noBgWhiteOnlyLogo.png" 
            alt="Linqra Logo"
            className="hero-logo"
          />
          <div className="hero-separator"></div>
          <a 
            href="https://github.com/mehmetsen80/Linqra"
            target="_blank" 
            rel="noopener noreferrer"
            className="version-badge"
          >
            v0.6
          </a>
          <div className="hero-separator"></div>
          <div className="cta-buttons">
            <button 
              className="cta-button primary"
              onClick={handleGetStarted}
            >
              Login Linqra
            </button>
            {/* <a 
              href="https://docs.linqra.com"
              className="cta-button secondary"
              target="_blank" 
              rel="noopener noreferrer"
            >
              Learn More
            </a> */}
            <Link 
              to="/linq-protocol"
              className="cta-button secondary"
            >
              Linq Protocol
            </Link>
          </div>
        </div>
      </div>

      <div className="tagline">
          Agentic Framework for Enterprises
      </div>

      {/* Linqra Integration Section: Java & Python AI Apps */}
      <div className="linqra-integration-section">
        <div className="integration-apps-grid">
          <div className="integration-app-card">
            <i className="fab fa-java"></i>
            <h3>Java AI App</h3>
            <p>
              Seamlessly integrate your Java-based AI applications with Linqra. Enjoy unified workflows, secure deployment, and robust analytics for your enterprise Java solutions.
            </p>
          </div>
          <div className="integration-app-card">
            <i className="fab fa-python"></i>
            <h3>Python AI App</h3>
            <p>
              Connect your Python AI models and workflows to Linqra for instant deployment, orchestration, and monitoring. Perfect for data science, ML, and automation projects.
            </p>
          </div>
        </div>
      </div>

      <div className="problem-section">
      <h2>The Challenge of Modern AI Integration</h2>          
        <div className="problem-intro">
          <p>
            Building AI Agents is complicated—Linqra makes it simple with a unified gateway and protocol for easy, secure deployment.
          </p>
          <Link 
            to="/linq-protocol"
            className="learn-more-link"
          >
            Learn more about Linq Protocol →
          </Link>
        </div>
        <ul className="problem-highlights">
          <li><i className="fas fa-brain"></i> <strong>AI Agents, Simplified:</strong> Build autonomous, workflow-driven applications without the usual complexity.</li>
          <li><i className="fas fa-network-wired"></i> <strong>Unified Workflows:</strong>  Orchestrate multi-step AI tasks easily with a single protocol and unified gateway.</li>
          <li><i className="fas fa-lock"></i> <strong>Enterprise Security:</strong> Secure every step of your AI pipeline with built-in authentication and authorization.</li>
        </ul>
      </div>

      <div className="sdk-coming-soon-section">
        <div className="sdk-icon-wrapper">
          <i className="fas fa-cubes sdk-big-icon"></i>
        </div>
        <h2>Developer SDK</h2>
        <p>
          Build, extend, and integrate AI Agents even faster with the upcoming Linqra SDK for developers.
        </p>
        <button className="sdk-coming-soon-btn" disabled>
          SDK Coming Soon
        </button>
      </div>

      <div id="features" className="features-section">
        <h2>AI Agents with Enterprise-Grade Security</h2>
        <div className="features-grid">
          <div className="feature-card">
            <i className="fas fa-robot"></i>
            <h3>AI Agents</h3>
            <p>Discover and deploy AI applications with just a few clicks, from chatbots to specialized ML models</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-shield-alt"></i>
            <h3>Secure Gateway</h3>
            <p>Enterprise-grade security layer for both traditional APIs and AI services with unified authentication</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-link"></i>
            <h3>Linq Protocol</h3>
            <p>Standardized communication protocol that simplifies integration of AI apps with your existing infrastructure</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-rocket"></i>
            <h3>One-Click Deployment</h3>
            <p>Deploy AI applications instantly with automated scaling and load balancing</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-chart-line"></i>
            <h3>Usage Analytics</h3>
            <p>Monitor AI app performance, usage patterns, and costs in real-time</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-puzzle-piece"></i>
            <h3>Custom Integration</h3>
            <p>Easily integrate AI apps into your existing workflows with our flexible API architecture</p>
          </div>
        </div>
      </div>

      <div className="linq-protocol-section">
        <h2>Linq Protocol - Unified AI Communication</h2>
        <div className="protocol-content">
          <div className="protocol-description">
            <p>
              Linq Protocol simplifies complex AI workflows into a single request. 
              Instead of making multiple API calls, orchestrate your entire AI pipeline in one unified protocol.
            </p>
          </div>
          <div className="protocol-comparison">
            <div className="traditional-way">
              <h3>Traditional Way</h3>
              <div className="code-block">
                <code>
                  {`// First API Call
POST /v1/chat/completions
Authorization: Bearer sk-...
Content-Type: application/json

{
  "model": "gpt-3.5-turbo",
  "messages": [
    {"role": "user", "content": "Analyze this text"}
  ]
}

// Second API Call
POST /v1/embeddings
Authorization: Bearer sk-...
Content-Type: application/json

{
  "model": "text-embedding-ada-002",
  "input": "Process the analysis"
}`}
                </code>
              </div>
            </div>
            <div className="linq-way">
              <h3>Linq Protocol</h3>
              <div className="code-block">
                <code>
                  POST /linq<br/>
                  X-API-Key: lm_...<br/>
                  Content-Type: application/json<br/>
                  <br/>
                  {`{
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
            "id": "chat",
            "service": "chat-service",
            "action": "completion",
            "input": {
              "message": "Analyze this text"
            }
          },
          {
            "id": "embedding",
            "service": "embedding-service",
            "action": "create",
            "input": {
              "text": "#{chat.response}"
            },
            "dependsOn": ["chat"]
          }
        ]
      }
    }
  }
}`}
                </code>
              </div>
            </div>
          </div>
          <div className="protocol-benefits">
            <div className="benefit">
              <i className="fas fa-sitemap"></i>
              <h4>Workflow Orchestration</h4>
              <p>Chain multiple AI services in a single request</p>
            </div>
            <div className="benefit">
              <i className="fas fa-shield-alt"></i>
              <h4>Built-in Security</h4>
              <p>Unified authentication across all services</p>
            </div>
            <div className="benefit">
              <i className="fas fa-code"></i>
              <h4>Simple Integration</h4>
              <p>One protocol for your entire AI pipeline</p>
            </div>
          </div>
        </div>
      </div>

      <div className="whats-different-section">
        <h2>What's Different: Linqra vs. MCP</h2>
        <div className="comparison-table-wrapper">
          <table className="comparison-table">
            <thead>
              <tr>
                <th>Feature</th>
                <th>Linqra Server (Linq Protocol)</th>
                <th>MCP Server (Modal Context Protocol)</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Gateway/API Layer</td>
                <td>Yes - Centralized gateway with dynamic routing, resiliency, and unified API management</td>
                <td>No - Protocol for AI-tool interactions, not a gateway layer</td>
              </tr>
              <tr>
                <td>Workflow Orchestration</td>
                <td>Seamless API conversion to Linq Protocol. Sequential/parallel steps coming via SDK, with visual designer planned</td>
                <td>No (focus on tool interoperability) - Orchestration handled by external platforms (Zapier, n8n) or custom implementations</td>
              </tr>
              <tr>
                <td>Security</td>
                <td>Enterprise-grade with built-in OAuth 2.0, TLS, scopes, and Keycloak integration</td>
                <td>Standardized security (OAuth 2.0, TLS, scopes) - implementation depends on server configuration</td>
              </tr>
              <tr>
                <td>Dynamic Routing</td>
                <td>Yes - Built-in dynamic routing with rule-based routing and service discovery for scalable AI app ecosystems</td>
                <td>No - Routing handled by external gateways or platforms (e.g., Higress MCP in Go/Envoy)</td>
              </tr>
              <tr>
                <td>Analytics/Monitoring</td>
                <td>Yes - Built-in analytics with request latency, error rates, and AI model performance metrics</td>
                <td>No - Monitoring requires external tools or custom implementation</td>
              </tr>
              <tr>
                <td>Tool Integrations</td>
                <td>Built-in support for external APIs, AI models, and SaaS tools (expanding integration ecosystem in development)</td>
                <td>Extensive integrations with tools like Google Drive, Excel, Dropbox, Slack, and more via standardized MCP servers and automation platforms (e.g., Zapier, n8n, Composio)</td>
              </tr>
              <tr>
                <td>Developer SDK</td>
                <td>Coming soon - SDK development in progress, leveraging Linq Protocol for seamless API integration and workflow management</td>
                <td>Yes - Composio SDK (JavaScript/TypeScript, Python) with support for 200+ tools, plus additional SDKs for specific MCP servers</td>
              </tr>
              <tr>
                <td>Context Management</td>
                <td>Advanced context management with multi-step workflow orchestration and state persistence</td>
                <td>Advanced context management with standardized protocol for multi-modal AI applications</td>
              </tr>
              <tr>
                <td>Interoperability</td>
                <td>High (via gateway/protocol)</td>
                <td>High (between AI models/services)</td>
              </tr>
              <tr>
                <td>Use Case Focus</td>
                <td>AI Agent deployment & workflow simplification</td>
                <td>Multi-modal AI context/state handling</td>
              </tr>
              <tr>
                <td>Architecture</td>
                <td>Centralized logic with unified protocol (Linq Protocol) that orchestrates workflows through sequential steps</td>
                <td>Distributed and decentralized logic across multiple MCP servers, each handling specific tool interactions independently</td>
              </tr>
              <tr>
                <td>Deployment Model</td>
                <td>Hybrid SaaS - Run on Linqra.com or self-hosted in your own network</td>
                <td>Self-hosted only - Requires setting up MCP servers in your infrastructure</td>
              </tr>
              <tr>
                <td>Platform Capabilities</td>
                <td>AI Agents with team/organization management, unified API Routes for both traditional APIs and Linq Protocol endpoints</td>
                <td>Protocol for tool interactions - No built-in app store or team management</td>
              </tr>
              <tr>
                <td>RAG Support</td>
                <td>Yes - Native RAG customization in development with planned configurable retrieval pipelines, embedding models, and centralized orchestration</td>
                <td>Yes - Indirect support via standardized data retrieval and context-retrieval tools; relies on external RAG frameworks (e.g., LangChain) and distributed MCP servers</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div className="benefits-section">
        <div className="container">
          <h2>Why Choose Linqra?</h2>
          <div className="benefits-grid">
            <div className="benefit-item">
              <i className="fas fa-magic"></i>
              <h3>AI Made Simple</h3>
              <p>Deploy production-ready AI applications without the complexity</p>
            </div>
            <div className="benefit-item">
              <i className="fas fa-lock"></i>
              <h3>Enterprise Ready</h3>
              <p>Built-in security and compliance for enterprise AI deployment</p>
            </div>
            <div className="benefit-item">
              <i className="fas fa-layer-group"></i>
              <h3>Unified Platform</h3>
              <p>One platform for discovering, deploying, and managing AI applications</p>
            </div>
          </div>
        </div>
      </div>

      <div id="how-it-works" className="architecture-section">
        <h2>How It Works?</h2>
        
        <div className="architecture-explanation">
          <p>
            Linqra is a unified gateway that seamlessly handles requests from traditional APIs, user applications, 
            and AI services. Our centralized architecture simplifies integration by using a single, consistent protocol 
            for all interactions.
          </p>
          <p>
            Every request is enhanced with enterprise-grade features:
          </p>
          <ul>
            <li>
              <strong>Security</strong>
              <span>Zero-trust architecture ensuring comprehensive protection at every layer</span>
            </li>
            <li>
              <strong>Resiliency</strong>
              <span>Built-in circuit breakers, automatic retries, and intelligent rate limiting</span>
            </li>
            <li>
              <strong>Dynamic Routing</strong>
              <span>Instant service discovery and automatic routing without configuration</span>
            </li>
            <li>
              <strong>Analytics</strong>
              <span>Comprehensive metrics and insights for real-time monitoring</span>
            </li>
          </ul>
        </div>

        <div className="architecture-content">
          <div className="architecture-image">
            <img 
              src={architectureDiagram} 
              alt="Linqra Architecture"
              className="arch-diagram clickable"
              onClick={() => setShowImageModal(true)}
            />
          </div>
          <div className="architecture-features">
            <div className="arch-feature">
              <i className="fas fa-shield-alt"></i>
              <div>
                <h3>Security</h3>
                <p>Enterprise-grade authentication, authorization, and API security controls</p>
              </div>
            </div>
            <div className="arch-feature">
              <i className="fas fa-sync-alt"></i>
              <div>
                <h3>Resiliency</h3>
                <p>Advanced circuit breaking, rate limiting, and robust fault tolerance for reliable services</p>
              </div>
            </div>
            <div className="arch-feature">
              <i className="fas fa-route"></i>
              <div>
                <h3>Dynamic Routing</h3>
                <p>Intelligent request routing with automated real-time service discovery</p>
              </div>
            </div>
            <div className="arch-feature">
              <i className="fas fa-chart-bar"></i>
              <div>
                <h3>Analytics</h3>
                <p>Advanced monitoring and real-time performance insights for your services</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="deployment-section">
        <h2>Deploy Your Own AI Agents</h2>
        <div className="deployment-options">
          <div className="deployment-card">
            <i className="fas fa-building"></i>
            <h3>Enterprise On-Premise</h3>
            <p>Deploy within your secure corporate network</p>
            <ul>
              <li>Full control over data and security</li>
              <li>Integrate with existing infrastructure</li>
              <li>Custom AI model deployment</li>
              <li>Air-gapped environments supported</li>
            </ul>
          </div>
          <div className="deployment-card">
            <i className="fas fa-cloud"></i>
            <h3>Private Cloud</h3>
            <p>Deploy in your cloud environment</p>
            <ul>
              <li>AWS, Azure, GCP support</li>
              <li>Kubernetes-ready deployment</li>
              <li>Automated scaling</li>
              <li>Cloud-native security</li>
            </ul>
          </div>
          <div className="deployment-card">
            <i className="fas fa-rocket"></i>
            <h3>Startup Quick-Start</h3>
            <p>Get started in minutes</p>
            <ul>
              <li>Docker compose setup</li>
              <li>Development environment</li>
              <li>Easy configuration</li>
              <li>Community support</li>
            </ul>
          </div>
        </div>
        <div className="installation-steps">
          <h3>Quick Installation</h3>
          <div className="code-block">
            <code>
              # Clone the repository<br/>
              git clone https://github.com/mehmetsen80/Linqra<br/>
              <br/>
              # Start with Docker Compose<br/>
              docker-compose up
            </code>
          </div>
          <a href="https://docs.linqra.com/installation" className="docs-link">
            View detailed installation guide <i className="fas fa-arrow-right"></i>
          </a>
        </div>
      </div>

      <footer className="footer">
        <div className="container">
          <div className="footer-content">
            <p className="copyright">&copy; 2025 Linqra. All rights reserved.</p>
            <div className="footer-links">
              <Link to="/privacy-policy" className="footer-link">Privacy Policy</Link>
              <Link to="/terms-of-service" className="footer-link">Terms of Service</Link>
            </div>
          </div>
        </div>
      </footer>

      <ImageModal 
        show={showImageModal}
        onHide={() => setShowImageModal(false)}
        imageSrc={architectureDiagram}
      />
    </div>
  );
}

export default Home;