import React, { useState, useEffect, useRef } from 'react';
import { Alert, Table, Badge, Spinner, Breadcrumb, Card, Row, Col, Modal, OverlayTrigger, Tooltip, Form, InputGroup } from 'react-bootstrap';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../utils/roleUtils';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import Button from '../../components/common/Button';
import { HiPlus, HiPlay, HiPencilAlt, HiTrash, HiEye } from 'react-icons/hi';
import workflowService from '../../services/workflowService';
import { format } from 'date-fns';
import { Link, useNavigate } from 'react-router-dom';
import { JsonView, allExpanded, darkStyles, defaultStyles } from 'react-json-view-lite';
import 'react-json-view-lite/dist/index.css';
import './styles.css';
import ConfirmationModal from '../../components/common/ConfirmationModal';
import ExecutionDetailsModal from '../../components/workflows/ExecutionDetailsModal';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import CreateWorkflowModal from '../../components/workflows/CreateWorkflowModal';
import ExecuteConfirmationModal from '../../components/workflows/ExecuteConfirmationModal';

function Workflows() {
    const { currentTeam, loading: teamLoading, selectedTeam } = useTeam();
    const { user } = useAuth();
    const canEditWorkflow = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    const [workflows, setWorkflows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [selectedWorkflow, setSelectedWorkflow] = useState(null);
    const [selectedRowId, setSelectedRowId] = useState(null);
    const [executions, setExecutions] = useState([]);
    const [loadingExecutions, setLoadingExecutions] = useState(false);
    const [selectedExecution, setSelectedExecution] = useState(null);
    const [showExecutionModal, setShowExecutionModal] = useState(false);
    const [showExecuteConfirm, setShowExecuteConfirm] = useState(false);
    const [workflowToExecute, setWorkflowToExecute] = useState(null);
    const [executing, setExecuting] = useState(false);
    const jsonViewerRef = useRef(null);
    const navigate = useNavigate();
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [workflowToDelete, setWorkflowToDelete] = useState(null);
    const [deleteError, setDeleteError] = useState(null);

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

    const loadWorkflowExecutions = async (workflowId) => {
        try {
            setLoadingExecutions(true);
            const response = await workflowService.getWorkflowExecutions(workflowId);
            if (response.success) {
                setExecutions(response.data);
            } else {
                console.error('Failed to load executions:', response.error);
            }
        } catch (err) {
            console.error('Error loading executions:', err);
        } finally {
            setLoadingExecutions(false);
        }
    };

    const handleRowClick = (workflow) => {
        setSelectedWorkflow(workflow);
        setSelectedRowId(workflow.id);
        loadWorkflowExecutions(workflow.id);
    };

    const handleStepClick = (stepIndex) => {
        // Find all step elements in the JSON view
        const jsonViewer = jsonViewerRef.current;
        if (!jsonViewer) return;

        // Get all text nodes that might contain step data
        const textNodes = jsonViewer.querySelectorAll('span');
        
        // Find the step that contains the target and intent
        const step = selectedWorkflow.request?.query?.workflow[stepIndex];
        if (!step) return;

        console.log('Looking for step:', step);

        // Find the element that contains this step's data
        const stepElement = Array.from(textNodes).find(element => {
            const text = element.textContent;
            console.log('Checking text:', text);
            return text.includes(step.target) || (step.intent && text.includes(step.intent));
        });

        console.log('Found element:', stepElement);

        if (stepElement) {
            // Find the parent element that contains the entire step object
            // Look for the parent that contains both step number and target
            let stepBlock = stepElement;
            while (stepBlock && !stepBlock.textContent.includes(`step:${stepIndex + 1}`)) {
                stepBlock = stepBlock.parentElement;
            }
            
            console.log('Found step block:', stepBlock);
            
            if (stepBlock) {
                // Scroll to the element
                stepBlock.scrollIntoView({ 
                    behavior: 'smooth', 
                    block: 'center',
                    inline: 'center'
                });
                
                // Add highlight class
                stepBlock.classList.add('highlight-step');
                
                // Remove highlight after animation
                setTimeout(() => {
                    stepBlock.classList.remove('highlight-step');
                }, 2000);
            }
        }
    };

    const handleExecutionClick = (execution) => {
        setSelectedExecution(execution);
        setShowExecutionModal(true);
    };

    const handleExecuteClick = (workflow, e) => {
        e.stopPropagation(); // Prevent row selection
        setWorkflowToExecute(workflow);
        setShowExecuteConfirm(true);
    };

    const handleExecuteConfirm = async () => {
        if (!workflowToExecute) return;

        try {
            setExecuting(true);
            const response = await workflowService.executeWorkflow(workflowToExecute.id);
            if (response.success) {
                showSuccessToast(response.message);
                // Refresh executions if this workflow is currently selected
                if (selectedWorkflow?.id === workflowToExecute.id) {
                    loadWorkflowExecutions(workflowToExecute.id);
                }
            } else {
                const errorMessage = response.details 
                    ? `${response.error}: ${response.details}`
                    : response.error;
                showErrorToast(errorMessage);
            }
        } catch (err) {
            console.error('Error executing workflow:', err);
            const errorMessage = err.response?.data?.message || 
                               err.response?.data?.error || 
                               err.message || 
                               'Failed to execute workflow';
            showErrorToast(errorMessage);
        } finally {
            setExecuting(false);
            setShowExecuteConfirm(false);
            setWorkflowToExecute(null);
        }
    };

    const handleCreateSuccess = () => {
        loadWorkflows();
    };

    const handleDeleteClick = (workflow) => {
        setWorkflowToDelete(workflow);
        setShowDeleteModal(true);
    };

    const handleDeleteConfirm = async () => {
        try {
            const response = await workflowService.deleteWorkflow(workflowToDelete.id);
            if (response.success) {
                // Remove the deleted workflow from the list
                setWorkflows(workflows.filter(w => w.id !== workflowToDelete.id));
                setShowDeleteModal(false);
                setWorkflowToDelete(null);
                setDeleteError(null);
                showSuccessToast('Workflow deleted successfully');
            } else {
                const errorMessage = response.error || 'Failed to delete workflow';
                setDeleteError(errorMessage);
                showErrorToast(errorMessage);
            }
        } catch (error) {
            const errorMessage = error.response?.data?.message || 
                               error.response?.data?.error || 
                               error.message || 
                               'Failed to delete workflow';
            setDeleteError(errorMessage);
            showErrorToast(errorMessage);
        }
    };

    if (teamLoading) {
        return <LoadingSpinner />;
    }

    if (!currentTeam && !isSuperAdmin(user)) {
        return (
          <div className="workflows-container">
            <Alert variant="info" className="no-team-alert">
              <Alert.Heading>No Team Access</Alert.Heading>
              <p>
                You currently don't have access to any team. Please contact your administrator 
                to get assigned to a team to view the workflows and access other features.
              </p>
            </Alert>
          </div>
        );
      }

    if (loading) {
        return (
            <div className="workflows-container">
                <div className="loading-container">
                    <Spinner animation="border" role="status">
                        <span className="visually-hidden">Loading...</span>
                    </Spinner>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="workflows-container">
                <Alert variant="danger">
                    <Alert.Heading>Error</Alert.Heading>
                    <p>{error}</p>
                </Alert>
            </div>
        );
    }

    const formatDate = (dateArray) => {
        if (!dateArray || dateArray.length < 7) return 'N/A';
        const [year, month, day, hour, minute, second] = dateArray;
        return format(new Date(year, month - 1, day, hour, minute, second), 'MMM d, yyyy HH:mm');
    };

    const getStepCount = (workflow) => {
        return workflow.request?.query?.workflow?.length || 0;
    };

    return (
        <div className="workflows-container">
            <Card className="mb-4 mx-1 p-0">
                <Card.Header className="d-flex justify-content-between align-items-center bg-light">
                    <Breadcrumb className="bg-light mb-0">
                        <Breadcrumb.Item 
                            linkAs={Link} 
                            linkProps={{ to: '/organizations' }}
                        >
                            {currentTeam?.organization?.name || 'Organization'}
                        </Breadcrumb.Item>
                        <Breadcrumb.Item 
                            linkAs={Link} 
                            linkProps={{ to: '/teams' }}
                        >
                            {currentTeam?.name || 'All Teams'}
                        </Breadcrumb.Item>
                        <Breadcrumb.Item active>
                            Workflows
                        </Breadcrumb.Item>
                    </Breadcrumb>

                    <OverlayTrigger
                        placement="top"
                        overlay={
                            <Tooltip id="create-workflow-tooltip">
                                {canEditWorkflow ? 'Create a new workflow' : 'Only team admins can create workflows'}
                            </Tooltip>
                        }
                    >
                        <div>
                            <Button 
                                variant="primary"
                                disabled={!canEditWorkflow}
                                onClick={() => setShowCreateModal(true)}
                            >
                                <HiPlus /> Create Workflow
                            </Button>
                        </div>
                    </OverlayTrigger>
                </Card.Header>
            </Card>

            <Table responsive hover className="workflows-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Name</th>
                        <th>Steps</th>
                        <th>Version</th>
                        <th>Last Updated</th>
                        <th>Created By</th>
                        <th>Status</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    {workflows.map((workflow) => (
                        <tr 
                            key={workflow.id} 
                            onClick={() => handleRowClick(workflow)}
                            className={`${selectedRowId === workflow.id ? 'selected-row' : ''}`}
                            style={{ cursor: 'pointer' }}
                        >
                            <td>
                                <code className="text-muted">{workflow.id}</code>
                            </td>
                            <td>{workflow.name}</td>
                            <td>
                                <Badge bg="info">
                                    {getStepCount(workflow)} steps
                                </Badge>
                            </td>
                            <td>
                                <Badge bg="secondary">
                                    v{workflow.version}
                                </Badge>
                            </td>
                            <td>{formatDate(workflow.updatedAt)}</td>
                            <td>{workflow.createdBy || 'N/A'}</td>
                            <td>
                                <Badge bg={workflow.public ? "success" : "warning"}>
                                    {workflow.public ? "Public" : "Private"}
                                </Badge>
                            </td>
                            <td>
                                <div className="action-buttons">
                                    <OverlayTrigger
                                        placement="top"
                                        overlay={
                                            <Tooltip id={`execute-tooltip-${workflow.id}`}>
                                                {workflow.public || currentTeam?.id === workflow.teamId
                                                    ? 'Execute this workflow' 
                                                    : 'This workflow is private and not accessible to your team'}
                                            </Tooltip>
                                        }
                                    >
                                        <div>
                                            <Button 
                                                variant="outline-primary" 
                                                size="sm" 
                                                className="me-2"
                                                onClick={(e) => handleExecuteClick(workflow, e)}
                                                disabled={!workflow.public && currentTeam?.id !== workflow.teamId}
                                            >
                                                <HiPlay className="me-1" /> Execute
                                            </Button>
                                        </div>
                                    </OverlayTrigger>
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
                                                className="me-2"
                                                onClick={(e) => {
                                                    e.stopPropagation(); // Prevent row selection
                                                    navigate(`/workflows/${workflow.id}/edit`);
                                                }}
                                                disabled={!canEditWorkflow && !workflow.public}
                                            >
                                                <HiPencilAlt className="me-1" /> Edit
                                            </Button>
                                        </div>
                                    </OverlayTrigger>
                                    <OverlayTrigger
                                        placement="top"
                                        overlay={
                                            <Tooltip id={`delete-tooltip-${workflow.id}`}>
                                                {canEditWorkflow ? 'Delete this workflow' : 'Only team admins can delete workflows'}
                                            </Tooltip>
                                        }
                                    >
                                        <div>
                                            <Button 
                                                variant="outline-danger" 
                                                size="sm"
                                                disabled={!canEditWorkflow}
                                                onClick={() => handleDeleteClick(workflow)}
                                            >
                                                <HiTrash className="me-1" /> Delete
                                            </Button>
                                        </div>
                                    </OverlayTrigger>
                                </div>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </Table>

            {selectedWorkflow && (
                <Card className="mt-4">
                    <Card.Header className="bg-light">
                        <h5 className="mb-0">Workflow Details: {selectedWorkflow.name}</h5>
                    </Card.Header>
                    <Card.Body className="p-0">
                        <Row className="g-0">
                            <Col md={6} className="border-end">
                                <div className="p-3">
                                    <h6 className="mb-3">JSON View</h6>
                                    <div className="json-viewer" ref={jsonViewerRef}>
                                        <JsonView 
                                            data={selectedWorkflow} 
                                            shouldExpandNode={allExpanded}
                                            style={defaultStyles}
                                        />
                                    </div>
                                </div>
                            </Col>
                            <Col md={6}>
                                <div className="p-3">
                                    <h6 className="mb-3">Steps</h6>
                                    <div className="steps-list">
                                        {selectedWorkflow.request?.query?.workflow?.map((step, index) => (
                                            <div 
                                                key={index}
                                                className="step-item"
                                                onClick={() => handleStepClick(index)}
                                            >
                                                <Badge bg="info" className="me-2">Step {index + 1}</Badge>
                                                <span className="step-target">{step.target}</span>
                                                {step.intent && (
                                                    <span className="step-intent ms-2 text-muted">
                                                        ({step.intent})
                                                    </span>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </Col>
                        </Row>
                    </Card.Body>
                </Card>
            )}

            {selectedWorkflow && (
                <Card className="mt-4">
                    <Card.Header className="bg-light">
                        <h5 className="mb-0">Execution Pipeline</h5>
                    </Card.Header>
                    <Card.Body>
                        {loadingExecutions ? (
                            <div className="text-center">
                                <Spinner animation="border" role="status">
                                    <span className="visually-hidden">Loading executions...</span>
                                </Spinner>
                            </div>
                        ) : (
                            <div className="execution-pipeline">
                                {executions.map((execution) => (
                                    <OverlayTrigger
                                        key={execution._id?.$oid || execution.id || execution._id}
                                        placement="top"
                                        overlay={
                                            <Tooltip id={`tooltip-${execution._id?.$oid || execution.id || execution._id}`}>
                                                <div>
                                                    Executed at: {formatDate(execution.executedAt?.$date || execution.executedAt)}
                                                    {execution.agentId && (
                                                        <><br/>Triggered by Agent: {execution.agentName} ({execution.agentId})</>
                                                    )}
                                                </div>
                                            </Tooltip>
                                        }
                                    >
                                        <div 
                                            className="execution-item"
                                            onClick={() => handleExecutionClick(execution)}
                                        >
                                            <div className="execution-status">
                                                <Badge bg={execution.status === 'SUCCESS' ? 'success' : 'danger'}>
                                                    {execution.status}
                                                </Badge>
                                                {execution.agentId ? (
                                                    <>
                                                        <Badge bg="primary" className="ms-1 agent-indicator">
                                                            A
                                                        </Badge>
                                                        <small className="text-muted ms-1">[Agent]</small>
                                                    </>
                                                ) : null}
                                            </div>
                                            <div className="execution-duration">
                                                {execution.durationMs?.$numberLong || execution.durationMs}ms
                                            </div>
                                            <div className="execution-result">
                                                {execution.response?.result?.finalResult}
                                            </div>
                                        </div>
                                    </OverlayTrigger>
                                ))}
                            </div>
                        )}
                    </Card.Body>
                </Card>
            )}

            {/* Execution Details Modal */}
            <ExecutionDetailsModal
                show={showExecutionModal}
                onHide={() => setShowExecutionModal(false)}
                execution={selectedExecution}
            />

            {/* Execute Confirmation Modal */}
            <ExecuteConfirmationModal
                show={showExecuteConfirm}
                onHide={() => setShowExecuteConfirm(false)}
                onConfirm={handleExecuteConfirm}
                workflow={workflowToExecute}
                executing={executing}
            />

            {/* Create Workflow Modal */}
            <CreateWorkflowModal
                show={showCreateModal}
                onHide={() => setShowCreateModal(false)}
                onSuccess={handleCreateSuccess}
            />

            {/* Delete Confirmation Modal */}
            <Modal show={showDeleteModal} onHide={() => setShowDeleteModal(false)}>
                <Modal.Header closeButton>
                    <Modal.Title>Confirm Delete</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {deleteError ? (
                        <div className="alert alert-danger">{deleteError}</div>
                    ) : (
                        <p>Are you sure you want to delete this workflow? This action cannot be undone.</p>
                    )}
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowDeleteModal(false)}>
                        Cancel
                    </Button>
                    <Button variant="danger" onClick={handleDeleteConfirm}>
                        Delete
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
}

export default Workflows;