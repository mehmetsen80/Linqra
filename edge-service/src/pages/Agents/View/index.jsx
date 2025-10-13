import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Alert, Card, Badge, Breadcrumb, Row, Col, Spinner } from 'react-bootstrap';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../../utils/roleUtils';
import Button from '../../../components/common/Button';
import { HiArrowLeft, HiPencilAlt, HiTrash, HiPlus } from 'react-icons/hi';
import agentService from '../../../services/agentService';
import agentMonitoringService from '../../../services/agentMonitoringService';
import { format, isValid, parseISO } from 'date-fns';
import './styles.css';

function ViewAgent() {
    const { agentId } = useParams();
    const navigate = useNavigate();
    const { currentTeam } = useTeam();
    const { user } = useAuth();
    const canEditAgent = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    const [agent, setAgent] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [performance, setPerformance] = useState(null);
    const [health, setHealth] = useState(null);
    const [loadingStats, setLoadingStats] = useState(true);
    const [tasks, setTasks] = useState([]);
    const [loadingTasks, setLoadingTasks] = useState(true);

    const loadAgent = async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await agentService.getAgent(currentTeam.id, agentId);
            if (response.success) {
                setAgent(response.data);
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load agent');
            console.error('Error loading agent:', err);
        } finally {
            setLoading(false);
        }
    };

    const loadAgentStats = async () => {
        try {
            setLoadingStats(true);
            
            // Load performance data
            const perfResponse = await agentMonitoringService.getAgentPerformance(agentId);
            if (perfResponse.success) {
                setPerformance(perfResponse.data);
            }

            // Load health data
            const healthResponse = await agentMonitoringService.getAgentHealth(agentId);
            if (healthResponse.success) {
                setHealth(healthResponse.data);
            }
        } catch (err) {
            console.error('Error loading agent stats:', err);
        } finally {
            setLoadingStats(false);
        }
    };

    const loadAgentTasks = async () => {
        try {
            setLoadingTasks(true);
            const response = await agentService.getTasksByAgent(agentId);
            if (response.success) {
                setTasks(response.data);
            }
        } catch (err) {
            console.error('Error loading agent tasks:', err);
        } finally {
            setLoadingTasks(false);
        }
    };

    useEffect(() => {
        if (currentTeam) {
            loadAgent();
            loadAgentStats();
            loadAgentTasks();
        }
    }, [agentId, currentTeam]);

    const formatDate = (dateValue, formatStr = 'MMM dd, yyyy HH:mm') => {
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

    if (loading) {
        return (
            <div className="view-agent-page">
                <div className="text-center py-5">
                    <Spinner animation="border" role="status">
                        <span className="visually-hidden">Loading...</span>
                    </Spinner>
                </div>
            </div>
        );
    }

    if (error || !agent) {
        return (
            <div className="view-agent-page">
                <Alert variant="danger">
                    <Alert.Heading>Error</Alert.Heading>
                    <p>{error || 'Agent not found'}</p>
                    <Button variant="outline-danger" onClick={() => navigate('/agents')}>
                        <HiArrowLeft /> Back to Agents
                    </Button>
                </Alert>
            </div>
        );
    }

    return (
        <div className="view-agent-page">
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
                        <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/agents' }}>
                            Agents
                        </Breadcrumb.Item>
                        <Breadcrumb.Item active>{agent.name}</Breadcrumb.Item>
                    </Breadcrumb>

                    <div className="d-flex gap-2">
                        <Button variant="outline-secondary" size="sm" onClick={() => navigate('/agents')}>
                            <HiArrowLeft /> Back
                        </Button>
                        {canEditAgent && (
                            <>
                                <Button variant="primary" size="sm" onClick={() => navigate(`/agents/${agentId}/edit`)}>
                                    <HiPencilAlt /> Edit
                                </Button>
                                <Button variant="outline-danger" size="sm" onClick={() => console.log('Delete agent')}>
                                    <HiTrash /> Delete
                                </Button>
                            </>
                        )}
                    </div>
                </Card.Header>
            </Card>

            <Row>
                <Col lg={8}>
                    <Card className="mb-4">
                        <Card.Header>
                            <h5 className="mb-0">Agent Information</h5>
                        </Card.Header>
                        <Card.Body>
                            <Row>
                                <Col md={6} className="mb-3">
                                    <div className="info-section">
                                        <label>Name</label>
                                        <p className="info-value">{agent.name}</p>
                                    </div>
                                </Col>
                                <Col md={6} className="mb-3">
                                    <div className="info-section">
                                        <label>Status</label>
                                        <div>
                                            <Badge bg={agent.enabled ? 'success' : 'secondary'}>
                                                {agent.enabled ? 'Active' : 'Inactive'}
                                            </Badge>
                                        </div>
                                    </div>
                                </Col>
                                <Col md={12} className="mb-3">
                                    <div className="info-section">
                                        <label>Description</label>
                                        <p className="info-value">{agent.description || 'No description provided'}</p>
                                    </div>
                                </Col>
                                <Col md={6} className="mb-3">
                                    <div className="info-section">
                                        <label>Supported Intents</label>
                                        <div className="info-value">
                                            {agent.supportedIntents && agent.supportedIntents.length > 0 ? (
                                                agent.supportedIntents.map((intent, idx) => (
                                                    <Badge key={idx} bg="secondary" className="me-1 mb-1">
                                                        {intent}
                                                    </Badge>
                                                ))
                                            ) : (
                                                <span className="text-muted">None</span>
                                            )}
                                        </div>
                                    </div>
                                </Col>
                                <Col md={6} className="mb-3">
                                    <div className="info-section">
                                        <label>Capabilities</label>
                                        <div className="info-value">
                                            {agent.capabilities && agent.capabilities.length > 0 ? (
                                                agent.capabilities.map((capability, idx) => (
                                                    <Badge key={idx} bg="secondary" className="me-1 mb-1">
                                                        {capability}
                                                    </Badge>
                                                ))
                                            ) : (
                                                <span className="text-muted">None</span>
                                            )}
                                        </div>
                                    </div>
                                </Col>
                                <Col md={6} className="mb-3">
                                    <div className="info-section">
                                        <label>Created By</label>
                                        <p className="info-value">{agent.createdBy || 'Unknown'}</p>
                                    </div>
                                </Col>
                                <Col md={6} className="mb-3">
                                    <div className="info-section">
                                        <label>Created At</label>
                                        <p className="info-value">{formatDate(agent.createdAt)}</p>
                                    </div>
                                </Col>
                                {agent.updatedBy && (
                                    <Col md={6} className="mb-3">
                                        <div className="info-section">
                                            <label>Updated By</label>
                                            <p className="info-value">{agent.updatedBy}</p>
                                        </div>
                                    </Col>
                                )}
                                {agent.updatedAt && (
                                    <Col md={6} className="mb-3">
                                        <div className="info-section">
                                            <label>Last Updated</label>
                                            <p className="info-value">{formatDate(agent.updatedAt)}</p>
                                        </div>
                                    </Col>
                                )}
                            </Row>
                        </Card.Body>
                    </Card>
                </Col>

                <Col lg={4}>
                    <Card className="mb-4">
                        <Card.Header>
                            <h5 className="mb-0">Quick Stats</h5>
                        </Card.Header>
                        <Card.Body>
                            {loadingStats ? (
                                <div className="text-center py-3">
                                    <Spinner animation="border" size="sm" />
                                </div>
                            ) : (
                                <>
                                    <div className="stat-item">
                                        <div className="stat-label">Total Executions</div>
                                        <div className="stat-value">{performance?.totalExecutions || 0}</div>
                                    </div>
                                    <div className="stat-item">
                                        <div className="stat-label">Success Rate</div>
                                        <div className="stat-value">
                                            {performance?.successRate ? `${performance.successRate.toFixed(1)}%` : 'N/A'}
                                        </div>
                                    </div>
                                    <div className="stat-item">
                                        <div className="stat-label">Avg Execution Time</div>
                                        <div className="stat-value">
                                            {performance?.averageExecutionTimeMs 
                                                ? `${(performance.averageExecutionTimeMs / 1000).toFixed(2)}s`
                                                : 'N/A'}
                                        </div>
                                    </div>
                                    <div className="stat-item">
                                        <div className="stat-label">Can Execute</div>
                                        <div className="stat-value">
                                            <Badge bg={health?.canExecute ? 'success' : 'danger'}>
                                                {health?.canExecute ? 'Ready' : 'Not Ready'}
                                            </Badge>
                                        </div>
                                    </div>
                                    <div className="stat-item">
                                        <div className="stat-label">Last Run</div>
                                        <div className="stat-value text-muted">
                                            {health?.lastRun ? formatDate(health.lastRun) : 'Never'}
                                        </div>
                                    </div>
                                </>
                            )}
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            {performance && (
                <Row className="mb-4">
                    <Col lg={6}>
                        <Card>
                            <Card.Header>
                                <h5 className="mb-0">Status Breakdown</h5>
                            </Card.Header>
                            <Card.Body>
                                <div className="stat-item">
                                    <div className="stat-label">Completed</div>
                                    <div className="stat-value text-success">{performance.statusBreakdown?.completed || 0}</div>
                                </div>
                                <div className="stat-item">
                                    <div className="stat-label">Running</div>
                                    <div className="stat-value text-primary">{performance.statusBreakdown?.running || 0}</div>
                                </div>
                                <div className="stat-item">
                                    <div className="stat-label">Failed</div>
                                    <div className="stat-value text-danger">{performance.statusBreakdown?.failed || 0}</div>
                                </div>
                                <div className="stat-item">
                                    <div className="stat-label">Cancelled</div>
                                    <div className="stat-value text-warning">{performance.statusBreakdown?.cancelled || 0}</div>
                                </div>
                                <div className="stat-item">
                                    <div className="stat-label">Timeout</div>
                                    <div className="stat-value text-muted">{performance.statusBreakdown?.timeout || 0}</div>
                                </div>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col lg={6}>
                        <Card>
                            <Card.Header>
                                <h5 className="mb-0">Result Breakdown</h5>
                            </Card.Header>
                            <Card.Body>
                                <div className="stat-item">
                                    <div className="stat-label">Successful</div>
                                    <div className="stat-value text-success">{performance.resultBreakdown?.successful || 0}</div>
                                </div>
                                <div className="stat-item">
                                    <div className="stat-label">Failed</div>
                                    <div className="stat-value text-danger">{performance.resultBreakdown?.failed || 0}</div>
                                </div>
                                <div className="stat-item">
                                    <div className="stat-label">Partial Success</div>
                                    <div className="stat-value text-warning">{performance.resultBreakdown?.partialSuccess || 0}</div>
                                </div>
                                <div className="stat-item">
                                    <div className="stat-label">Skipped</div>
                                    <div className="stat-value text-muted">{performance.resultBreakdown?.skipped || 0}</div>
                                </div>
                                <div className="stat-item">
                                    <div className="stat-label">Unknown</div>
                                    <div className="stat-value text-muted">{performance.resultBreakdown?.unknown || 0}</div>
                                </div>
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>
            )}

            <Card>
                <Card.Header className="d-flex justify-content-between align-items-center">
                    <h5 className="mb-0">Agent Tasks</h5>
                    {canEditAgent && (
                        <Button 
                            variant="primary"
                            onClick={() => console.log('Create task')}
                        >
                            <HiPlus /> Create Task
                        </Button>
                    )}
                </Card.Header>
                <Card.Body className="p-0 my-2">
                    {loadingTasks ? (
                        <div className="text-center py-3">
                            <Spinner animation="border" size="sm" />
                        </div>
                    ) : tasks.length === 0 ? (
                        <div className="text-center py-4 text-muted">
                            <i className="fas fa-tasks fa-3x mb-3"></i>
                            <p>No tasks configured for this agent</p>
                        </div>
                    ) : (
                        <div className="table-responsive">
                            <table className="table table-hover">
                                <thead>
                                    <tr>
                                        <th>Task Name</th>
                                        <th>Task Type</th>
                                        <th>Status</th>
                                        <th>Trigger</th>
                                        <th>Cron</th>
                                        <th>Cron Description</th>
                                        <th>Last Run</th>
                                        <th>Next Run</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {tasks.map((task) => (
                                        <tr key={task.id} style={{ cursor: 'pointer' }} onClick={() => console.log('View task', task.id)}>
                                            <td>{task.name}</td>
                                            <td>
                                                <Badge bg={
                                                    task.taskType === 'WORKFLOW_TRIGGER' ? 'primary' :
                                                    task.taskType === 'WORKFLOW_EMBEDDED' ? 'info' :
                                                    task.taskType === 'API_CALL' ? 'warning' :
                                                    'secondary'
                                                }>
                                                    {task.taskType?.replace(/_/g, ' ')}
                                                </Badge>
                                            </td>
                                            <td>
                                                <Badge bg={task.enabled ? 'success' : 'secondary'}>
                                                    {task.enabled ? 'Active' : 'Inactive'}
                                                </Badge>
                                            </td>
                                            <td>
                                                <Badge bg={
                                                    task.executionTrigger === 'MANUAL' ? 'secondary' :
                                                    task.executionTrigger === 'CRON' ? 'primary' :
                                                    task.executionTrigger === 'EVENT_DRIVEN' ? 'warning' :
                                                    'info'
                                                }>
                                                    {task.executionTrigger}
                                                </Badge>
                                            </td>
                                            <td>
                                                {task.cronExpression ? (
                                                    <code className="text-muted small">{task.cronExpression}</code>
                                                ) : (
                                                    <span className="text-muted">N/A</span>
                                                )}
                                            </td>
                                            <td className="text-muted">
                                                {task.cronDescription || 'N/A'}
                                            </td>
                                            <td className="text-muted">{formatDate(task.lastRun)}</td>
                                            <td className="text-muted">{formatDate(task.nextRun)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </Card.Body>
            </Card>
        </div>
    );
}

export default ViewAgent;

