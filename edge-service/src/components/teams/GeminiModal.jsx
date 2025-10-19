import React, { useState, useEffect } from 'react';
import { Modal, Form, Alert, Spinner } from 'react-bootstrap';
import { HiKey } from 'react-icons/hi';
import { SiGoogle } from 'react-icons/si';
import { linqLlmModelService } from '../../services/linqLlmModelService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import Button from '../../components/common/Button';

const GeminiModal = ({ show, onHide, team }) => {
  const [loading, setLoading] = useState(false);
  const [apiKey, setApiKey] = useState('');
  const [endpoint, setEndpoint] = useState('https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent');
  const [supportedIntents, setSupportedIntents] = useState(['generate', 'summarize', 'translate']);

  useEffect(() => {
    if (team && team.linqLlmModels) {
      const geminiLlmModel = team.linqLlmModels.find(model => model.target === 'gemini');
      if (geminiLlmModel) {
        setApiKey(geminiLlmModel.apiKey || '');
        setEndpoint(geminiLlmModel.endpoint || '');
        setSupportedIntents(geminiLlmModel.supportedIntents || []);
      }
    }
  }, [team]);

  const handleSubmit = async (e) => {
    e.preventDefault();

    // Validate required fields
    const requiredFields = {
      endpoint: 'Endpoint',
      apiKey: 'API Key',
      supportedIntents: 'Supported Intents'
    };

    const missingFields = Object.entries(requiredFields)
      .filter(([key]) => {
        const value = key === 'supportedIntents' ? supportedIntents : apiKey;
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
        target: 'gemini',
        endpoint: endpoint,
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        authType: 'api_key_query',
        apiKey: apiKey,
        supportedIntents: supportedIntents,
        team: team.id
      };
      await linqLlmModelService.saveConfiguration(config);
      showSuccessToast('Configuration saved successfully');
      onHide();
    } catch (error) {
      showErrorToast('Failed to save configuration');
      console.error('Error saving configuration:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCopyApiKey = () => {
    navigator.clipboard.writeText(apiKey);
    showSuccessToast('API key copied to clipboard');
  };

  return (
    <Modal show={show} onHide={onHide} size="lg">
      <Modal.Header closeButton>
        <Modal.Title>
          <SiGoogle className="me-2" size={24} />
          Gemini Configuration
          <span className="ms-2 text-muted">- {team?.name}</span>
        </Modal.Title>
      </Modal.Header>
      <Form onSubmit={handleSubmit}>
        <Modal.Body>
          <Alert variant="info">
            Configure Gemini API settings for team: <strong>{team?.name}</strong>
          </Alert>

          <Form.Group className="mb-3">
            <Form.Label>API Key</Form.Label>
            <div className="d-flex gap-2">
              <Form.Control
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="Enter your Gemini API key"
                required
              />
              <button 
                type="button"
                className="btn btn-outline-primary"
                onClick={handleCopyApiKey}
                disabled={!apiKey}
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
            <Form.Label>Endpoint</Form.Label>
            <Form.Control
              type="text"
              value={endpoint}
              onChange={(e) => setEndpoint(e.target.value)}
              placeholder="Enter the Gemini API endpoint"
              required
            />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Supported Intents</Form.Label>
            <Form.Control
              type="text"
              value={supportedIntents.join(', ')}
              onChange={(e) => setSupportedIntents(e.target.value.split(',').map(intent => intent.trim()))}
              placeholder="Enter supported intents (comma-separated)"
              required
            />
            <Form.Text className="text-muted">
              Example: generate, summarize, translate
            </Form.Text>
          </Form.Group>
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
          <div>
            <Button 
              variant="primary" 
              type="submit"
              className="btn-save"
              loading={loading}
            >
              {team?.linqLlmModels?.some(model => model.target === 'gemini') ? (
                'Save Configuration'
              ) : (
                'Create Gemini Configuration'
              )}
            </Button>
          </div>
        </Modal.Footer>
      </Form>
    </Modal>
  );
};

export default GeminiModal;
