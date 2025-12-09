import React from 'react';
import { Link } from 'react-router-dom';
import Footer from '../../components/common/Footer';
import Button from '../../components/common/Button';
import '../Home/styles.css';
import './styles.css';

const Careers = () => {
    // Dummy job data
    const jobs = [
        { title: "Senior Security Engineer", location: "New York, NY (Hybrid)", team: "Engineering" },
        { title: "Backend Developer (Java/Spring)", location: "Remote", team: "Engineering" },
        { title: "Compliance Product Manager", location: "San Francisco, CA", team: "Product" },
    ];

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
            <div className="hero-section enterprise-hero careers-hero-bg">
                <div className="hero-content">
                    <h1 className="enterprise-title">Join the Team</h1>
                    <p className="hero-text">
                        Build the future of Secure AI. We're looking for obsessive engineers who sweat the details of cryptography and distributed systems.
                    </p>
                </div>
            </div>

            {/* CONTENT */}
            <div className="features-section careers-content">
                <div className="container careers-container">
                    <div className="careers-header">
                        {/* 
                <h2>Open Positions</h2>
                <p>Come work with us on the hard problems.</p>
                */}
                    </div>

                    <div className="jobs-list">
                        {/* 
                {jobs.map((job, index) => (
                    <div key={index} className="job-card">
                        <div className="job-card-info">
                            <h3 className="job-title">{job.title}</h3>
                            <p className="job-meta">{job.team} • {job.location}</p>
                        </div>
                        <Button variant="secondary" className="small">Apply Now</Button>
                    </div>
                ))}
                */}
                        <div className="no-positions-card">
                            <h3 className="no-positions-title">No Open Positions</h3>
                            <p className="no-positions-text">
                                We don't have any open roles right now, but we're always looking for exceptional talent.<br />
                                Check back soon or follow us on LinkedIn.
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            <Footer />
        </div>
    );
};

export default Careers;
