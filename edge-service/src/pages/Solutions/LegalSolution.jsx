import { Link } from 'react-router-dom';
import Footer from '../../components/common/Footer';
import '../Home/styles.css';

const LegalSolution = () => {
    return (
        <div className="home-container">
            <nav className="solution-nav">
                <Link to="/" className="navbar-logo">
                    <img src="/images/noBgWhiteOnlyLogo.png" alt="Linqra" />
                </Link>
                <div className="navbar-links">
                    <Link to="/login" className="nav-link">Login</Link>
                    <Link to="/contact" className="cta-button primary small">Get Started</Link>
                </div>
            </nav>

            <div className="hero-section enterprise-hero" style={{ background: 'linear-gradient(135deg, #7f1d1d 0%, #991b1b 100%)' }}>
                <div className="hero-content">
                    <h1 className="enterprise-title">AI for Law Firms</h1>
                    <p className="hero-text">
                        Maintain <strong>Attorney-Client Privilege</strong> with dedicated cryptographic isolation.<br />
                        Accelerate discovery and contract review with Zero-Retention AI models.
                    </p>
                </div>
            </div>

            <div className="features-section" style={{ background: '#fff' }}>
                <div className="container">
                    <div className="dev-grid-layout">
                        <div className="dev-column">
                            <h3><i className="fas fa-gavel" style={{ color: '#dc2626' }}></i> Privilege Check</h3>
                            <p>Our "Blind AI" architecture ensures that even Linqra engineers cannot access your documents. Using Per-Tenant Keys, you hold the encryption keys.</p>
                        </div>
                        <div className="dev-column">
                            <h3><i className="fas fa-search" style={{ color: '#dc2626' }}></i> eDiscovery</h3>
                            <p>Process millions of documents for relevance without training public models. Your data stays transient and is never used for model training.</p>
                        </div>
                    </div>
                </div>
            </div>

            <Footer />
        </div>
    );
};

export default LegalSolution;
