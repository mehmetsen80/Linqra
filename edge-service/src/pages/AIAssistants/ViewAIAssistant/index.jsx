import React, { useState, useEffect } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import {
    Card,
    Breadcrumb,
    Badge,
    Alert,
    Table
} from 'react-bootstrap';
import Button from '../../../components/common/Button';
import { HiArrowLeft, HiDatabase, HiDownload, HiChatAlt } from 'react-icons/hi';
import { LoadingSpinner } from '../../../components/common/LoadingSpinner';
import aiAssistantService from '../../../services/aiAssistantService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { formatDate } from '../../../utils/dateUtils';
import Footer from '../../../components/common/Footer';
import docReviewService from '../../../services/docReviewService';
import './styles.css';

function ViewAIAssistant() {
    const { assistantId } = useParams();
    const navigate = useNavigate();
    const { currentTeam } = useTeam();
    const { user } = useAuth();
    const [assistant, setAssistant] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Doc Review specific state
    const [reviewHistory, setReviewHistory] = useState([]);
    const [loadingHistory, setLoadingHistory] = useState(false);


    useEffect(() => {
        if (assistantId && currentTeam) {
            loadAssistant();
        }
    }, [assistantId, currentTeam]);

    useEffect(() => {
        if (assistant && assistant.category === 'REVIEW_DOC' && currentTeam) {
            loadReviewHistory();
        }
    }, [assistant, currentTeam]);

    const loadAssistant = async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await aiAssistantService.getAssistant(assistantId);
            if (response.success) {
                setAssistant(response.data);
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load AI assistant');
            console.error('Error loading AI assistant:', err);
        } finally {
            setLoading(false);
        }
    };

    const loadReviewHistory = async () => {
        if (!currentTeam?.id) return;
        try {
            setLoadingHistory(true);
            const response = await docReviewService.getReviewsByTeam(currentTeam.id);
            if (response.success) {
                setReviewHistory(response.data || []);
            }
        } catch (err) {
            console.error('Error loading review history:', err);
        } finally {
            setLoadingHistory(false);
        }
    };

    if (loading) {
        return <LoadingSpinner />;
    }

    if (error || !assistant) {
        return (
            <div className="view-assistant-page">
                <Alert variant="danger">
                    {error || 'AI Assistant not found'}
                </Alert>
                <Button variant="secondary" onClick={() => navigate('/ai-assistants')}>
                    <HiArrowLeft className="me-1" /> Back to AI Assistants
                </Button>
            </div>
        );
    }

    // If it's NOT a Doc Review assistant, delegate to ChatAssistant component
    // Removed early delegation to unify UI

    // Otherwise render the Unified View
    return (
        <div className="view-assistant-page">
            {/* Header */}
            <Card className="mb-4 border-0">
                <Card.Body>
                    <Breadcrumb className="mb-0">
                        <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/dashboard' }}>Home</Breadcrumb.Item>
                        <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/ai-assistants' }}>AI Assistants</Breadcrumb.Item>
                        <Breadcrumb.Item active>{assistant.name}</Breadcrumb.Item>
                    </Breadcrumb>
                </Card.Body>
            </Card>

            <Card className="mb-4 border-0">
                <Card.Body>
                    <div className="d-flex align-items-center gap-2 mb-2">
                        <Button
                            variant="link"
                            onClick={() => navigate('/ai-assistants')}
                            className="p-0"
                        >
                            <HiArrowLeft className="text-primary" size={24} />
                        </Button>
                        <h4 className="mb-0">
                            {assistant.name}
                        </h4>
                        <Badge bg={assistant.status === 'ACTIVE' ? 'success' : assistant.status === 'INACTIVE' ? 'secondary' : 'warning'}>
                            {assistant.status || 'DRAFT'}
                        </Badge>
                        <Badge bg="primary">{assistant.category}</Badge>
                    </div>
                    {assistant.description && (
                        <div className="text-muted text-start">
                            {assistant.description}
                        </div>
                    )}
                </Card.Body>
            </Card>

            {/* Configuration Details Card */}
            <Card className="mb-4">
                <Card.Header className="text-start">
                    <h5 className="mb-0">Configuration Summary</h5>
                </Card.Header>
                <Card.Body>
                    <div className="row mb-4 mt-2">
                        <div className="col-md-6">
                            <h6 className="fw-bold text-muted mb-3 ms-3 text-start">Model Configuration</h6>
                            <Table size="sm" borderless className="small">
                                <tbody>
                                    <tr>
                                        <td className="text-muted" style={{ width: '140px' }}>Provider:</td>
                                        <td className="fw-medium">{assistant.defaultModel?.provider || 'N/A'}</td>
                                    </tr>
                                    <tr>
                                        <td className="text-muted">Model:</td>
                                        <td className="fw-medium">{assistant.defaultModel?.modelName || 'N/A'}</td>
                                    </tr>
                                    <tr>
                                        <td className="text-muted">Temperature:</td>
                                        <td className="fw-medium">{assistant.defaultModel?.settings?.temperature ?? 'N/A'}</td>
                                    </tr>
                                    <tr>
                                        <td className="text-muted">Max Tokens:</td>
                                        <td className="fw-medium">{assistant.defaultModel?.settings?.max_tokens ?? 'N/A'}</td>
                                    </tr>
                                </tbody>
                            </Table>
                        </div>
                        <div className="col-md-6">
                            <h6 className="fw-bold text-muted mb-3 text-start ms-3">Context & Security</h6>
                            <Table size="sm" borderless className="small">
                                <tbody>
                                    <tr>
                                        <td className="text-muted" style={{ width: '140px' }}>Context Strategy:</td>
                                        <td className="fw-medium text-capitalize">{assistant.contextManagement?.strategy?.replace('_', ' ') || 'Sliding Window'}</td>
                                    </tr>
                                    <tr>
                                        <td className="text-muted">History Depth:</td>
                                        <td className="fw-medium">{assistant.contextManagement?.maxRecentMessages || 10} msgs</td>
                                    </tr>
                                    <tr>
                                        <td className="text-muted">Access Type:</td>
                                        <td className="fw-medium">
                                            <Badge bg={assistant.accessControl?.type === 'PUBLIC' ? 'success' : 'secondary'} className="fw-normal">
                                                {assistant.accessControl?.type || 'PRIVATE'}
                                            </Badge>
                                        </td>
                                    </tr>
                                    {assistant.accessControl?.allowedDomains?.length > 0 && (
                                        <tr>
                                            <td className="text-muted align-top">Allowed Domains:</td>
                                            <td className="fw-medium text-break">
                                                {assistant.accessControl.allowedDomains.map(d => (
                                                    <div key={d}>{d}</div>
                                                ))}
                                            </td>
                                        </tr>
                                    )}
                                    <tr>
                                        <td className="text-muted">Guardrails:</td>
                                        <td>
                                            <div className="d-flex gap-1">
                                                {assistant.guardrails?.piiDetectionEnabled && <Badge bg="info" className="fw-normal">PII</Badge>}
                                                {assistant.guardrails?.auditLoggingEnabled && <Badge bg="dark" className="fw-normal">Audit</Badge>}
                                                {!assistant.guardrails?.piiDetectionEnabled && !assistant.guardrails?.auditLoggingEnabled && <span className="text-muted">-</span>}
                                            </div>
                                        </td>
                                    </tr>
                                </tbody>
                            </Table>
                        </div>
                    </div>
                </Card.Body>
            </Card>

            {assistant.selectedTasks && assistant.selectedTasks.length > 0 && (
                <Card className="mb-4">
                    <Card.Header className="text-start">
                        <h5 className="mb-0">Enabled Agent Tasks</h5>
                    </Card.Header>
                    <Card.Body className="px-0">
                        <div className="d-flex flex-wrap gap-2">
                            {assistant.selectedTasks.map((task, idx) => (
                                <Badge key={idx} bg="light" text="dark" className="border fw-normal">
                                    <HiDatabase className="me-1 text-muted" />
                                    {task.taskName}
                                </Badge>
                            ))}
                        </div>
                    </Card.Body>
                </Card>
            )}

            <Card className="mb-4">
                <Card.Header className="text-start">
                    <h5 className="mb-0">System Personality & Instructions</h5>
                </Card.Header>
                <Card.Body className="px-0">
                    <div className="bg-light p-3 rounded small text-start border">
                        <ReactMarkdown>{assistant.systemPrompt || 'No prompt configured.'}</ReactMarkdown>
                    </div>
                </Card.Body>
            </Card>

            {/* Actions Card */}
            <Card className="mb-4">
                <Card.Body>
                    <div className="d-flex justify-content-between align-items-center">
                        <div>
                            <h5 className="mb-1">Assistant Actions</h5>
                            <p className="text-muted mb-0 small">Interact with this assistant</p>
                        </div>
                        <div>
                            {assistant.category === 'CHAT' && (
                                <Button
                                    variant="primary"
                                    onClick={() => navigate(`/ai-assistants/${assistantId}/chat`)}
                                >
                                    <HiChatAlt className="me-2" /> Start Conversation
                                </Button>
                            )}
                            {assistant.category === 'REVIEW_DOC' && (
                                <Button
                                    variant="primary"
                                    onClick={() => navigate('/doc-review')}
                                >
                                    <HiDatabase className="me-2" /> Open Doc Review
                                </Button>
                            )}
                        </div>
                    </div>
                </Card.Body>
            </Card>

            {/* Contract Review History for REVIEW_DOC Category */}
            {assistant.category === 'REVIEW_DOC' && (
                <Card>
                    <Card.Header>
                        <h5 className="mb-0">Doc Review History</h5>
                    </Card.Header>
                    <Card.Body>
                        {loadingHistory ? (
                            <div className="text-center p-4">
                                <LoadingSpinner />
                            </div>
                        ) : reviewHistory.length === 0 ? (
                            <div className="text-center py-4 text-muted">
                                <HiDatabase className="mb-2" size={32} />
                                <p>No doc reviews found.</p>
                            </div>
                        ) : (
                            <Table responsive hover>
                                <thead>
                                    <tr>
                                        <th>Date</th>
                                        <th>Document</th>
                                        <th>Review Status</th>
                                        <th>Reviewer</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {reviewHistory.map((review) => (
                                        <tr key={review.id}>
                                            <td>{formatDate(review.createdAt)}</td>
                                            <td>
                                                {review.documentName || 'Unknown Document'}
                                                {review.documentVersion && <span className="text-muted small ms-1">(v{review.documentVersion})</span>}
                                            </td>
                                            <td>
                                                <Badge bg={review.status === 'COMPLETED' ? 'success' : review.status === 'FAILED' ? 'danger' : 'warning'}>
                                                    {review.status}
                                                </Badge>
                                            </td>
                                            <td>{review.createdBy || 'Unknown'}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </Table>
                        )}
                    </Card.Body>
                </Card>
            )}

            <Footer />
        </div>
    );
}

export default ViewAIAssistant;
