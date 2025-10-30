import React, { useState, useEffect } from 'react';
import { Alert, Table, Badge, Breadcrumb, Card, Modal, Form } from 'react-bootstrap';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../utils/roleUtils';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import Button from '../../components/common/Button';
import { HiPlus, HiPencilAlt, HiTrash, HiEye } from 'react-icons/hi';
import agentService from '../../services/agentService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import { format, isValid, parseISO } from 'date-fns';
import { Link, useNavigate } from 'react-router-dom';
import AgentStats from '../../components/dashboard/AgentStats';
import './styles.css';

function Agents() {
    const { currentTeam, loading: teamLoading } = useTeam();
    const { user } = useAuth();
    const canEditAgent = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    const [agents, setAgents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [taskCounts, setTaskCounts] = useState({});
    const navigate = useNavigate();
    const [showCreateAgentModal, setShowCreateAgentModal] = useState(false);
    const [newAgent, setNewAgent] = useState({
        name: '',
        description: '',
        supportedIntents: [],
        capabilities: []
    });
    const [creatingAgent, setCreatingAgent] = useState(false);

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
                
                // Fetch task counts for each agent
                const counts = {};
                await Promise.all(
                    response.data.map(async (agent) => {
                        const tasksResponse = await agentService.getTasksByAgent(agent.id);
                        if (tasksResponse.success) {
                            counts[agent.id] = tasksResponse.data.length;
                        } else {
                            counts[agent.id] = 0;
                        }
                    })
                );
                setTaskCounts(counts);
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

    const handleNewAgentChange = (e) => {
        const { name, value } = e.target;
        setNewAgent(prev => ({
            ...prev,
            [name]: value
        }));
    };

    const handleMultiSelectChange = (field, selectedOptions) => {
        const values = Array.from(selectedOptions).map(option => option.value);
        setNewAgent(prev => ({
            ...prev,
            [field]: values
        }));
    };

    const handleCreateAgent = async () => {
        if (!newAgent.name || !newAgent.description) {
            showErrorToast('Please fill in all required fields');
            return;
        }

        try {
            setCreatingAgent(true);
            const response = await agentService.createAgent(newAgent);
            if (response.success) {
                showSuccessToast('Agent created successfully!');
                setShowCreateAgentModal(false);
                setNewAgent({
                    name: '',
                    description: '',
                    supportedIntents: [],
                    capabilities: []
                });
                loadAgents(); // Reload agents
                // Navigate to the new agent
                navigate(`/agents/${response.data.id}`);
            } else {
                showErrorToast(response.error || 'Failed to create agent');
            }
        } catch (error) {
            console.error('Error creating agent:', error);
            showErrorToast(error.response?.data?.message || 'Failed to create agent');
        } finally {
            setCreatingAgent(false);
        }
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
                        <Breadcrumb.Item 
                            onClick={() => currentTeam?.id && navigate(`/teams/${currentTeam.id}`)}
                            style={{ cursor: currentTeam?.id ? 'pointer' : 'default' }}
                        >
                            {currentTeam?.name || 'Team'}
                        </Breadcrumb.Item>
                        <Breadcrumb.Item active>Agents</Breadcrumb.Item>
                    </Breadcrumb>

                    {canEditAgent && (
                        <Button 
                            variant="primary"
                            onClick={() => setShowCreateAgentModal(true)}
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
                        <Card.Header className="text-start">
                            <h5 className="mb-0">Agents{currentTeam?.name ? ` - ${currentTeam.name}` : ''}</h5>
                        </Card.Header>
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
                                                    <Badge bg="info">
                                                        {taskCounts[agent.id] !== undefined ? taskCounts[agent.id] : '...'} 
                                                        {taskCounts[agent.id] === 1 ? ' task' : ' tasks'}
                                                    </Badge>
                                                </td>
                                                <td>
                                                    {formatDate(agent.createdAt)}
                                                </td>
                                                <td onClick={(e) => e.stopPropagation()}>
                                                    {canEditAgent && (
                                                        <div className="action-buttons">
                                                            <Button
                                                                variant="link"
                                                                size="sm"
                                                                onClick={() => navigate(`/agents/${agent.id}`)}
                                                                title="View Details"
                                                            >
                                                                <HiEye /> View Agent
                                                            </Button>
                                                        </div>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </Table>
                            )}
                        </Card.Body>
                    </Card>

            {/* Agent Analytics */}
            
            <div className="mt-4">
                <AgentStats />
            </div>

            {/* Create Agent Modal */}
            <Modal
                show={showCreateAgentModal}
                onHide={() => setShowCreateAgentModal(false)}
                centered
                size="lg"
            >
                <Modal.Header closeButton>
                    <Modal.Title>Create New Agent</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label>Name <span className="text-danger">*</span></Form.Label>
                            <Form.Control
                                type="text"
                                name="name"
                                value={newAgent.name}
                                onChange={handleNewAgentChange}
                                placeholder="Enter agent name"
                            />
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Description <span className="text-danger">*</span></Form.Label>
                            <Form.Control
                                as="textarea"
                                name="description"
                                value={newAgent.description}
                                onChange={handleNewAgentChange}
                                placeholder="Enter agent description"
                                rows={3}
                            />
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Supported Intents</Form.Label>
                            <Form.Select
                                multiple
                                value={newAgent.supportedIntents}
                                onChange={(e) => handleMultiSelectChange('supportedIntents', e.target.selectedOptions)}
                                style={{ height: '150px' }}
                            >
                                <option value="MONGODB_READ">MongoDB Read</option>
                                <option value="MONGODB_WRITE">MongoDB Write</option>
                                <option value="MILVUS_READ">Milvus Read</option>
                                <option value="MILVUS_WRITE">Milvus Write</option>
                                <option value="LLM_ANALYSIS">LLM Analysis</option>
                                <option value="LLM_GENERATION">LLM Generation</option>
                                <option value="API_INTEGRATION">API Integration</option>
                                <option value="WORKFLOW_ORCHESTRATION">Workflow Orchestration</option>
                                <option value="DATA_TRANSFORMATION">Data Transformation</option>
                                <option value="NOTIFICATION_SENDING">Notification Sending</option>
                                <option value="FILE_PROCESSING">File Processing</option>
                                <option value="MONITORING">Monitoring</option>
                                <option value="REPORTING">Reporting</option>
                                <option value="SCHEDULING">Scheduling</option>
                                <option value="EVENT_HANDLING">Event Handling</option>
                            </Form.Select>
                            <Form.Text className="text-muted">
                                Hold Ctrl/Cmd to select multiple intents
                            </Form.Text>
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Capabilities</Form.Label>
                            <Form.Select
                                multiple
                                value={newAgent.capabilities}
                                onChange={(e) => handleMultiSelectChange('capabilities', e.target.selectedOptions)}
                                style={{ height: '150px' }}
                            >
                                <option value="MONGODB_ACCESS">MongoDB Access</option>
                                <option value="MILVUS_ACCESS">Milvus Access</option>
                                <option value="LLM_INTEGRATION">LLM Integration</option>
                                <option value="HTTP_CLIENT">HTTP Client</option>
                                <option value="FILE_SYSTEM_ACCESS">File System Access</option>
                                <option value="EMAIL_SENDING">Email Sending</option>
                                <option value="SMS_SENDING">SMS Sending</option>
                                <option value="SLACK_INTEGRATION">Slack Integration</option>
                                <option value="WEBHOOK_HANDLING">Webhook Handling</option>
                                <option value="CRON_SCHEDULING">Cron Scheduling</option>
                                <option value="EVENT_STREAMING">Event Streaming</option>
                                <option value="DATA_ENCRYPTION">Data Encryption</option>
                                <option value="IMAGE_PROCESSING">Image Processing</option>
                                <option value="PDF_PROCESSING">PDF Processing</option>
                                <option value="JSON_PROCESSING">JSON Processing</option>
                                <option value="XML_PROCESSING">XML Processing</option>
                                <option value="CSV_PROCESSING">CSV Processing</option>
                                <option value="TEMPLATE_RENDERING">Template Rendering</option>
                                <option value="METRICS_COLLECTION">Metrics Collection</option>
                                <option value="LOG_ANALYSIS">Log Analysis</option>
                                <option value="BACKUP_OPERATIONS">Backup Operations</option>
                                <option value="CACHE_MANAGEMENT">Cache Management</option>
                            </Form.Select>
                            <Form.Text className="text-muted">
                                Hold Ctrl/Cmd to select multiple capabilities
                            </Form.Text>
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowCreateAgentModal(false)}>
                        Cancel
                    </Button>
                    <Button 
                        variant="primary" 
                        onClick={handleCreateAgent}
                        disabled={creatingAgent || !newAgent.name || !newAgent.description}
                    >
                        {creatingAgent ? 'Creating...' : 'Create Agent'}
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
}

export default Agents;

