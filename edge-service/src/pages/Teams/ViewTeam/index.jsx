import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Badge, 
  Card, 
  Row, 
  Col, 
  Button as BootstrapButton,
  Spinner,
  OverlayTrigger,
  Tooltip
} from 'react-bootstrap';
import Button from '../../../components/common/Button';
import { 
  HiArrowLeft,
  HiPencil, 
  HiUsers, 
  HiTemplate, 
  HiDocumentText,
  HiOfficeBuilding,
  HiClock,
  HiLockClosed,
  HiKey,
  HiSparkles
} from 'react-icons/hi';
import { SiOpenai, SiGoogle, SiAnthropic } from 'react-icons/si';
import { FaCloud } from 'react-icons/fa';
import { teamService } from '../../../services/teamService';
import { linqLlmModelService } from '../../../services/linqLlmModelService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import TeamMembersModal from '../../../components/teams/TeamMembersModal';
import TeamRoutesModal from '../../../components/teams/TeamRoutesModal';
import TeamEditModal from '../../../components/teams/TeamEditModal';
import TeamApiKeysModal from '../../../components/teams/TeamApiKeysModal';
import OpenAIModal from '../../../components/teams/OpenAIModal';
import GeminiModal from '../../../components/teams/GeminiModal';
import CohereModal from '../../../components/teams/CohereModal';
import ClaudeModal from '../../../components/teams/ClaudeModal';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import './styles.css';

