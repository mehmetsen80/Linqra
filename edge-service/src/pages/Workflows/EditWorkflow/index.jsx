import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin } from '../../../utils/roleUtils';
import workflowService from '../../../services/workflowService';
import './styles.css';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { Form, Card, Spinner, Badge, Modal } from 'react-bootstrap';
import Button from '../../../components/common/Button';
import { format } from 'date-fns';

function EditWorkflow() {
    const { workflowId } = useParams();
    const navigate = useNavigate();
    const { currentTeam, loading: teamLoading, selectedTeam } = useTeam();
    const { user } = useAuth();
    const [workflow, setWorkflow] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [versions, setVersions] = useState([]);
    const [showConfirmModal, setShowConfirmModal] = useState(false);
    const [showRollbackModal, setShowRollbackModal] = useState(false);
    const [showCompareModal, setShowCompareModal] = useState(false);
    const [selectedVersion, setSelectedVersion] = useState(null);
    const [compareVersions, setCompareVersions] = useState({ version1: '', version2: '' });
    const [saving, setSaving] = useState(false);
    const [showMetadataModal, setShowMetadataModal] = useState(false);

    useEffect(() => {
        if (currentTeam) {
            loadWorkflow();
            loadVersions();
        }
    }, [currentTeam]);

    const loadWorkflow = async () => {
        try {
            setLoading(true);
            const response = await workflowService.getWorkflowById(workflowId);
            if (response.success) {
                // Get the latest version number from versions array
                const latestVersion = versions[0]?.version;
                setWorkflow({
                    ...response.data,
                    version: latestVersion
                });
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load workflow');
            console.error('Error loading workflow:', err);
        } finally {
            setLoading(false);
        }
    };

    const loadVersions = async () => {
        try {
            const response = await workflowService.getWorkflowVersions(workflowId);
            if (response.success) {
                setVersions(response.data);
            }
        } catch (err) {
            console.error('Error loading versions:', err);
        }
    };

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        
        if (name === 'request') {
            try {
                // Try to parse the JSON input
                const parsedJson = JSON.parse(value);
                setWorkflow(prev => ({
                    ...prev,
                    request: parsedJson
                }));
            } catch (err) {
                // If parsing fails, just update the raw value
                setWorkflow(prev => ({
                    ...prev,
                    request: value
                }));
            }
        } else {
            setWorkflow(prev => ({
                ...prev,
                [name]: value
            }));
        }
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            // Ensure request is properly stringified if it's an object
            const workflowToSave = {
                ...workflow,
                request: typeof workflow.request === 'object' ? workflow.request : JSON.parse(workflow.request)
            };
            const response = await workflowService.createNewVersion(workflowId, workflowToSave);
            if (response.success) {
                showSuccessToast('Workflow updated successfully');
                loadWorkflow();
                loadVersions();
            } else {
                showErrorToast(response.error || 'Failed to update workflow');
            }
        } catch (err) {
            showErrorToast('Failed to update workflow');
            console.error('Error updating workflow:', err);
        } finally {
            setSaving(false);
            setShowConfirmModal(false);
        }
    };

    const handleRollbackClick = (version) => {
        setSelectedVersion(version);
        setShowRollbackModal(true);
    };

    const handleRollback = async () => {
        if (!selectedVersion) return;

        try {
            setSaving(true);
            const response = await workflowService.rollbackToVersion(workflowId, selectedVersion.id);
            if (response.success) {
                showSuccessToast('Rolled back to previous version');
                loadWorkflow();
                loadVersions();
            } else {
                showErrorToast(response.error || 'Failed to rollback version');
            }
        } catch (err) {
            showErrorToast('Failed to rollback version');
            console.error('Error rolling back version:', err);
        } finally {
            setSaving(false);
            setShowRollbackModal(false);
            setSelectedVersion(null);
        }
    };

    const handleCompareClick = (version) => {
        setCompareVersions({
            version1: workflow?.version,
            version2: version.version
        });
        setShowCompareModal(true);
    };

    const formatDate = (date) => {
        if (!date) return 'N/A';
        return format(new Date(date), 'MMM d, yyyy HH:mm');
    };

    const highlightDifferences = (obj1, obj2, path = '') => {
        if (!obj1 || !obj2) return {};
        
        const result = {};
        
        // Handle arrays
        if (Array.isArray(obj1) && Array.isArray(obj2)) {
            obj1.forEach((item, index) => {
                if (index < obj2.length) {
                    const nestedDiffs = highlightDifferences(item, obj2[index], `${path}[${index}]`);
                    Object.assign(result, nestedDiffs);
                }
            });
            return result;
        }
        
        // Handle objects
        if (typeof obj1 === 'object' && typeof obj2 === 'object') {
            const allKeys = new Set([...Object.keys(obj1), ...Object.keys(obj2)]);
            
            allKeys.forEach(key => {
                const currentPath = path ? `${path}.${key}` : key;
                
                // If both values are objects, compare them recursively
                if (obj1[key] && obj2[key] && 
                    typeof obj1[key] === 'object' && typeof obj2[key] === 'object') {
                    const nestedDiffs = highlightDifferences(obj1[key], obj2[key], currentPath);
                    Object.assign(result, nestedDiffs);
                } 
                // If values are different, mark this path as different
                else if (JSON.stringify(obj1[key]) !== JSON.stringify(obj2[key])) {
                    result[currentPath] = true;
                }
            });
            
            return result;
        }
        
        // For primitive values, compare directly
        if (JSON.stringify(obj1) !== JSON.stringify(obj2)) {
            result[path] = true;
        }
        
        return result;
    };

    const renderJsonWithHighlights = (obj, differences, path = '') => {
        if (!obj) return null;
        
        if (Array.isArray(obj)) {
            return (
                <div>
                    [
                    <div style={{ marginLeft: '20px' }}>
                        {obj.map((item, index) => (
                            <div key={index}>
                                {renderJsonWithHighlights(item, differences, `${path}[${index}]`)}
                                {index < obj.length - 1 && ','}
                            </div>
                        ))}
                    </div>
                    ]
                </div>
            );
        }
        
        if (typeof obj === 'object' && obj !== null) {
            return (
                <div>
                    {'{'}
                    <div style={{ marginLeft: '20px' }}>
                        {Object.entries(obj).map(([key, value], index, array) => {
                            const currentPath = path ? `${path}.${key}` : key;
                            const isDifferent = differences[currentPath];
                            
                            return (
                                <div key={key} className={isDifferent ? 'diff-changed' : ''}>
                                    <span className="json-key">"{key}"</span>: {renderJsonWithHighlights(value, differences, currentPath)}
                                    {index < array.length - 1 && ','}
                                </div>
                            );
                        })}
                    </div>
                    {'}'}
                </div>
            );
        }
        
        return (
            <span className="json-value">
                {typeof obj === 'string' ? `"${obj}"` : obj}
            </span>
        );
    };

    const handleMetadataSave = async () => {
        try {
            await workflowService.updateWorkflow(workflowId, workflow);
            showSuccessToast('Workflow details updated successfully');
            setShowMetadataModal(false);
        } catch (err) {
            showErrorToast('Failed to update workflow details');
        }
    };

    if (loading) {
        return (
            <div className="d-flex justify-content-center align-items-center" style={{ height: '200px' }}>
                <Spinner animation="border" role="status">
                    <span className="visually-hidden">Loading...</span>
                </Spinner>
            </div>
        );
    }

    if (error) {
        return (
            <div className="alert alert-danger" role="alert">
                {error}
            </div>
        );
    }

    return (
        <div className="edit-workflow-container">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h2>Edit Workflow</h2>
                <div className="d-flex gap-2">
                    <Button 
                        variant="outline-primary" 
                        onClick={() => setShowMetadataModal(true)}
                    >
                        Edit Workflow Details
                    </Button>
                    <Button 
                        variant="outline-secondary" 
                        onClick={() => navigate('/workflows')}
                    >
                        Back to Workflows
                    </Button>
                </div>
            </div>

            <div className="row">
                <div className="col-md-8">
                    <Card className="mb-4">
                        <Card.Body>
                            <Form onSubmit={(e) => e.preventDefault()}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Name</Form.Label>
                                    <Form.Control
                                        type="text"
                                        name="name"
                                        value={workflow?.name || ''}
                                        onChange={handleInputChange}
                                        placeholder="Enter workflow name"
                                    />
                                </Form.Group>

                                <Form.Group className="mb-3">
                                    <Form.Label>Description</Form.Label>
                                    <Form.Control
                                        as="textarea"
                                        name="description"
                                        value={workflow?.description || ''}
                                        onChange={handleInputChange}
                                        placeholder="Enter workflow description"
                                        rows={3}
                                    />
                                </Form.Group>

                                <Form.Group className="mb-3">
                                    <Form.Label>Request</Form.Label>
                                    <Form.Control
                                        as="textarea"
                                        name="request"
                                        value={typeof workflow?.request === 'object' ? JSON.stringify(workflow.request, null, 2) : workflow?.request || ''}
                                        onChange={handleInputChange}
                                        rows={30}
                                        className="font-monospace"
                                    />
                                </Form.Group>

                                <div className="d-flex justify-content-between">
                                    <Button 
                                        variant="outline-secondary" 
                                        onClick={() => setWorkflow(null)}
                                    >
                                        Reset
                                    </Button>
                                    <Button 
                                        variant="primary" 
                                        onClick={() => setShowConfirmModal(true)}
                                        disabled={saving}
                                    >
                                        {saving ? (
                                            <>
                                                <Spinner
                                                    as="span"
                                                    animation="border"
                                                    size="sm"
                                                    role="status"
                                                    aria-hidden="true"
                                                    className="me-2"
                                                />
                                                Saving...
                                            </>
                                        ) : 'Save Changes'}
                                    </Button>
                                </div>
                            </Form>
                        </Card.Body>
                    </Card>
                </div>

                <div className="col-md-4">
                    <Card>
                        <Card.Header>
                            <h5 className="mb-0">Version History</h5>
                        </Card.Header>
                        <Card.Body className="version-history">
                            {versions.map((version) => (
                                <div key={version.id} className="version-item">
                                    <div className="d-flex justify-content-between align-items-center mb-2">
                                        <div className="d-flex align-items-center">
                                            <Badge bg="secondary" className="me-2">v{version.version}</Badge>
                                            {version.version === versions[0]?.version && (
                                                <Badge bg="success">Current</Badge>
                                            )}
                                        </div>
                                        <small className="text-muted">
                                            {formatDate(version.createdAt)}
                                        </small>
                                    </div>
                                    <p className="version-description mb-2">
                                        {version.changeDescription.replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d+/, (match) => format(new Date(match), 'MMM d, yyyy HH:mm'))}
                                    </p>
                                    <div className="d-flex justify-content-between align-items-center">
                                        <small className="text-muted">
                                            By {version.createdBy || 'System'}
                                        </small>
                                        {version.version !== workflow?.version && (
                                            <div className="d-flex gap-2">
                                                <Button
                                                    variant="outline-info"
                                                    size="sm"
                                                    onClick={() => handleCompareClick(version)}
                                                >
                                                    Compare
                                                </Button>
                                                <Button
                                                    variant="outline-primary"
                                                    size="sm"
                                                    onClick={() => handleRollbackClick(version)}
                                                >
                                                    Rollback
                                                </Button>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </Card.Body>
                    </Card>
                </div>
            </div>

            <Modal
                show={showCompareModal}
                onHide={() => setShowCompareModal(false)}
                fullscreen
            >
                <Modal.Header closeButton>
                    <Modal.Title>Compare Versions</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <div className="compare-form">
                        <div className="version-selectors mb-4">
                            <div className="row g-3 align-items-center">
                                <div className="col-5">
                                    <div className="version-content p-3 bg-light rounded">
                                        <h6 className="mb-3">Version {compareVersions.version1}</h6>
                                        <div className="diff-view">
                                            <div className="diff-section">
                                                <h6 className="diff-title">Name</h6>
                                                <div className="diff-content">
                                                    {workflow?.name}
                                                </div>
                                            </div>
                                            <div className="diff-section">
                                                <h6 className="diff-title">Description</h6>
                                                <div className="diff-content">
                                                    {workflow?.description}
                                                </div>
                                            </div>
                                            <div className="diff-section">
                                                <h6 className="diff-title">Request</h6>
                                                <pre className="diff-content">
                                                    {renderJsonWithHighlights(
                                                        workflow?.request || {},
                                                        highlightDifferences(
                                                            workflow?.request || {},
                                                            versions.find(v => v.version === compareVersions.version2)?.request || {}
                                                        )
                                                    )}
                                                </pre>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-2 text-center">
                                    <span className="vs-badge">VS</span>
                                </div>
                                <div className="col-5">
                                    <div className="version-content p-3 bg-light rounded">
                                        <h6 className="mb-3">Version {compareVersions.version2}</h6>
                                        <div className="diff-view">
                                            <div className="diff-section">
                                                <h6 className="diff-title">Name</h6>
                                                <div className="diff-content">
                                                    {workflow?.name}
                                                </div>
                                            </div>
                                            <div className="diff-section">
                                                <h6 className="diff-title">Description</h6>
                                                <div className="diff-content">
                                                    {workflow?.description}
                                                </div>
                                            </div>
                                            <div className="diff-section">
                                                <h6 className="diff-title">Request</h6>
                                                <pre className="diff-content">
                                                    {renderJsonWithHighlights(
                                                        versions.find(v => v.version === compareVersions.version2)?.request || {},
                                                        highlightDifferences(
                                                            workflow?.request || {},
                                                            versions.find(v => v.version === compareVersions.version2)?.request || {}
                                                        )
                                                    )}
                                                </pre>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowCompareModal(false)}>
                        Close
                    </Button>
                </Modal.Footer>
            </Modal>

            <ConfirmationModal
                show={showRollbackModal}
                onHide={() => {
                    setShowRollbackModal(false);
                    setSelectedVersion(null);
                }}
                onConfirm={handleRollback}
                title="Rollback Version"
                message={`Are you sure you want to rollback from version ${workflow?.version} to version ${selectedVersion?.version}?`}
                confirmLabel={saving ? (
                    <>
                        <Spinner
                            as="span"
                            animation="border"
                            size="sm"
                            role="status"
                            aria-hidden="true"
                            className="me-2"
                        />
                        Rolling back...
                    </>
                ) : "Rollback"}
                variant="warning"
                disabled={saving}
            />

            <ConfirmationModal
                show={showConfirmModal}
                onHide={() => setShowConfirmModal(false)}
                onConfirm={handleSave}
                title="Save Changes"
                message="Are you sure you want to save these changes? This will create a new version of the workflow."
            />

            <Modal
                show={showMetadataModal}
                onHide={() => setShowMetadataModal(false)}
                centered
            >
                <Modal.Header closeButton>
                    <Modal.Title>Edit Workflow Details</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label>Name</Form.Label>
                            <Form.Control
                                type="text"
                                name="name"
                                value={workflow?.name || ''}
                                onChange={handleInputChange}
                                placeholder="Enter workflow name"
                            />
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Description</Form.Label>
                            <Form.Control
                                as="textarea"
                                name="description"
                                value={workflow?.description || ''}
                                onChange={handleInputChange}
                                placeholder="Enter workflow description"
                                rows={3}
                            />
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowMetadataModal(false)}>
                        Cancel
                    </Button>
                    <Button variant="primary" onClick={handleMetadataSave}>
                        Save Changes
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
}

export default EditWorkflow;