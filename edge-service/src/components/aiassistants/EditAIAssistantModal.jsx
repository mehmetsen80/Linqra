import React, { useState, useEffect } from 'react';
import { Modal, Form, Row, Col, Accordion } from 'react-bootstrap';
import Select from 'react-select';
import Button from '../common/Button';
import aiAssistantService from '../../services/aiAssistantService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';

const EditAIAssistantModal = ({
  show,
  onHide,
  assistant,
  onSuccess,
  availableAgentTasks,
  availableModels
}) => {
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    status: 'DRAFT',
    defaultModel: {
      provider: 'openai',
      modelName: '',
      modelCategory: 'openai-chat',
      settings: {
        temperature: 0.7,
        max_tokens: 2000
      }
    },
    systemPrompt: 'You are a helpful AI assistant.',
    selectedTasks: [],
    accessControl: {
      type: 'PRIVATE',
      allowedDomains: []
    },
    contextManagement: {
      strategy: 'sliding_window',
      maxRecentMessages: 10,
      maxTotalTokens: 4000
    },
    guardrails: {
      piiDetectionEnabled: true,
      auditLoggingEnabled: true
    }
  });

  const [updating, setUpdating] = useState(false);

  useEffect(() => {
    if (assistant && show) {
      setFormData({
        name: assistant.name || '',
        description: assistant.description || '',
        status: assistant.status || 'DRAFT',
        defaultModel: assistant.defaultModel || {
          provider: 'openai',
          modelName: '',
          modelCategory: 'openai-chat',
          settings: {
            temperature: 0.7,
            max_tokens: 2000
          }
        },
        systemPrompt: assistant.systemPrompt || 'You are a helpful AI assistant.',
        selectedTasks: assistant.selectedTasks || [],
        accessControl: assistant.accessControl || {
          type: 'PRIVATE',
          allowedDomains: []
        },
        contextManagement: assistant.contextManagement || {
          strategy: 'sliding_window',
          maxRecentMessages: 10,
          maxTotalTokens: 4000
        },
        guardrails: assistant.guardrails || {
          piiDetectionEnabled: true,
          auditLoggingEnabled: true
        }
      });
    }
  }, [assistant, show]);

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    
    if (name.includes('.')) {
      // Nested field (e.g., "defaultModel.modelName")
      const [parent, child] = name.split('.');
      setFormData(prev => ({
        ...prev,
        [parent]: {
          ...prev[parent],
          [child]: type === 'checkbox' ? checked : (type === 'number' ? (value ? Number(value) : null) : value)
        }
      }));
    } else if (name.includes('settings.')) {
      // Settings field (e.g., "settings.temperature")
      const settingKey = name.replace('settings.', '');
      setFormData(prev => ({
        ...prev,
        defaultModel: {
          ...prev.defaultModel,
          settings: {
            ...prev.defaultModel.settings || {},
            [settingKey]: type === 'number' ? (value ? Number(value) : null) : value
          }
        }
      }));
    } else if (name.includes('contextManagement.')) {
      const contextKey = name.replace('contextManagement.', '');
      setFormData(prev => ({
        ...prev,
        contextManagement: {
          ...prev.contextManagement,
          [contextKey]: type === 'number' ? (value ? Number(value) : null) : value
        }
      }));
    } else if (name.includes('guardrails.')) {
      const guardrailKey = name.replace('guardrails.', '');
      setFormData(prev => ({
        ...prev,
        guardrails: {
          ...prev.guardrails,
          [guardrailKey]: checked
        }
      }));
    } else {
      setFormData(prev => ({
        ...prev,
        [name]: type === 'checkbox' ? checked : value
      }));
    }
  };

  const handleProviderChange = (e) => {
    const provider = e.target.value;
    // Filter models by provider and find a default chat model
    const providerModels = availableModels?.filter(m => m.provider === provider) || [];
    const chatModel = providerModels.find(m => 
      m.modelCategory?.includes('chat')
    ) || providerModels[0];

    setFormData(prev => ({
      ...prev,
      defaultModel: {
        ...prev.defaultModel,
        provider,
        modelName: chatModel?.modelName || prev.defaultModel.modelName,
        modelCategory: chatModel?.modelCategory || `${provider}-chat`
      }
    }));
  };

  const handleModelChange = (selectedOption) => {
    if (selectedOption) {
      setFormData(prev => ({
        ...prev,
        defaultModel: {
          ...prev.defaultModel,
          modelName: selectedOption.value,
          modelCategory: selectedOption.modelCategory,
          provider: selectedOption.provider || prev.defaultModel.provider
        }
      }));
    }
  };

  const handleTaskSelection = (selectedOptions) => {
    const selectedTasks = (selectedOptions || []).map(option => ({
      taskId: option.value,
      taskName: option.label
    }));

    setFormData(prev => ({
      ...prev,
      selectedTasks
    }));
  };

  const handleAccessControlChange = (e) => {
    const { name, value, type, checked } = e.target;
    const key = name.replace('accessControl.', '');
    
    setFormData(prev => ({
      ...prev,
      accessControl: {
        ...prev.accessControl,
        [key]: type === 'checkbox' ? checked : value
      }
    }));
  };

  const handleAllowedDomainsChange = (e) => {
    const value = e.target.value || '';
    const domains = value
      .split('\n')
      .map(d => d.trim())
      .filter(Boolean);
    
    setFormData(prev => ({
      ...prev,
      accessControl: {
        ...prev.accessControl,
        allowedDomains: domains
      }
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!formData.name || !formData.description) {
      return;
    }

    try {
      setUpdating(true);
      const response = await aiAssistantService.updateAssistant(assistant.id, formData);
      
      if (response.success) {
        showSuccessToast(response.message || 'AI Assistant updated successfully');
        onSuccess();
      } else {
        showErrorToast(response.error || 'Failed to update AI assistant');
      }
    } catch (error) {
      console.error('Error updating AI assistant:', error);
      showErrorToast(error.response?.data?.message || 'Failed to update AI assistant');
    } finally {
      setUpdating(false);
    }
  };

  // Prepare model options for react-select
  const modelOptions = availableModels
    ?.map(m => ({
      value: m.modelName,
      label: `${m.displayName || m.modelName} (${m.provider}, ${m.modelName})`,
      modelCategory: m.modelCategory,
      provider: m.provider
    })) || [];

  const selectedModelOption = modelOptions.find(
    opt => opt.value === formData.defaultModel?.modelName
  );

  const selectedTaskOptions = availableAgentTasks.filter(task =>
    formData.selectedTasks.some(st => st.taskId === task.value)
  );

  const customSelectStyles = {
    control: (base) => ({
      ...base,
      minHeight: '38px'
    }),
    menuPortal: (base) => ({ ...base, zIndex: 9999 })
  };

  if (!assistant) return null;

  return (
    <Modal show={show} onHide={onHide} centered size="lg">
      <Modal.Header closeButton>
        <Modal.Title>Edit AI Assistant</Modal.Title>
      </Modal.Header>
      <Form onSubmit={handleSubmit}>
        <Modal.Body style={{ maxHeight: '70vh', overflowY: 'auto' }}>
          {/* Basic Information */}
          <Form.Group className="mb-3">
            <Form.Label>Name <span className="text-danger">*</span></Form.Label>
            <Form.Control
              type="text"
              name="name"
              value={formData.name}
              onChange={handleInputChange}
              placeholder="Enter AI assistant name"
              required
            />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Description <span className="text-danger">*</span></Form.Label>
            <Form.Control
              as="textarea"
              rows={3}
              name="description"
              value={formData.description}
              onChange={handleInputChange}
              placeholder="Enter AI assistant description"
              required
            />
          </Form.Group>

          <Row className="mb-3">
            <Col md={6}>
              <Form.Group>
                <Form.Label>Status</Form.Label>
                <Form.Select
                  name="status"
                  value={formData.status}
                  onChange={handleInputChange}
                >
                  <option value="DRAFT">Draft</option>
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </Form.Select>
              </Form.Group>
            </Col>
          </Row>

          <Accordion defaultActiveKey="0" className="mb-3">
            {/* Default Model Configuration */}
            <Accordion.Item eventKey="0">
              <Accordion.Header>Default Model Configuration</Accordion.Header>
              <Accordion.Body>
                <Row>
                  <Col md={6}>
                    <Form.Group className="mb-3">
                      <Form.Label>Provider <span className="text-danger">*</span></Form.Label>
                      <Form.Select
                        name="defaultModel.provider"
                        value={formData.defaultModel?.provider || 'openai'}
                        onChange={handleProviderChange}
                        required
                      >
                        <option value="openai">OpenAI</option>
                        <option value="anthropic">Anthropic</option>
                        <option value="gemini">Google Gemini</option>
                        <option value="cohere">Cohere</option>
                      </Form.Select>
                    </Form.Group>
                  </Col>
                  <Col md={6}>
                    <Form.Group className="mb-3">
                      <Form.Label>Model <span className="text-danger">*</span></Form.Label>
                      <Select
                        options={modelOptions}
                        value={selectedModelOption}
                        onChange={handleModelChange}
                        placeholder="Select a model..."
                        styles={customSelectStyles}
                        menuPortalTarget={document.body}
                        isSearchable
                      />
                    </Form.Group>
                  </Col>
                </Row>

                <Row>
                  <Col md={6}>
                    <Form.Group className="mb-3">
                      <Form.Label>Temperature</Form.Label>
                      <Form.Control
                        type="number"
                        step="0.1"
                        min="0"
                        max="2"
                        name="settings.temperature"
                        value={formData.defaultModel?.settings?.temperature || 0.7}
                        onChange={handleInputChange}
                      />
                      <Form.Text className="text-muted">
                        Controls randomness (0.0 to 2.0)
                      </Form.Text>
                    </Form.Group>
                  </Col>
                  <Col md={6}>
                    <Form.Group className="mb-3">
                      <Form.Label>Max Tokens</Form.Label>
                      <Form.Control
                        type="number"
                        min="1"
                        name="settings.max_tokens"
                        value={formData.defaultModel?.settings?.max_tokens || 2000}
                        onChange={handleInputChange}
                      />
                      <Form.Text className="text-muted">
                        Maximum tokens in response
                      </Form.Text>
                    </Form.Group>
                  </Col>
                </Row>
              </Accordion.Body>
            </Accordion.Item>

            {/* System Prompt */}
            <Accordion.Item eventKey="1">
              <Accordion.Header>System Prompt / Personality</Accordion.Header>
              <Accordion.Body>
                <Form.Group className="mb-3">
                  <Form.Label>System Prompt</Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={4}
                    name="systemPrompt"
                    value={formData.systemPrompt}
                    onChange={handleInputChange}
                    placeholder="You are a helpful AI assistant..."
                  />
                  <Form.Text className="text-muted">
                    Define the assistant's personality and behavior
                  </Form.Text>
                </Form.Group>
              </Accordion.Body>
            </Accordion.Item>

            {/* Selected Agent Tasks */}
            <Accordion.Item eventKey="2">
              <Accordion.Header>Selected Agent Tasks</Accordion.Header>
              <Accordion.Body>
                <Form.Group className="mb-3">
                  <Form.Label>Agent Tasks</Form.Label>
                  <Select
                    isMulti
                    options={availableAgentTasks}
                    value={selectedTaskOptions}
                    onChange={handleTaskSelection}
                    placeholder="Select agent tasks this assistant can execute..."
                    styles={customSelectStyles}
                    menuPortalTarget={document.body}
                    isSearchable
                  />
                  <Form.Text className="text-muted">
                    Choose which Agent Tasks this assistant can execute. Each task uses its own existing configuration and logic.
                  </Form.Text>
                </Form.Group>
              </Accordion.Body>
            </Accordion.Item>

            {/* Access Control */}
            <Accordion.Item eventKey="3">
              <Accordion.Header>Access Control</Accordion.Header>
              <Accordion.Body>
                <Form.Group className="mb-3">
                  <Form.Label>Access Type</Form.Label>
                  <Form.Select
                    name="accessControl.type"
                    value={formData.accessControl?.type || 'PRIVATE'}
                    onChange={handleAccessControlChange}
                  >
                    <option value="PRIVATE">Private (Team Only)</option>
                    <option value="PUBLIC">Public (Widget Deployment)</option>
                  </Form.Select>
                  <Form.Text className="text-muted">
                    Public assistants can be embedded as widgets on external websites
                  </Form.Text>
                </Form.Group>
                <Form.Group className="mb-3">
                  <Form.Label>Allowed Domains (optional)</Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={2}
                    value={(formData.accessControl?.allowedDomains || []).join('\n')}
                    onChange={handleAllowedDomainsChange}
                    placeholder="example.com&#10;app.example.org"
                  />
                  <Form.Text className="text-muted">
                    One domain per line. If left empty, the widget will be allowed on any domain (subject to your backend CORS configuration).
                  </Form.Text>
                </Form.Group>
              </Accordion.Body>
            </Accordion.Item>

            {/* Context Management */}
            <Accordion.Item eventKey="4">
              <Accordion.Header>Context Management</Accordion.Header>
              <Accordion.Body>
                <Row>
                  <Col md={6}>
                    <Form.Group className="mb-3">
                      <Form.Label>Max Recent Messages</Form.Label>
                      <Form.Control
                        type="number"
                        min="1"
                        name="contextManagement.maxRecentMessages"
                        value={formData.contextManagement?.maxRecentMessages || 10}
                        onChange={handleInputChange}
                      />
                      <Form.Text className="text-muted">
                        Number of most recent messages from the conversation to include in each API request. For example, if set to 10, only the last 10 messages (user + assistant pairs) will be sent to the LLM, even if the conversation has 100+ messages stored in the database. However, if these messages exceed the "Max Total Tokens" limit, fewer messages will be included to respect the token budget.
                      </Form.Text>
                    </Form.Group>
                  </Col>
                  <Col md={6}>
                    <Form.Group className="mb-3">
                      <Form.Label>Max Total Tokens</Form.Label>
                      <Form.Control
                        type="number"
                        min="1"
                        name="contextManagement.maxTotalTokens"
                        value={formData.contextManagement?.maxTotalTokens || 4000}
                        onChange={handleInputChange}
                      />
                      <Form.Text className="text-muted">
                        Maximum tokens from conversation history to include in each API request. Full conversation is stored in database, but only this amount will be sent to the LLM per request to manage context window and costs. This works together with "Max Recent Messages" - if the recent messages exceed this token limit, fewer messages will be sent to stay within the budget, prioritizing the most recent messages first.
                      </Form.Text>
                    </Form.Group>
                  </Col>
                </Row>
              </Accordion.Body>
            </Accordion.Item>

            {/* Guardrails */}
            <Accordion.Item eventKey="5">
              <Accordion.Header>Guardrails</Accordion.Header>
              <Accordion.Body>
                <Form.Group className="mb-3">
                  <Form.Check
                    type="checkbox"
                    name="guardrails.piiDetectionEnabled"
                    label="Enable PII Detection"
                    checked={formData.guardrails?.piiDetectionEnabled || false}
                    onChange={handleInputChange}
                  />
                  <Form.Text className="text-muted">
                    Detect and automatically redact personally identifiable information in messages
                  </Form.Text>
                </Form.Group>
                <Form.Group className="mb-3">
                  <Form.Check
                    type="checkbox"
                    name="guardrails.auditLoggingEnabled"
                    label="Enable Audit Logging"
                    checked={formData.guardrails?.auditLoggingEnabled || false}
                    onChange={handleInputChange}
                  />
                  <Form.Text className="text-muted">
                    Log all assistant interactions for audit purposes
                  </Form.Text>
                </Form.Group>
              </Accordion.Body>
            </Accordion.Item>

          </Accordion>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" type="button" onClick={onHide}>
            Cancel
          </Button>
          <Button
            variant="primary"
            type="submit"
            disabled={updating || !formData.name || !formData.description || !formData.defaultModel?.modelName}
          >
            {updating ? 'Updating...' : 'Update AI Assistant'}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
};

export default EditAIAssistantModal;

