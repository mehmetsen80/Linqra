import React, { useMemo } from 'react';
import { Modal, Tabs, Tab, Badge, Card } from 'react-bootstrap';
import Button from '../Button';
import ReactMarkdown from 'react-markdown';
import './styles.css';

const LinqConfigViewerModal = ({
    show,
    onHide,
    configText = '',
    taskName = ''
}) => {
    
    // Parse the config text
    const { parsedConfig, error } = useMemo(() => {
        try {
            if (!configText) return { parsedConfig: null, error: null };
            const parsed = JSON.parse(configText);
            return { parsedConfig: parsed, error: null };
        } catch (e) {
            return { parsedConfig: null, error: e.message };
        }
    }, [configText]);

    const workflowSteps = parsedConfig?.query?.workflow || [];
    
    const renderPayloadContent = (content) => {
        if (typeof content === 'string') {
            return (
                <div className="markdown-content">
                    <ReactMarkdown>{content}</ReactMarkdown>
                </div>
            );
        }
        return <pre className="small bg-light p-2 rounded">{JSON.stringify(content, null, 2)}</pre>;
    };

    return (
        <Modal
            show={show}
            onHide={onHide}
            centered
            size="xl"
            animation={true}
            className="linq-config-viewer-modal"
        >
            <Modal.Header closeButton className="border-0 p-3 bg-light">
                <Modal.Title className="d-flex align-items-center gap-2 text-dark">
                    <i className="fas fa-sitemap text-secondary"></i> {taskName || 'Linq Configuration Viewer'}
                </Modal.Title>
            </Modal.Header>
            <Modal.Body className="p-4" style={{ maxHeight: '75vh', overflowY: 'auto' }}>
                {error ? (
                    <div className="alert alert-danger">
                        <i className="fas fa-exclamation-triangle me-2"></i>
                        Invalid JSON Configuration: {error}
                    </div>
                ) : !parsedConfig ? (
                    <div className="text-muted text-center py-4">No configuration provided</div>
                ) : (
                    <div>
                        <div className="mb-4">
                            <h5 className="border-bottom pb-2">General Info</h5>
                            <div className="d-flex flex-wrap gap-3">
                                {parsedConfig?.link?.target && (
                                    <div><strong>Link Target:</strong> <Badge bg="secondary">{parsedConfig.link.target}</Badge></div>
                                )}
                                {parsedConfig?.query?.intent && (
                                    <div><strong>Intent:</strong> <Badge bg="info" text="dark">{parsedConfig.query.intent}</Badge></div>
                                )}
                            </div>
                        </div>

                        <h5 className="border-bottom pb-2 mb-3">Workflow Steps ({workflowSteps.length})</h5>
                        
                        {workflowSteps.length === 0 ? (
                            <p className="text-muted">No workflow steps defined.</p>
                        ) : (
                            <Tabs defaultActiveKey="0" className="mb-3 custom-tabs">
                                {workflowSteps.map((step, index) => (
                                    <Tab eventKey={index.toString()} key={index} title={
                                        <span>
                                            Step {step.step || index + 1}
                                        </span>
                                    }>
                                        <div className="p-4 border border-top-0 rounded-bottom shadow-sm bg-light mb-3">
                                            <div className="d-flex align-items-center w-100 mb-3">
                                                <div className="flex-grow-1">
                                                    <h5 className="mb-0"><strong>{step.description || 'Unnamed Step'}</strong></h5>
                                                </div>
                                                <Badge bg={step.target?.includes('openai') || step.target?.includes('gemini') ? 'success' : 'secondary'}>
                                                    {step.target}
                                                </Badge>
                                            </div>
                                            
                                            <div className="mb-4">
                                                <div className="d-flex gap-4 text-muted small mb-2 border-bottom pb-2">
                                                    <div><strong>Action:</strong> {step.action}</div>
                                                    {step.llmConfig?.model && (
                                                        <div><strong>Model:</strong> <code className="bg-white px-1 border rounded text-dark">{step.llmConfig.model}</code></div>
                                                    )}
                                                    {step.llmConfig?.settings?.temperature !== undefined && (
                                                        <div><strong>Temp:</strong> {step.llmConfig.settings.temperature}</div>
                                                    )}
                                                </div>
                                            </div>
                                            
                                            <h6 className="mt-3 mb-3 text-secondary">Payload Messages</h6>
                                            {Array.isArray(step.payload) ? (
                                                <div className="d-flex flex-column gap-3">
                                                    {step.payload.map((msg, idx) => (
                                                        <Card key={idx} className={`border-0 shadow-sm ${msg.role === 'system' ? 'border-start border-4 border-warning' : 'border-start border-4 border-info'}`}>
                                                            <Card.Header className="bg-white py-2 d-flex justify-content-between align-items-center">
                                                                <Badge bg={msg.role === 'system' ? 'warning' : msg.role === 'user' ? 'info' : 'secondary'} text={msg.role === 'system' ? 'dark' : 'white'} className="text-uppercase">
                                                                    {msg.role}
                                                                </Badge>
                                                            </Card.Header>
                                                            <Card.Body className="py-3 bg-white">
                                                                {renderPayloadContent(msg.content)}
                                                            </Card.Body>
                                                        </Card>
                                                    ))}
                                                </div>
                                            ) : (
                                                <pre className="bg-white p-3 rounded shadow-sm border small">{JSON.stringify(step.payload, null, 2)}</pre>
                                            )}
                                        </div>
                                    </Tab>
                                ))}
                            </Tabs>
                        )}
                    </div>
                )}
            </Modal.Body>
            <Modal.Footer className="border-0 bg-light">
                <Button 
                    variant="secondary" 
                    onClick={onHide}
                    className="px-4"
                >
                    Close
                </Button>
            </Modal.Footer>
        </Modal>
    );
};

export default LinqConfigViewerModal;
