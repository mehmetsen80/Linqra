import React from 'react';
import { Card } from 'react-bootstrap';
import { HiClock, HiCalendar, HiKey, HiViewGrid, HiDocumentText, HiUserGroup, HiOfficeBuilding, HiShieldCheck, HiHashtag, HiSparkles, HiCollection, HiDatabase, HiChatAlt, HiLightBulb } from 'react-icons/hi';
import { useAuth } from '../../../contexts/AuthContext';
import { useTeam } from '../../../contexts/TeamContext';
import { useEnvironment } from '../../../contexts/EnvironmentContext';
import { Link } from 'react-router-dom';
import { isSuperAdmin, hasAdminAccess } from '../../../utils/roleUtils';
import './styles.css';

const UserSummary = () => {
  const { user } = useAuth();
  const { currentTeam } = useTeam();
  const { profile, isProd, isLoading } = useEnvironment();

  const canAccessAdminFeatures = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
  const lastLogin = localStorage.getItem('lastLoginTime') || new Date().toISOString();
  const formattedLastLogin = new Date(lastLogin).toLocaleString();

  const renderEnvironmentBadge = () => {
    if (isLoading) {
      return <div className="environment-badge loading">Loading...</div>;
    }
    return (
      <div className="environment-badge">
        {(profile || 'UNKNOWN').toUpperCase()}
      </div>
    );
  };

  if (isLoading) {
    return <Card className="user-summary mb-4"><Card.Body>Loading...</Card.Body></Card>;
  }

  return (
    <Card className="user-summary mb-4 p-2">
      <Card.Body>
        <div className="d-flex justify-content-between align-items-center">
          <div className="user-info">
            <h4>Welcome back, {user?.username || 'User'}</h4>
            <div className="text-muted d-flex align-items-center gap-2">
              <HiClock /> Last login: {formattedLastLogin}
            </div>
            <div className="text-muted d-flex align-items-center gap-2 mt-1">
              <HiCalendar /> Current team: {currentTeam?.name || 'No team selected'}
            </div>
            {currentTeam?.id && (
              <div className="text-muted d-flex align-items-center gap-2 mt-1">
                <HiHashtag /> Team ID: {currentTeam.id}
              </div>
            )}
            <div className="text-muted d-flex align-items-center gap-2 mt-1">
              <HiShieldCheck /> Role: {currentTeam?.roles?.[0] || 'USER'}
            </div>
          </div>
          
          <div className="quick-actions">
            <div className="quick-actions-row">
              {/* First Row */}
              <div className="quick-actions-group">
                {!isProd() && (
                  <div className="token-section">
                    {renderEnvironmentBadge()}
                    <Link to="/view-token" className="quick-action-button">
                      <HiKey />
                      <span>View Token</span>
                    </Link>
                  </div>
                )}
                <Link to="/api-routes" className="quick-action-button">
                  <HiViewGrid />
                  <span>Apps</span>
                </Link>
                <Link to="/service-status" className="quick-action-button">
                  <HiDocumentText />
                  <span>Service Status</span>
                </Link>
                <Link to="/ai-assistants" className="quick-action-button">
                  <HiChatAlt />
                  <span>AI Assistants</span>
                </Link>
                <Link to="/knowledge-hub" className="quick-action-button">
                  <HiLightBulb />
                  <span>Knowledge Hub</span>
                </Link>
              </div>
            </div>
            {/* Second Row - LLM Models, RAG, and Admin Features */}
            <div className="quick-actions-row">
              <div className="quick-actions-group">
                <Link to="/llm-models" className="quick-action-button">
                  <HiSparkles />
                  <span>LLM Models</span>
                </Link>
                <Link to="/llm-usage" className="quick-action-button">
                  <HiCollection />
                  <span>LLM Usage</span>
                </Link>
                <Link to="/rag" className="quick-action-button">
                  <HiDatabase />
                  <span>RAG</span>
                </Link>
                {canAccessAdminFeatures && (
                  <>
                    <Link to="/teams" className="quick-action-button">
                      <HiUserGroup />
                      <span>Teams</span>
                    </Link>
                    <Link to="/organizations" className="quick-action-button">
                      <HiOfficeBuilding />
                      <span>Organizations</span>
                    </Link>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </Card.Body>
    </Card>
  );
};

export default UserSummary; 