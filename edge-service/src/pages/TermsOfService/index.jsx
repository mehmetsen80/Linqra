import React from 'react';
import { Link } from 'react-router-dom';
import './styles.css';

function TermsOfService() {
  return (
    <div className="terms-of-service-container">
      <nav className="top-nav">
        <div className="nav-links">
          <Link to="/" className="nav-link">Home</Link>
          <a href="https://docs.linqra.com" target="_blank" rel="noopener noreferrer">Docs</a>
          <a href="https://github.com/mehmetsen80/Linqra" target="_blank" rel="noopener noreferrer">GitHub</a>
        </div>
      </nav>

      <div className="hero-section">
        <div className="hero-content">
          <h1>Terms of Service</h1>
          <p className="hero-text">
            Please read these terms carefully before using our services. By using Linqra, you agree to these terms.
          </p>
        </div>
      </div>

      <div className="content-wrapper">
        <div className="terms-of-service-content">
          <p className="last-updated">Last updated: January 2025</p>
          
          <section>
            <h2>1. Acceptance of Terms</h2>
            <p>
              By accessing and using Linqra, a product of DIPME, .CORP company ("the Service"), you accept and agree to be bound by the terms and provision of this agreement. If you do not agree to abide by the above, please do not use this service.
            </p>
          </section>

          <section>
            <h2>2. Description of Service</h2>
            <p>
              Linqra is an AI integration platform that provides:
            </p>
            <ul>
              <li>Unified gateway for AI services and traditional APIs</li>
              <li>Workflow orchestration and management</li>
              <li>Enterprise-grade security and authentication</li>
              <li>Dynamic routing and service discovery</li>
              <li>Analytics and monitoring capabilities</li>
              <li>Team and organization management</li>
            </ul>
          </section>

          <section>
            <h2>3. User Accounts and Registration</h2>
            <h3>3.1 Account Creation</h3>
            <p>To access certain features of the Service, you must create an account. You agree to:</p>
            <ul>
              <li>Provide accurate and complete information</li>
              <li>Maintain the security of your account credentials</li>
              <li>Notify us immediately of any unauthorized use</li>
              <li>Accept responsibility for all activities under your account</li>
            </ul>

            <h3>3.2 Account Security</h3>
            <p>You are responsible for maintaining the confidentiality of your account information and for all activities that occur under your account.</p>
          </section>

          <section>
            <h2>4. Acceptable Use Policy</h2>
            <p>You agree not to use the Service to:</p>
            <ul>
              <li>Violate any applicable laws or regulations</li>
              <li>Infringe upon intellectual property rights</li>
              <li>Transmit harmful, offensive, or inappropriate content</li>
              <li>Attempt to gain unauthorized access to the Service</li>
              <li>Interfere with or disrupt the Service</li>
              <li>Use the Service for any illegal or unauthorized purpose</li>
              <li>Generate excessive load on our infrastructure</li>
            </ul>
          </section>

          <section>
            <h2>5. API Usage and Rate Limits</h2>
            <h3>5.1 API Access</h3>
            <p>Access to our APIs is subject to:</p>
            <ul>
              <li>Valid authentication credentials</li>
              <li>Compliance with rate limits and quotas</li>
              <li>Acceptance of our API documentation and guidelines</li>
            </ul>

            <h3>5.2 Rate Limiting</h3>
            <p>We reserve the right to implement rate limiting to ensure fair usage and system stability. Excessive usage may result in temporary or permanent restrictions.</p>
          </section>

          <section>
            <h2>6. Data and Privacy</h2>
            <p>Your use of the Service is also governed by our Privacy Policy, which is incorporated into these Terms by reference. By using the Service, you consent to the collection and use of information as detailed in our Privacy Policy.</p>
          </section>

          <section>
            <h2>7. Intellectual Property Rights</h2>
            <h3>7.1 Our Rights</h3>
            <p>The Service and its original content, features, and functionality are owned by Linqra and are protected by international copyright, trademark, patent, trade secret, and other intellectual property laws.</p>

            <h3>7.2 Your Rights</h3>
            <p>You retain ownership of any content you submit, post, or display on or through the Service. By submitting content, you grant us a worldwide, non-exclusive, royalty-free license to use, reproduce, and distribute such content.</p>
          </section>

          <section>
            <h2>8. Service Availability and Modifications</h2>
            <h3>8.1 Service Availability</h3>
            <p>We strive to maintain high availability but do not guarantee uninterrupted access to the Service. The Service may be temporarily unavailable due to maintenance, updates, or other factors.</p>

            <h3>8.2 Service Modifications</h3>
            <p>We reserve the right to modify, suspend, or discontinue the Service at any time with or without notice. We shall not be liable to you or any third party for any modification, suspension, or discontinuance.</p>
          </section>

          <section>
            <h2>9. Third-Party Services and Integrations</h2>
            <p>The Service may integrate with third-party services and APIs. We are not responsible for:</p>
            <ul>
              <li>The availability or accuracy of third-party services</li>
              <li>The content, products, or services available from third parties</li>
              <li>Any damages or losses resulting from third-party services</li>
            </ul>
          </section>

          <section>
            <h2>10. Disclaimers and Limitations</h2>
            <h3>10.1 Service Disclaimer</h3>
            <p>The Service is provided "as is" and "as available" without warranties of any kind, either express or implied.</p>

            <h3>10.2 Limitation of Liability</h3>
            <p>In no event shall Linqra be liable for any indirect, incidental, special, consequential, or punitive damages, including without limitation, loss of profits, data, use, goodwill, or other intangible losses.</p>
          </section>

          <section>
            <h2>11. Indemnification</h2>
            <p>You agree to defend, indemnify, and hold harmless Linqra from and against any claims, damages, obligations, losses, liabilities, costs, or debt arising from your use of the Service or violation of these Terms.</p>
          </section>

          <section>
            <h2>12. Termination</h2>
            <p>We may terminate or suspend your account and access to the Service immediately, without prior notice, for any reason, including breach of these Terms. Upon termination, your right to use the Service will cease immediately.</p>
          </section>

          <section>
            <h2>13. Governing Law and Dispute Resolution</h2>
            <p>These Terms shall be governed by and construed in accordance with the laws of the jurisdiction in which Linqra operates. Any disputes arising from these Terms or your use of the Service shall be resolved through binding arbitration.</p>
          </section>

          <section>
            <h2>14. Changes to Terms</h2>
            <p>We reserve the right to modify these Terms at any time. We will notify users of any material changes by:</p>
            <ul>
              <li>Posting the updated Terms on our website</li>
              <li>Sending email notifications to registered users</li>
              <li>Displaying prominent notices in our services</li>
            </ul>
            <p>Your continued use of the Service after such modifications constitutes acceptance of the updated Terms.</p>
          </section>

          <section>
            <h2>15. Contact Information</h2>
            <p>If you have questions about these Terms of Service, please contact us:</p>
            <ul>
              <li>Company: DIPME, .CORP</li>
              <li>Product: Linqra</li>
              <li>Email: legal@linqra.com</li>
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
    </div>
  );
}

export default TermsOfService; 