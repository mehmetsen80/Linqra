import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Alert, Card, Badge, Breadcrumb, Row, Col, Spinner, Modal, Form, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../../utils/roleUtils';
import Button from '../../../components/common/Button';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import { HiArrowLeft, HiPencilAlt, HiTrash, HiPlus } from 'react-icons/hi';
import agentService from '../../../services/agentService';
import agentMonitoringService from '../../../services/agentMonitoringService';
import agentTaskService from '../../../services/agentTaskService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { format, isValid, parseISO } from 'date-fns';
import AgentStats from '../../../components/dashboard/AgentStats';
import AgentTasksAnalytics from '../../../components/dashboard/AgentTasksAnalytics';
import Footer from '../../../components/common/Footer';
import './styles.css';
import CreateAgentTaskModal from '../../../components/agents/CreateAgentTaskModal';
import EditAgentModal from '../../../components/agents/EditAgentModal';

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
    const [showCreateTaskModal, setShowCreateTaskModal] = useState(false);
    const [newTask, setNewTask] = useState({
        name: '',
        description: '',
        taskType: 'WORKFLOW_EMBEDDED',
        priority: 5,
        maxRetries: 3,
        timeoutMinutes: 120
    });
    const [creatingTask, setCreatingTask] = useState(false);
    const [showEditAgentModal, setShowEditAgentModal] = useState(false);
    const [editedAgent, setEditedAgent] = useState(null);
    const [savingAgent, setSavingAgent] = useState(false);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [deleting, setDeleting] = useState(false);

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

    const handleNewTaskChange = (e) => {
        const { name, value } = e.target;
        setNewTask(prev => ({
            ...prev,
            [name]: name === 'priority' || name === 'maxRetries' || name === 'timeoutMinutes'
                ? parseInt(value) || 0
                : value
        }));
    };

    const handleEditAgentClick = () => {
        setEditedAgent({
            ...agent,
            supportedIntents: agent.supportedIntents || [],
            capabilities: agent.capabilities || []
        });
        setShowEditAgentModal(true);
    };

    const handleEditedAgentChange = (e) => {
        const { name, value } = e.target;
        setEditedAgent(prev => ({
            ...prev,
            [name]: value
        }));
    };

    const handleMultiSelectChange = (field, selectedOptions) => {
        const values = (selectedOptions || []).map(option => option.value);
        setEditedAgent(prev => ({
            ...prev,
            [field]: values
        }));
    };

    const handleSaveAgent = async () => {
        if (!editedAgent.name || !editedAgent.description) {
            showErrorToast('Please fill in all required fields');
            return;
        }

        try {
            setSavingAgent(true);
            const response = await agentService.updateAgent(agentId, editedAgent);
            if (response.success) {
                showSuccessToast('Agent updated successfully');
                setShowEditAgentModal(false);
                loadAgent(); // Reload agent data
            } else {
                showErrorToast(response.error || 'Failed to update agent');
            }
        } catch (error) {
            console.error('Error updating agent:', error);
            showErrorToast(error.response?.data?.message || 'Failed to update agent');
        } finally {
            setSavingAgent(false);
        }
    };

    const handleDeleteAgent = async () => {
        try {
            setDeleting(true);
            const response = await agentService.deleteAgent(agentId);
            if (response.success) {
                showSuccessToast('Agent deleted successfully');
                navigate('/agents'); // Navigate back to agents list
            } else {
                showErrorToast(response.error || 'Failed to delete agent');
            }
        } catch (error) {
            console.error('Error deleting agent:', error);
            showErrorToast(error.response?.data?.message || 'Failed to delete agent');
        } finally {
            setDeleting(false);
            setShowDeleteConfirm(false);
        }
    };

    const handleCreateTask = async () => {
        if (!newTask.name || !newTask.description) {
            showErrorToast('Please fill in all required fields');
            return;
        }

        try {
            setCreatingTask(true);

            // Construct minimal valid linq_config based on taskType
            let linq_config;
            if (newTask.taskType === 'WORKFLOW_EMBEDDED') {
                // For WORKFLOW_EMBEDDED: must have linq_config with embedded workflow steps
                linq_config = {
                    link: {
                        target: "workflow",
                        action: "execute"
                    },
                    query: {
                        intent: "placeholder_intent",
                        params: {},
                        workflow: [
                            {
                                step: 1,
                                target: "placeholder-service",
                                action: "fetch",
                                intent: "/api/placeholder",
                                params: {},
                                payload: null,
                                llmConfig: null,
                                async: null,
                                cacheConfig: null
                            }
                        ]
                    }
                };
            } else if (newTask.taskType === 'WORKFLOW_TRIGGER') {
                // For WORKFLOW_TRIGGER: must have linq_config with workflowId
                linq_config = {
                    link: {
                        target: "workflow",
                        action: "execute"
                    },
                    query: {
                        intent: "placeholder_intent",
                        workflowId: "", // User must set this later
                        params: {}
                    }
                };
            }

            const taskData = {
                ...newTask,
                agentId: agentId,
                linq_config: linq_config
            };

            const response = await agentTaskService.createAgentTask(taskData);

            if (response.success) {
                showSuccessToast('Task created successfully!');
                setShowCreateTaskModal(false);
                setNewTask({
                    name: '',
                    description: '',
                    taskType: 'WORKFLOW_EMBEDDED',
                    priority: 5,
                    maxRetries: 3,
                    timeoutMinutes: 30
                });
                loadAgentTasks(); // Reload tasks
                // Navigate to the new task
                navigate(`/agents/${agentId}/tasks/${response.data.id}`);
            } else {
                showErrorToast(response.error || 'Failed to create task');
            }
        } catch (error) {
            console.error('Error creating task:', error);
            showErrorToast(error.response?.data?.message || 'Failed to create task');
        } finally {
            setCreatingTask(false);
        }
    };

    const formatDate = (dateValue, formatStr = 'MMM dd, yyyy HH:mm') => {
        if (!dateValue) return 'N/A';
        try {
            // Handle array format [year, month, day, hour, minute, second?, nano?]
            if (Array.isArray(dateValue) && dateValue.length >= 5) {
                const year = dateValue[0];
                const month = dateValue[1] - 1; // JavaScript months are 0-based
                const day = dateValue[2];
                const hour = dateValue[3];
                const minute = dateValue[4];
                const second = dateValue[5] || 0;

                // Create UTC date
                const date = new Date(Date.UTC(year, month, day, hour, minute, second));
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

    const convertCronDescriptionToLocal = (cronDescription, cronExpression) => {
        if (!cronDescription || !cronExpression) return cronDescription || 'N/A';

        try {
            // Parse cron expression to get UTC hour and minute
            const cronParts = cronExpression.trim().split(/\s+/);
            if (cronParts.length < 6) return cronDescription;

            const utcMinute = parseInt(cronParts[1]) || 0;
            const utcHour = parseInt(cronParts[2]) || 0;

            // Convert UTC to local time
            const utcDate = new Date(Date.UTC(2000, 0, 1, utcHour, utcMinute));
            const localHour = utcDate.getHours();
            const localMinute = utcDate.getMinutes();

            // Convert to 12-hour format
            const utcHour12 = utcHour === 0 ? 12 : utcHour > 12 ? utcHour - 12 : utcHour;
            const utcAmPm = utcHour >= 12 ? 'PM' : 'AM';
            const localHour12 = localHour === 0 ? 12 : localHour > 12 ? localHour - 12 : localHour;
            const localAmPm = localHour >= 12 ? 'PM' : 'AM';

            let localDescription = cronDescription;

            // Try different time patterns
            const utcPattern1 = `${utcHour12}:${utcMinute.toString().padStart(2, '0')} ${utcAmPm}`;
            const localPattern1 = `${localHour12}:${localMinute.toString().padStart(2, '0')} ${localAmPm}`;

            if (localDescription.includes(utcPattern1)) {
                localDescription = localDescription.replace(new RegExp(utcPattern1.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), localPattern1);
            } else if (localDescription.includes(`${utcHour12} ${utcAmPm}`)) {
                localDescription = localDescription.replace(new RegExp(`${utcHour12} ${utcAmPm}`.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), `${localHour12} ${localAmPm}`);
            }

            return localDescription;
        } catch (error) {
            console.error('Error converting cron description to local:', error);
            return cronDescription;
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
                        <Breadcrumb.Item
                            onClick={() => currentTeam?.id && navigate(`/teams/${currentTeam.id}`)}
                            style={{ cursor: currentTeam?.id ? 'pointer' : 'default' }}
                        >
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
                                <Button variant="primary" size="sm" onClick={handleEditAgentClick}>
                                    <HiPencilAlt /> Edit
                                </Button>
                                <OverlayTrigger
                                    placement="bottom"
                                    overlay={
                                        <Tooltip id="delete-agent-tooltip">
                                            {tasks.length > 0
                                                ? `Cannot delete agent with ${tasks.length} task(s). Please delete all tasks first.`
                                                : 'Delete this agent'}
                                        </Tooltip>
                                    }
                                >
                                    <span className="d-inline-block">
                                        <Button
                                            variant="outline-danger"
                                            size="sm"
                                            disabled={tasks.length > 0}
                                            onClick={() => setShowDeleteConfirm(true)}
                                            style={tasks.length > 0 ? { pointerEvents: 'none' } : {}}
                                        >
                                            <HiTrash /> Delete
                                        </Button>
                                    </span>
                                </OverlayTrigger>
                            </>
                        )}
                    </div>
                </Card.Header>
            </Card>

            <Row>
                <Col lg={8}>
                    <Card className="mb-4">
                        <Card.Header>
                            <h5 className="mb-0">Agent Information{agent?.name ? ` - ${agent.name}` : ''}</h5>
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
                            <h5 className="mb-0">Quick Stats{agent?.name ? ` - ${agent.name}` : ''}</h5>
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


            <Card className="mb-4">
                <Card.Header className="d-flex justify-content-between align-items-center">
                    <h5 className="mb-0">Agent Tasks{agent?.name ? ` - ${agent.name}` : ''}</h5>
                    {canEditAgent && (
                        <Button
                            variant="primary"
                            onClick={() => setShowCreateTaskModal(true)}
                            disabled={!canEditAgent}
                        >
                            <HiPlus /> Create Agent Task
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
                                        <tr key={task.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/agents/${agentId}/tasks/${task.id}`)}>
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
                                                        task.executionTrigger === 'CRON' ? 'secondary' :
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
                                                {convertCronDescriptionToLocal(task.cronDescription, task.cronExpression)}
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

            {/* Task Performance Analytics */}
            <AgentTasksAnalytics agentId={agentId} teamId={currentTeam?.id} />

            {performance && (
                <Row className="mb-4">
                    <Col lg={6}>
                        <Card>
                            <Card.Header>
                                <h5 className="mb-0">Status Breakdown{agent?.name ? ` - ${agent.name}` : ''}</h5>
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
                                <h5 className="mb-0">Result Breakdown{agent?.name ? ` - ${agent.name}` : ''}</h5>
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

            {/* Agent Statistics Dashboard */}
            <div className="mb-4">
                <AgentStats agentId={agentId} />
            </div>

            <CreateAgentTaskModal
                show={showCreateTaskModal}
                onHide={() => setShowCreateTaskModal(false)}
                onChange={handleNewTaskChange}
                onCreate={handleCreateTask}
                newTask={newTask}
                creating={creatingTask}
            />

            <EditAgentModal
                show={showEditAgentModal}
                onHide={() => setShowEditAgentModal(false)}
                onSave={handleSaveAgent}
                onChange={handleEditedAgentChange}
                onMultiSelectChange={handleMultiSelectChange}
                editedAgent={editedAgent}
                saving={savingAgent}
            />

            {/* Delete Confirmation Modal */}
            <ConfirmationModal
                show={showDeleteConfirm}
                onHide={() => setShowDeleteConfirm(false)}
                onConfirm={handleDeleteAgent}
                title="Delete Agent"
                message={`Are you sure you want to delete "${agent?.name}"? This action cannot be undone.`}
                confirmLabel={deleting ? (
                    <>
                        <Spinner
                            as="span"
                            animation="border"
                            size="sm"
                            role="status"
                            aria-hidden="true"
                            className="me-2"
                        />
                        Deleting...
                    </>
                ) : "Delete Agent"}
                variant="danger"
                disabled={deleting}
            />
            <Footer />
        </div>
    );
}

export default ViewAgent;

