import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Modal, Button } from 'react-bootstrap';
import architectureDiagram from '/images/linqra-diagram-transparent.svg';
import ImageModal from '../../components/common/ImageModal';
import Footer from '../../components/common/Footer';
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
          <a href="#solutions">Solutions</a>
          <a href="#security">Security</a>
          <a href="https://docs.linqra.com" target="_blank" rel="noopener noreferrer">Docs</a>
          {isAuthenticated ? (
            <Link to="/dashboard" className="auth-link">Dashboard</Link>
          ) : (
            <div className="auth-links">
              <Link to="/login" className="auth-link">Login</Link>
            </div>
          )}
        </div>
      </nav>

      {/* ENTERPRISE HERO SECTION */}
      <div className="hero-section enterprise-hero">
        <div className="hero-content">
          <img
            src="/images/noBgWhiteOnlyLogo.png"
            alt="Linqra Logo"
            className="hero-logo"
          />
          <div className="enterprise-title">Integration Complexity Creates Security Gaps. We Close Them.</div>
          <p className="hero-text">
            One orchestration platform for all your AI—encrypted, auditable, and compliance-ready from day one.
          </p>

          <div className="cta-buttons">
            <button
              className="cta-button primary"
              onClick={handleGetStarted}
            >
              Start Secure Trial
            </button>
            <a
              href="#security"
              className="cta-button secondary"
            >
              View Security Architecture
            </a>
          </div>
        </div>
      </div>

      {/* TRUST STRIP */}
      <div className="trust-strip">
        <div className="trust-badge" title="Data encrypted at rest and in transit">
          <i className="fas fa-lock"></i> AES-256 Encrypted
        </div>
        <div className="trust-badge" title="Ready for SOC 2 Type II Audits">
          <i className="fas fa-file-contract"></i> SOC 2 Ready
        </div>
        <div className="trust-badge" title="HIPAA Compliant for PHI data">
          <i className="fas fa-heartbeat"></i> HIPAA Compliant
        </div>
        <div className="trust-badge" title="GDPR & CCPA Ready">
          <i className="fas fa-globe-europe"></i> GDPR Ready
        </div>
        <div className="trust-badge" title="Financial Services Compatible">
          <i className="fas fa-university"></i> GLBA Ready
        </div>
      </div>

      <div className="orchestration-section">
        <h2 className="home-section-title">Unified AI Orchestration Architecture</h2>
        <div className="hero-image-container">
          <img src="/images/linqra_orchestrator_final.png" alt="Unified AI Orchestrator" className="hero-image" />
        </div>
      </div>

      {/* SOLUTIONS GRID (Replaces "Core Components") */}
      <div id="solutions" className="core-components-section">
        <h2 className="home-section-title">Industry-Specific AI Solutions</h2>
        <p className="section-subtitle">Don't risk your license with generic AI tools. Linqra is built for compliance.</p>

        <div className="solutions-grid">
          {/* FINANCE CARD */}
          <div className="solution-card">
            <div className="card-icon finance-icon">
              <i className="fas fa-chart-line"></i>
            </div>
            <h3>Financial Services</h3>
            <p>Automate fraud detection and portfolio analysis without exposing customer PII.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> <strong>Audit Logs:</strong> Immutable S3 Archival with Versioning.</li>
              <li><i className="fas fa-check"></i> <strong>Data Sovereignty:</strong> Keep data in Your AWS VPC.</li>
              <li><i className="fas fa-check"></i> <strong>Fraud:</strong> Real-time anomaly detection agents.</li>
            </ul>
          </div>

          {/* LEGAL CARD */}
          <div className="solution-card">
            <div className="card-icon legal-icon">
              <i className="fas fa-gavel"></i>
            </div>
            <h3>Legal & Law Firms</h3>
            <p>Process contracts and discovery documents with strict Attorney-Client privilege.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> <strong>Isolation:</strong> Per-Tenant Encryption Keys.</li>
              <li><i className="fas fa-check"></i> <strong>Privilege:</strong> No data training on your documents.</li>
              <li><i className="fas fa-check"></i> <strong>Discovery:</strong> AI-powered document review workflows.</li>
            </ul>
          </div>

          {/* HEALTHCARE CARD */}
          <div className="solution-card">
            <div className="card-icon health-icon">
              <i className="fas fa-user-md"></i>
            </div>
            <h3>Healthcare (HIPAA)</h3>
            <p>Enhance patient care with AI while adhering to strict HIPAA privacy rules.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> <strong>PHI Protection:</strong> Auto-redaction of PII/PHI.</li>
              <li><i className="fas fa-check"></i> <strong>Access Control:</strong> Role-Based Access (RBAC).</li>
              <li><i className="fas fa-check"></i> <strong>BAA:</strong> We sign Business Associate Agreements.</li>
            </ul>
          </div>
        </div>
      </div>

      {/* AGENT CAPABILITIES SECTION */}
      <div className="core-components-section" style={{ paddingTop: '0' }}>
        <h2 className="home-section-title">Build Intelligent AI Workflows</h2>
        <p className="section-subtitle">Transform your documents into active agents. Securely. Privately. Compliantly.</p>

        <div className="solutions-grid">
          {/* CUSTOM AGENTS CARD */}
          <div className="solution-card">
            <div className="card-icon finance-icon">
              <i className="fas fa-robot"></i>
            </div>
            <h3>Custom AI Assistants</h3>
            <p>Design specialized agents that run on your choice of LLMs. From Legal Reviewers to Financial Analysts.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> <strong>Orchestration:</strong> Chain multiple agents for complex tasks.</li>
              <li><i className="fas fa-check"></i> <strong>Controls:</strong> Define strict boundaries and tools.</li>
              <li><i className="fas fa-check"></i> <strong>Audit:</strong> Every agent action is logged and traceable.</li>
            </ul>
          </div>

          {/* KNOWLEDGE HUB CARD */}
          <div className="solution-card">
            <div className="card-icon legal-icon">
              <i className="fas fa-file-invoice"></i>
            </div>
            <h3>Secure Knowledge Hub</h3>
            <p>Upload your sensitive PDFs, Excel sheets, and Contracts. We index them into a secure, private vector store.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> <strong>Isolation:</strong> Per-Tenant Vector Collections.</li>
              <li><i className="fas fa-check"></i> <strong>Encryption:</strong> Documents encrypted before indexing.</li>
              <li><i className="fas fa-check"></i> <strong>Management:</strong> Easy upload & version control.</li>
            </ul>
          </div>

          {/* CHAT WITH DOCS CARD */}
          <div className="solution-card">
            <div className="card-icon health-icon">
              <i className="fas fa-comments"></i>
            </div>
            <h3>Talk to Your Data (RAG)</h3>
            <p>Empower your agents to "read" and cite your internal documents to answer questions with high accuracy.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> <strong>Citations:</strong> Agents provide sources for their answers.</li>
              <li><i className="fas fa-check"></i> <strong>Privacy:</strong> Data never trains public models.</li>
              <li><i className="fas fa-check"></i> <strong>Context:</strong> Smart retrieval for relevant answers.</li>
            </ul>
          </div>
        </div>
      </div>

      {/* SECURITY DEEP DIVE */}
      <div id="security" className="compliance-section">
        <div className="container">
          <div className="compliance-content">
            <div className="compliance-text">
              <h2>Built on a Foundation of Trust</h2>
              <p>Security isn't a "feature" at Linqra—it's the architecture.</p>

              <div className="compliance-checklist">
                <div className="checklist-item">
                  <div className="check-icon"><i className="fas fa-shield-alt"></i></div>
                  <div>
                    <h4>Zero-Trust Architecture</h4>
                    <p>Every request is authenticated via mTLS and OIDC tokens. No implicit trust between microservices.</p>
                  </div>
                </div>
                <div className="checklist-item">
                  <div className="check-icon"><i className="fas fa-database"></i></div>
                  <div>
                    <h4>Immutable Ledger</h4>
                    <p>All actions are logged to a tamper-proof S3 bucket with Object Lock enabled for legal hold capability.</p>
                  </div>
                </div>
                <div className="checklist-item">
                  <div className="check-icon"><i className="fas fa-key"></i></div>
                  <div>
                    <h4>Bring Your Own Key (BYOK)</h4>
                    <p>Dedicated Per-Tenant Encryption Keys. Your data is cryptographically isolated from all other tenants.</p>
                  </div>
                </div>
              </div>
            </div>
            <div className="compliance-image">
              <img
                src={architectureDiagram}
                alt="Linqra Security Architecture"
                className="security-diagram clickable"
                onClick={() => setShowImageModal(true)}
              />
              <p className="caption">Click to view Security Architecture</p>
            </div>
          </div>
        </div>
      </div>

      {/* TECHNICAL DETAILS (Always Visible) */}
      <div className="developer-deep-dive-section">
        <h2>Technical & Compliance Validation</h2>

        <div className="dev-grid-layout">
          {/* COLUMN 1: SECURITY SPECS (User Liked This) */}
          <div className="dev-column">
            <h3>Security Specifications</h3>
            <div className="specs-table-container">
              <table className="specs-table">
                <thead>
                  <tr>
                    <th>Feature</th>
                    <th>Specification</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><strong>Encryption Algo</strong></td>
                    <td>AES-256-GCM (NIST Approved)</td>
                  </tr>
                  <tr>
                    <td><strong>Key Management</strong></td>
                    <td>Vault + Per-Tenant Rotary Keys</td>
                  </tr>
                  <tr>
                    <td><strong>Audit Integrity</strong></td>
                    <td>S3 Object Lock (WORM) Compliant</td>
                  </tr>
                  <tr>
                    <td><strong>Data Isolation</strong></td>
                    <td>Cryptographic & Logical (VPC)</td>
                  </tr>
                  <tr>
                    <td><strong>Transport Security</strong></td>
                    <td>TLS 1.3 (Mutual Auth Supported)</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* COLUMN 2: COMPLIANCE MATRIX (New Table) */}
          <div className="dev-column">
            <h3>Compliance Coverage</h3>
            <div className="specs-table-container">
              <table className="specs-table">
                <thead>
                  <tr>
                    <th>Requirement</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><strong>HIPAA (PHI)</strong></td>
                    <td><i className="fas fa-check-circle" style={{ color: '#10b981' }}></i> Ready</td>
                  </tr>
                  <tr>
                    <td><strong>GDPR / CCPA</strong></td>
                    <td><i className="fas fa-check-circle" style={{ color: '#10b981' }}></i> Ready</td>
                  </tr>
                  <tr>
                    <td><strong>SOC 2 Type II</strong></td>
                    <td><i className="fas fa-check-circle" style={{ color: '#10b981' }}></i> Ready</td>
                  </tr>
                  <tr>
                    <td><strong>GLBA (Finance)</strong></td>
                    <td><i className="fas fa-check-circle" style={{ color: '#10b981' }}></i> Ready</td>
                  </tr>
                  <tr>
                    <td><strong>Data Residency</strong></td>
                    <td><i className="fas fa-check-circle" style={{ color: '#10b981' }}></i> US/EU</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <Footer />

      <ImageModal
        show={showImageModal}
        onHide={() => setShowImageModal(false)}
        imageSrc={architectureDiagram}
      />
    </div>
  );
}

export default Home;