import React, { useState, useEffect } from 'react';
import { Modal, Form, Alert, Spinner, Table, Row, Col, Accordion, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { HiTrash, HiServer } from 'react-icons/hi'; // Using Server icon as generic placeholder or Ollama specific if available
import { linqLlmModelService } from '../../services/linqLlmModelService';
import llmModelService from '../../services/llmModelService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import Button from '../../components/common/Button';
import ConfirmationModal from '../../components/common/ConfirmationModal';


const initialFormData = {
    modelCategory: 'ollama-chat',
    provider: 'ollama',
    endpoint: 'http://localhost:11434/api/chat',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    },
    authType: 'none',
    apiKey: '', // Not used for Ollama usually
    modelName: '',
    supportedIntents: ['generate', 'summarize', 'translate'],
    teamId: ''
};

function OllamaModal({ show, onHide, team, onTeamUpdate }) {
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState(initialFormData);
    const [existingConfigs, setExistingConfigs] = useState([]);
    const [loadingConfigs, setLoadingConfigs] = useState(false);
    const [availableModels, setAvailableModels] = useState([]);
    const [loadingModels, setLoadingModels] = useState(false);
    const [showConfirmRemove, setShowConfirmRemove] = useState(false);
    const [configToRemove, setConfigToRemove] = useState(null);

    // Reset form when modal is hidden
    useEffect(() => {
        if (!show) {
            setFormData(initialFormData);
        }
    }, [show]);

    // Fetch team configuration and models when modal is shown
    useEffect(() => {
        if (show && team) {
            // Reset form to initial state when modal opens
            setFormData({
                ...initialFormData,
                team: team.name,
                teamId: team.id
            });
            fetchAvailableModels();
            fetchExistingConfigs();
        }
    }, [show, team]);

    const fetchAvailableModels = async () => {
        try {
            setLoadingModels(true);
            const models = await llmModelService.getModelsByProvider('ollama');
            setAvailableModels(models || []);
        } catch (error) {
            console.error('Error fetching available models:', error);
        } finally {
            setLoadingModels(false);
        }
    };

    const fetchExistingConfigs = async () => {
        try {
            setLoadingConfigs(true);
            // Fetch 'ollama-chat' and 'ollama-embed' configurations
            const configs = await linqLlmModelService.getLlmModelByModelCategories(team.id, ['ollama-chat', 'ollama-embed']);
            setExistingConfigs(configs || []);
        } catch (error) {
            console.error('Error fetching existing configurations:', error);
        } finally {
            setLoadingConfigs(false);
        }
    };

    if (!team) return null;

    const handleSave = async (e) => {
        e.preventDefault(); // Prevent form submission

        // Validate required fields
        const requiredFields = {
            endpoint: 'Endpoint',
            modelName: 'Model Name',
            supportedIntents: 'Supported Intents'
        };

        const missingFields = Object.entries(requiredFields)
            .filter(([key]) => {
                const value = formData[key];
                return !value || (Array.isArray(value) && value.length === 0);
            })
            .map(([_, label]) => label);

        if (missingFields.length > 0) {
            showErrorToast(`Please fill in all required fields: ${missingFields.join(', ')}`);
            return;
        }

        try {
            setLoading(true);
            const config = {
                provider: 'ollama',
                modelCategory: formData.modelCategory,
                endpoint: formData.endpoint,
                method: 'POST',
                headers: formData.headers,
                authType: 'none',
                apiKey: '',
                modelName: formData.modelName,
                supportedIntents: formData.supportedIntents,
                teamId: formData.teamId
            };
            await linqLlmModelService.saveConfiguration(config);
            showSuccessToast('Configuration saved successfully');
            await fetchExistingConfigs();
            if (onTeamUpdate) {
                await onTeamUpdate();
            }
            // Reset form for next configuration
            setFormData({
                ...initialFormData,
                team: team.name,
                teamId: team.id
            });
        } catch (error) {
            showErrorToast('Failed to save configuration');
            console.error('Error saving configuration:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleDeleteClick = (config) => {
        setConfigToRemove(config);
        setShowConfirmRemove(true);
    };

    const handleConfirmDelete = async () => {
        if (!configToRemove) return;

        try {
            setLoading(true);
            await linqLlmModelService.deleteConfiguration(configToRemove.id);
            showSuccessToast('Configuration removed successfully');
            await fetchExistingConfigs();
            if (onTeamUpdate) {
                await onTeamUpdate();
            }
            setShowConfirmRemove(false);
            setConfigToRemove(null);
        } catch (error) {
            showErrorToast('Failed to remove configuration');
            console.error('Error removing configuration:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleModelNameChange = (modelName) => {
        // Find the selected model
        const selectedModel = availableModels.find(model => model.modelName === modelName);

        if (!selectedModel) {
            // If clearing or invalid, just update name
            setFormData(prev => ({ ...prev, modelName }));
            return;
        }

        const category = selectedModel.category.toLowerCase();
        const isEmbedding = category === 'embedding' || category === 'ollama-embed';

        const modelCategory = isEmbedding ? 'ollama-embed' : 'ollama-chat';
        const defaultEndpoint = selectedModel.endpoint || 'http://localhost:11434/api/chat';

        // Logic: If endpoint is empty, populate with default. If user typed something, keep it.
        // Also, if selecting an existing config, load that config entirely.
        const existingConfig = existingConfigs.find(config => config.modelName === modelName);

        if (existingConfig) {
            setFormData({
                ...formData,
                modelName: modelName,
                modelCategory: existingConfig.modelCategory,
                endpoint: existingConfig.endpoint,
                headers: existingConfig.headers,
                supportedIntents: existingConfig.supportedIntents,
                apiKey: '',
                authType: 'none'
            });
        } else {
            const supportedIntents = isEmbedding
                ? ['embed']
                : ['generate', 'summarize', 'translate'];

            setFormData(prev => ({
                ...prev,
                modelName: modelName,
                modelCategory: modelCategory,
                // Only update endpoint if it's currently empty or was default
                endpoint: (!prev.endpoint || prev.endpoint === initialFormData.endpoint) ? defaultEndpoint : prev.endpoint,
                supportedIntents: supportedIntents
            }));
        }
    };

    return (
        <>
            <Modal show={show} onHide={onHide} size="lg">
                <Modal.Header closeButton>
                    <Modal.Title>
                        <HiServer className="me-2" size={24} style={{ color: '#000000' }} />
                        Ollama Configuration
                        <span className="ms-2 text-muted">- {team.name}</span>
                    </Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleSave}>
                    <Modal.Body>
                        {loading ? (
                            <div className="text-center">
                                <Spinner animation="border" />
                            </div>
                        ) : (
                            <>
                                <Alert variant="info">
                                    Configure local Ollama models for team: <strong>{team.name}</strong>.
                                    <br />
                                    <small>Ensure Ollama is running and accessible (default: <code>http://localhost:11434</code>).</small>
                                </Alert>

                                <Row>
                                    <Col md={12}>
                                        <Form.Group className="mb-3">
                                            <Form.Label>Model Type</Form.Label>
                                            {loadingModels ? (
                                                <Spinner animation="border" size="sm" className="me-2" />
                                            ) : (
                                                <Form.Select
                                                    value={formData.modelName || ''}
                                                    onChange={(e) => handleModelNameChange(e.target.value)}
                                                    required
                                                >
                                                    <option value="">Select a model...</option>
                                                    {availableModels.map(model => {
                                                        const isConfigured = existingConfigs.some(config => config.modelName === model.modelName);
                                                        return (
                                                            <option
                                                                key={model.id}
                                                                value={model.modelName}
                                                                className={isConfigured ? 'text-secondary' : ''}
                                                            >
                                                                {model.displayName} ({model.modelName}) - {model.category}
                                                                {isConfigured ? ' (Configured)' : ''}
                                                            </option>
                                                        );
                                                    })}
                                                </Form.Select>
                                            )}
                                            <Form.Text className="text-muted">
                                                Select the Ollama model to use.
                                            </Form.Text>
                                        </Form.Group>
                                    </Col>
                                </Row>

                                {formData.modelName && (
                                    <Accordion className="mb-3">
                                        <Accordion.Item eventKey="0">
                                            <Accordion.Header>
                                                Configuration Details
                                            </Accordion.Header>
                                            <Accordion.Body>
                                                <Row>
                                                    <Col md={6}>
                                                        <strong>Model Category:</strong>
                                                        <p className="text-muted mb-0">{formData.modelCategory}</p>
                                                    </Col>
                                                    <Col md={6}>
                                                        <strong>Auth Type:</strong>
                                                        <p className="text-muted mb-0">{formData.authType}</p>
                                                    </Col>
                                                </Row>
                                                <hr />
                                                <Row>
                                                    <Col md={12}>
                                                        <strong>Supported Intents:</strong>
                                                        <div className="mt-2">
                                                            {formData.supportedIntents.map(intent => (
                                                                <span key={intent} className="badge bg-primary me-2">{intent}</span>
                                                            ))}
                                                        </div>
                                                    </Col>
                                                </Row>
                                            </Accordion.Body>
                                        </Accordion.Item>
                                    </Accordion>
                                )}

                                <Form.Group className="mb-3">
                                    <Form.Label>Endpoint</Form.Label>
                                    <Form.Control
                                        type="text"
                                        value={formData.endpoint}
                                        onChange={(e) => setFormData({ ...formData, endpoint: e.target.value })}
                                        placeholder="http://localhost:11434/api/chat"
                                        required
                                    />
                                    <Form.Text className="text-muted">
                                        The full URL to the Ollama API endpoint (e.g., /api/chat or /api/embeddings).
                                    </Form.Text>
                                </Form.Group>

                                {/* No API Key for Ollama */}

                                <Form.Group className="mb-3">
                                    <Form.Label>Headers</Form.Label>
                                    <Form.Control
                                        type="text"
                                        value={formData.headers ? Object.entries(formData.headers).map(([key, value]) => `${key}: ${value}`).join('; ') : 'Content-Type: application/json'}
                                        onChange={(e) => {
                                            const headerString = e.target.value;
                                            const headers = {};
                                            if (headerString.trim()) {
                                                headerString.split(';').forEach(pair => {
                                                    const [key, value] = pair.split(':').map(s => s.trim());
                                                    if (key && value) {
                                                        headers[key] = value;
                                                    }
                                                });
                                            }
                                            setFormData({
                                                ...formData,
                                                headers: Object.keys(headers).length > 0 ? headers : {
                                                    'Content-Type': 'application/json'
                                                }
                                            });
                                        }}
                                        placeholder="Content-Type: application/json"
                                    />
                                    <Form.Text className="text-muted">
                                        Enter headers in format "key: value; key2: value2".
                                    </Form.Text>
                                </Form.Group>

                            </>
                        )}
                    </Modal.Body>
                    <Modal.Footer className="d-flex justify-content-between">
                        <div>
                            <Button
                                variant="secondary"
                                onClick={onHide}
                                className="btn-cancel"
                                disabled={loading}
                            >
                                Cancel
                            </Button>
                        </div>
                        <div className="d-flex gap-2">
                            <Button
                                variant="outline-secondary"
                                onClick={() => setFormData({
                                    ...initialFormData,
                                    team: team.name,
                                    teamId: team.id
                                })}
                                disabled={loading}
                            >
                                Reset
                            </Button>
                            <Button
                                variant="primary"
                                type="submit"
                                className="btn-save"
                                loading={loading}
                            >
                                Save Configuration
                            </Button>
                        </div>
                    </Modal.Footer>
                </Form>
                <hr />

                {/* Existing Configurations */}
                <div className="p-3">
                    <h5>Existing Ollama Configurations</h5>
                    {loadingConfigs ? (
                        <div className="text-center py-4">
                            <Spinner animation="border" size="sm" /> Loading configurations...
                        </div>
                    ) : existingConfigs.length === 0 ? (
                        <p className="text-muted">No Ollama configurations for this team yet.</p>
                    ) : (
                        <Table hover className="mt-3">
                            <thead>
                                <tr>
                                    <th>Model Category</th>
                                    <th>Model Type</th>
                                    <th>Endpoint</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {existingConfigs.map(config => (
                                    <tr
                                        key={config.id}
                                        style={{ cursor: 'pointer' }}
                                        onClick={() => handleModelNameChange(config.modelName)}
                                    >
                                        <td>{config.modelCategory || 'ollama-chat'}</td>
                                        <td>{config.modelName || 'N/A'}</td>
                                        <td>
                                            <OverlayTrigger
                                                placement="top"
                                                overlay={<Tooltip>{config.endpoint}</Tooltip>}
                                            >
                                                <code className="text-truncate d-block" style={{ maxWidth: '200px', wordBreak: 'break-all', whiteSpace: 'pre-wrap' }}>
                                                    {config.endpoint || '—'}
                                                </code>
                                            </OverlayTrigger>
                                        </td>
                                        <td onClick={(e) => e.stopPropagation()}>
                                            <Button
                                                variant="outline-danger"
                                                size="sm"
                                                onClick={() => handleDeleteClick(config)}
                                                disabled={loading}
                                                className="d-flex align-items-center gap-2"
                                            >
                                                <HiTrash size={16} />
                                                Remove
                                            </Button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </Table>
                    )}
                </div>

                <ConfirmationModal
                    show={showConfirmRemove}
                    onHide={() => {
                        setShowConfirmRemove(false);
                        setConfigToRemove(null);
                    }}
                    onConfirm={handleConfirmDelete}
                    title="Remove Configuration"
                    message={configToRemove ?
                        `Are you sure you want to remove the ${configToRemove.modelName} (${configToRemove.modelCategory}) configuration?`
                        : ''
                    }
                    confirmLabel="Remove"
                    variant="danger"
                />
            </Modal>
        </>
    );
}

export default OllamaModal;
