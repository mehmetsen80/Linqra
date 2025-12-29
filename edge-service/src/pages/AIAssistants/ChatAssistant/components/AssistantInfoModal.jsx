import React from 'react';
import { Modal, Button, Badge } from 'react-bootstrap';
import ReactMarkdown from 'react-markdown';
import { formatDate } from '../../../../utils/dateUtils';

const AssistantInfoModal = ({ show, onHide, assistant }) => {
    if (!assistant) return null;

    return (
        <Modal show={show} onHide={onHide} size="lg" centered>
            <Modal.Header closeButton>
                <Modal.Title>Assistant Information</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <div className="mb-4">
                    <h5 className="fw-bold">{assistant.name}</h5>
                    {assistant.description && (
                        <p className="text-muted">{assistant.description}</p>
                    )}
                    <div className="d-flex gap-2">
                        {assistant.accessControl?.type && (
                            <Badge bg={assistant.accessControl.type === 'PUBLIC' ? 'success' : 'secondary'}>
                                {assistant.accessControl.type}
                            </Badge>
                        )}
                        <Badge bg="info" text="dark">
                            {assistant.category || 'General'}
                        </Badge>
                    </div>
                </div>

                <div className="row g-3 mb-4">
                    <div className="col-md-4">
                        <div className="p-3 bg-light rounded h-100 border">
                            <small className="text-muted d-block text-uppercase fw-bold mb-1" style={{ fontSize: '0.7rem' }}>Default Model</small>
                            <div className="fw-medium text-break">{assistant.defaultModel?.modelName || 'Default'}</div>
                        </div>
                    </div>
                    <div className="col-md-4">
                        <div className="p-3 bg-light rounded h-100 border">
                            <small className="text-muted d-block text-uppercase fw-bold mb-1" style={{ fontSize: '0.7rem' }}>Default Provider</small>
                            <div className="fw-medium text-break">{assistant.defaultModel?.provider || 'N/A'}</div>
                        </div>
                    </div>
                    <div className="col-md-4">
                        <div className="p-3 bg-light rounded h-100 border">
                            <small className="text-muted d-block text-uppercase fw-bold mb-1" style={{ fontSize: '0.7rem' }}>Temperature</small>
                            <div className="fw-medium">{assistant.defaultModel?.temperature ?? 'Default'}</div>
                        </div>
                    </div>
                </div>

                <div className="mb-3">
                    <h6 className="fw-bold">System Prompt & Instructions</h6>
                    <div className="bg-light p-3 rounded border" style={{ maxHeight: '300px', overflowY: 'auto' }}>
                        {assistant.systemPrompt ? (
                            <div className="small">
                                <ReactMarkdown>{assistant.systemPrompt}</ReactMarkdown>
                            </div>
                        ) : (
                            <span className="text-muted small">No system prompt configured.</span>
                        )}
                    </div>
                </div>

                {assistant.selectedTasks && assistant.selectedTasks.length > 0 && (
                    <div className="mb-3">
                        <h6 className="fw-bold">Enabled Capabilities</h6>
                        <div className="d-flex flex-column gap-2">
                            {assistant.selectedTasks.map((task, idx) => {
                                const taskName = task.taskName || task.name || (typeof task === 'string' ? task : 'Unknown Task');
                                return (
                                    <div key={idx} className="bg-light p-3 rounded border">
                                        <div className="fw-bold mb-2 d-flex align-items-center">
                                            <Badge bg="primary" className="me-2">Task</Badge>
                                            {taskName.replace(/_/g, ' ')}
                                        </div>
                                        {task.steps && task.steps.length > 0 ? (
                                            <div className="ps-3 border-start border-primary border-2">
                                                <small className="text-muted d-block mb-1 fw-bold">STEPS:</small>
                                                <ul className="list-unstyled mb-0 small">
                                                    {task.steps.map((step, stepIdx) => (
                                                        <li key={stepIdx} className="mb-1 d-flex align-items-start">
                                                            <span className="me-2 text-muted">{stepIdx + 1}.</span>
                                                            <span>{step.description || step.action || 'Step execution'}</span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            </div>
                                        ) : (
                                            <small className="text-muted">No specific steps configured.</small>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}

                <div className="text-muted small text-end border-top pt-2 mt-4">
                    Created: {formatDate(assistant.createdAt)}
                </div>
            </Modal.Body>
            <Modal.Footer>
                <Button variant="secondary" onClick={onHide}>
                    Close
                </Button>
            </Modal.Footer>
        </Modal>
    );
};

export default AssistantInfoModal;
