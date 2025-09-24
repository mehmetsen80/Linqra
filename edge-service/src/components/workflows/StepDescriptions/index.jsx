import React, { useState } from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import ViewStepDescriptionModal from '../ViewStepDescriptionModal';
import './styles.css';

const StepDescriptions = ({ workflow }) => {
    const [hoveredStep, setHoveredStep] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [selectedStep, setSelectedStep] = useState(null);

    if (!workflow) {
        return null;
    }

    // Handle different possible structures
    let workflowSteps = null;
    if (workflow.workflow) {
        workflowSteps = workflow.workflow;
    } else if (workflow.query && workflow.query.workflow) {
        workflowSteps = workflow.query.workflow;
    } else if (Array.isArray(workflow)) {
        workflowSteps = workflow;
    }

    if (!workflowSteps || !Array.isArray(workflowSteps)) {
        return null;
    }

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

        // Map targets and actions to appropriate icons
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
                return '#17a2b8'; // blue for search
            }
            if (intent.includes('/records')) {
                return '#28a745'; // green for save
            }
        }

        // Known AI/ML services
        const aiServiceColors = {
            'openai': '#28a745',         // green
            'gemini': '#007bff',         // primary blue
            'anthropic': '#ff6b35',      // orange
            'huggingface': '#ffcc02',    // yellow
            'cohere': '#39c5bb'          // teal
        };

        // Core infrastructure
        const coreServiceColors = {
            'api-gateway': '#6c757d',    // gray
            'auth-service': '#dc3545',   // red
            'notification-service': '#ffc107' // amber
        };

        // Check known services first
        if (aiServiceColors[target]) {
            return aiServiceColors[target];
        }
        if (coreServiceColors[target]) {
            return coreServiceColors[target];
        }

        // Dynamic color assignment for unknown services
        // Generate consistent color based on service name
        const colors = [
            '#6f42c1', // purple
            '#e83e8c', // pink  
            '#fd7e14', // orange
            '#20c997', // teal
            '#6610f2', // indigo
            '#d63384', // raspberry
            '#198754', // dark green
            '#0d6efd'  // bright blue
        ];

        // Simple hash function to get consistent color for service name
        let hash = 0;
        for (let i = 0; i < target.length; i++) {
            hash = target.charCodeAt(i) + ((hash << 5) - hash);
        }
        const colorIndex = Math.abs(hash) % colors.length;
        
        return colors[colorIndex];
    };

    const handleStepClick = (step) => {
        setSelectedStep(step);
        setShowModal(true);
    };

    const handleCloseModal = () => {
        setShowModal(false);
        // Clear the selected step after a brief delay to prevent flash of old content
        setTimeout(() => {
            setSelectedStep(null);
        }, 150);
    };

    const getStepLabel = (target, action, intent) => {
        // Provide more specific labels for Milvus operations
        if (target === 'api-gateway' && intent && intent.startsWith('/api/milvus/')) {
            if (intent.includes('/search')) {
                return {
                    target: 'milvus-search',
                    action: 'search'
                };
            }
            if (intent.includes('/records')) {
                return {
                    target: 'milvus-store',
                    action: 'save'
                };
            }
        }

        return {
            target: target,
            action: action
        };
    };

    const renderTooltip = (step) => (
        <Tooltip id={`step-${step.step}-tooltip`} className="step-tooltip">
            <div className="tooltip-content">
                <div className="tooltip-header">
                    <strong>Step {step.step}: {step.target}</strong>
                </div>
                <div className="tooltip-action">
                    <i className={getStepIcon(step.target, step.action, step.intent)}></i>
                    Action: {getStepLabel(step.target, step.action, step.intent).action}
                </div>
                {step.description && (
                    <div className="tooltip-description">
                        {step.description}
                    </div>
                )}
                {step.intent && (
                    <div className="tooltip-intent">
                        Intent: {step.intent}
                    </div>
                )}
            </div>
        </Tooltip>
    );

    return (
        <div className="step-descriptions-container">
            <div className="steps-header">
                <h6 className="steps-title">
                    <i className="fas fa-route me-2"></i>
                    Workflow Steps
                </h6>
            </div>
            
            <div className="steps-flow">
                {workflowSteps.map((step, index) => (
                    <React.Fragment key={step.step}>
                        <OverlayTrigger
                            placement="top"
                            delay={{ show: 250, hide: 400 }}
                            overlay={renderTooltip(step)}
                        >
                            <div 
                                className={`step-box ${hoveredStep === step.step ? 'hovered' : ''}`}
                                style={{ '--step-color': getStepColor(step.target, step.intent) }}
                                onMouseEnter={() => setHoveredStep(step.step)}
                                onMouseLeave={() => setHoveredStep(null)}
                                onClick={() => handleStepClick(step)}
                            >
                                <div 
                                    className="step-number" 
                                    style={{ backgroundColor: getStepColor(step.target, step.intent) }}
                                >
                                    {step.step}
                                </div>
                                <div className="step-icon">
                                    <i className={getStepIcon(step.target, step.action, step.intent)}></i>
                                </div>
                                <div className="step-label">
                                    <div className="step-target">{getStepLabel(step.target, step.action, step.intent).target}</div>
                                    <div className="step-action">{getStepLabel(step.target, step.action, step.intent).action}</div>
                                </div>
                            </div>
                        </OverlayTrigger>
                        
                        {index < workflowSteps.length - 1 && (
                            <div className="step-arrow">
                                <i className="fas fa-arrow-right"></i>
                            </div>
                        )}
                    </React.Fragment>
                ))}
            </div>

            <ViewStepDescriptionModal
                show={showModal}
                onHide={handleCloseModal}
                step={selectedStep}
            />
        </div>
    );
};

export default StepDescriptions; 