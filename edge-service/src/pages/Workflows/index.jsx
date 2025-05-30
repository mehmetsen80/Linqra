import React, { useState, useEffect, useRef } from 'react';
import { Alert, Table, Badge, Spinner, Breadcrumb, Card, Row, Col, Modal, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin } from '../../utils/roleUtils';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import Button from '../../components/common/Button';
import { HiPlus, HiChevronRight, HiPencilAlt, HiTrash } from 'react-icons/hi';
import workflowService from '../../services/workflowService';
import { format } from 'date-fns';
import { Link } from 'react-router-dom';
import { JsonView, allExpanded, darkStyles, defaultStyles } from 'react-json-view-lite';
import 'react-json-view-lite/dist/index.css';
import './styles.css';

function Workflows() {
    const { currentTeam, loading: teamLoading, selectedTeam } = useTeam();
    const { user } = useAuth();
    const [workflows, setWorkflows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [selectedWorkflow, setSelectedWorkflow] = useState(null);
    const [selectedRowId, setSelectedRowId] = useState(null);
    const [executions, setExecutions] = useState([]);
    const [loadingExecutions, setLoadingExecutions] = useState(false);
    const [selectedExecution, setSelectedExecution] = useState(null);
    const [showExecutionModal, setShowExecutionModal] = useState(false);
    const jsonViewerRef = useRef(null);

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

                    <Button variant="primary">
                        <HiPlus /> Create Workflow
                    </Button>
                </Card.Header>
            </Card>

            <Table responsive hover className="workflows-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Name</th>
                        <th>Description</th>
                        <th>Steps</th>
                        <th>Version</th>
                        <th>Last Updated</th>
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
                            <td>{workflow.description || 'No description'}</td>
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
                            <td>
                                <Badge bg={workflow.public ? "success" : "warning"}>
                                    {workflow.public ? "Public" : "Private"}
                                </Badge>
                            </td>
                            <td>
                                <div className="action-buttons">
                                    <Button variant="outline-primary" size="sm" className="me-2">
                                        Execute
                                    </Button>
                                    <Button variant="outline-secondary" size="sm" className="me-2">
                                        Edit
                                    </Button>
                                    <Button variant="outline-danger" size="sm">
                                        Delete
                                    </Button>
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
                                                Executed at: {formatDate(execution.executedAt?.$date || execution.executedAt)}
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
            <Modal 
                show={showExecutionModal} 
                onHide={() => setShowExecutionModal(false)}
                size="lg"
                centered
            >
                <Modal.Header closeButton>
                    <Modal.Title>Execution Details</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {selectedExecution && (
                        <div className="execution-details">
                            <div className="mb-3">
                                <h6>Status</h6>
                                <Badge bg={selectedExecution.status === 'SUCCESS' ? 'success' : 'danger'}>
                                    {selectedExecution.status}
                                </Badge>
                            </div>
                            <div className="mb-3">
                                <h6>Duration</h6>
                                <p>{selectedExecution.durationMs?.$numberLong || selectedExecution.durationMs}ms</p>
                            </div>
                            <div className="mb-3">
                                <h6>Executed At</h6>
                                <p>{formatDate(selectedExecution.executedAt?.$date || selectedExecution.executedAt)}</p>
                            </div>
                            <div className="mb-3">
                                <h6>Final Result</h6>
                                <p className="final-result">
                                    {selectedExecution.response?.result?.finalResult}
                                </p>
                            </div>

                            {/* Step Pipeline */}
                            <div className="mb-4">
                                <h6>Step Pipeline</h6>
                                <div className="step-pipeline">
                                    {selectedExecution.response?.result?.steps?.map((step, index) => {
                                        const stepMetadata = selectedExecution.response?.metadata?.workflowMetadata?.find(
                                            meta => meta.step === step.step
                                        );
                                        return (
                                            <OverlayTrigger
                                                key={step.step}
                                                placement="top"
                                                overlay={
                                                    <Tooltip id={`tooltip-step-${step.step}`}>
                                                        Duration: {stepMetadata?.durationMs?.$numberLong || stepMetadata?.durationMs}ms
                                                        <br />
                                                        Executed at: {formatDate(stepMetadata?.executedAt?.$date || stepMetadata?.executedAt)}
                                                    </Tooltip>
                                                }
                                            >
                                                <div className="step-pipeline-item">
                                                    <div className="step-pipeline-status">
                                                        <Badge bg={stepMetadata?.status === 'success' ? 'success' : 'danger'}>
                                                            Step {step.step}
                                                        </Badge>
                                                    </div>
                                                    <div className="step-pipeline-target">
                                                        {step.target}
                                                    </div>
                                                    <div className="step-pipeline-result">
                                                        {step.result?.content || 
                                                         step.result?.fullName || 
                                                         JSON.stringify(step.result).slice(0, 50) + '...'}
                                                    </div>
                                                </div>
                                            </OverlayTrigger>
                                        );
                                    })}
                                </div>
                            </div>

                            <div>
                                <h6>Full Execution Data</h6>
                                <div className="json-viewer" style={{ height: '400px' }}>
                                    <JsonView 
                                        data={selectedExecution} 
                                        shouldExpandNode={allExpanded}
                                        style={defaultStyles}
                                    />
                                </div>
                            </div>
                        </div>
                    )}
                </Modal.Body>
            </Modal>
        </div>
    );
}

export default Workflows;