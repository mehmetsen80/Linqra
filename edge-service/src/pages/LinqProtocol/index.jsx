import React from 'react';
import { Link, Outlet, NavLink } from 'react-router-dom';
import { FaBook, FaProjectDiagram } from 'react-icons/fa';
import './styles.css';
import { useAuth } from '../../contexts/AuthContext';

function LinqProtocol() {
    const { isAuthenticated } = useAuth();
    return (
        <div className="home-container">
            

            <div className="protocol-content">
                <div className="protocol-layout">
                    <nav className="protocol-navigation">
                        <div className="nav-section">
                            <h3 className="nav-section-title">
                                <span className="section-icon"><FaBook /></span>
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

                        <div className="nav-section">
                            <h3 className="nav-section-title">
                                <span className="section-icon"><FaProjectDiagram /></span>
                                Workflow
                            </h3>
                            <nav>
                                <ul className="protocol-nav-list">
                                    <li>
                                        <NavLink 
                                            to="/linq-protocol/workflow"
                                            className={({ isActive }) => isActive ? 'active' : ''}
                                            end
                                        >
                                            Overview
                                        </NavLink>
                                    </li>
                                    <li>
                                        <NavLink 
                                            to="/linq-protocol/workflow/create"
                                            className={({ isActive }) => isActive ? 'active' : ''}
                                        >
                                            Creating Workflows
                                        </NavLink>
                                    </li>
                                    <li>
                                        <NavLink 
                                            to="/linq-protocol/workflow/execute"
                                            className={({ isActive }) => isActive ? 'active' : ''}
                                        >
                                            Executing Workflows
                                        </NavLink>
                                    </li>
                                    <li>
                                        <NavLink 
                                            to="/linq-protocol/workflow/examples"
                                            className={({ isActive }) => isActive ? 'active' : ''}
                                        >
                                            Examples
                                        </NavLink>
                                    </li>
                                </ul>
                            </nav>
                        </div>
                    </nav>

                    <div className="protocol-documentation">

                    <nav className="top-nav-protocol">
                        <div className="nav-links-protocol">
                            <Link to="/">Home</Link>
                            <a href="https://docs.linqra.com" target="_blank" rel="noopener noreferrer">Docs</a>
                            <a href="https://github.com/mehmetsen80/Linqra" target="_blank" rel="noopener noreferrer">GitHub</a>
                            {/* <div className="auth-links">
                                <Link to="/login" className="auth-link">Login</Link>
                            </div> */}


                            {isAuthenticated ? (
                                <Link to="/dashboard" className="auth-link">Dashboard</Link>
                            ) : (
                                <div className="auth-links">
                                <Link to="/login" className="auth-link">Login</Link>
                                {/* TODO: Uncomment this when we decide for the registration */}
                                {/* <Link to="/register" className="auth-link">Register</Link> */}
                                </div>
                            )}


                        </div>
                    </nav>

            <div className="hero-section-protocol">
                <div className="hero-content-protocol">
                    <h1>Linq Protocol</h1>
                    <div className="hero-separator"></div>
                    <p className="hero-text-protocol">
                        A unified protocol for service communication that simplifies API interactions through a standardized interface.
                    </p>
                </div>
            </div>

                        
                        <Outlet />
                    </div>
                </div>
            </div>
        </div>
    );
}

export default LinqProtocol;