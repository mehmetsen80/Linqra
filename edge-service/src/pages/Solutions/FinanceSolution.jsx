import { Link } from 'react-router-dom';
import Footer from '../../components/common/Footer';
import '../Home/styles.css'; // Reuse home styles

const FinanceSolution = () => {
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
            <div className="hero-section enterprise-hero">
                <div className="hero-content">
                    <h1 className="enterprise-title">AI for Financial Services</h1>
                    <p className="hero-text">
                        Ensure <strong>GLBA & SOX Compliance</strong> properly with Immutable Audit Trails.<br />
                        Automate fraud detection and portfolio analysis without data leaving your VPC.
                    </p>
                </div>
            </div>

            {/* FEATURES */}
            <div className="features-section" style={{ background: '#fff' }}>
                <div className="container">
                    <div className="dev-grid-layout">
                        <div className="dev-column">
                            <h3><i className="fas fa-university" style={{ color: '#2563eb' }}></i> GLBA Ready</h3>
                            <p>Protect customer financial data with AES-256 encryption. Our architecture ensures that PII is redacted before it ever touches an AI model.</p>
                        </div>
                        <div className="dev-column">
                            <h3><i className="fas fa-file-invoice-dollar" style={{ color: '#2563eb' }}></i> SOX Audit Trails</h3>
                            <p>Every decision made by an AI agent is logged to an immutable text ledger (S3 Object Lock), providing a legally defensible audit trail.</p>
                        </div>
                    </div>
                </div>
            </div>

            {/* FOOTER */}
            <Footer />
        </div>
    );
};

export default FinanceSolution;
