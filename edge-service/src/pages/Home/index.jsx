import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Modal, Button, OverlayTrigger, Tooltip } from 'react-bootstrap';
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
          {/* <a href="#solutions">Solutions</a>
          <a href="#security">Security</a>
          <a href="https://docs.linqra.com" target="_blank" rel="noopener noreferrer">Docs</a> */}
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
          <div className="enterprise-title">AI Orchestration for Regulated Industries</div>
          <p className="hero-text">
            Simplify AI integration, close security gaps—compliance-ready from day one.
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
        <div className="trust-title">
          <i className="fas fa-lock"></i> AES-256 Encrypted
        </div>

        {/* Row 1 */}
        <OverlayTrigger
          placement="top"
          overlay={
            <Tooltip id="tooltip-soc2">
              <strong>Service Organization Control 2 (SOC 2)</strong><br />
              A gold standard for SaaS security.<br /><br />
              <strong>Why Linqra is Ready:</strong> We implement strict access controls (RBAC), immutable audit logs, and encrypted backups to satisfy Trust Services Criteria.
            </Tooltip>
          }
        >
          <div className="trust-badge">
            <i className="fas fa-file-contract"></i> SOC 2 Ready
          </div>
        </OverlayTrigger>

        <OverlayTrigger
          placement="top"
          overlay={
            <Tooltip id="tooltip-hipaa">
              <strong>HIPAA (Healthcare)</strong><br />
              Protects sensitive patient health information (PHI).<br /><br />
              <strong>Why Linqra is Compliant:</strong> We isolate PHI using per-team encryption keys (AES-256) and sign Business Associate Agreements (BAA).
            </Tooltip>
          }
        >
          <div className="trust-badge">
            <i className="fas fa-heartbeat"></i> HIPAA Compliant
          </div>
        </OverlayTrigger>

        <OverlayTrigger
          placement="top"
          overlay={
            <Tooltip id="tooltip-gdpr">
              <strong>GDPR (Europe)</strong><br />
              The toughest privacy and security law in the world.<br /><br />
              <strong>Why Linqra is Ready:</strong> We support "Right to be Forgotten" via crypto-shredding and enforce strict data residency controls.
            </Tooltip>
          }
        >
          <div className="trust-badge">
            <i className="fas fa-globe-europe"></i> GDPR Ready
          </div>
        </OverlayTrigger>

        <OverlayTrigger
          placement="top"
          overlay={
            <Tooltip id="tooltip-glba">
              <strong>GLBA (Financial Services)</strong><br />
              Requires financial institutions to protect customer data.<br /><br />
              <strong>Why Linqra is Ready:</strong> We enforce strict separation of duties and financial-grade encryption for all data at rest and in transit.
            </Tooltip>
          }
        >
          <div className="trust-badge">
            <i className="fas fa-university"></i> GLBA Ready
          </div>
        </OverlayTrigger>

        {/* Force new row */}
        <div style={{ width: '100%', height: '0' }}></div>

        {/* Row 2 */}
        <OverlayTrigger
          placement="top"
          overlay={
            <Tooltip id="tooltip-ferpa">
              <strong>FERPA (Education)</strong><br />
              Protects student education records access and privacy.<br /><br />
              <strong>Why Linqra is Ready:</strong> We act as a "School Official" with legitimate educational interest, securing records with encryption and RBAC.
            </Tooltip>
          }
        >
          <div className="trust-badge">
            <i className="fas fa-user-graduate"></i> FERPA Ready
          </div>
        </OverlayTrigger>

        <OverlayTrigger
          placement="top"
          overlay={
            <Tooltip id="tooltip-aba">
              <strong>ABA Model Rules (Legal)</strong><br />
              Requires lawyers to protect client confidentiality.<br /><br />
              <strong>Why Linqra is Ready:</strong> Our "blind" architecture ensures attorney-client privilege is preserved—even we cannot see your data.
            </Tooltip>
          }
        >
          <div className="trust-badge">
            <i className="fas fa-balance-scale"></i> ABA Ready
          </div>
        </OverlayTrigger>

        <OverlayTrigger
          placement="top"
          overlay={
            <Tooltip id="tooltip-iso">
              <strong>ISO 27001</strong><br />
              International standard for Information Security Management.<br /><br />
              <strong>Why Linqra is Aligned:</strong> Our security policies, risk management, and operational controls are built on the ISO 27001 framework.
            </Tooltip>
          }
        >
          <div className="trust-badge">
            <i className="fas fa-shield-alt"></i> ISO 27001 Aligned
          </div>
        </OverlayTrigger>

        <OverlayTrigger
          placement="top"
          overlay={
            <Tooltip id="tooltip-ccpa">
              <strong>CCPA (California)</strong><br />
              Gives consumers control over their personal information.<br /><br />
              <strong>Why Linqra is Ready:</strong> We provide detailed data mapping and automated "Do Not Sell" compliance features.
            </Tooltip>
          }
        >
          <div className="trust-badge">
            <i className="fas fa-landmark"></i> CCPA Ready
          </div>
        </OverlayTrigger>
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
          {/* EDUCATION CARD (Moved to top) */}
          <div className="solution-card">
            <div className="card-icon education-icon">
              <i className="fas fa-user-graduate"></i>
            </div>
            <h3>Education (FERPA)</h3>
            <p>Safeguard student records and research data with institutional control.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> FERPA-compliant encryption</li>
              <li><i className="fas fa-check"></i> "School Official" status ready</li>
              <li><i className="fas fa-check"></i> Isolated research environments</li>
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
              <li><i className="fas fa-check"></i> Per-tenant encryption keys</li>
              <li><i className="fas fa-check"></i> No data training on your documents</li>
              <li><i className="fas fa-check"></i> AI-powered document review workflows</li>
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
              <li><i className="fas fa-check"></i> Auto-redaction of PII/PHI</li>
              <li><i className="fas fa-check"></i> Role-Based Access Control (RBAC)</li>
              <li><i className="fas fa-check"></i> We sign Business Associate Agreements</li>
            </ul>
          </div>

          {/* FINANCE CARD (Moved from top) */}
          <div className="solution-card">
            <div className="card-icon finance-icon">
              <i className="fas fa-chart-line"></i>
            </div>
            <h3>Financial Services</h3>
            <p>Automate fraud detection and portfolio analysis without exposing customer PII.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> Immutable S3 audit logs with versioning</li>
              <li><i className="fas fa-check"></i> Keep data in your AWS VPC</li>
              <li><i className="fas fa-check"></i> Real-time fraud detection agents</li>
            </ul>
          </div>

          {/* TRANSPORTATION CARD (New) */}
          <div className="solution-card">
            <div className="card-icon transport-icon">
              <i className="fas fa-truck-moving"></i>
            </div>
            <h3>Transportation & Logistics</h3>
            <p>Optimize fleet operations and routes while securing driver data.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> Secure IoT data ingestion</li>
              <li><i className="fas fa-check"></i> Driver PII protection</li>
              <li><i className="fas fa-check"></i> Real-time supply chain analytics</li>
            </ul>
          </div>

          {/* GOVERNMENT CARD (New) */}
          <div className="solution-card">
            <div className="card-icon gov-icon">
              <i className="fas fa-landmark"></i>
            </div>
            <h3>Government & Defense</h3>
            <p>Modernize public services with sovereign, fed-ready AI infrastructure.</p>
            <ul className="solution-features">
              <li><i className="fas fa-check"></i> Private Cloud / Air-gapped options</li>
              <li><i className="fas fa-check"></i> Strict data residency controls</li>
              <li><i className="fas fa-check"></i> FedRAMP-aligned security headers</li>
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
              <li><i className="fas fa-check"></i> Chain agents for complex tasks</li>
              <li><i className="fas fa-check"></i> Define strict boundaries and tools</li>
              <li><i className="fas fa-check"></i> Every action is logged and traceable</li>
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
              <li><i className="fas fa-check"></i> Per-tenant vector collections</li>
              <li><i className="fas fa-check"></i> Documents encrypted before indexing</li>
              <li><i className="fas fa-check"></i> Easy upload and version control</li>
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
              <li><i className="fas fa-check"></i> Agents cite sources for answers</li>
              <li><i className="fas fa-check"></i> Data never trains public models</li>
              <li><i className="fas fa-check"></i> Smart retrieval for relevant answers</li>
            </ul>
          </div>
        </div>
      </div>

      {/* FEATURES SECTION - AI Agents with Enterprise-Grade Security */}
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

      {/* HOW IT WORKS SECTION */}
      <div id="how-it-works" className="architecture-section">
        <h2>How It Works?</h2>

        <div className="architecture-explanation">
          <p>
            Linqra is a unified gateway that seamlessly handles requests from traditional APIs, user applications,
            and AI services.
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

      {/* SDK COMING SOON SECTION - Full Width Two Column */}
      <div className="sdk-coming-soon-section">
        {/* Header Row - Developer Friendly Title */}
        <div className="developer-friendly-header">
          <div className="developer-friendly-icon">
            <i className="fas fa-code fa-2x"></i>
          </div>
          <h2>Developer Friendly</h2>
        </div>

        {/* Two Column Content */}
        <div className="sdk-content-wrapper">
          {/* Left Column - Why Custom */}
          <div className="sdk-left-column">
            <p>
              Linqra is built for regulated industries—healthcare, legal, and finance—but serves as a
              powerful backend API and AI orchestration platform for any institution, company, or startup
              that needs enterprise-grade security and compliance.
            </p>
            <ul className="developer-benefits">
              <li>
                <i className="fas fa-check-circle"></i>
                <span><strong>Custom Workflows:</strong> Build AI pipelines tailored to your business logic</span>
              </li>
              <li>
                <i className="fas fa-check-circle"></i>
                <span><strong>Linq Protocol:</strong> Unified API layer that standardizes communication—the SDK is built on it</span>
              </li>
              <li>
                <i className="fas fa-check-circle"></i>
                <span><strong>Extensible Agents:</strong> Create specialized AI agents with custom tools</span>
              </li>
              <li>
                <i className="fas fa-check-circle"></i>
                <span><strong>White-Label Ready:</strong> Embed Linqra capabilities into your own products</span>
              </li>
            </ul>
          </div>

          {/* Right Column - SDK Coming Soon */}
          <div className="sdk-right-column">
            <div className="sdk-icon-wrapper">
              <i className="fas fa-cubes sdk-big-icon"></i>
            </div>
            <h2>Developer SDK</h2>
            <p>
              Build, extend, and integrate AI Agents even faster with the upcoming Linqra SDK for developers.
            </p>
            <div className="sdk-language-icons">
              <div className="sdk-lang-icon" title="Python SDK">
                <i className="fab fa-python"></i>
                <span>Python</span>
              </div>
              <div className="sdk-lang-icon" title="Java SDK">
                <i className="fab fa-java"></i>
                <span>Java</span>
              </div>
            </div>
            <button className="sdk-coming-soon-btn" disabled>
              SDK Coming Soon
            </button>
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