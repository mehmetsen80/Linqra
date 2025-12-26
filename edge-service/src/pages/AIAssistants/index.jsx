import React, { useState, useEffect } from 'react';
import { Alert, Table, Badge, Breadcrumb, Card, Form, Spinner } from 'react-bootstrap';
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
                            <HiChatAlt className="fa-3x text-muted mb-3" style={{ fontSize: '3rem' }} />
                            <h5 className="text-muted">No AI assistants found</h5>
                            <p className="text-muted">
                                {canEditAssistant
                                    ? 'Create your first AI assistant to get started'
                                    : 'No AI assistants have been created yet'}
                            </p>
                        </div>
                    ) : (
                        <Table hover responsive className="ai-assistants-table">
                            <thead>
                                <tr>
                                    <th>Name</th>
                                    <th>Description</th>
                                    <th>Category</th>
                                    <th>Status</th>
                                    <th>Access</th>
                                    <th>Tasks</th>
                                    <th>Model</th>
                                    <th>Created</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {assistants.map((assistant) => (
                                    <tr
                                        key={assistant.id}
                                        onClick={() => handleViewAssistant(assistant)}
                                        style={{ cursor: 'pointer' }}
                                    >
                                        <td>
                                            <div className="assistant-name">
                                                <HiChatAlt className="me-2" />
                                                {assistant.name}
                                            </div>
                                        </td>
                                        <td>
                                            <div className="assistant-description">
                                                {assistant.description || 'No description'}
                                            </div>
                                        </td>
                                        <td>
                                            <Badge bg={
                                                assistant.category === 'REVIEW_DOC' ? 'primary' : 'info'
                                            }>
                                                {assistant.category || 'CHAT'}
                                            </Badge>
                                        </td>
                                        <td>
                                            <Badge bg={
                                                assistant.status === 'ACTIVE' ? 'success' :
                                                    assistant.status === 'INACTIVE' ? 'secondary' :
                                                        'warning'
                                            }>
                                                {assistant.status || 'DRAFT'}
                                            </Badge>
                                        </td>
                                        <td>
                                            <Badge bg={
                                                assistant.accessControl?.type === 'PUBLIC' ? 'info' : 'secondary'
                                            }>
                                                {assistant.accessControl?.type === 'PUBLIC' ? 'Public' : 'Private'}
                                            </Badge>
                                        </td>
                                        <td>
                                            <Badge bg="info">
                                                {taskCounts[assistant.id] !== undefined ? taskCounts[assistant.id] : 0}
                                                {taskCounts[assistant.id] === 1 ? ' task' : ' tasks'}
                                            </Badge>
                                        </td>
                                        <td>
                                            <span className="text-muted small">
                                                {assistant.defaultModel?.modelName || 'N/A'}
                                            </span>
                                        </td>
                                        <td>
                                            {formatDate(assistant.createdAt)}
                                        </td>
                                        <td onClick={(e) => e.stopPropagation()}>
                                            <div className="action-buttons d-flex gap-2">
                                                <Button
                                                    variant="link"
                                                    size="sm"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        handleViewAssistant(assistant);
                                                    }}
                                                    title="View & Chat"
                                                >
                                                    <HiEye />
                                                </Button>
                                                {canEditAssistant && (
                                                    <>
                                                        <Button
                                                            variant="link"
                                                            size="sm"
                                                            onClick={(e) => handleEdit(assistant, e)}
                                                            title="Edit Assistant"
                                                        >
                                                            <HiPencilAlt />
                                                        </Button>
                                                        <Button
                                                            variant="link"
                                                            size="sm"
                                                            onClick={(e) => handleDelete(assistant, e)}
                                                            title="Delete Assistant"
                                                            className="text-danger"
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

