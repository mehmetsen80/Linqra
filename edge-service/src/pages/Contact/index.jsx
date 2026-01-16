import React from 'react';
import { Link } from 'react-router-dom';
import Footer from '../../components/common/Footer';
import '../Home/styles.css';
import './styles.css';

const Contact = () => {
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
            <div className="hero-section enterprise-hero contact-hero-bg">
                <div className="hero-content">
                    <h1 className="enterprise-title">Talk to Sales</h1>
                    <p className="hero-text">
                        Ready to deploy Secure AI? Let's discuss your compliance requirements and how Linqra can fit into your architecture.
                    </p>
                </div>
            </div>

            {/* CONTENT */}
            <div className="features-section contact-content">
                <div className="contact-container">
                    <div className="contact-info-card">
                        <p className="contact-label">Please email us at:</p>
                        <a href="mailto:msen@linqra.com" className="contact-email">msen@linqra.com</a>
                    </div>
                </div>
            </div>

            <Footer />
        </div>
    );
};

export default Contact;
