import React, { useState } from 'react';
import { Modal, Tabs, Tab, Badge } from 'react-bootstrap';
import JSONEditor from 'react-json-editor-ajrm';
import locale from 'react-json-editor-ajrm/locale/en';
import './styles.css';

const ViewStepDescriptionModal = ({ show, onHide, step }) => {
    const [activeTab, setActiveTab] = useState('overview');

    // Reset to overview tab when modal opens with new step
    React.useEffect(() => {
        if (show && step) {
            setActiveTab('overview');
        }
    }, [show, step]);

    if (!step || !show) return null;

    const getStepIcon = (target, action, intent) => {
        // Check for specific Milvus operations first
        if (target === 'api-gateway' && intent && intent.startsWith('/api/milvus/')) {
            if (intent.includes('/search')) {
                return 'fas fa-search';
            }
            if (intent.includes('/records')) {
                return 'fas fa-save';
            }
        }

        const iconMap = {
            'quotes-service': 'fas fa-user-friends',
            'api-gateway': 'fas fa-database',
            'openai': 'fas fa-robot',
            'gemini': 'fas fa-brain',
            'milvus': 'fas fa-vector-square'
        };

        const actionIconMap = {
            'fetch': 'fas fa-download',
            'create': 'fas fa-plus-circle',
            'generate': 'fas fa-magic',
            'search': 'fas fa-search'
        };

        return iconMap[target] || actionIconMap[action] || 'fas fa-cog';
    };

    const getStepColor = (target, intent) => {
        // Different colors for different Milvus operations
        if (target === 'api-gateway' && intent && intent.startsWith('/api/milvus/')) {
            if (intent.includes('/search')) {
                return '#17a2b8';
            }
            if (intent.includes('/records')) {
                return '#28a745';
            }
        }

        const aiServiceColors = {
            'openai': '#28a745',
            'gemini': '#007bff',
            'anthropic': '#ff6b35',
            'huggingface': '#ffcc02',
            'cohere': '#39c5bb'
        };

        const coreServiceColors = {
            'api-gateway': '#6c757d',
            'auth-service': '#dc3545',
            'notification-service': '#ffc107'
        };

        if (aiServiceColors[target]) {
            return aiServiceColors[target];
        }
        if (coreServiceColors[target]) {
            return coreServiceColors[target];
        }

        const colors = [
            '#6f42c1', '#e83e8c', '#fd7e14', '#20c997',
            '#6610f2', '#d63384', '#198754', '#0d6efd'
        ];

        let hash = 0;
        for (let i = 0; i < target.length; i++) {
            hash = target.charCodeAt(i) + ((hash << 5) - hash);
        }
        const colorIndex = Math.abs(hash) % colors.length;
        return colors[colorIndex];
    };

    const getStepLabel = (target, action, intent) => {
        if (target === 'api-gateway' && intent && intent.startsWith('/api/milvus/')) {
            if (intent.includes('/search')) {
                return { target: 'milvus-search', action: 'search' };
            }
            if (intent.includes('/records')) {
                return { target: 'milvus-store', action: 'save' };
            }
        }
        return { target: target, action: action };
    };

    const stepLabel = getStepLabel(step.target, step.action, step.intent);
    const stepColor = getStepColor(step.target, step.intent);
    const stepIcon = getStepIcon(step.target, step.action, step.intent);

    const formatStepJson = () => {
        const stepCopy = { ...step };
        // Remove description from JSON view to avoid redundancy
        delete stepCopy.description;
        return stepCopy;
    };

    return (
        <Modal
            show={show}
            onHide={onHide}
            size="lg"
            centered
            className="view-step-modal"
        >
            <Modal.Header closeButton={false} className="step-modal-header">
                <div className="step-header-content">
                    <div className="step-badge" style={{ backgroundColor: stepColor }}>
                        <i className={stepIcon}></i>
                        <span className="step-number">{step.step}</span>
                    </div>
                    <div className="step-title-info">
                        <Modal.Title className="step-modal-title">
                            {stepLabel.target}
                        </Modal.Title>
                        <div className="step-subtitle">
                            <Badge bg="secondary" className="me-2">{stepLabel.action}</Badge>
                            {step.intent && (
                                <span className="step-intent">{step.intent}</span>
                            )}
                        </div>
                    </div>
                    <button 
                        className="custom-close-btn"
                        onClick={onHide}
                        aria-label="Close"
                    >
                        ×
                    </button>
                </div>
            </Modal.Header>
            
            <Modal.Body className="step-modal-body">
                <Tabs
                    activeKey={activeTab}
                    onSelect={(k) => setActiveTab(k)}
                    className="step-tabs"
                >
                    <Tab eventKey="overview" title="Overview">
                        <div className="tab-content-wrapper">
                            <div className="step-overview">
                                {step.description && (
                                    <div className="step-description-section">
                                        <h6 className="section-title">
                                            <i className="fas fa-info-circle me-2"></i>
                                            Description
                                        </h6>
                                        <p className="step-description-text">
                                            {step.description}
                                        </p>
                                    </div>
                                )}

                                <div className="step-details-grid">
                                    <div className="detail-item">
                                        <div className="detail-label">Target Service</div>
                                        <div className="detail-value">
                                            <i className={stepIcon} style={{ color: stepColor }}></i>
                                            {stepLabel.target}
                                        </div>
                                    </div>
                                    
                                    <div className="detail-item">
                                        <div className="detail-label">Action</div>
                                        <div className="detail-value">
                                            <Badge bg="primary">{stepLabel.action}</Badge>
                                        </div>
                                    </div>

                                    {step.intent && (
                                        <div className="detail-item">
                                            <div className="detail-label">Intent/Endpoint</div>
                                            <div className="detail-value">
                                                <code className="intent-code">{step.intent}</code>
                                            </div>
                                        </div>
                                    )}

                                    {step.async !== null && (
                                        <div className="detail-item">
                                            <div className="detail-label">Execution Mode</div>
                                            <div className="detail-value">
                                                <Badge bg={step.async ? "warning" : "info"}>
                                                    {step.async ? "Asynchronous" : "Synchronous"}
                                                </Badge>
                                            </div>
                                        </div>
                                    )}

                                    {step.toolConfig && (
                                        <div className="detail-item">
                                            <div className="detail-label">AI Model</div>
                                            <div className="detail-value">
                                                <Badge bg="success">{step.toolConfig.model}</Badge>
                                            </div>
                                        </div>
                                    )}

                                    {step.cacheConfig && step.cacheConfig.enabled && (
                                        <div className="detail-item">
                                            <div className="detail-label">Cache Settings</div>
                                            <div className="detail-value">
                                                <Badge bg="info">
                                                    TTL: {step.cacheConfig.ttl}s
                                                </Badge>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    </Tab>

                    <Tab eventKey="configuration" title="Configuration">
                        <div className="tab-content-wrapper">
                            <div className="json-section">
                                <h6 className="section-title">
                                    <i className="fas fa-code me-2"></i>
                                    Step Configuration (JSON)
                                </h6>
                                <div className="json-editor-container">
                                    <JSONEditor
                                        placeholder={formatStepJson()}
                                        locale={locale}
                                        height="400px"
                                        width="100%"
                                        viewOnly={true}
                                        theme={{
                                            background: '#f8f9fa',
                                            default: '#333',
                                            string: '#ce9178',
                                            number: '#b5cea8',
                                            colon: '#49b4bb',
                                            keys: '#9cdcfe',
                                            keys_whiteSpace: '#af74a5',
                                            primitive: '#6b9955'
                                        }}
                                    />
                                </div>
                            </div>
                        </div>
                    </Tab>

                    {step.toolConfig && (
                        <Tab eventKey="ai-config" title="AI Settings">
                            <div className="tab-content-wrapper">
                                <div className="ai-config-section">
                                    <h6 className="section-title">
                                        <i className="fas fa-robot me-2"></i>
                                        AI Model Configuration
                                    </h6>
                                    
                                    <div className="ai-details-grid">
                                        <div className="detail-item">
                                            <div className="detail-label">Model</div>
                                            <div className="detail-value">
                                                <Badge bg="primary">{step.toolConfig.model}</Badge>
                                            </div>
                                        </div>

                                        {step.toolConfig.settings && Object.entries(step.toolConfig.settings).map(([key, value]) => (
                                            <div key={key} className="detail-item">
                                                <div className="detail-label">{key}</div>
                                                <div className="detail-value">
                                                    <code>{JSON.stringify(value)}</code>
                                                </div>
                                            </div>
                                        ))}
                                    </div>

                                    <div className="json-section mt-4">
                                        <h6 className="section-title">
                                            <i className="fas fa-cog me-2"></i>
                                            Tool Configuration (JSON)
                                        </h6>
                                        <div className="json-editor-container">
                                            <JSONEditor
                                                placeholder={step.toolConfig}
                                                locale={locale}
                                                height="300px"
                                                width="100%"
                                                viewOnly={true}
                                                theme={{
                                                    background: '#f8f9fa',
                                                    default: '#333',
                                                    string: '#ce9178',
                                                    number: '#b5cea8',
                                                    colon: '#49b4bb',
                                                    keys: '#9cdcfe',
                                                    keys_whiteSpace: '#af74a5',
                                                    primitive: '#6b9955'
                                                }}
                                            />
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Tab>
                    )}
                </Tabs>
            </Modal.Body>
        </Modal>
    );
};

export default ViewStepDescriptionModal; 