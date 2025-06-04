import React, { useState, useEffect } from 'react';
import { Table, Badge, Spinner, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../../utils/roleUtils';
import { LoadingSpinner } from '../../../components/common/LoadingSpinner';
import { HiPencilAlt } from 'react-icons/hi';
import Button from '../../../components/common/Button';
import workflowService from '../../../services/workflowService';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import './styles.css';

function Workflows() {
    const { currentTeam } = useTeam();
    const { user } = useAuth();
    const navigate = useNavigate();
    const canEditWorkflow = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    const [workflows, setWorkflows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (currentTeam) {
            loadWorkflows();
        }
    }, [currentTeam]);

    const loadWorkflows = async () => {
        try {
            setLoading(true);
            const response = await workflowService.getAllTeamWorkflows();
            if (response.success) {
                setWorkflows(response.data);
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load workflows');
            console.error('Error loading workflows:', err);
        } finally {
            setLoading(false);
        }
    };

    const formatDate = (dateArray) => {
        if (!dateArray || dateArray.length < 7) return 'N/A';
        const [year, month, day, hour, minute, second] = dateArray;
        return format(new Date(year, month - 1, day, hour, minute, second), 'MMM d, yyyy HH:mm');
    };

    const getStepCount = (workflow) => {
        return workflow.request?.query?.workflow?.length || 0;
    };

    if (loading) {
        return (
            <div className="dashboard-workflows-loading">
                <Spinner animation="border" role="status">
                    <span className="visually-hidden">Loading...</span>
                </Spinner>
            </div>
        );
    }

    if (error) {
        return (
            <div className="dashboard-workflows-error">
                <p className="text-danger">{error}</p>
            </div>
        );
    }

    return (
        <div className="dashboard-workflows">
            <h4 className="dashboard-section-title">Recent Workflows</h4>
            <Table responsive hover className="dashboard-workflows-table">
                <thead>
                    <tr>
                        <th className="col-name">Name</th>
                        <th className="col-steps">Steps</th>
                        <th className="col-version">Version</th>
                        <th className="col-updated">Last Updated</th>
                        <th className="col-created">Created By</th>
                        <th className="col-status">Status</th>
                        <th className="col-actions">Actions</th>
                    </tr>
                </thead>
                <tbody>
                    {workflows.slice(0, 5).map((workflow) => (
                        <tr key={workflow.id}>
                            <td className="col-name">{workflow.name}</td>
                            <td className="col-steps">
                                <Badge bg="info">
                                    {getStepCount(workflow)} steps
                                </Badge>
                            </td>
                            <td className="col-version">
                                <Badge bg="secondary">
                                    v{workflow.version}
                                </Badge>
                            </td>
                            <td className="col-updated">{formatDate(workflow.updatedAt)}</td>
                            <td className="col-created">{workflow.createdBy || 'N/A'}</td>
                            <td className="col-status">
                                <Badge bg={workflow.public ? "success" : "warning"}>
                                    {workflow.public ? "Public" : "Private"}
                                </Badge>
                            </td>
                            <td className="col-actions">
                                <div className="dashboard-workflow-actions">
                                    <OverlayTrigger
                                        placement="top"
                                        overlay={
                                            <Tooltip id={`edit-tooltip-${workflow.id}`}>
                                                {isSuperAdmin(user) || hasAdminAccess(user, currentTeam)
                                                    ? 'Edit this workflow' 
                                                    : workflow.public 
                                                        ? 'View this workflow (read-only)'
                                                        : 'Only team admins can edit workflows'}
                                            </Tooltip>
                                        }
                                    >
                                        <div>
                                            <Button 
                                                variant="outline-secondary" 
                                                size="sm"
                                                onClick={() => navigate(`/workflows/${workflow.id}/edit`)}
                                                disabled={!canEditWorkflow && !workflow.public}
                                            >
                                                <HiPencilAlt className="me-1" /> Edit
                                            </Button>
                                        </div>
                                    </OverlayTrigger>
                                </div>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </Table>
        </div>
    );
}

export default Workflows;