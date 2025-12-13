import { Link } from 'react-router-dom';
import Footer from '../../components/common/Footer';
import '../Home/styles.css';

const HealthcareSolution = () => {
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

            <div className="hero-section enterprise-hero" style={{ background: 'linear-gradient(135deg, #064e3b 0%, #065f46 100%)' }}>
                <div className="hero-content">
                    <h1 className="enterprise-title">AI for Healthcare</h1>
                    <p className="hero-text">
                        <strong>HIPAA-Compliant</strong> AI Agents that understand Clinical Workflows.<br />
                        Analyze patient data with automatic <strong>PHI Redaction</strong> and audit logging.
                    </p>
                </div>
            </div>

            <div className="features-section" style={{ background: '#fff' }}>
                <div className="container">
                    <div className="dev-grid-layout">
                        <div className="dev-column">
                            <h3><i className="fas fa-heartbeat" style={{ color: '#059669' }}></i> PHI Redaction</h3>
                            <p>Our DLP Service automatically identifies and redacts 18 HIPAA identifiers (Names, SSNs, MRNs) before data enters the LLM context window.</p>
                        </div>
                        <div className="dev-column">
                            <h3><i className="fas fa-file-signature" style={{ color: '#059669' }}></i> BAA Ready</h3>
                            <p>We sign Business Associate Agreements (BAA) to ensure we share liability and commitment to protecting patient data capabilities.</p>
                        </div>
                    </div>
                </div>
            </div>

            <Footer />
        </div>
    );
};

export default HealthcareSolution;
