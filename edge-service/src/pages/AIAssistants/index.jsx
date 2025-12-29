import React, { useState, useEffect } from 'react';
import { Alert, Badge, Breadcrumb, Card, Form, Spinner, Row, Col } from 'react-bootstrap';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../utils/roleUtils';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import Button from '../../components/common/Button';
import { HiPlus, HiPencilAlt, HiTrash, HiChatAlt, HiEye } from 'react-icons/hi';
import aiAssistantService from '../../services/aiAssistantService';
import agentService from '../../services/agentService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import { Link, useNavigate } from 'react-router-dom';
import { formatDate } from '../../utils/dateUtils';
import './styles.css';
import CreateAIAssistantModal from '../../components/aiassistants/CreateAIAssistantModal';
import EditAIAssistantModal from '../../components/aiassistants/EditAIAssistantModal';
import ConfirmationModal from '../../components/common/ConfirmationModal';
import Footer from '../../components/common/Footer';
import { linqLlmModelService } from '../../services/linqLlmModelService';

function AIAssistants() {
    const { currentTeam, loading: teamLoading } = useTeam();
    const { user } = useAuth();
    const canEditAssistant = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    const [assistants, setAssistants] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [taskCounts, setTaskCounts] = useState({});
    const navigate = useNavigate();
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showEditModal, setShowEditModal] = useState(false);
    const [editingAssistant, setEditingAssistant] = useState(null);
    const [availableAgentTasks, setAvailableAgentTasks] = useState([]);
    const [availableModels, setAvailableModels] = useState([]);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [assistantToDelete, setAssistantToDelete] = useState(null);
    const [deleting, setDeleting] = useState(false);

    useEffect(() => {
        if (currentTeam) {
            loadAssistants();
            loadAvailableAgentTasks();
            loadAvailableModels();
        }
    }, [currentTeam]);

    const loadAssistants = async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await aiAssistantService.getAllAssistants(currentTeam.id);
            if (response.success) {
                setAssistants(response.data);

                // Fetch task counts for each assistant
                const counts = {};
                await Promise.all(
                    response.data.map(async (assistant) => {
                        const taskCount = assistant.selectedTasks?.length || 0;
                        counts[assistant.id] = taskCount;
                    })
                );
                setTaskCounts(counts);
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load AI assistants');
            console.error('Error loading AI assistants:', err);
        } finally {
            setLoading(false);
        }
    };

    const loadAvailableAgentTasks = async () => {
        try {
            // Get all agents for the team
            const agentsResponse = await agentService.getAgentsByTeam(currentTeam.id);
            if (agentsResponse.success && agentsResponse.data) {
                // Get tasks for each agent and flatten
                const allTasksPromises = agentsResponse.data.map(agent =>
                    agentService.getTasksByAgent(agent.id)
                        .then(response => {
                            if (response.success && response.data) {
                                return response.data.map(task => ({
                                    value: task.id,
                                    label: `${task.name} (${agent.name})`,
                                    task: task,
                                    agent: agent
                                }));
                            }
                            return [];
                        })
                        .catch(err => {
                            console.error(`Error loading tasks for agent ${agent.id}:`, err);
                            return [];
                        })
                );

                const allTasksArrays = await Promise.all(allTasksPromises);
                const allTasks = allTasksArrays.flat();
                setAvailableAgentTasks(allTasks);
            }
        } catch (err) {
            console.error('Error loading available agent tasks:', err);
        }
    };

    const loadAvailableModels = async () => {
        try {
            // Load ONLY team-specific models for assistant default model selection
            // Load ONLY team-specific models for assistant default model selection

            // We want all configured models for the team, not just chat ones, but typically we filter for chat capable ones
            // linqLlmModelService.getTeamConfiguration returns a list of LinqLlmModel objects
            const teamModels = await linqLlmModelService.getTeamConfiguration(currentTeam.id);

            // Map the team models to the format expected by the dropdown (similar to LlmModel)
            // LlmModel has: provider, modelName, modelCategory, etc.
            // LinqLlmModel has the same fields.
            // We should filter for likely chat models if needed, or rely on the backend to provide valid ones.
            // For now, let's include all active team models.

            // Filter for active models that are chat-capable (exclude embeddings)
            const activeTeamModels = teamModels.filter(m =>
                m.active !== false &&
                m.modelCategory?.toLowerCase().includes('chat')
            );
            setAvailableModels(activeTeamModels);
        } catch (err) {
            console.error('Error loading available models:', err);
        }
    };

    const handleCreateSuccess = () => {
        setShowCreateModal(false);
        loadAssistants();
    };

    const handleEdit = (assistant, e) => {
        e.stopPropagation();
        setEditingAssistant(assistant);
        setShowEditModal(true);
    };

    const handleEditSuccess = () => {
        setShowEditModal(false);
        setEditingAssistant(null);
        loadAssistants();
    };

    const handleDelete = (assistant, e) => {
        e.stopPropagation();
        setAssistantToDelete(assistant);
        setShowDeleteModal(true);
    };

    const handleDeleteConfirm = async () => {
        if (!assistantToDelete) return;

        try {
            setDeleting(true);
            const response = await aiAssistantService.deleteAssistant(assistantToDelete.id);
            if (response.success) {
                showSuccessToast('AI Assistant deleted successfully');
                setShowDeleteModal(false);
                setAssistantToDelete(null);
                loadAssistants();
            } else {
                showErrorToast(response.error || 'Failed to delete AI assistant');
            }
        } catch (err) {
            console.error('Error deleting AI assistant:', err);
            showErrorToast('Failed to delete AI assistant');
        } finally {
            setDeleting(false);
        }
    };

    const handleViewAssistant = (assistant) => {
        navigate(`/ai-assistants/${assistant.id}`);
    };


    if (teamLoading || loading) {
        return <LoadingSpinner />;
    }

    return (
        <div className="ai-assistants-page">
            {/* Breadcrumb */}
            <Card className="breadcrumb-card mb-3">
                <Card.Body>
                    <Breadcrumb>
                        <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/dashboard' }}>
                            Home
                        </Breadcrumb.Item>
                        <Breadcrumb.Item
                            onClick={() => navigate(`/teams/${currentTeam?.id}`)}
                            style={{ cursor: 'pointer' }}
                        >
                            {currentTeam?.name || 'Team'}
                        </Breadcrumb.Item>
                        <Breadcrumb.Item active>AI Assistants</Breadcrumb.Item>
                    </Breadcrumb>
                </Card.Body>
            </Card>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError(null)}>
                    {error}
                </Alert>
            )}

            <Card className="ai-assistants-table-card">
                <Card.Header>
                    <div className="d-flex align-items-center justify-content-between">
                        <h5 className="mb-0">AI Assistants{currentTeam?.name ? ` - ${currentTeam.name}` : ''}</h5>
                        {canEditAssistant && (
                            <Button
                                variant="primary"
                                onClick={() => setShowCreateModal(true)}
                            >
                                <HiPlus /> Create AI Assistant
                            </Button>
                        )}
                    </div>
                </Card.Header>
                <Card.Body>

                    {assistants.length === 0 ? (
                        <div className="text-center py-5">
                            <HiChatAlt className="fa-3x mb-3" style={{ fontSize: '3rem', color: '#ed7534' }} />
                            <h5 className="text-muted">No AI assistants found</h5>
                            <p className="text-muted">
                                {canEditAssistant
                                    ? 'Create your first AI assistant to get started'
                                    : 'No AI assistants have been created yet'}
                            </p>
                        </div>
                    ) : (
                        <Row xs={1} md={2} lg={3} className="g-4">
                            {assistants.map((assistant) => (
                                <Col key={assistant.id}>
                                    <Card
                                        className="h-100 shadow assistant-card border p-2"
                                        onClick={() => handleViewAssistant(assistant)}
                                        style={{ cursor: 'pointer', transition: 'transform 0.2s' }}
                                    >
                                        <Card.Header className="bg-white border-bottom-0 pt-2 pb-0">
                                            <div className="d-flex justify-content-end gap-1 mb-1">
                                                <Badge bg={
                                                    assistant.accessControl?.type === 'PUBLIC' ? 'success' : 'secondary'
                                                } style={{ fontSize: '0.65rem' }}>
                                                    {assistant.accessControl?.type === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE'}
                                                </Badge>
                                                <Badge bg={
                                                    assistant.category === 'REVIEW_DOC' ? 'primary' : 'info'
                                                } style={{ fontSize: '0.65rem' }}>
                                                    {assistant.category || 'CHAT'}
                                                </Badge>
                                                <Badge bg={
                                                    assistant.status === 'ACTIVE' ? 'success' :
                                                        assistant.status === 'INACTIVE' ? 'secondary' :
                                                            'warning'
                                                } style={{ fontSize: '0.65rem' }}>
                                                    {assistant.status || 'DRAFT'}
                                                </Badge>
                                            </div>
                                            <div className="d-flex align-items-center">
                                                <div className="rounded-circle me-2 d-flex align-items-center justify-content-center" style={{ backgroundColor: '#e9ecef', width: '40px', height: '40px', minWidth: '40px' }}>
                                                    <HiChatAlt size={20} style={{ color: '#ed7534' }} />
                                                </div>
                                                <h6 className="mb-0 fw-bold" title={assistant.name}>
                                                    {assistant.name}
                                                </h6>
                                            </div>
                                        </Card.Header>
                                        <Card.Body className="pt-2 pb-0 d-flex flex-column">
                                            <p className="text-muted small mb-3 flex-grow-1">
                                                {assistant.description || 'No description provided.'}
                                            </p>

                                            <div className="d-flex flex-wrap gap-2 mb-2">
                                                <Badge bg="light" text="dark" className="border">
                                                    {assistant.defaultModel?.modelName || 'N/A'}
                                                </Badge>
                                                <Badge bg="light" text="dark" className="border">
                                                    {taskCounts[assistant.id] !== undefined ? taskCounts[assistant.id] : 0} Tasks
                                                </Badge>
                                            </div>
                                        </Card.Body>
                                        <Card.Footer className="bg-white border-top-0 pt-0 pb-3 mt-auto">
                                            <hr className="my-2" />
                                            <div className="d-flex justify-content-between align-items-center">
                                                <div className="d-flex flex-column">
                                                    <span className="text-muted small" style={{ fontSize: '0.7rem' }}>
                                                        Created: {formatDate(assistant.createdAt)}
                                                    </span>
                                                </div>

                                                <div className="d-flex gap-1" onClick={(e) => e.stopPropagation()}>
                                                    <Button
                                                        variant="light"
                                                        size="sm"
                                                        className="btn-icon-only rounded-circle"
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            handleViewAssistant(assistant);
                                                        }}
                                                        title="View & Chat"
                                                        style={{ width: '32px', height: '32px', padding: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                                    >
                                                        <HiEye />
                                                    </Button>
                                                    {canEditAssistant && (
                                                        <>
                                                            <Button
                                                                variant="light"
                                                                size="sm"
                                                                className="btn-icon-only rounded-circle"
                                                                onClick={(e) => handleEdit(assistant, e)}
                                                                title="Edit"
                                                                style={{ width: '32px', height: '32px', padding: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                                            >
                                                                <HiPencilAlt />
                                                            </Button>
                                                            <Button
                                                                variant="light"
                                                                size="sm"
                                                                className="btn-icon-only rounded-circle text-danger"
                                                                onClick={(e) => handleDelete(assistant, e)}
                                                                title="Delete"
                                                                style={{ width: '32px', height: '32px', padding: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                                            >
                                                                <HiTrash />
                                                            </Button>
                                                        </>
                                                    )}
                                                </div>
                                            </div>
                                        </Card.Footer>
                                    </Card>
                                </Col>
                            ))}
                        </Row>
                    )}
                </Card.Body>
            </Card>

            <CreateAIAssistantModal
                show={showCreateModal}
                onHide={() => setShowCreateModal(false)}
                onSuccess={handleCreateSuccess}
                availableAgentTasks={availableAgentTasks}
                availableModels={availableModels}
                teamId={currentTeam?.id}
            />

            {editingAssistant && (
                <EditAIAssistantModal
                    show={showEditModal}
                    onHide={() => {
                        setShowEditModal(false);
                        setEditingAssistant(null);
                    }}
                    assistant={editingAssistant}
                    onSuccess={handleEditSuccess}
                    availableAgentTasks={availableAgentTasks}
                    availableModels={availableModels}
                />
            )}

            <ConfirmationModal
                show={showDeleteModal}
                onHide={() => {
                    setShowDeleteModal(false);
                    setAssistantToDelete(null);
                }}
                onConfirm={handleDeleteConfirm}
                title="Delete AI Assistant"
                message={`Are you sure you want to delete "${assistantToDelete?.name}"? This will also delete all associated conversations. This action cannot be undone.`}
                variant="danger"
                confirmLabel={deleting ? 'Deleting...' : 'Delete'}
                cancelLabel="Cancel"
                disabled={deleting}
            />
            <Footer />
        </div>
    );
}

export default AIAssistants;

