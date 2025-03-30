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
          {/* <h1>Modern API Gateway Solution</h1>
          <p className="hero-text">
            Monitor and manage your microservices with ease
          </p> */}
          <div className="cta-buttons">
            <button 
              className="cta-button primary"
              onClick={handleGetStarted}
            >
              Get Started
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
        <h2>Unified Gateway for APIs and AI Services</h2>
        <div className="features-grid">
          <div className="feature-card">
            <i className="fas fa-network-wired"></i>
            <h3>Hybrid Architecture</h3>
            <p>Seamlessly manage both traditional REST APIs and modern AI services through a single, unified gateway</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-brain"></i>
            <h3>AI-Ready Integration</h3>
            <p>Native support for AI model deployment, inference optimization, and intelligent request routing</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-link"></i>
            <h3>Linq Protocol</h3>
            <p>Innovative protocol that unifies API calls into a streamlined POST-based approach for both traditional and AI endpoints</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-shield-alt"></i>
            <h3>Enterprise Security</h3>
            <p>Comprehensive security with authentication, authorization, and advanced threat protection for all service types</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-tachometer-alt"></i>
            <h3>High Performance</h3>
            <p>Optimized for both API and AI workloads, delivering fast response times with efficient resource utilization</p>
          </div>
          <div className="feature-card">
            <i className="fas fa-chart-line"></i>
            <h3>Unified Monitoring</h3>
            <p>Real-time metrics and analytics for both API and AI services in a single, comprehensive dashboard</p>
          </div>
        </div>
      </div>

      <div className="benefits-section">
        <div className="container">
          <h2>Why Choose Linqra?</h2>
          <div className="benefits-grid">
            <div className="benefit-item">
              <i className="fas fa-rocket"></i>
              <h3>Quick Setup</h3>
              <p>Get started in minutes with our intuitive configuration</p>
            </div>
            <div className="benefit-item">
              <i className="fas fa-code"></i>
              <h3>Developer Friendly</h3>
              <p>Built by developers to make API management intuitive and efficient</p>
            </div>
            <div className="benefit-item">
              <i className="fas fa-layer-group"></i>
              <h3>Scalable Architecture</h3>
              <p>Effortlessly scales from startup to enterprise</p>
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