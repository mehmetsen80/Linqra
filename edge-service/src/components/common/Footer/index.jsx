import React from 'react';
import { Link } from 'react-router-dom';

const Footer = () => {
    return (
        <footer className="footer">
            <div className="container">
                <div className="footer-content">
                    <div className="footer-brand">
                        <h4>Linqra</h4>
                        <p className="copyright">&copy; 2025 Linqra Inc. All rights reserved.</p>
                        <Link to="/" className="footer-link home-footer-link">Home</Link>
                    </div>
                    <div className="footer-links-col">
                        <h5>Solutions</h5>
                        <Link to="/solutions/finance" className="footer-link">Finance</Link>
                        <Link to="/solutions/legal" className="footer-link">Legal</Link>
                        <Link to="/solutions/healthcare" className="footer-link">Healthcare</Link>
                    </div>
                    <div className="footer-links-col">
                        <h5>Company</h5>
                        <Link to="/about" className="footer-link">About Us</Link>
                        <Link to="/careers" className="footer-link">Careers</Link>
                        <Link to="/contact" className="footer-link">Contact Sales</Link>
                    </div>
                    <div className="footer-links-col">
                        <h5>Legal</h5>
                        <Link to="/privacy-policy" className="footer-link">Privacy Policy</Link>
                        <Link to="/terms-of-service" className="footer-link">Terms of Service</Link>
                        <Link to="/security-policy" className="footer-link">Security Policy</Link>
                    </div>
                </div>
            </div>
        </footer>
    );
};

export default Footer;
