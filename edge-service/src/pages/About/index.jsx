import React from 'react';
import { Link } from 'react-router-dom';
import Footer from '../../components/common/Footer';
import EcosystemVisualizer from './EcosystemVisualizer';
import '../Home/styles.css';
import './styles.css';

const About = () => {
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
            <div className="hero-section enterprise-hero about-hero-bg">
                <div className="hero-content">
                    <h1 className="enterprise-title">Orchestrating the AI Ecosystem</h1>
                    <p className="hero-text">
                        Unify your AI models, legacy systems, and compliance controls under <strong>one intelligent gateway</strong>.
                        Stop building plumbing. Start orchestrating intelligence.
                    </p>
                </div>
            </div>

            {/* CONTENT */}
            <div className="features-section about-content">
                <div className="container about-container">

                    <div className="about-grid">
                        <div className="about-card">
                            <div className="about-card-header">
                                <div className="about-icon-wrapper"><i className="fa-solid fa-network-wired"></i></div>
                                <h2 className="about-title">The "Sprawl" Problem</h2>
                            </div>
                            <p className="about-text">
                                The modern enterprise is drowning in fragmentation. <strong>Model Sprawl</strong> means managing dozens of API keys, schemas, and
                                security protocols across OpenAI, Anthropic, and Google.
                            </p>
                        </div>

                        <div className="about-card">
                            <div className="about-card-header">
                                <div className="about-icon-wrapper"><i className="fa-solid fa-server"></i></div>
                                <h2 className="about-title">Bridge the Legacy Gap</h2>
                            </div>
                            <p className="about-text">
                                We built Linqra to be the universal connective tissue. We don't just secure your AI; we <strong>integrate your legacy apps</strong> into
                                the same secure orchestration layer, giving them a new lease on life.
                            </p>
                        </div>

                        <div className="about-card">
                            <div className="about-card-header">
                                <div className="about-icon-wrapper"><i className="fa-solid fa-shield-halved"></i></div>
                                <h2 className="about-title">Security Standard</h2>
                            </div>
                            <p className="about-text">
                                Security shouldn't be a bottleneck uniquely for "regulated" work. Linqra provides <em>all</em> your integrations—legacy or AI—with
                                out-of-the-box <strong>Per-Tenant Encryption</strong> and <strong>Immutable Audit Ledgers</strong>.
                            </p>
                        </div>
                    </div>

                    <div className="visualizer-section">
                        <EcosystemVisualizer />
                    </div>


                </div>
            </div>

            <Footer />
        </div>
    );
};

export default About;
