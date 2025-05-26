import React, { useState, useEffect } from 'react';
import { Modal, Card, Row, Col, Form, Button, Spinner } from 'react-bootstrap';
import { HiKey } from 'react-icons/hi';
import { linqToolService } from '../../services/linqToolService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';

const initialFormData = {
  target: 'openai',
  endpoint: 'https://api.openai.com/v1/chat/completions',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  headersText: JSON.stringify({ 'Content-Type': 'application/json' }, null, 2),
  authType: 'bearer',
  apiKey: '',
  supportedIntents: ['generate', 'summarize', 'translate'],
  team: '',
  teamId: ''
};

function OpenAIModal({ show, onHide, team }) {
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState(initialFormData);
  const [headersText, setHeadersText] = useState(JSON.stringify({ 'Content-Type': 'application/json' }, null, 2));

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
          method: config.method,
          authType: config.authType,
          headers: config.headers,
          apiKey: config.apiKey,
          supportedIntents: config.supportedIntents
        }));
        setHeadersText(JSON.stringify(config.headers, null, 2));
      }
    } catch (error) {
      console.error('Error fetching configuration:', error);
    } finally {
      setLoading(false);
    }
  };

  if (!team) return null;

  const handleSave = async () => {
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
        method: formData.method,
        authType: formData.authType,
        headers: formData.headers,
        apiKey: formData.apiKey,
        supportedIntents: formData.supportedIntents,
        team: formData.teamId
      };
      await linqToolService.saveConfiguration(config);
      showSuccessToast('Configuration saved successfully');
      onHide();
    } catch (error) {
      showErrorToast('Failed to save configuration');
      console.error('Error saving configuration:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleHeadersChange = (e) => {
    setHeadersText(e.target.value);
    try {
      const parsedHeaders = JSON.parse(e.target.value);
      setFormData(prev => ({
        ...prev,
        headers: parsedHeaders
      }));
    } catch (error) {
      // Invalid JSON, don't update formData
    }
  };

  const handleHeadersBlur = () => {
    try {
      const parsedHeaders = JSON.parse(headersText);
      setFormData(prev => ({
        ...prev,
        headers: parsedHeaders
      }));
    } catch (error) {
      showErrorToast('Invalid JSON format for headers');
      // Reset to last valid state
      setHeadersText(JSON.stringify(formData.headers, null, 2));
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
          OpenAI Configuration
          <span className="ms-2 text-muted">- {team.name}</span>
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {loading ? (
          <div className="text-center">
            <Spinner animation="border" />
          </div>
        ) : (
          <Form>
            <Row className="g-4">
              {/* Target Field (Readonly) */}
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Target</Form.Label>
                  <Form.Control
                    type="text"
                    value={formData.target || ''}
                    readOnly
                    disabled
                  />
                </Form.Group>
              </Col>

              {/* Endpoint Field */}
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Endpoint</Form.Label>
                  <Form.Control
                    type="text"
                    value={formData.endpoint || ''}
                    onChange={(e) => setFormData({...formData, endpoint: e.target.value})}
                  />
                </Form.Group>
              </Col>

              {/* Method Field (Readonly) */}
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Method</Form.Label>
                  <Form.Control
                    type="text"
                    value={formData.method || ''}
                    readOnly
                    disabled
                  />
                </Form.Group>
              </Col>

              {/* Auth Type Field (Readonly) */}
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Auth Type</Form.Label>
                  <Form.Control
                    type="text"
                    value={formData.authType || ''}
                    readOnly
                    disabled
                  />
                </Form.Group>
              </Col>

              {/* Headers Field */}
              <Col md={12}>
                <Form.Group>
                  <Form.Label>Headers</Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={3}
                    value={headersText}
                    onChange={handleHeadersChange}
                    onBlur={handleHeadersBlur}
                    placeholder='{
  "Content-Type": "application/json",
  "Custom-Header": "value"
}'
                  />
                  <Form.Text className="text-muted">
                    Enter headers as a JSON object with key-value pairs
                  </Form.Text>
                </Form.Group>
              </Col>

              {/* API Key Field */}
              <Col md={12}>
                <Card className="border-0 bg-light p-2">
                  <Card.Body>
                    <div className="d-flex align-items-center mb-3">
                      <HiKey className="text-primary me-2" size={24} />
                      <h5 className="mb-0">API Key</h5>
                    </div>
                    <div className="d-flex gap-2">
                      <Form.Control
                        type="text"
                        value={formData.apiKey || ''}
                        onChange={(e) => setFormData({...formData, apiKey: e.target.value})}
                        placeholder="Enter your OpenAI API key"
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
                  </Card.Body>
                </Card>
              </Col>

              {/* Supported Intents Field */}
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Supported Intents</Form.Label>
                  <Form.Control
                    type="text"
                    value={(formData.supportedIntents || []).join(', ')}
                    onChange={(e) => {
                      const intents = e.target.value.split(',').map(i => i.trim());
                      setFormData({...formData, supportedIntents: intents});
                    }}
                    placeholder="generate, summarize, translate"
                  />
                  <Form.Text className="text-muted">
                    Enter intents separated by commas
                  </Form.Text>
                </Form.Group>
              </Col>

              {/* Team ID Field (Readonly) */}
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Team ID</Form.Label>
                  <Form.Control
                    type="text"
                    value={formData.teamId || ''}
                    readOnly
                    disabled
                  />
                </Form.Group>
              </Col>
            </Row>
          </Form>
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
            onClick={handleSave}
            className="btn-save"
            disabled={loading}
          >
            {loading ? (
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
            ) : team.linqTools?.some(tool => tool.target === 'openai') ? (
              'Save Configuration'
            ) : (
              'Create OpenAI Configuration'
            )}
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
}

export default OpenAIModal;
