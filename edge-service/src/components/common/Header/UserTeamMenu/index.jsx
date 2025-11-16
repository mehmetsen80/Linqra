import React, { useState } from 'react';
import { Dropdown, Badge } from 'react-bootstrap';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../../../contexts/AuthContext';
import { useTeam } from '../../../../contexts/TeamContext';
import {
  HiOutlineTemplate,
  HiOutlineChartBar,
  HiOutlineStatusOnline,
  HiOutlineLogout,
  HiOutlineSparkles,
  HiOutlineDatabase,
  HiOutlineLightningBolt,
  HiOutlineLightBulb,
  HiOutlineCollection,
  HiOutlineDesktopComputer,
  HiOutlineChatAlt
} from 'react-icons/hi';
import Tippy from '@tippyjs/react';
import 'tippy.js/dist/tippy.css';
import 'tippy.js/themes/light.css';
import './styles.css';
import ConfirmationModal from '../../ConfirmationModal';
import { RoleBadge, AuthBadge } from '../../../../utils/roleUtils';

const UserTeamMenu = () => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { currentTeam, userTeams, switchTeam } = useTeam();
  const [isOpen, setIsOpen] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  const handleTeamSwitch = async (teamId) => {
    await switchTeam(teamId);
    setIsOpen(false);
  };

  const handleLogoutClick = () => {
    setShowLogoutConfirm(true);
  };

  const handleLogoutConfirm = () => {
    setShowLogoutConfirm(false);
    logout();
  };

  const renderRoutesList = (routes) => {
    if (!routes || routes.length === 0) return null;
    
    return (
      <div className="routes-list">
        {routes.map((route, index) => (
          <div key={index} className="route-item">
            <span className="route-name">{route.name}</span>
            <div className="route-details">
              <span className="route-path">{route.path}</span>
              <Badge bg="light" text="dark" className="route-method">
                {route.method}
              </Badge>
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <>
      <Dropdown show={isOpen} onToggle={(isOpen) => setIsOpen(isOpen)} align="end">
        <Dropdown.Toggle variant="link" id="user-team-dropdown">
          {currentTeam ? (
            <>
              <div className="current-team-info">
                <span 
                  className="team-name" 
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate(`/teams/${currentTeam.id}`);
                  }}
                  style={{ cursor: 'pointer' }}
                  onMouseEnter={(e) => e.target.style.textDecoration = 'underline'}
                  onMouseLeave={(e) => e.target.style.textDecoration = 'none'}
                >
                  {currentTeam.name}
                </span>
                <span className="current-org-name">{currentTeam.organization?.name}</span>
              </div>
              {currentTeam.roles?.map((role, index) => (
                <Badge key={index} bg="light" text="dark" className="role-badge">
                  {role}
                </Badge>
              ))}
              <Tippy
                content={renderRoutesList(currentTeam.routes)}
                interactive={true}
                arrow={true}
                duration={200}
                placement="bottom"
              >
                <span className="routes-wrapper">
                  <Badge bg="info" className="routes-badge">
                    <HiOutlineTemplate size={14} /> {currentTeam.routes?.length || 0}
                  </Badge>
                </span>
              </Tippy>
              <span className="separator">|</span>
              <div className="user-info">
                <span className="user-name">{user?.username}</span>
                <span className="user-email">{user?.email}</span>
              </div>
            </>
          ) : (
            <span className="user-name">{user?.name || user?.email}</span>
          )}
        </Dropdown.Toggle>

        <Dropdown.Menu>
          {userTeams.length > 0 && (
            <>
              <Dropdown.Header>Your Teams</Dropdown.Header>
              {userTeams.map((team) => (
                <Dropdown.Item
                  key={team.id}
                  onClick={() => handleTeamSwitch(team.id)}
                  active={currentTeam?.id === team.id}
                >
                  <div className="team-menu-item">
                    <div className="team-menu-info">
                      <div className="team-menu-header">
                        <span className="team-menu-item-name">{team.name}</span>
                        <span className="team-menu-item-org">{team.organization?.name || 'No Organization'}</span>
                      </div>
                      <div className="team-menu-badges">
                        <Tippy
                          content={renderRoutesList(team.routes)}
                          interactive={true}
                          arrow={true}
                          duration={200}
                          placement="right"
                        >
                          <Badge bg="info" className="routes-badge">
                            <HiOutlineTemplate size={14} /> {team.routes?.length || 0}
                          </Badge>
                        </Tippy>
                        {team.roles?.map((role, index) => (
                          <Badge key={index} bg="secondary" className="role-badge">
                            {role}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  </div>
                </Dropdown.Item>
              ))}
              <Dropdown.Divider />
            </>
          )}

          {/* User Section */}
          <Dropdown.Header>User Information</Dropdown.Header>
          <div className="user-info-section">
            <div className="auth-type">
              <AuthBadge authType={user?.authType || 'LOCAL'} />
            </div>
            <div className="user-name">
              {user?.username}
              <RoleBadge user={user} />
            </div>
            <div className="user-email">{user?.email}</div>
          </div>
          <Dropdown.Divider />
          <Dropdown.Header>Quick Links</Dropdown.Header>
          <Dropdown.Item href="/metrics">
            <HiOutlineChartBar size={16} style={{ marginRight: '8px' }} />
            API Metrics
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/service-status">
            <HiOutlineStatusOnline size={16} style={{ marginRight: '8px' }} />
            Service Status
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/api-routes">
            <HiOutlineTemplate size={16} style={{ marginRight: '8px' }} />
            Apps
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/agents">
            <HiOutlineDesktopComputer size={16} style={{ marginRight: '8px' }} />
            Agents
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/ai-assistants">
            <HiOutlineChatAlt size={16} style={{ marginRight: '8px' }} />
            AI Assistants
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/llm-models">
            <HiOutlineSparkles size={16} style={{ marginRight: '8px' }} />
            LLM Models
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/llm-usage">
            <HiOutlineCollection size={16} style={{ marginRight: '8px' }} />
            LLM Usage
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/execution-monitoring">
            <HiOutlineLightningBolt size={16} style={{ marginRight: '8px' }} />
            Executions
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/knowledge-hub">
            <HiOutlineLightBulb size={16} style={{ marginRight: '8px' }} />
            Knowledge Hub
          </Dropdown.Item>
          <Dropdown.Item as={Link} to="/rag">
            <HiOutlineDatabase size={16} style={{ marginRight: '8px' }} />
            RAG Collections
          </Dropdown.Item>
          <Dropdown.Divider />
          <Dropdown.Item onClick={handleLogoutClick}>
            <HiOutlineLogout size={16} style={{ marginRight: '8px' }} />
            Sign Out
          </Dropdown.Item>
        </Dropdown.Menu>
      </Dropdown>

      <ConfirmationModal
        show={showLogoutConfirm}
        onHide={() => setShowLogoutConfirm(false)}
        onConfirm={handleLogoutConfirm}
        title="Sign Out Confirmation"
        message="Are you sure you want to sign out?"
      />
    </>
  );
};

export default UserTeamMenu; 