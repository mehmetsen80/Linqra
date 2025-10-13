import React, { useState, useEffect } from 'react';
import { Alert, Table, Badge, Breadcrumb, Card } from 'react-bootstrap';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../utils/roleUtils';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import Button from '../../components/common/Button';
import { HiPlus, HiPencilAlt, HiTrash, HiEye } from 'react-icons/hi';
import agentService from '../../services/agentService';
import { format, isValid, parseISO } from 'date-fns';
import { Link, useNavigate } from 'react-router-dom';
import './styles.css';

function Agents() {
    const { currentTeam, loading: teamLoading } = useTeam();
    const { user } = useAuth();
    const canEditAgent = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    const [agents, setAgents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const navigate = useNavigate();

    useEffect(() => {
        if (currentTeam) {
            loadAgents();
        }
    }, [currentTeam]);

    const loadAgents = async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await agentService.getAgentsByTeam(currentTeam.id);
            if (response.success) {
                setAgents(response.data);
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load agents');
            console.error('Error loading agents:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleRowClick = (agent) => {
        navigate(`/agents/${agent.id}`);
    };

    const formatDate = (dateValue, formatStr = 'MMM dd, yyyy') => {
        if (!dateValue) return 'N/A';
        try {
            // Handle array format [year, month, day, hour, minute, second, nano]
            if (Array.isArray(dateValue) && dateValue.length >= 6) {
                const [year, month, day, hour, minute, second] = dateValue;
                const date = new Date(year, month - 1, day, hour, minute, second);
                return isValid(date) ? format(date, formatStr) : 'N/A';
            }
            // Handle string or Date object
            const date = typeof dateValue === 'string' ? parseISO(dateValue) : new Date(dateValue);
            return isValid(date) ? format(date, formatStr) : 'N/A';
        } catch (error) {
            console.error('Error formatting date:', dateValue, error);
            return 'N/A';
        }
    };

    if (teamLoading || loading) {
        return <LoadingSpinner />;
    }

    return (
        <div className="agents-page">
            <Card className="mb-4 mx-1 p-0">
                <Card.Header className="d-flex justify-content-between align-items-center bg-light">
                    <Breadcrumb className="bg-light mb-0">
                        <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/' }}>
                            Home
                        </Breadcrumb.Item>
                        <Breadcrumb.Item 
                            linkAs={Link} 
                            linkProps={{ to: '/organizations' }}
                        >
                            {currentTeam?.organization?.name || 'Organization'}
                        </Breadcrumb.Item>
                        <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/teams' }}>
                            {currentTeam?.name || 'Team'}
                        </Breadcrumb.Item>
                        <Breadcrumb.Item active>Agents</Breadcrumb.Item>
                    </Breadcrumb>

                    {canEditAgent && (
                        <Button 
                            variant="primary"
                            onClick={() => console.log('Create agent')}
                        >
                            <HiPlus /> Create Agent
                        </Button>
                    )}
                </Card.Header>
            </Card>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError(null)}>
                    {error}
                </Alert>
            )}

            <Card className="agents-table-card">
                        <Card.Body>
                            {agents.length === 0 ? (
                                <div className="text-center py-5">
                                    <i className="fas fa-robot fa-3x text-muted mb-3"></i>
                                    <h5 className="text-muted">No agents found</h5>
                                    <p className="text-muted">
                                        {canEditAgent 
                                            ? 'Create your first agent to get started' 
                                            : 'No agents have been created yet'}
                                    </p>
                                </div>
                            ) : (
                                <Table hover responsive className="agents-table">
                                    <thead>
                                        <tr>
                                            <th>Name</th>
                                            <th>Description</th>
                                            <th>Status</th>
                                            <th>Tasks</th>
                                            <th>Created</th>
                                            <th>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {agents.map((agent) => (
                                            <tr 
                                                key={agent.id}
                                                onClick={() => handleRowClick(agent)}
                                                style={{ cursor: 'pointer' }}
                                            >
                                                <td>
                                                    <div className="agent-name">
                                                        <i className="fas fa-robot me-2"></i>
                                                        {agent.name}
                                                    </div>
                                                </td>
                                                <td>
                                                    <div className="agent-description">
                                                        {agent.description || 'No description'}
                                                    </div>
                                                </td>
                                                <td>
                                                    <Badge bg={agent.enabled ? 'success' : 'secondary'}>
                                                        {agent.enabled ? 'Active' : 'Inactive'}
                                                    </Badge>
                                                </td>
                                                <td>
                                                    <Badge bg="info">0 tasks</Badge>
                                                </td>
                                                <td>
                                                    {formatDate(agent.createdAt)}
                                                </td>
                                                <td onClick={(e) => e.stopPropagation()}>
                                                    <div className="action-buttons">
                                                        <Button
                                                            variant="link"
                                                            size="sm"
                                                            onClick={() => navigate(`/agents/${agent.id}`)}
                                                            title="View Details"
                                                        >
                                                            <HiEye />
                                                        </Button>
                                                        {canEditAgent && (
                                                            <>
                                                                <Button
                                                                    variant="link"
                                                                    size="sm"
                                                                    onClick={() => navigate(`/agents/${agent.id}/edit`)}
                                                                    title="Edit Agent"
                                                                >
                                                                    <HiPencilAlt />
                                                                </Button>
                                                                <Button
                                                                    variant="link"
                                                                    size="sm"
                                                                    className="text-danger"
                                                                    onClick={() => console.log('Delete agent', agent.id)}
                                                                    title="Delete Agent"
                                                                >
                                                                    <HiTrash />
                                                                </Button>
                                                            </>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </Table>
                            )}
                        </Card.Body>
                    </Card>
        </div>
    );
}

export default Agents;

