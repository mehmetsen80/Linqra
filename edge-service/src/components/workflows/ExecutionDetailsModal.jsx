import React, { useState } from 'react';
import { Modal, Badge, OverlayTrigger, Tooltip, Row, Col, Tabs, Tab } from 'react-bootstrap';
import { JsonView, allExpanded, defaultStyles } from 'react-json-view-lite';
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

    if (!execution) return null;

    return (
        <Modal
            show={show}
            onHide={onHide}
            size="xl"
            centered
            dialogClassName="modal-90w"
        >
            <Modal.Header closeButton>
                <Modal.Title>Execution Details</Modal.Title>
            </Modal.Header>
            <Modal.Body style={{ maxHeight: 'calc(90vh - 120px)', overflowY: 'auto' }}>
                <Tabs
                    activeKey={activeTab}
                    onSelect={(k) => setActiveTab(k)}
                    className="mb-3"
                >
                    <Tab eventKey="overview" title={<><i className="fas fa-info-circle me-2"></i>Overview</>}>
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
                                                            <br />
                                                            ID: {execution.agentId}
                                                        </div>
                                                    </Tooltip>
                                                }
                                            >
                                                <Badge bg="secondary" className="d-flex align-items-center gap-2 p-2" style={{ cursor: 'pointer' }}>
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
                                {execution.response?.result?.finalResult ? (
                                    <div className="final-result final-result-markdown">
                                        <ReactMarkdown>
                                            {execution.response.result.finalResult}
                                        </ReactMarkdown>
                                    </div>
                                ) : (
                                    <p className="final-result text-muted">No final result available.</p>
                                )}
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
                                                        {(() => {
                                                            // Handle Claude API response (content is array of objects with type and text)
                                                            if (step.result?.content && Array.isArray(step.result.content) && step.result.content.length > 0) {
                                                                // If content array has text property (Claude format)
                                                                const firstContent = step.result.content[0];
                                                                if (typeof firstContent === 'object' && firstContent.text) {
                                                                    return firstContent.text;
                                                                }
                                                                // Otherwise stringify the content
                                                                return JSON.stringify(step.result.content);
                                                            }
                                                            // Handle other providers
                                                            if (step.result?.content) {
                                                                return step.result.content;
                                                            }
                                                            if (step.result?.fullName) {
                                                                return step.result.fullName;
                                                            }
                                                            // Fallback to stringifying the result
                                                            return JSON.stringify(step.result).slice(0, 50) + '...';
                                                        })()}
                                                    </div>
                                                </div>
                                            </OverlayTrigger>
                                        );
                                    })}
                                </div>
                            </div>
                        </div>
                    </Tab>

                    <Tab eventKey="fullData" title={<><i className="fas fa-code me-2"></i>Full Execution Data</>}>
                        <div className="json-viewer" style={{ height: 'calc(90vh - 240px)', overflow: 'auto' }}>
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