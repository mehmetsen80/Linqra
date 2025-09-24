import React from 'react';
import { Badge, Spinner, OverlayTrigger, Tooltip } from 'react-bootstrap';
import ConfirmationModal from '../common/ConfirmationModal';

const ExecuteConfirmationModal = ({ 
    show, 
    onHide, 
    onConfirm, 
    workflow, 
    executing = false 
}) => {
    const truncateIntent = (intent, maxLength = 58) => {
        if (!intent) return '';
        return intent.length > maxLength ? `${intent.substring(0, maxLength)}...` : intent;
    };

    return (
        <ConfirmationModal
            show={show}
            onHide={onHide}
            onConfirm={onConfirm}
            title={workflow?.name}
            message={
                <div>
                    <p>Are you sure you want to execute this workflow?</p>
                    <div className="mt-3">
                        <h6>Workflow Steps:</h6>
                        <div className="workflow-steps-preview">
                            {workflow?.request?.query?.workflow?.map((step, index) => (
                                <div key={index} className="workflow-step-preview">
                                    <Badge bg="info" className="me-2">Step {index + 1}</Badge>
                                    <span className="step-target">{step.target}</span>
                                    {step.intent && (
                                        <OverlayTrigger
                                            placement="top"
                                            overlay={
                                                <Tooltip id={`intent-tooltip-${index}`}>
                                                    {step.intent}
                                                </Tooltip>
                                            }
                                        >
                                            <span className="step-intent ms-2 text-muted">
                                                ({truncateIntent(step.intent)})
                                            </span>
                                        </OverlayTrigger>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            }
            confirmLabel={executing ? (
                <>
                    <Spinner
                        as="span"
                        animation="border"
                        size="sm"
                        role="status"
                        aria-hidden="true"
                        className="me-2"
                    />
                    Executing...
                </>
            ) : "Execute"}
            variant="primary"
            disabled={executing}
        />
    );
};

export default ExecuteConfirmationModal; 