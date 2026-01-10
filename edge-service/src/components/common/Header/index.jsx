import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin } from '../../../utils/roleUtils';
import './styles.css';
import { Navbar, Container, Nav, Dropdown } from 'react-bootstrap';
import UserTeamMenu from './UserTeamMenu';
import { useTeam } from '../../../contexts/TeamContext';
import TokenExpiryDisplay from './TokenExpiryDisplay';
import { NavLink } from 'react-router-dom';
import { HiOutlineBell } from 'react-icons/hi';
import { securityIncidentService } from '../../../services/securityIncidentService';

const SecurityBell = () => {
  const [hasNewIncidents, setHasNewIncidents] = useState(false);
  const navigate = useNavigate();

  React.useEffect(() => {
    const checkIncidents = async () => {
      try {
        const incidents = await securityIncidentService.getAllIncidents('OPEN');
        const critical = incidents.some(i => i.severity === 'HIGH' || i.severity === 'CRITICAL');
        setHasNewIncidents(critical);
      } catch (e) {
        console.error("Failed to check security incidents", e);
      }
    };

    checkIncidents();
    const interval = setInterval(checkIncidents, 30000); // Poll every 30s
    return () => clearInterval(interval);
  }, []);

  return (
    <div
      className="security-bell-wrapper"
      onClick={() => navigate('/security/incidents')}
      style={{ cursor: 'pointer', position: 'relative', color: 'rgba(255, 255, 255, 0.85)', display: 'flex', alignItems: 'center' }}
      title="Security Incidents"
    >
      <HiOutlineBell size={20} />
      {hasNewIncidents && (
        <span style={{
          position: 'absolute',
          top: -2,
          right: -2,
          width: '8px',
          height: '8px',
          backgroundColor: '#ef4444',
          borderRadius: '50%',
          border: '1px solid white'
        }} />
      )}
    </div>
  );
};

const Header = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentTeam } = useTeam();
  const [showLLMDropdown, setShowLLMDropdown] = useState(false);

  // Debug logging
  // console.log('Header Render - User:', user?.username, 'Roles:', user?.roles);
  // console.log('Is Super Admin:', isSuperAdmin(user));
  // console.log('Location:', location.pathname);

  const handleNavClick = (path, e) => {
    if (e) e.preventDefault();
    navigate(path);
  };

  const isViewTokenPage = location.pathname === '/view-token';

  return (
    <Navbar expand="lg" className="app-header">
      <Container fluid>
        <Navbar.Brand as={Link} to="/">
          <img
            src="/images/color_logo_no_background.png"
            alt="Linqra Logo"
            className="header-logo"
          />
        </Navbar.Brand>
        <Navbar.Toggle aria-controls="basic-navbar-nav" />
        <Navbar.Collapse id="basic-navbar-nav">
          <Nav className="me-auto">
            {/* <Nav.Link as={NavLink} to="/">Home</Nav.Link>
            <span className="nav-separator"> | </span> */}
            <Nav.Link as={NavLink} to="/dashboard">Dashboard</Nav.Link>
            <span className="nav-separator"> | </span>
            <Nav.Link as={NavLink} to="/api-routes">Apps</Nav.Link>
            {/* Workflows navigation removed - workflow functionality is now integrated into Agents
                Users can create and execute workflows directly within the Agent interface,
                eliminating the need for a separate workflows section */}
            {/* <span className="nav-separator"> | </span> */}
            {/* <Nav.Link as={NavLink} to="/workflows">Workflows</Nav.Link> */}
            <span className="nav-separator"> | </span>
            <Nav.Link as={NavLink} to="/agents">Agents</Nav.Link>
            <span className="nav-separator"> | </span>
            <Nav.Link as={NavLink} to="/ai-assistants">AI Assistants</Nav.Link>
            <span className="nav-separator"> | </span>
            {/* <Dropdown 
              show={showLLMDropdown}
              onMouseEnter={() => setShowLLMDropdown(true)}
              onMouseLeave={() => setShowLLMDropdown(false)}
            >
              <Dropdown.Toggle variant="link" className="nav-link-dropdown">
                LLMs
              </Dropdown.Toggle>
              <Dropdown.Menu>
                <Dropdown.Item as={NavLink} to="/llm-usage">LLM Usage</Dropdown.Item>
                <Dropdown.Item as={NavLink} to="/llm-models">LLM Models</Dropdown.Item>
              </Dropdown.Menu>
            </Dropdown>
            <span className="nav-separator"> | </span> */}
            <Nav.Link as={NavLink} to="/execution-monitoring">Executions</Nav.Link>
            <span className="nav-separator"> | </span>
            <Nav.Link as={NavLink} to="/knowledge-hub">Knowledge Hub</Nav.Link>
            <span className="nav-separator"> | </span>
            <Nav.Link as={NavLink} to="/audits">Audits</Nav.Link>
            {/* <span className="nav-separator"> | </span>
            <Nav.Link as={NavLink} to="/rag">RAG</Nav.Link> */}
          </Nav>
          {user ? (
            <div className="d-flex align-items-center gap-3">
              {(user?.roles?.includes('ADMIN') || isSuperAdmin(user)) && (
                <SecurityBell />
              )}
              {isViewTokenPage && <TokenExpiryDisplay />}
              <UserTeamMenu />
            </div>
          ) : (
            <div className="d-flex">
              <Nav.Link href="#" onClick={(e) => handleNavClick('/login', e)}>Login</Nav.Link>
              <Nav.Link href="#" onClick={(e) => handleNavClick('/register', e)}>Register</Nav.Link>
              <span className="nav-separator"> | </span>
              <Nav.Link as={NavLink} to="/protocol">Linq Protocol</Nav.Link>
            </div>
          )}
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
};

export default Header;