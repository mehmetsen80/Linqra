import React, { useState, useEffect } from 'react';
import { Modal, Form, Alert, Spinner } from 'react-bootstrap';
import { HiKey } from 'react-icons/hi';
import { SiOpenai } from 'react-icons/si';
import { linqToolService } from '../../services/linqToolService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import Button from '../../components/common/Button';

const initialFormData = {
  target: 'openai',
  endpoint: 'https://api.openai.com/v1/chat/completions',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  authType: 'bearer',
  apiKey: '',
  supportedIntents: ['generate', 'summarize', 'translate'],
  team: '',
  teamId: ''
};

function OpenAIModal({ show, onHide, team, onTeamUpdate }) {
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState(initialFormData);

  // Reset form when modal is hidden
  useEffect(() => {
    if (!show) {
      setFormData(initialFormData);
    }
  }, [show]);

  // Fetch team configuration when modal is shown
  useEffect(() => {
    if (show && team) {
      setFormData(prev => ({
        ...prev,
        team: team.name,
        teamId: team.id
      }));
      fetchConfiguration();
    }
  }, [show, team]);

  const fetchConfiguration = async () => {
    try {
      setLoading(true);
      const config = await linqToolService.getToolConfiguration(team.id, 'openai');
      if (config) {
        setFormData(prev => ({
          ...prev,
          endpoint: config.endpoint,
          apiKey: config.apiKey,
          supportedIntents: config.supportedIntents
        }));
      }
    } catch (error) {
      console.error('Error fetching configuration:', error);
    } finally {
      setLoading(false);
    }
  };

  if (!team) return null;

  const handleSave = async (e) => {
    e.preventDefault(); // Prevent form submission
    
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
        target: 'openai',
        endpoint: formData.endpoint,
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        authType: 'bearer',
        apiKey: formData.apiKey,
        supportedIntents: formData.supportedIntents,
        team: formData.teamId
      };
      await linqToolService.saveConfiguration(config);
      showSuccessToast('Configuration saved successfully');
      if (onTeamUpdate) {
        await onTeamUpdate();
      }
      onHide();
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

  return (
    <Modal show={show} onHide={onHide} size="lg">
      <Modal.Header closeButton>
        <Modal.Title>
          <SiOpenai className="me-2" size={24} />
          OpenAI Configuration
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
                Configure OpenAI API settings for team: <strong>{team.name}</strong>
              </Alert>

              <Form.Group className="mb-3">
                <Form.Label>API Key</Form.Label>
                <div className="d-flex gap-2">
                  <Form.Control
                    type="password"
                    value={formData.apiKey || ''}
                    onChange={(e) => setFormData({...formData, apiKey: e.target.value})}
                    placeholder="Enter your OpenAI API key"
                    required
                  />
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
                  Your API key will be securely stored and used for OpenAI API requests.
                </Form.Text>
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>Endpoint</Form.Label>
                <Form.Control
                  type="text"
                  value={formData.endpoint || ''}
                  onChange={(e) => setFormData({...formData, endpoint: e.target.value})}
                  placeholder="Enter the OpenAI API endpoint"
                  required
                />
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>Supported Intents</Form.Label>
                <Form.Control
                  type="text"
                  value={(formData.supportedIntents || []).join(', ')}
                  onChange={(e) => {
                    const intents = e.target.value.split(',').map(i => i.trim());
                    setFormData({...formData, supportedIntents: intents});
                  }}
                  placeholder="generate, summarize, translate"
                  required
                />
                <Form.Text className="text-muted">
                  Enter intents separated by commas
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
          <div>
            <Button 
              variant="primary" 
              type="submit"
              className="btn-save"
              loading={loading}
            >
              {team?.linqTools?.some(tool => tool.target === 'openai') ? (
                'Save Configuration'
              ) : (
                'Create OpenAI Configuration'
              )}
            </Button>
          </div>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}

export default OpenAIModal;
