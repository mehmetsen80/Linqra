import React, { useState } from 'react';
import { Modal, Badge, OverlayTrigger, Tooltip, Row, Col, Tabs, Tab } from 'react-bootstrap';
import { JsonView, allExpanded, defaultStyles } from 'react-json-view-lite';
import 'react-json-view-lite/dist/index.css';
import { format } from 'date-fns';
import ReactMarkdown from 'react-markdown';

const ExecutionDetailsModal = ({
    show,
    onHide,
    execution,
    formatDate = (date) => {
        if (!date) return 'N/A';
        try {
            let dateObj;
            if (Array.isArray(date)) {
                dateObj = new Date(date[0], date[1] - 1, date[2], date[3], date[4], date[5]);
            } else {
                dateObj = new Date(date);
            }
            if (isNaN(dateObj.getTime())) return 'N/A';
            return format(dateObj, 'MMM d, yyyy HH:mm');
        } catch (err) {
            console.error('Error formatting date:', err);
            return 'N/A';
        }
    }
}) => {
    const [activeTab, setActiveTab] = useState('overview');
    const [selectedStepIndex, setSelectedStepIndex] = useState(0);

    if (!execution) return null;

    const getStatusVariant = (status) => {
        switch (String(status).toLowerCase()) {
            case 'success':
            case 'completed':
                return 'success';
            case 'skipped':
                return 'info';
            case 'queued':
            case 'pending':
                return 'warning';
            case 'running':
                return 'primary';
            case 'error':
            case 'failed':
                return 'danger';
            default:
                return 'secondary';
        }
    };

    const steps = execution.response?.result?.steps || [];
    const workflowMetadata = execution.response?.metadata?.workflowMetadata || [];
    const requestSteps = execution.request?.query?.workflow || [];

    const selectedStep = steps[selectedStepIndex];
    const selectedStepMeta = workflowMetadata.find(m => m.step === selectedStep?.step);
    const selectedStepConfig = requestSteps.find(s => s.step === selectedStep?.step);

    return (
        <Modal
            show={show}
            onHide={onHide}
            size="xl"
            centered
            dialogClassName="modal-90w"
        >
            <style>
                {`
                    .tooltip {
                        z-index: 10001 !important;
                    }
                    .text-linqra-primary {
                        color: #ed7534 !important;
                    }
                `}
            </style>
            <Modal.Header closeButton>
                <Modal.Title className="d-flex align-items-center gap-2">
                    <i className="fas fa-terminal text-primary"></i>
                    Workflow Execution Details
                </Modal.Title>
            </Modal.Header>
            <Modal.Body style={{ maxHeight: 'calc(90vh - 120px)', overflowY: 'auto' }}>
                <Tabs
                    activeKey={activeTab}
                    onSelect={(k) => setActiveTab(k)}
                    className="mb-3"
                >
                    <Tab eventKey="overview" title={<><i className="fas fa-info-circle me-2"></i>Overview</>}>
                        <div className="execution-details">
                            <Row className="mb-4">
                                <Col md={3}>
                                    <div className="p-3 border rounded bg-light shadow-sm">
                                        <h6 className="text-muted small text-uppercase">Status</h6>
                                        <Badge bg={execution.status === 'SUCCESS' ? 'success' : 'danger'} className="px-3 py-2 fs-6">
                                            {execution.status}
                                        </Badge>
                                    </div>
                                </Col>
                                <Col md={3}>
                                    <div className="p-3 border rounded bg-light shadow-sm">
                                        <h6 className="text-muted small text-uppercase">Duration</h6>
                                        <p className="mb-0 fw-bold fs-5">{execution.durationMs?.$numberLong || execution.durationMs}ms</p>
                                    </div>
                                </Col>
                                <Col md={3}>
                                    <div className="p-3 border rounded bg-light shadow-sm">
                                        <h6 className="text-muted small text-uppercase">Executed At</h6>
                                        <p className="mb-0 fw-bold">{formatDate(execution.executedAt?.$date || execution.executedAt)}</p>
                                    </div>
                                </Col>
                                <Col md={3}>
                                    <div className="p-3 border rounded bg-light shadow-sm">
                                        <h6 className="text-muted small text-uppercase">Source</h6>
                                        <div className="trigger-indicator">
                                            {execution.agentId ? (
                                                <OverlayTrigger
                                                    placement="top"
                                                    overlay={
                                                        <Tooltip id={`agent-tooltip-${execution.id}`} style={{ zIndex: 10000 }}>
                                                            <strong>{execution.agentName || 'Unknown Agent'}</strong>
                                                            <br />
                                                            ID: {execution.agentId}
                                                        </Tooltip>
                                                    }
                                                >
                                                    <Badge bg="secondary" className="d-flex align-items-center gap-2 p-2" style={{ cursor: 'pointer' }}>
                                                        <i className="fas fa-robot"></i>
                                                        Agent Context
                                                    </Badge>
                                                </OverlayTrigger>
                                            ) : (
                                                <Badge bg="dark" className="d-flex align-items-center gap-1 p-2">
                                                    <i className="fas fa-user"></i>
                                                    Manual Call
                                                </Badge>
                                            )}
                                        </div>
                                    </div>
                                </Col>
                            </Row>

                            {/* Workflow Execution Summary (Top Level Result) */}
                            {execution.response?.result?.finalResult && (
                                <div className="mb-4">
                                    <h6 className="d-flex align-items-center gap-2 mb-3">
                                        <i className="fas fa-check-circle text-success"></i>
                                        Workflow Execution Summary
                                    </h6>
                                    <div className="final-result p-3 border rounded bg-white shadow-sm">
                                        <ReactMarkdown>
                                            {execution.response.result.finalResult}
                                        </ReactMarkdown>
                                    </div>
                                </div>
                            )}

                            {/* Step Pipeline */}
                            <div className="mb-4">
                                <h6 className="d-flex align-items-center gap-2 mb-3">
                                    <i className="fas fa-project-diagram text-primary"></i>
                                    Step Pipeline
                                </h6>
                                <div className="step-pipeline d-flex flex-wrap gap-2 p-3 bg-light border rounded">
                                    {steps.map((step, index) => {
                                        const stepMetadata = workflowMetadata.find(meta => meta.step === step.step);
                                        const stepConfig = requestSteps.find(s => s.step === step.step);
                                        // Use step.status if available (new backend), otherwise fallback to metadata
                                        const currentStatus = step.status || stepMetadata?.status;
                                        const variant = getStatusVariant(currentStatus);
                                        const isActive = selectedStepIndex === index;

                                        return (
                                            <OverlayTrigger
                                                key={step.step}
                                                placement="top"
                                                overlay={
                                                    <Tooltip id={`tooltip-step-${step.step}`} style={{ zIndex: 10001 }}>
                                                        <div className="text-start p-1">
                                                            <div className="fw-bold border-bottom border-light pb-1 mb-1">
                                                                Step {step.step}: {step.description || stepConfig?.description || `Step ${step.step}`}
                                                            </div>
                                                            <div className="small mb-1">
                                                                <span className="text-linqra-primary fw-bold">{stepConfig?.action?.toUpperCase() || 'UNKNOWN'}</span>: <code className="text-light-info">{stepConfig?.intent || 'No Intent'}</code>
                                                            </div>
                                                            <div className="small opacity-75">
                                                                Status: <span className={`text-${variant}`}>{currentStatus || 'Unknown'}</span>
                                                                <br />
                                                                Duration: {stepMetadata?.durationMs?.$numberLong || stepMetadata?.durationMs || 0}ms
                                                                <br />
                                                                Target: <span className="text-info">{step.target}</span>
                                                            </div>
                                                        </div>
                                                    </Tooltip>
                                                }
                                            >
                                                <div
                                                    className={`step-pipeline-item p-2 rounded border shadow-sm d-flex flex-column align-items-center justify-content-center cursor-pointer ${isActive ? 'ring-2 ring-primary border-primary bg-white' : 'bg-white'}`}
                                                    style={{ minWidth: '100px', cursor: 'pointer', transition: 'all 0.2s' }}
                                                    onClick={() => setSelectedStepIndex(index)}
                                                >
                                                    <Badge bg={variant} className="mb-1 w-100 uppercase">
                                                        Step {step.step}
                                                    </Badge>
                                                    <div className="small text-truncate w-100 text-center text-muted" title={step.target}>
                                                        {step.target}
                                                    </div>
                                                    <div className="x-small text-truncate w-100 text-center text-linqra-primary font-monospace fw-bold opacity-75" title={stepConfig?.action} style={{ fontSize: '0.65rem' }}>
                                                        {stepConfig?.action?.toUpperCase() || 'UNKNOWN'}
                                                    </div>
                                                </div>
                                            </OverlayTrigger>
                                        );
                                    })}
                                </div>
                            </div>

                            {/* Detailed Step Results */}
                            {selectedStep && (
                                <div className="step-details-inspector p-4 border rounded shadow-sm">
                                    <div className="d-flex justify-content-between align-items-center mb-3 border-bottom pb-2">
                                        <h5 className="mb-0">
                                            Step {selectedStep.step}: {selectedStep.description || selectedStepConfig?.description || 'No Description'}
                                        </h5>
                                        <Badge bg={getStatusVariant(selectedStep.status || selectedStepMeta?.status)} className="p-2 fs-6">
                                            {(selectedStep.status || selectedStepMeta?.status)?.toUpperCase() || 'UNKNOWN'}
                                        </Badge>
                                    </div>

                                    <Row>
                                        <Col md={6}>
                                            <h6 className="small text-muted text-uppercase mb-2">Intent & Action</h6>
                                            <div className="mb-3 p-2 bg-dark text-light rounded font-monospace small">
                                                <span className="text-warning">{selectedStepConfig?.action?.toUpperCase() || 'UNKNOWN'}</span> {selectedStepConfig?.intent || 'No Intent'}
                                            </div>

                                            {selectedStepConfig?.condition && (
                                                <div className="mb-3">
                                                    <h6 className="small text-muted text-uppercase mb-1">Condition</h6>
                                                    <Badge bg="secondary" className="font-monospace px-2 py-1">
                                                        {selectedStepConfig.condition}
                                                    </Badge>
                                                </div>
                                            )}
                                        </Col>
                                        <Col md={6}>
                                            <h6 className="small text-muted text-uppercase mb-2">Execution Metadata</h6>
                                            <ul className="list-unstyled small">
                                                <li><strong>Executed At:</strong> {formatDate(selectedStepMeta?.executedAt?.$date || selectedStepMeta?.executedAt)}</li>
                                                <li><strong>Duration:</strong> {selectedStepMeta?.durationMs?.$numberLong || selectedStepMeta?.durationMs || 0}ms</li>
                                                <li><strong>Async:</strong> {selectedStep.isAsync ? 'Yes' : 'No'}</li>
                                            </ul>
                                        </Col>
                                    </Row>

                                    <div className="mt-3">
                                        <Tabs defaultActiveKey="result" className="small-tabs">
                                            <Tab eventKey="result" title="Result">
                                                <div className="mt-3 p-3 bg-light rounded border" style={{ minHeight: '100px', maxHeight: '400px', overflowY: 'auto' }}>
                                                    {(selectedStep.status === 'skipped' || selectedStepMeta?.status === 'skipped') ? (
                                                        <div className="text-center py-4">
                                                            <i className="fas fa-forward fa-3x text-muted mb-3"></i>
                                                            <p className="mb-1 text-muted">This step was skipped because the condition was not met.</p>
                                                            {selectedStep.result?.condition && (
                                                                <div className="mt-2">
                                                                    <small className="text-muted">Condition: </small>
                                                                    <code className="text-primary">{selectedStep.result.condition}</code>
                                                                </div>
                                                            )}
                                                            <hr className="w-25 mx-auto" />
                                                            {selectedStep.result && Object.keys(selectedStep.result).length > 0 && (
                                                                <div className="json-result text-start opacity-75">
                                                                    <JsonView
                                                                        data={selectedStep.result}
                                                                        shouldExpandNode={allExpanded}
                                                                        style={defaultStyles}
                                                                    />
                                                                </div>
                                                            )}
                                                        </div>
                                                    ) : selectedStep.result ? (
                                                        <div className="json-result">
                                                            <JsonView
                                                                data={selectedStep.result}
                                                                shouldExpandNode={allExpanded}
                                                                style={defaultStyles}
                                                            />
                                                        </div>
                                                    ) : (
                                                        <p className="text-muted italic">No result data available for this step.</p>
                                                    )}
                                                </div>
                                            </Tab>
                                            <Tab eventKey="payload" title="Request Payload">
                                                <div className="mt-3 p-3 bg-light rounded border" style={{ minHeight: '100px', maxHeight: '400px', overflowY: 'auto' }}>
                                                    {selectedStepConfig?.payload ? (
                                                        <JsonView
                                                            data={selectedStepConfig.payload}
                                                            shouldExpandNode={allExpanded}
                                                            style={defaultStyles}
                                                        />
                                                    ) : (
                                                        <p className="text-muted italic">No request payload sent.</p>
                                                    )}
                                                </div>
                                            </Tab>
                                        </Tabs>
                                    </div>
                                </div>
                            )}
                        </div>
                    </Tab>

                    <Tab eventKey="fullData" title={<><i className="fas fa-code me-2"></i>Raw JSON</>}>
                        <div className="json-viewer border rounded bg-light" style={{ height: 'calc(90vh - 240px)', overflow: 'auto' }}>
                            <JsonView
                                data={execution}
                                shouldExpandNode={allExpanded}
                                style={defaultStyles}
                            />
                        </div>
                    </Tab>
                </Tabs>
            </Modal.Body>
        </Modal >
    );
};

export default ExecutionDetailsModal;
