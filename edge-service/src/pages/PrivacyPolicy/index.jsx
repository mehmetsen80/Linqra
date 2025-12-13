import Footer from '../../components/common/Footer';
import React from 'react';
import { Link } from 'react-router-dom';
import './styles.css';

function PrivacyPolicy() {
  return (
    <div className="privacy-policy-container">
      <nav className="top-nav">
        <div className="nav-links">
          <Link to="/" className="nav-link">Home</Link>
          <a href="https://docs.linqra.com" target="_blank" rel="noopener noreferrer">Docs</a>
          <a href="https://github.com/mehmetsen80/Linqra" target="_blank" rel="noopener noreferrer">GitHub</a>
        </div>
      </nav>

      {/* ... keeping existing Hero and Content ... */}

      <div className="hero-section">
        <div className="hero-content">
          <h1>Privacy Policy</h1>
          <p className="hero-text">
            We are committed to protecting your privacy and ensuring transparency in how we handle your data.
          </p>
        </div>
      </div>

      <div className="content-wrapper">
        <div className="privacy-policy-content">
          {/* ... existing content ... */}
          <p className="last-updated">Last updated: January 2025</p>

          <section>
            <h2>1. Introduction</h2>
            <p>
              Linqra ("we," "our," or "us") is a product of DIPME, .CORP company, committed to protecting your privacy. This Privacy Policy explains how we collect, use, disclose, and safeguard your information when you use our AI integration platform and services.
            </p>
          </section>

          <section>
            <h2>2. Information We Collect</h2>
            <h3>2.1 Personal Information</h3>
            <p>We may collect personal information that you provide directly to us, including:</p>
            <ul>
              <li>Name and contact information (email address, phone number)</li>
              <li>Account credentials and authentication information</li>
              <li>Organization and team details</li>
              <li>Communication preferences</li>
            </ul>

            <h3>2.2 Usage Information</h3>
            <p>We automatically collect certain information about your use of our services:</p>
            <ul>
              <li>API usage patterns and metrics</li>
              <li>Service performance data</li>
              <li>Error logs and diagnostic information</li>
              <li>Device and browser information</li>
            </ul>

            <h3>2.3 AI Service Data</h3>
            <p>When you integrate AI services through our platform:</p>
            <ul>
              <li>API request and response data</li>
              <li>Workflow execution logs</li>
              <li>Model performance metrics</li>
              <li>Service configuration data</li>
            </ul>
          </section>

          <section>
            <h2>3. How We Use Your Information</h2>
            <p>We use the collected information for the following purposes:</p>
            <ul>
              <li>Providing and maintaining our services</li>
              <li>Processing API requests and managing workflows</li>
              <li>Improving service performance and reliability</li>
              <li>Providing customer support and technical assistance</li>
              <li>Sending service updates and notifications</li>
              <li>Ensuring security and preventing fraud</li>
              <li>Complying with legal obligations</li>
            </ul>
          </section>

          <section>
            <h2>4. Data Sharing and Disclosure</h2>
            <p>We do not sell, trade, or otherwise transfer your personal information to third parties, except in the following circumstances:</p>
            <ul>
              <li>With your explicit consent</li>
              <li>To service providers who assist in operating our platform</li>
              <li>To comply with legal requirements or court orders</li>
              <li>To protect our rights, property, or safety</li>
              <li>In connection with a business transfer or acquisition</li>
            </ul>
          </section>

          <section>
            <h2>5. Data Security</h2>
            <p>We implement appropriate technical and organizational measures to protect your information:</p>
            <ul>
              <li>Encryption of data in transit and at rest</li>
              <li>Regular security assessments and updates</li>
              <li>Access controls and authentication mechanisms</li>
              <li>Monitoring and logging of system activities</li>
              <li>Employee training on data protection</li>
            </ul>
          </section>

          <section>
            <h2>6. Data Retention</h2>
            <p>We retain your information for as long as necessary to:</p>
            <ul>
              <li>Provide our services</li>
              <li>Comply with legal obligations</li>
              <li>Resolve disputes</li>
              <li>Enforce our agreements</li>
            </ul>
            <p>You may request deletion of your data, subject to legal and contractual requirements.</p>
          </section>

          <section>
            <h2>7. Your Rights and Choices</h2>
            <p>You have the right to:</p>
            <ul>
              <li>Access and review your personal information</li>
              <li>Correct inaccurate or incomplete data</li>
              <li>Request deletion of your data</li>
              <li>Object to certain processing activities</li>
              <li>Withdraw consent where applicable</li>
              <li>Export your data in a portable format</li>
            </ul>
          </section>

          <section>
            <h2>8. International Data Transfers</h2>
            <p>Your information may be transferred to and processed in countries other than your own. We ensure appropriate safeguards are in place to protect your data in accordance with applicable data protection laws.</p>
          </section>

          <section>
            <h2>9. Cookies and Tracking Technologies</h2>
            <p>We use cookies and similar technologies to:</p>
            <ul>
              <li>Remember your preferences and settings</li>
              <li>Analyze service usage and performance</li>
              <li>Provide personalized content and features</li>
              <li>Ensure security and prevent fraud</li>
            </ul>
            <p>You can control cookie settings through your browser preferences.</p>
          </section>

          <section>
            <h2>10. Children's Privacy</h2>
            <p>Our services are not intended for children under 13 years of age. We do not knowingly collect personal information from children under 13.</p>
          </section>

          <section>
            <h2>11. Changes to This Policy</h2>
            <p>We may update this Privacy Policy from time to time. We will notify you of any material changes by:</p>
            <ul>
              <li>Posting the updated policy on our website</li>
              <li>Sending email notifications to registered users</li>
              <li>Displaying prominent notices in our services</li>
            </ul>
          </section>

          <section>
            <h2>12. Contact Us</h2>
            <p>If you have questions about this Privacy Policy or our data practices, please contact us:</p>
            <ul>
              <li>Company: DIPME, .CORP</li>
              <li>Product: Linqra</li>
              <li>Email: msen@dipme.app</li>
              <li>GitHub: <a href="https://github.com/mehmetsen80/Linqra" target="_blank" rel="noopener noreferrer">https://github.com/mehmetsen80/Linqra</a></li>
              <li>Documentation: <a href="https://docs.linqra.com" target="_blank" rel="noopener noreferrer">https://docs.linqra.com</a></li>
            </ul>
          </section>

          <div className="back-to-home">
            <Link to="/" className="back-link">
              <i className="fas fa-arrow-left"></i> Back to Home
            </Link>
          </div>
        </div>
      </div>
      <Footer />
    </div>
  );
}

export default PrivacyPolicy; 