import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import architectureDiagram from '/images/linqra_high_level_architecture.png';
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
              <Link to="/register" className="auth-link">Register</Link>
            </div>
          )}
        </div>
      </nav>

      <div className="hero-section">
        <div className="hero-content">
          <img 
            src="/images/noBgWhite.png" 
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
            v0.1
          </a>
          <div className="hero-separator"></div>
          <div className="cta-buttons">
            <button 
              className="cta-button primary"
              onClick={handleGetStarted}
            >
              Explore AI Apps
            </button>
            <a 
              href="https://docs.linqra.com"
              className="cta-button secondary"
              target="_blank" 
              rel="noopener noreferrer"
            >
              Learn More
            </a>
          </div>
        </div>
      </div>

      <div id="features" className="features-section">
        <h2>AI App Store with Enterprise-Grade Security</h2>
        <div className="features-grid">
          <div className="feature-card">
            <i className="fas fa-store"></i>
            <h3>AI App Marketplace</h3>
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
        <h2>Deploy Your Own AI App Store</h2>
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
          <p className="copyright">&copy; 2025 Linqra. All rights reserved.</p>
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