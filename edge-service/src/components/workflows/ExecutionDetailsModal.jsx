import React from 'react';
import { Modal, Badge, OverlayTrigger, Tooltip, Row, Col } from 'react-bootstrap';
import { JsonView, allExpanded, defaultStyles } from 'react-json-view-lite';
import { format } from 'date-fns';

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
    if (!execution) return null;

    return (
        <Modal 
            show={show} 
            onHide={onHide}
            size="lg"
            centered
        >
            <Modal.Header closeButton>
                <Modal.Title>Execution Details</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <div className="execution-details">
                    <Row className="mb-3">
                        <Col md={3}>
                            <h6>Status</h6>
                            <Badge bg={execution.status === 'SUCCESS' ? 'success' : 'danger'}>
                                {execution.status}
                            </Badge>
                        </Col>
                        <Col md={3}>
                            <h6>Duration</h6>
                            <p className="mb-0">{execution.durationMs?.$numberLong || execution.durationMs}ms</p>
                        </Col>
                        <Col md={3}>
                            <h6>Executed At</h6>
                            <p className="mb-0">{formatDate(execution.executedAt?.$date || execution.executedAt)}</p>
                        </Col>
                        <Col md={3}>
                            <h6>Trigger</h6>
                            <div className="trigger-indicator">
                                {execution.agentId ? (
                                    <OverlayTrigger
                                        placement="top"
                                        overlay={
                                            <Tooltip id={`agent-tooltip-${execution.id}`}>
                                                <div>
                                                    <strong>{execution.agentName || 'Unknown Agent'}</strong>
                                                    <br/>
                                                    ID: {execution.agentId}
                                                </div>
                                            </Tooltip>
                                        }
                                    >
                                        <Badge bg="secondary" className="d-flex align-items-center gap-2 p-2" style={{cursor: 'pointer'}}>
                                            <i className="fas fa-robot"></i>
                                            Agent
                                        </Badge>
                                    </OverlayTrigger>
                                ) : (
                                    <Badge bg="secondary" className="d-flex align-items-center gap-1 p-2">
                                        <i className="fas fa-user"></i>
                                        Manual
                                    </Badge>
                                )}
                            </div>
                        </Col>
                    </Row>
                    <div className="mb-3">
                        <h6>Final Result</h6>
                        <p className="final-result">
                            {execution.response?.result?.finalResult}
                        </p>
                    </div>

                    {/* Step Pipeline */}
                    <div className="mb-4">
                        <h6>Step Pipeline</h6>
                        <div className="step-pipeline">
                            {execution.response?.result?.steps?.map((step, index) => {
                                const stepMetadata = execution.response?.metadata?.workflowMetadata?.find(
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
                                                <Badge bg={(stepMetadata?.status === 'success' || stepMetadata?.status === 'completed') ? 'success' : 'danger'}>
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
                                data={execution} 
                                shouldExpandNode={allExpanded}
                                style={defaultStyles}
                            />
                        </div>
                    </div>
                </div>
            </Modal.Body>
        </Modal>
    );
};

export default ExecutionDetailsModal; 