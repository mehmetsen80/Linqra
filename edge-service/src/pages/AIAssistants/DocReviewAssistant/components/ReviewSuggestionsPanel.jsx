
import React from 'react';
import { Badge, Card } from 'react-bootstrap';
import Button from '../../../../components/common/Button';
import { HiCheck, HiX, HiExclamation, HiLightBulb } from 'react-icons/hi';

const ReviewSuggestionsPanel = ({ reviewPoints = [], onAction, loading, activePointId, onPointSelect, documentName }) => {
    const getVerdictBadge = (verdict) => {
        switch (verdict?.toUpperCase()) {
            case 'ACCEPT':
                return <Badge bg="success"><HiCheck className="me-1" />Accept</Badge>;
            case 'REJECT':
                return <Badge bg="danger"><HiX className="me-1" />Reject</Badge>;
            case 'WARNING':
                return <Badge bg="warning" text="dark"><HiExclamation className="me-1" />Warning</Badge>;
            default:
                return <Badge bg="secondary">{verdict || 'Review'}</Badge>;
        }
    };

    const handleAction = (pointId, accepted) => {
        if (onAction) {
            onAction(pointId, accepted);
        }
    };

    const handleCardClick = (pointId) => {
        if (onPointSelect) {
            onPointSelect(pointId);
        }
    };

    if (loading) {
        return (
            <div className="review-suggestions-panel p-3">
                <h6 className="mb-3 d-flex align-items-center">
                    <HiLightBulb className="me-2 text-warning" />
                    AI Analysis
                </h6>
                <div className="text-center text-muted py-4">
                    <div className="spinner-border spinner-border-sm me-2" role="status" />
                    Analyzing document...
                </div>
            </div>
        );
    }

    if (!reviewPoints || reviewPoints.length === 0) {
        return (
            <div className="review-suggestions-panel p-3">
                <h6 className="mb-3 d-flex align-items-center">
                    <HiLightBulb className="me-2 text-warning" />
                    AI Suggestions
                </h6>
                {documentName && (
                    <div className="mb-3 p-2 bg-light rounded text-muted small border">
                        Output for: <strong>{documentName}</strong>
                    </div>
                )}
                <p className="text-muted small mb-0">
                    No review points yet. Start the AI analysis to get suggestions.
                </p>
            </div>
        );
    }

    return (
        <div className="review-suggestions-panel p-3">
            <h6 className="mb-3 d-flex align-items-center justify-content-between">
                <span className="d-flex align-items-center">
                    <HiLightBulb className="me-2 text-warning" />
                    AI Suggestions
                </span>
                <Badge bg="secondary" pill>{reviewPoints.length}</Badge>
            </h6>

            {documentName && (
                <div className="mb-3 p-2 bg-light rounded text-muted small border d-flex justify-content-between align-items-center">
                    <span className="text-truncate" title={documentName}>
                        For: <strong>{documentName}</strong>
                    </span>
                </div>
            )}

            <div className="review-points-list">
                {reviewPoints.map((point, index) => {
                    const isActive = activePointId === point.id;
                    return (
                        <Card
                            key={point.id || index}
                            className={`mb-3 review-point-card ${isActive ? 'border-primary shadow-sm' : ''}`}
                            style={isActive ? { backgroundColor: '#f0f7ff', cursor: 'pointer', transition: 'all 0.2s' } : { cursor: 'pointer', transition: 'all 0.2s' }}
                            onClick={() => handleCardClick(point.id)}
                        >
                            <Card.Body className="p-3">
                                {/* Verdict Badge */}
                                <div className="mb-2">
                                    {getVerdictBadge(point.verdict)}
                                </div>

                                {/* Original Text */}
                                {point.originalText && (
                                    <div className="mb-2">
                                        <small className="text-muted d-block mb-1">Original:</small>
                                        <div className="p-2 bg-light rounded small" style={{ borderLeft: '3px solid var(--primary-color)' }}>
                                            "{point.originalText}"
                                        </div>
                                    </div>
                                )}

                                {/* AI Reasoning */}
                                {point.reasoning && (
                                    <div className="mb-2">
                                        <small className="text-muted d-block mb-1">Analysis:</small>
                                        <p className="small mb-0">{point.reasoning}</p>
                                    </div>
                                )}

                                {/* Suggestion */}
                                {point.suggestion && (
                                    <div className="mb-3">
                                        <small className="text-muted d-block mb-1">Suggestion:</small>
                                        <div className="p-2 bg-success bg-opacity-10 rounded small" style={{ borderLeft: '3px solid var(--bs-success)' }}>
                                            {point.suggestion}
                                        </div>
                                    </div>
                                )}

                                {/* Action Buttons */}
                                {point.userAccepted === null || point.userAccepted === undefined ? (
                                    <div className="d-flex gap-2" onClick={(e) => e.stopPropagation()}>
                                        <Button
                                            variant="success"
                                            size="sm"
                                            className="flex-grow-1"
                                            onClick={() => handleAction(point.id, true)}
                                        >
                                            <HiCheck className="me-1" /> Accept
                                        </Button>
                                        <Button
                                            variant="outline-danger"
                                            size="sm"
                                            className="flex-grow-1"
                                            onClick={() => handleAction(point.id, false)}
                                        >
                                            <HiX className="me-1" /> Reject
                                        </Button>
                                    </div>
                                ) : (
                                    <div className={`text-center p-2 rounded ${point.userAccepted ? 'bg-success bg-opacity-10 text-success' : 'bg-danger bg-opacity-10 text-danger'}`}>
                                        <small>
                                            {point.userAccepted ? (
                                                <><HiCheck className="me-1" /> Accepted</>
                                            ) : (
                                                <><HiX className="me-1" /> Rejected</>
                                            )}
                                        </small>
                                    </div>
                                )}
                            </Card.Body>
                        </Card>
                    );
                })}
            </div>
        </div>
    );
};

export default ReviewSuggestionsPanel;
