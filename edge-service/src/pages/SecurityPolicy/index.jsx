import React from 'react';
import { Link } from 'react-router-dom';
import Footer from '../../components/common/Footer';
import '../Home/styles.css';
import './styles.css';

const SecurityPolicy = () => {
    return (
        <div className="home-container">
            {/* NAV (Overlay) */}
            <nav className="solution-nav">
                <Link to="/" className="navbar-logo">
                    <img src="/images/noBgWhiteOnlyLogo.png" alt="Linqra" />
                </Link>
                <div className="navbar-links">
                    <Link to="/login" className="nav-link">Login</Link>
                    <Link to="/contact" className="cta-button primary small">Get Started</Link>
                </div>
            </nav>

            {/* HERO */}
            <div className="hero-section enterprise-hero security-hero-bg">
                <div className="hero-content">
                    <h1 className="enterprise-title">Information Security Policy (ISP)</h1>
                    <p className="hero-text">
                        <strong>Version:</strong> 1.0 &nbsp;|&nbsp; <strong>Effective:</strong> 2025-12-08 &nbsp;|&nbsp; <strong>Owner:</strong> CISO
                    </p>
                </div>
            </div>

            {/* CONTENT */}
            <div className="features-section security-content">
                <div className="container security-container">
                    <div className="policy-document">

                        <div className="policy-section">
                            <h2>1. Purpose</h2>
                            <p>The purpose of this policy is to establish the framework for establishing, implementing, maintaining, and continually improving the information security management system (ISMS) within Linqra. It ensures the confidentiality, integrity, and availability of customer and company data.</p>
                        </div>

                        <div className="policy-section">
                            <h2>2. Scope</h2>
                            <p>This policy applies to all employees, contractors, and third-party vendors who access Linqra's systems, data, or networks. It covers all cloud infrastructure (AWS), databases (MongoDB), and application services (Spring Boot Microservices, React Frontend).</p>
                        </div>

                        <div className="policy-section">
                            <h2>3. Data Classification</h2>
                            <p>All data managed by Linqra is classified into the following categories:</p>
                            <div className="table-wrapper">
                                <table className="policy-table">
                                    <thead>
                                        <tr>
                                            <th>Level</th>
                                            <th>Classification</th>
                                            <th>Examples</th>
                                            <th>Handling Requirements</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr>
                                            <td><span className="badge-level l4">L4</span></td>
                                            <td><strong>Restricted / PHI</strong></td>
                                            <td>Customer Data, Encryption Keys, Health Records.</td>
                                            <td>Encrypted at Rest & Transit. Strict Need-to-Know. MFA required.</td>
                                        </tr>
                                        <tr>
                                            <td><span className="badge-level l3">L3</span></td>
                                            <td><strong>Confidential</strong></td>
                                            <td>Internal strategic documents, non-public designs.</td>
                                            <td>Access limited to employees. No public sharing.</td>
                                        </tr>
                                        <tr>
                                            <td><span className="badge-level l2">L2</span></td>
                                            <td><strong>Internal</strong></td>
                                            <td>Employee directories, internal announcements.</td>
                                            <td>Available to all employees.</td>
                                        </tr>
                                        <tr>
                                            <td><span className="badge-level l1">L1</span></td>
                                            <td><strong>Public</strong></td>
                                            <td>Marketing website, public documentation.</td>
                                            <td>No restrictions.</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        <div className="policy-section">
                            <h2>4. Encryption Standards</h2>
                            <p>To protect Highly Confidential data:</p>
                            <ul className="policy-list">
                                <li>
                                    <strong>At Rest:</strong>
                                    <ul>
                                        <li><strong>Application Level:</strong> All tenant data MUST be encrypted using <strong>AES-256-GCM</strong> with a unique key per tenant.</li>
                                        <li><strong>Database Level:</strong> All storage volumes (EBS/Atlas) MUST be encrypted.</li>
                                        <li><strong>Backups:</strong> All S3 objects MUST be encrypted and immutable (Object Lock).</li>
                                    </ul>
                                </li>
                                <li>
                                    <strong>In Transit:</strong>
                                    <ul>
                                        <li>All external traffic MUST use <strong>TLS 1.2+</strong> (HTTPS).</li>
                                        <li>Internal microservices communication SHOULD use mTLS or internal VPC routing.</li>
                                    </ul>
                                </li>
                            </ul>
                        </div>

                        <div className="policy-section">
                            <h2>5. Access Control</h2>
                            <p>Access to systems follows the <strong>Principle of Least Privilege</strong>.</p>
                            <ul className="policy-list">
                                <li><strong>Authentication:</strong> All users must authenticate via <strong>Single Sign-On (SSO/OIDC)</strong>.</li>
                                <li><strong>MFA:</strong> Multi-Factor Authentication is MANDATORY for all administrative access.</li>
                                <li><strong>Review:</strong> Access rights are reviewed quarterly. Access is immediately revoked upon termination.</li>
                            </ul>
                        </div>

                        <div className="policy-section">
                            <h2>6. Vulnerability Management</h2>
                            <ul className="policy-list">
                                <li><strong>Scanning:</strong> Automated dependency scanning (GitHub Dependency Review) runs on every Pull Request.</li>
                                <li><strong>Patching:</strong> Critical vulnerabilities (CVSS &gt; 9.0) must be remediated within <strong>7 days</strong>. High (CVSS &gt; 7.0) within <strong>30 days</strong>.</li>
                                <li><strong>Penetration Testing:</strong> Third-party penetration tests are conducted annually.</li>
                            </ul>
                        </div>

                        <div className="policy-section">
                            <h2>7. Acceptable Use</h2>
                            <ul className="policy-list">
                                <li>Employees must lock their screens when leaving their desk.</li>
                                <li>No sharing of passwords or API keys.</li>
                                <li>No storage of customer data on unencrypted personal devices.</li>
                            </ul>
                        </div>

                        <div className="policy-section">
                            <h2>8. Enforcement</h2>
                            <p>Violations of this policy may result in disciplinary action, up to and including termination of employment and legal action.</p>
                        </div>

                        <div className="policy-signature">
                            <p className="signature-title">Approved By:</p>
                            <p className="signature-name">Mehmet Sen (CTO)</p>
                        </div>

                    </div>
                </div>
            </div>

            <Footer />
        </div>
    );
};

export default SecurityPolicy;
