import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  HiUserGroup, 
  HiPlus, 
  HiEye, 
  HiPencil, 

  HiRefresh, 
  HiTrash, 

} from 'react-icons/hi';
import { Spinner, OverlayTrigger, Tooltip, Table } from 'react-bootstrap';
import CreateTeamModal from '../../components/teams/CreateTeamModal';
import TeamDetailsModal from '../../components/teams/TeamDetailsModal';
import TeamEditModal from '../../components/teams/TeamEditModal';
import { teamService } from '../../services/teamService';
import './styles.css';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import ConfirmationModal from '../../components/common/ConfirmationModal';
import Button from '../../components/common/Button';

function Teams() {
  const navigate = useNavigate();
  const [teams, setTeams] = useState([]);
  const [loading, setLoading] = useState(true);
  const [operationLoading, setOperationLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedTeam, setSelectedTeam] = useState(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [confirmModal, setConfirmModal] = useState({
    show: false,
    title: '',
    message: '',
    onConfirm: () => {},
    variant: 'danger'
  });

  useEffect(() => {
    fetchTeams();
  }, []);

  const fetchTeams = async () => {
    try {
      setLoading(true);
      const { data, error } = await teamService.getAllTeams();
      if (error) throw new Error(error);
      // Sort teams by createdAt in ascending order to match backend
      const sortedTeams = [...data].sort((a, b) => 
        new Date(a.createdAt) - new Date(b.createdAt)
      );
      setTeams(sortedTeams);
      setLoading(false);
    } catch (err) {
      setError('Failed to load teams');
      setLoading(false);
    }
  };

  const handleCreateTeam = async (teamData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.createTeam(teamData);
      if (error) throw new Error(error);
      
      await fetchTeams();
      setShowCreateModal(false);
      showSuccessToast(`Team "${data.name}" created successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to create team');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleDeleteTeam = async (teamId) => {
    const team = teams.find(t => t.id === teamId);
    try {
      setOperationLoading(true);
      const { error } = await teamService.deleteTeam(teamId);
      if (error) throw new Error(error);
      setConfirmModal(prev => ({ ...prev, show: false }));
      await fetchTeams();
      showSuccessToast(`Team "${team.name}" deleted successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to delete team');
    } finally {
      setOperationLoading(false);
    }
  };

  const confirmDelete = (team) => {
    setConfirmModal({
      show: true,
      title: 'Delete Team',
      message: `Are you sure you want to delete team "${team.name}"? This action cannot be undone.`,
      onConfirm: () => handleDeleteTeam(team.id),
      variant: 'danger',
      confirmLabel: operationLoading ? 'Deleting...' : 'Delete',
      disabled: operationLoading
    });
  };

  const confirmToggleStatus = (team) => {
    const action = team.status === 'ACTIVE' ? 'deactivate' : 'activate';
    setConfirmModal({
      show: true,
      title: `${action.charAt(0).toUpperCase() + action.slice(1)} Team`,
      message: `Are you sure you want to ${action} team "${team.name}"?`,
      onConfirm: () => handleToggleTeamStatus(team),
      variant: 'warning',
      confirmLabel: operationLoading ? `${action.charAt(0).toUpperCase() + action.slice(1)}ing...` : action.charAt(0).toUpperCase() + action.slice(1),
      disabled: operationLoading
    });
  };


  const handleToggleTeamStatus = async (team) => {
    const action = team.status === 'ACTIVE' ? 'deactivate' : 'activate';
    
    try {
      setOperationLoading(true);
      const { data, error } = await teamService[`${action}Team`](team.id);
      if (error) throw new Error(error);
      
      setConfirmModal(prev => ({ ...prev, show: false }));
      setTeams(prev => prev.map(t => 
        t.id === team.id ? data : t
      ));
      showSuccessToast(`Team "${team.name}" ${action}d successfully`);
    } catch (err) {
      showErrorToast(err.message || `Failed to ${action} team`);
    } finally {
      setOperationLoading(false);
    }
  };

  const getDeleteButtonTooltip = (team) => {
    if (team.status === 'ACTIVE') {
      return 'Team must be deactivated before deletion';
    }
    if (team.routes?.length > 0) {
      return 'Team has assigned routes';
    }
    return '';
  };

  const canDeleteTeam = (team) => {
    return team.status === 'INACTIVE' && 
           (!team.routes || team.routes.length === 0);
  };


  const handleEditTeam = async (teamData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.updateTeam(selectedTeam.id, {
        name: teamData.name,
        description: teamData.description,
        organizationId: teamData.organizationId
      });
      
      if (error) throw new Error(error);
      
      await fetchTeams();
      setShowEditModal(false);
      showSuccessToast(`Team "${data.name}" updated successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to update team');
    } finally {
      setOperationLoading(false);
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
      minute: '2-digit'
    });
  };

  // First, add this helper function to group teams by organization
  const groupTeamsByOrganization = (teams) => {
    return teams.reduce((groups, team) => {
      const orgId = team.organization?.id || 'uncategorized';
      const orgName = team.organization?.name || 'Uncategorized';
      if (!groups[orgId]) {
        groups[orgId] = {
          name: orgName,
          teams: []
        };
      }
      groups[orgId].teams.push(team);
      return groups;
    }, {});
  };

  return (
    <div className="teams-container">
      <div className="card mb-4 border-0 mx-0">
        <div className="card-header">
          <div className="d-flex align-items-center">
            <div className="d-flex align-items-center gap-2">
              <HiUserGroup className="teams-icon" />
              <h4 className="mb-0">Teams</h4>
            </div>
            <div className="ms-auto">
              <Button 
                onClick={() => setShowCreateModal(true)}
                disabled={operationLoading}
                variant="primary"
              >
                {operationLoading ? (
                  <>
                    <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true" />
                    Creating...
                  </>
                ) : (
                  <>
                    <HiPlus /> Create Team
                  </>
                )}
              </Button>
            </div>
          </div>
        </div>
        <div className="card-body">
          {error && (
            <div className="alert alert-danger" role="alert">
              {error}
            </div>
          )}
          
          {loading ? (
            <div className="text-center">
              <Spinner animation="border" role="status">
                <span className="visually-hidden">Loading...</span>
              </Spinner>
            </div>
          ) : teams.length === 0 ? (
            <div className="no-teams-message text-center py-5">
              <h4>No Teams Found</h4>
              <p className="text-muted">
                Create your first team to start managing members and API routes.
              </p>
              <Button 
                variant="primary" 
                onClick={() => setShowCreateModal(true)}
                className="mt-3"
                disabled={operationLoading}
              >
                {operationLoading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
                    Creating...
                  </>
                ) : (
                  <>
                    <HiPlus /> Create Your First Team
                  </>
                )}
              </Button>
            </div>
          ) : (
            <div className="table-responsive">
              <Table hover striped responsive>
                <thead>
                  <tr>
                    <th>Name</th>
                    {/* <th>Organization</th> */}
                    <th>Status</th>
                    <th>Members</th>
                    <th>Apps</th>
                    <th>Models</th>
                    <th>Created By</th>
                    <th>Created Date</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(groupTeamsByOrganization(teams)).map(([orgId, org]) => (
                    <React.Fragment key={orgId}>
                      <tr className="table-group-header">
                        <td colSpan="8" className="bg-light">
                          <strong>{org.name}</strong> (Organization)
                        </td>
                      </tr>
                      {org.teams.map(team => (
                        <tr 
                          key={team.id} 
                          className={team.status === 'INACTIVE' ? 'table-secondary' : ''}
                          style={{ cursor: 'pointer' }}
                          onClick={() => navigate(`/teams/${team.id}`)}
                        >
                          <td>{team.name}</td>
                          {/* <td>{team.organization?.name || '-'}</td> */}
                          <td>
                            <span className={`badge ${team.status === 'ACTIVE' ? 'bg-success' : 'bg-secondary'}`}>
                              {team.status}
                            </span>
                          </td>
                          <td>
                            <span className="badge bg-info">
                              Members ({team.members?.length || 0})
                            </span>
                          </td>
                          <td>
                            <span className="badge bg-info">
                              Apps ({team.routes?.length || 0})
                            </span>
                          </td>
                          <td>
                            <span className="badge bg-info" style={{ fontSize: '0.8rem' }}>
                              Models ({team.linqLlmModels?.length || 0})
                            </span>
                          </td>
                          <td>
                            <span className="text-muted">{team.createdBy || 'N/A'}</span>
                          </td>
                          <td>
                            <span className="text-muted">{formatDate(team.createdAt)}</span>
                          </td>
                          <td>
                            <div className="d-flex gap-1 flex-wrap">
                              <button 
                                className="btn btn-sm btn-outline-primary action-button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSelectedTeam(team);
                                  setShowDetailsModal(true);
                                }}
                              >
                                <HiEye className="me-1" /> View
                              </button>
                              <button 
                                className="btn btn-sm btn-outline-secondary action-button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSelectedTeam(team);
                                  setShowEditModal(true);
                                }}
                                disabled={team.status === 'INACTIVE'}
                              >
                                <HiPencil className="me-1" /> Edit
                              </button>
                              <button 
                                className="btn btn-sm btn-outline-warning action-button status-action-button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  confirmToggleStatus(team);
                                }}
                                disabled={operationLoading}
                              >
                                <HiRefresh className="me-1" />
                                {team.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                              </button>
                              <OverlayTrigger
                                placement="top"
                                overlay={
                                  <Tooltip id={`delete-tooltip-${team.id}`}>
                                    {!canDeleteTeam(team) ? getDeleteButtonTooltip(team) : 'Delete this team'}
                                  </Tooltip>
                                }
                              >
                                <span className="d-inline-block">
                                  <button 
                                    className="btn btn-sm btn-outline-danger action-button"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      confirmDelete(team);
                                    }}
                                    disabled={operationLoading || !canDeleteTeam(team)}
                                  >
                                    <HiTrash className="me-1" /> Delete
                                  </button>
                                </span>
                              </OverlayTrigger>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </React.Fragment>
                  ))}
                </tbody>
              </Table>
            </div>
          )}
        </div>
      </div>

      <CreateTeamModal
        show={showCreateModal}
        onHide={() => setShowCreateModal(false)}
        onSubmit={handleCreateTeam}
        loading={loading}
      />

      <TeamDetailsModal
        show={showDetailsModal}
        onHide={() => setShowDetailsModal(false)}
        team={selectedTeam}
      />

      <TeamEditModal
        show={showEditModal}
        onHide={() => setShowEditModal(false)}
        onSubmit={handleEditTeam}
        loading={operationLoading}
        team={selectedTeam}
      />

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

export default Teams; 
