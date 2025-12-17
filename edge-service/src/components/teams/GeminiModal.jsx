import React, { useState, useEffect } from 'react';
import { Modal, Form, Alert, Spinner, Table, Row, Col, Accordion, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { HiKey, HiTrash, HiEye } from 'react-icons/hi';
import { SiGoogle } from 'react-icons/si';
import { linqLlmModelService } from '../../services/linqLlmModelService';
import llmModelService from '../../services/llmModelService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import Button from '../../components/common/Button';
import ConfirmationModal from '../../components/common/ConfirmationModal';

const initialFormData = {
  modelCategory: 'gemini-chat',
  provider: 'gemini',
  endpoint: '',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  authType: 'api_key_query',
  apiKey: '',
  modelName: '',
  supportedIntents: ['generate', 'summarize', 'translate'],
  teamId: ''
};

const GeminiModal = ({ show, onHide, team, onTeamUpdate }) => {
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState(initialFormData);
  const [existingConfigs, setExistingConfigs] = useState([]);
  const [loadingConfigs, setLoadingConfigs] = useState(false);
  const [availableModels, setAvailableModels] = useState([]);
  const [loadingModels, setLoadingModels] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
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
    }
  }, [show, team]);

  const fetchConfiguration = async () => {
    try {
      setLoading(true);
      // Fetch both 'gemini' and 'gemini-embed' configurations in one call
      const allConfigs = await linqLlmModelService.getLlmModelByModelCategories(team.id, ['gemini-chat', 'gemini-embed']);

      if (allConfigs && allConfigs.length > 0) {
        const config = allConfigs[0]; // Use the first configuration
        const intents = config.supportedIntents || [];
        setFormData(prev => ({
          ...prev,
          modelCategory: config.modelCategory || 'gemini-chat',
          endpoint: config.endpoint,
          authType: config.authType || 'api_key_query',
          apiKey: config.apiKey,
          supportedIntents: intents,
          modelName: config.modelName || ''
        }));
      }
    } catch (error) {
      console.error('Error fetching configuration:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchAvailableModels = async () => {
    try {
      setLoadingModels(true);
      const models = await llmModelService.getModelsByProvider('gemini');
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
      const configs = await linqLlmModelService.getLlmModelByModelCategories(team.id, ['gemini-chat', 'gemini-embed']);
      setExistingConfigs(configs || []);
    } catch (error) {
      console.error('Error fetching existing configurations:', error);
    } finally {
      setLoadingConfigs(false);
    }
  };

  useEffect(() => {
    if (show && team) {
      fetchExistingConfigs();
    }
  }, [show, team]);

  if (!team) return null;

  const handleSave = async (e) => {
    e.preventDefault();

    // Validate required fields
    const requiredFields = {
      endpoint: 'Endpoint',
      apiKey: 'API Key',
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
        provider: 'gemini',
        modelCategory: formData.modelCategory,
        endpoint: formData.endpoint,
        method: 'POST',
        headers: formData.headers,
        authType: formData.authType,
        apiKey: formData.apiKey,
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

  const handleCopyApiKey = () => {
    navigator.clipboard.writeText(formData.apiKey);
    showSuccessToast('API key copied to clipboard');
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

  const handleModelNameChange = async (modelName) => {
    const selectedModel = availableModels.find(model => model.modelName === modelName);

    if (!selectedModel || !selectedModel.endpoint) {
      console.error('Selected model not found or missing endpoint');
      return;
    }

    const category = selectedModel.category.toLowerCase();
    const isEmbedding = category === 'embedding';

    const modelCategory = isEmbedding ? 'gemini-embed' : 'gemini-chat';
    const endpoint = selectedModel.endpoint;
    const supportedIntents = isEmbedding
      ? ['embed']
      : ['generate', 'summarize', 'translate'];
    const headers = {
      'Content-Type': 'application/json'
    };

    const existingConfig = existingConfigs.find(config => config.modelName === modelName);

    setFormData({
      ...formData,
      modelName: modelName,
      modelCategory: existingConfig?.modelCategory || modelCategory,
      endpoint: existingConfig?.endpoint || endpoint,
      headers: existingConfig?.headers || headers,
      supportedIntents: existingConfig?.supportedIntents || supportedIntents,
      apiKey: existingConfig?.apiKey || '',
      authType: existingConfig?.authType || formData.authType
    });
  };

  return (
    <>
      <style>{`
        option.text-secondary {
          color: #6c757d !important;
        }
      `}</style>
      <Modal show={show} onHide={onHide} size="lg">
        <Modal.Header closeButton>
          <Modal.Title>
            <SiGoogle className="me-2" size={24} />
            Gemini Configuration
            <span className="ms-2 text-muted">- {team?.name}</span>
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
                  Configure Gemini API settings for team: <strong>{team?.name}</strong>
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
                            const costs = model.inputPricePer1M && model.outputPricePer1M
                              ? ` ($${model.inputPricePer1M}/$${model.outputPricePer1M} per 1M)`
                              : '';

                            return (
                              <option
                                key={model.id}
                                value={model.modelName}
                                disabled={isConfigured}
                                className={isConfigured ? 'text-secondary' : ''}
                              >
                                {model.displayName} ({model.modelName}) - {model.category}{costs}
                                {isConfigured ? ' (Configured)' : ''}
                              </option>
                            );
                          })}
                        </Form.Select>
                      )}
                      <Form.Text className="text-muted">
                        Select the Gemini model to use for this configuration
                      </Form.Text>
                    </Form.Group>
                  </Col>
                </Row>

                {formData.modelName && (
                  <Accordion className="mb-3">
                    <Accordion.Item eventKey="0">
                      <Accordion.Header style={{ backgroundColor: '#f8f9fa', border: '1px solid #dee2e6' }}>
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
                          <Col md={6}>
                            <strong>Endpoint:</strong>
                            <p className="text-muted mb-0 small">{formData.endpoint}</p>
                          </Col>
                          <Col md={6}>
                            <strong>Supported Intents:</strong>
                            <div className="mt-2">
                              {formData.supportedIntents.map(intent => (
                                <span key={intent} className="badge bg-primary me-2">{intent}</span>
                              ))}
                            </div>
                          </Col>
                        </Row>
                        <hr />
                        <Row>
                          <Col md={12}>
                            <strong>Headers:</strong>
                            <div className="mt-2">
                              {Object.entries(formData.headers || {}).map(([key, value]) => (
                                <span key={key} className="badge bg-secondary me-2">
                                  {key}: {value}
                                </span>
                              ))}
                            </div>
                          </Col>
                        </Row>
                      </Accordion.Body>
                    </Accordion.Item>
                  </Accordion>
                )}

                <Form.Group className="mb-3">
                  <Form.Label>API Key</Form.Label>
                  <div className="d-flex gap-2">
                    <Form.Control
                      type={showApiKey ? 'text' : 'password'}
                      value={formData.apiKey || ''}
                      onChange={(e) => setFormData({ ...formData, apiKey: e.target.value })}
                      placeholder="Enter your Gemini API key"
                      required
                    />
                    <button
                      type="button"
                      className="btn btn-outline-secondary"
                      onClick={() => setShowApiKey(!showApiKey)}
                      disabled={!formData.apiKey}
                    >
                      <HiEye size={16} className="me-1" />
                      {showApiKey ? 'Hide' : 'Show'}
                    </button>
                    <button
                      type="button"
                      className="btn btn-outline-primary"
                      onClick={handleCopyApiKey}
                      disabled={!formData.apiKey}
                    >
                      <HiKey size={16} className="me-1" />
                      Copy
                    </button>
                  </div>
                  <Form.Text className="text-muted">
                    Your API key will be securely stored and used for Gemini API requests.
                  </Form.Text>
                </Form.Group>

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
                disabled={existingConfigs.some(config => config.modelName === formData.modelName)}
              >
                {team?.linqLlmModels?.some(model => model.modelCategory === 'gemini-chat') ? (
                  'Save Configuration'
                ) : (
                  'Create Gemini Configuration'
                )}
              </Button>
            </div>
          </Modal.Footer>
        </Form>
        <hr />

        {/* Existing Configurations */}
        <div className="p-3">
          <h5>Existing Gemini Configurations</h5>
          {loadingConfigs ? (
            <div className="text-center py-4">
              <Spinner animation="border" size="sm" /> Loading configurations...
            </div>
          ) : existingConfigs.length === 0 ? (
            <p className="text-muted">No Gemini configurations for this team yet.</p>
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
                    <td>{config.modelCategory || 'gemini-chat'}</td>
                    <td>{config.modelName || 'N/A'}</td>
                    <td>
                      {config.endpoint && config.endpoint.length > 50 ? (
                        <OverlayTrigger
                          placement="top"
                          overlay={<Tooltip>{config.endpoint}</Tooltip>}
                        >
                          <code className="text-truncate d-block" style={{ maxWidth: '200px' }}>
                            {config.endpoint}
                          </code>
                        </OverlayTrigger>
                      ) : (
                        <code>{config.endpoint}</code>
                      )}
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
};

export default GeminiModal;
