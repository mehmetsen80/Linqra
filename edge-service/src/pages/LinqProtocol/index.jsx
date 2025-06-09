import React from 'react';
import { Link, Outlet, NavLink } from 'react-router-dom';
import './styles.css';

function LinqProtocol() {
    return (
        <div className="home-container">
            <nav className="top-nav">
                <div className="nav-links">
                    <Link to="/">Home</Link>
                    <a href="https://docs.linqra.com" target="_blank" rel="noopener noreferrer">Docs</a>
                    <a href="https://github.com/mehmetsen80/Linqra" target="_blank" rel="noopener noreferrer">GitHub</a>
                    <div className="auth-links">
                        <Link to="/login" className="auth-link">Login</Link>
                    </div>
                </div>
            </nav>

            <div className="hero-section">
                <div className="hero-content">
                    <h1>Linq Protocol</h1>
                    <div className="hero-separator"></div>
                    <p className="hero-text">
                        A unified protocol for service communication that simplifies API interactions through a standardized interface.
                    </p>
                </div>
            </div>

            <div className="protocol-content">
                <div className="protocol-layout">
                    <nav className="protocol-navigation">
                        <div className="nav-section">
                            <h3 className="nav-section-title">
                                <span className="section-icon">📚</span>
                                Basics
                            </h3>
                            <nav>
                                <ul className="protocol-nav-list">
                                    <li>
                                        <NavLink 
                                            to="/linq-protocol/basics"
                                            className={({ isActive }) => isActive ? 'active' : ''}
                                            end
                                        >
                                            Overview
                                        </NavLink>
                                    </li>
                                    <li>
                                        <NavLink 
                                            to="/linq-protocol/basics/request-structure"
                                            className={({ isActive }) => isActive ? 'active' : ''}
                                        >
                                            Request Structure
                                        </NavLink>
                                    </li>
                                    <li>
                                        <NavLink 
                                            to="/linq-protocol/basics/response-format"
                                            className={({ isActive }) => isActive ? 'active' : ''}
                                        >
                                            Response Format
                                        </NavLink>
                                    </li>
                                    <li>
                                        <NavLink 
                                            to="/linq-protocol/basics/error-handling"
                                            className={({ isActive }) => isActive ? 'active' : ''}
                                        >
                                            Error Handling
                                        </NavLink>
                                    </li>
                                </ul>
                            </nav>
                        </div>
                    </nav>

                    <div className="protocol-documentation">
                        <Outlet />
                    </div>
                </div>
            </div>
        </div>
    );
}

export default LinqProtocol;