function ViewTeam() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const [team, setTeam] = useState(null);
  const [llmModels, setLlmModels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [operationLoading, setOperationLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showMembersModal, setShowMembersModal] = useState(false);
  const [showRoutesModal, setShowRoutesModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showApiKeysModal, setShowApiKeysModal] = useState(false);
  const [showOpenAIModal, setShowOpenAIModal] = useState(false);
  const [showGeminiModal, setShowGeminiModal] = useState(false);
  const [showCohereModal, setShowCohereModal] = useState(false);
  const [showClaudeModal, setShowClaudeModal] = useState(false);
  const [confirmModal, setConfirmModal] = useState({
    show: false,
    title: '',
    message: '',
    onConfirm: () => {},
    variant: 'danger'
  });

  useEffect(() => {
    if (teamId) {
      fetchTeam();
      fetchLlmModels();
    }
  }, [teamId]);

  const fetchTeam = async () => {
    try {
      setLoading(true);
      setError(null);
      const { data, error } = await teamService.getTeamById(teamId);
      if (error) throw new Error(error);
      setTeam(data);
      setLoading(false);
    } catch (err) {
      setError('Failed to load team details');
      setLoading(false);
      console.error('Error fetching team:', err);
    }
  };

  const fetchLlmModels = async () => {
    try {
      const data = await linqLlmModelService.getTeamConfiguration(teamId);
      setLlmModels(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Error fetching LLM models:', err);
      setLlmModels([]);
    }
  };

  const formatDate = (dateInput) => {
    if (!dateInput) return 'N/A';
    
    let date;
    
    if (Array.isArray(dateInput)) {
      const [year, month, day, hour, minute, second] = dateInput;
      date = new Date(year, month - 1, day, hour, minute, second);
    } else {
      date = new Date(dateInput);
    }
    
    if (isNaN(date.getTime())) {
      return 'Invalid Date';
    }
    
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const handleAddMember = async (memberData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.addTeamMember(team.id, memberData);
      
      if (error) throw new Error(error);
      
      setTeam(data);
      setShowMembersModal(false);
      showSuccessToast(`Member added to team "${team.name}" successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to add member to team');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleRemoveMember = async (userId) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.removeTeamMember(team.id, userId);
      if (error) throw new Error(error);
      
      setTeam(data);
      showSuccessToast('Team member removed successfully');
    } catch (err) {
      showErrorToast(err.message || 'Failed to remove team member');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleEditTeam = async (teamData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.updateTeam(team.id, {
        name: teamData.name,
        description: teamData.description,
        organizationId: teamData.organizationId
      });
      
      if (error) throw new Error(error);
      
      setTeam(data);
      setShowEditModal(false);
      showSuccessToast(`Team "${data.name}" updated successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to update team');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleAddRoute = async (routeData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.addTeamRoute(
        team.id,
        routeData.routeId,
        routeData.permissions
      );
      
      if (error) throw new Error(error);
      
      setTeam(data);
      setShowRoutesModal(false);
      showSuccessToast(`Route added to team "${team.name}" successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to add route to team');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleRemoveRoute = async (routeId) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.removeTeamRoute(team.id, routeId);
      if (error) throw new Error(error);
      
      setTeam(data);
      showSuccessToast('Route removed successfully');
    } catch (err) {
      showErrorToast(err.message || 'Failed to remove route');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleCreateApiKey = async (apiKeyData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.createApiKey(apiKeyData);
      if (error) throw new Error(error);
      
      if (data && data.key) {
        showSuccessToast(
          <div>
            API key created successfully
            <br />
            <small className="text-monospace">Key: {data.key}</small>
          </div>,
          { autoClose: false }
        );
      }

      setShowApiKeysModal(false);
    } catch (err) {
      showErrorToast(err.message || 'Failed to create API key');
    } finally {
      setOperationLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="text-center py-5">
        <Spinner animation="border" role="status">
          <span className="visually-hidden">Loading...</span>
        </Spinner>
      </div>
    );
  }

  if (error || !team) {
    return (
      <div className="alert alert-danger" role="alert">
        {error || 'Team not found'}
      </div>
    );
  }

  return (
    <div className="view-team-container">
      {/* Header */}
      <Card className="mb-4 border-0">
        <Card.Body>
          <div className="d-flex align-items-center justify-content-between">
            <div className="d-flex align-items-center gap-2">
              <BootstrapButton 
                variant="link" 
                className="p-0"
                onClick={() => navigate('/teams')}
              >
                <HiArrowLeft size={24} />
              </BootstrapButton>
              <h4 className="mb-0">{team.name}</h4>
              <Badge 
                bg={team.status === 'ACTIVE' ? 'success' : 'secondary'} 
              >
                {team.status}
              </Badge>
            </div>
            <Button 
              variant="primary"
              onClick={() => setShowEditModal(true)}
              disabled={team.status === 'INACTIVE' || operationLoading}
            >
              <HiPencil style={{ marginRight: '4px' }} /> Edit Team
            </Button>
          </div>
        </Card.Body>
      </Card>

      {/* Main Content */}
      <Row className="g-4">
        {/* Team ID */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiDocumentText className="text-primary me-2" size={24} />
                <h5 className="mb-0">Team ID</h5>
              </div>
              <div className="d-flex align-items-center">
                <code className="bg-white px-2 py-1 rounded me-2">{team.id}</code>
                <BootstrapButton 
                  size="sm"
                  variant="outline-primary"
                  onClick={() => {
                    navigator.clipboard.writeText(team.id);
                    showSuccessToast('Team ID copied to clipboard');
                  }}
                >
                  <HiDocumentText size={16} className="me-1" />
                  Copy
                </BootstrapButton>
              </div>
            </Card.Body>
          </Card>
        </Col>

        {/* Organization */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiOfficeBuilding className="text-primary me-2" size={24} />
                <h5 className="mb-0">Organization</h5>
              </div>
              <p className="mb-0">{team.organization?.name || 'No organization assigned'}</p>
            </Card.Body>
          </Card>
        </Col>

        {/* Description */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiDocumentText className="text-primary me-2" size={24} />
                <h5 className="mb-0">Description</h5>
              </div>
              <p>{team.description || 'No description provided.'}</p>
            </Card.Body>
          </Card>
        </Col>

        {/* Statistics */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiUsers className="text-primary me-2" size={24} />
                <h5 className="mb-0">Team Statistics</h5>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Members</span>
                <Badge bg="info" pill className="px-2">
                  {team.members?.length || 0}
                </Badge>
              </div>
              <div className="d-flex justify-content-between align-items-center">
                <span className="text-muted h6">Apps</span>
                <Badge bg="info" pill className="px-2">
                  {team.routes?.length || 0}
                </Badge>
              </div>
            </Card.Body>
          </Card>
        </Col>

        {/* Timestamps */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiClock className="text-primary me-2" size={24} />
                <h5 className="mb-0">Timestamps</h5>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6 mb-0">Created</span>
                <span className="text-secondary">
                  {formatDate(team.createdAt)}
                </span>
              </div>
              {team.updatedAt && (
                <div className="d-flex justify-content-between align-items-center">
                  <span className="text-muted h6 mb-0">Last Updated</span>
                  <span className="text-secondary">
                    {formatDate(team.updatedAt)}
                  </span>
                </div>
              )}
            </Card.Body>
          </Card>
        </Col>

        {/* Actions */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <h5 className="mb-3">Team Management</h5>
              <div className="d-flex flex-wrap gap-2">
                <BootstrapButton
                  variant="outline-secondary"
                  onClick={() => setShowMembersModal(true)}
                  disabled={operationLoading}
                >
                  <HiUsers className="me-1" /> Manage Members ({team.members?.length || 0})
                </BootstrapButton>
                <BootstrapButton
                  variant="outline-info"
                  onClick={() => setShowRoutesModal(true)}
                  disabled={operationLoading}
                >
                  <HiTemplate className="me-1" /> Manage Apps ({team.routes?.length || 0})
                </BootstrapButton>
                <BootstrapButton
                  variant="outline-purple"
                  onClick={() => setShowApiKeysModal(true)}
                  disabled={team.status === 'INACTIVE' || operationLoading}
                >
                  <HiKey className="me-1" /> API Keys
                </BootstrapButton>
                <BootstrapButton
                  variant="outline-info"
                  onClick={() => setShowOpenAIModal(true)}
                  disabled={team.status === 'INACTIVE' || operationLoading}
                >
                  <SiOpenai className="me-1" size={16} /> OpenAI Config
                </BootstrapButton>
                <BootstrapButton
                  variant="outline-info"
                  onClick={() => setShowGeminiModal(true)}
                  disabled={team.status === 'INACTIVE' || operationLoading}
                >
                  <SiGoogle className="me-1" size={14} /> Gemini Config
                </BootstrapButton>
              </div>
            </Card.Body>
          </Card>
        </Col>

        {/* Members Table */}
        {team.members && team.members.length > 0 && (
          <Col md={12}>
            <Card className="border-0 bg-light">
              <Card.Body>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <div className="d-flex align-items-center">
                    <HiUsers className="text-primary me-2" size={24} />
                    <h5 className="mb-0">Team Members</h5>
                  </div>
                  <BootstrapButton 
                    size="sm" 
                    variant="outline-secondary"
                    onClick={() => setShowMembersModal(true)}
                  >
                    <HiUsers className="me-1" /> Manage
                  </BootstrapButton>
                </div>
                <div className="table-responsive">
                  <table className="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>Username</th>
                        <th>Role</th>
                        <th>Status</th>
                        <th>Joined At</th>
                      </tr>
                    </thead>
                    <tbody>
                      {team.members.map(member => (
                        <tr key={member.id}>
                          <td>{member.username}</td>
                          <td>
                            <Badge bg="primary">{member.role}</Badge>
                          </td>
                          <td>
                            <Badge bg={member.status === 'ACTIVE' ? 'success' : 'secondary'}>
                              {member.status}
                            </Badge>
                          </td>
                          <td>{formatDate(member.joinedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card.Body>
            </Card>
          </Col>
        )}

        {/* Routes Table */}
        {team.routes && team.routes.length > 0 && (
          <Col md={12}>
            <Card className="border-0 bg-light">
              <Card.Body>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <div className="d-flex align-items-center">
                    <HiTemplate className="text-primary me-2" size={24} />
                    <h5 className="mb-0">Apps</h5>
                  </div>
                  <BootstrapButton 
                    size="sm" 
                    variant="outline-info"
                    onClick={() => setShowRoutesModal(true)}
                  >
                    <HiTemplate className="me-1" /> Manage
                  </BootstrapButton>
                </div>
                <div className="table-responsive">
                  <table className="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>Route ID</th>
                        <th>Path</th>
                        <th>Version</th>
                        <th>Permissions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {team.routes.map(route => (
                        <tr key={route.id}>
                          <td>{route.routeIdentifier}</td>
                          <td>{route.path}</td>
                          <td>v{route.version}</td>
                          <td>
                            {route.permissions?.map(permission => (
                              <Badge 
                                key={permission} 
                                bg="info" 
                                className="me-1"
                              >
                                <HiLockClosed className="me-1" size={12} />
                                {permission}
                              </Badge>
                            ))}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card.Body>
            </Card>
          </Col>
        )}

        {/* LLM Models Table */}
        {llmModels && llmModels.length > 0 && (
          <Col md={12}>
            <Card className="border-0 bg-light">
              <Card.Body>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <div className="d-flex align-items-center">
                    <HiSparkles className="text-primary me-2" size={24} />
                    <h5 className="mb-0">LLM Models</h5>
                  </div>
                  <div className="d-flex gap-2">
                    <BootstrapButton 
                      size="sm" 
                      variant="outline-info"
                      onClick={() => setShowOpenAIModal(true)}
                      disabled={team.status === 'INACTIVE' || operationLoading}
                    >
                      <SiOpenai className="me-1" size={16} /> OpenAI
                    </BootstrapButton>
                    <BootstrapButton 
                      size="sm" 
                      variant="outline-info"
                      onClick={() => setShowGeminiModal(true)}
                      disabled={team.status === 'INACTIVE' || operationLoading}
                    >
                      <SiGoogle className="me-1" size={14} /> Gemini
                    </BootstrapButton>
                    <BootstrapButton 
                      size="sm" 
                      variant="outline-info"
                      onClick={() => setShowCohereModal(true)}
                      disabled={team.status === 'INACTIVE' || operationLoading}
                    >
                      <FaCloud className="me-1" size={16} /> Cohere
                    </BootstrapButton>
                    <BootstrapButton 
                      size="sm" 
                      variant="outline-info"
                      onClick={() => setShowClaudeModal(true)}
                      disabled={team.status === 'INACTIVE' || operationLoading}
                    >
                      <SiAnthropic className="me-1" size={16} /> Claude
                    </BootstrapButton>
                  </div>
                </div>
                <div className="table-responsive">
                  <table className="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>Provider</th>
                        <th>Model Category</th>
                        <th>Model Name</th>
                        <th>Endpoint</th>
                        <th>Auth Type</th>
                        <th>Supported Intents</th>
                      </tr>
                    </thead>
                    <tbody>
                      {llmModels.map(model => (
                        <tr key={model.id}>
                          <td>
                            <Badge bg={
                              model.provider?.toLowerCase() === 'openai' ? 'primary' :
                              model.provider?.toLowerCase() === 'gemini' ? 'warning' :
                              model.provider?.toLowerCase() === 'cohere' ? 'info' :
                              model.provider?.toLowerCase() === 'claude' ? 'danger' : 'secondary'
                            }>
                              {model.provider}
                            </Badge>
                          </td>
                                                   <td>
                            <Badge bg="secondary">{model.modelCategory}</Badge>
                          </td>
                          <td>{model.modelName}</td>
                          <td>
                            <OverlayTrigger
                              placement="top"
                              overlay={<Tooltip id={`tooltip-endpoint-${model.id}`}>{model.endpoint}</Tooltip>}
                            >
                              <code className="text-truncate" style={{maxWidth: '400px', display: 'block'}}>
                                {model.endpoint}
                              </code>
                            </OverlayTrigger>
                          </td>
                          <td>
                            <Badge bg="secondary">{model.authType}</Badge>
                          </td>
                          <td>
                            {model.supportedIntents?.map(intent => (
                              <Badge 
                                key={intent} 
                                bg="info" 
                                className="me-1"
                              >
                                {intent}
                              </Badge>
                            ))}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card.Body>
            </Card>
          </Col>
        )}
      </Row>

      {/* Modals */}
      {showMembersModal && (
        <TeamMembersModal
          show={true}
          onHide={() => setShowMembersModal(false)}
          team={team}
          onAddMember={handleAddMember}
          onRemoveMember={handleRemoveMember}
          loading={operationLoading}
        />
      )}

      {showRoutesModal && (
        <TeamRoutesModal
          show={true}
          onHide={() => setShowRoutesModal(false)}
          team={team}
          onAddRoute={handleAddRoute}
          onRemoveRoute={handleRemoveRoute}
          loading={operationLoading}
        />
      )}

      <TeamEditModal
        show={showEditModal}
        onHide={() => setShowEditModal(false)}
        onSubmit={handleEditTeam}
        loading={operationLoading}
        team={team}
      />

      <TeamApiKeysModal
        show={showApiKeysModal}
        onHide={() => setShowApiKeysModal(false)}
        team={team}
        onCreateApiKey={handleCreateApiKey}
        loading={operationLoading}
      />

      {showOpenAIModal && (
        <OpenAIModal
          show={true}
          onHide={() => setShowOpenAIModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      {showGeminiModal && (
        <GeminiModal
          show={true}
          onHide={() => setShowGeminiModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      {showCohereModal && (
        <CohereModal
          show={true}
          onHide={() => setShowCohereModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      {showClaudeModal && (
        <ClaudeModal
          show={true}
          onHide={() => setShowClaudeModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      <ConfirmationModal
        show={confirmModal.show}
        onHide={() => setConfirmModal(prev => ({ ...prev, show: false }))}
        onConfirm={confirmModal.onConfirm}
        title={confirmModal.title}
        message={confirmModal.message}
        variant={confirmModal.variant}
      />
    </div>
  );
}

export default ViewTeam;
