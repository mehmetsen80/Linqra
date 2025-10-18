import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Table, Button, Modal, Form, Alert, Spinner, Badge, Breadcrumb } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import llmModelService from '../../services/llmModelService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import { useTeam } from '../../contexts/TeamContext';
import './styles.css';

const LlmModels = () => {
  const { currentTeam } = useTeam();
  const [models, setModels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [editingModel, setEditingModel] = useState(null);
  const [formData, setFormData] = useState({
    modelName: '',
    displayName: '',
    provider: 'openai',
    category: 'chat',
    inputPricePer1M: 0,
    outputPricePer1M: 0,
    active: true,
    description: ''
  });

  useEffect(() => {
    loadModels();
  }, []);

  const loadModels = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await llmModelService.getAllModels();
      setModels(data);
    } catch (err) {
      console.error('Error loading LLM models:', err);
      setError('Failed to load LLM models');
      showErrorToast('Failed to load LLM models');
    } finally {
      setLoading(false);
    }
  };

  const handleShowModal = (model = null) => {
    if (model) {
      setEditingModel(model);
      setFormData({
        modelName: model.modelName,
        displayName: model.displayName || '',
        provider: model.provider,
        category: model.category || 'chat',
        inputPricePer1M: model.inputPricePer1M,
        outputPricePer1M: model.outputPricePer1M,
        active: model.active,
        description: model.description || ''
      });
    } else {
      setEditingModel(null);
      setFormData({
        modelName: '',
        displayName: '',
        provider: 'openai',
        category: 'chat',
        inputPricePer1M: 0,
        outputPricePer1M: 0,
        active: true,
        description: ''
      });
    }
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingModel(null);
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    try {
      if (editingModel) {
        await llmModelService.updateModel(editingModel.id, formData);
        showSuccessToast('Model updated successfully');
      } else {
        await llmModelService.createModel(formData);
        showSuccessToast('Model created successfully');
      }
      handleCloseModal();
      loadModels();
    } catch (err) {
      console.error('Error saving model:', err);
      showErrorToast(err.response?.data?.message || 'Failed to save model');
    }
  };

  const handleDelete = async (id, modelName) => {
    if (window.confirm(`Are you sure you want to delete "${modelName}"?`)) {
      try {
        await llmModelService.deleteModel(id);
        showSuccessToast('Model deleted successfully');
        loadModels();
      } catch (err) {
        console.error('Error deleting model:', err);
        showErrorToast('Failed to delete model');
      }
    }
  };

  const handleInitializeDefaults = async () => {
    try {
      await llmModelService.initializeDefaultModels();
      showSuccessToast('Default models initialized successfully');
      loadModels();
    } catch (err) {
      console.error('Error initializing defaults:', err);
      showErrorToast('Failed to initialize default models');
    }
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 4
    }).format(amount);
  };

  if (loading) {
    return (
      <Container className="llm-models-container mt-4">
        <div className="text-center py-5">
          <Spinner animation="border" variant="primary" />
          <p className="mt-3">Loading LLM models...</p>
        </div>
      </Container>
    );
  }

  return (
    <Container fluid className="llm-models-container">
      {/* Breadcrumb Navigation */}
      <Card className="breadcrumb-card mb-3">
        <Card.Body className="p-2 d-flex justify-content-between align-items-center">
          <Breadcrumb className="bg-light mb-0">
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/' }}>
              <i className="fas fa-home me-1"></i>
              Home
            </Breadcrumb.Item>
            <Breadcrumb.Item 
              linkAs={Link} 
              linkProps={{ to: '/organizations' }}
            >
              <i className="fas fa-building me-1"></i>
              {currentTeam?.organization?.name || 'Organization'}
            </Breadcrumb.Item>
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/teams' }}>
              <i className="fas fa-users me-1"></i>
              {currentTeam?.name || 'Team'}
            </Breadcrumb.Item>
            <Breadcrumb.Item active>
              <i className="fas fa-robot me-1"></i>
              LLM Models
            </Breadcrumb.Item>
          </Breadcrumb>
          <div>
            <Button variant="primary" onClick={() => handleShowModal()} size="sm" className="add-model-btn">
              <i className="fas fa-plus me-2"></i>
              Add Model
            </Button>
            {models.length === 0 && (
              <Button variant="outline-secondary" onClick={handleInitializeDefaults} size="sm" className="ms-2">
                <i className="fas fa-download me-2"></i>
                Initialize Defaults
              </Button>
            )}
          </div>
        </Card.Body>
      </Card>

      {/* Page Header */}
      <div className="page-header mb-4">
        <p className="page-description text-muted mb-0">
          Manage AI model configurations and pricing for accurate cost tracking
        </p>
      </div>

      {error && (
        <Alert variant="danger" className="mb-4">
          <i className="fas fa-exclamation-triangle me-2"></i>
          {error}
        </Alert>
      )}

      <Card className="models-card">
        <Card.Body>
          {models.length > 0 ? (
            <div className="table-responsive">
              <Table hover className="models-table">
                <thead>
                  <tr>
                    <th>Model Name</th>
                    <th>Display Name</th>
                    <th>Provider</th>
                    <th>Category</th>
                    <th className="text-end">Input Price/1M</th>
                    <th className="text-end">Output Price/1M</th>
                    <th className="text-center">Status</th>
                    <th className="text-center">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {models.map((model) => (
                    <tr key={model.id}>
                      <td>
                        <code className="model-name-code">{model.modelName}</code>
                      </td>
                      <td>{model.displayName || '-'}</td>
                      <td>
                        <Badge bg={
                          model.provider === 'openai' ? 'primary' :
                          model.provider === 'gemini' ? 'warning' :
                          model.provider === 'anthropic' ? 'info' : 'secondary'
                        }>
                          {model.provider}
                        </Badge>
                      </td>
                      <td>
                        <span className="category-badge">{model.category || 'chat'}</span>
                      </td>
                      <td className="text-end price-cell">{formatCurrency(model.inputPricePer1M)}</td>
                      <td className="text-end price-cell">{formatCurrency(model.outputPricePer1M)}</td>
                      <td className="text-center">
                        <Badge bg={model.active ? 'success' : 'secondary'}>
                          {model.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className="text-center">
                        <Button 
                          variant="outline-primary" 
                          size="sm" 
                          onClick={() => handleShowModal(model)}
                          className="me-2"
                        >
                          <i className="fas fa-edit"></i>
                        </Button>
                        <Button 
                          variant="outline-danger" 
                          size="sm" 
                          onClick={() => handleDelete(model.id, model.modelName)}
                        >
                          <i className="fas fa-trash"></i>
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          ) : (
            <Alert variant="info" className="mb-0 text-center">
              <i className="fas fa-info-circle me-2"></i>
              No LLM models found. Click "Initialize Defaults" to add standard models.
            </Alert>
          )}
        </Card.Body>
      </Card>

      {/* Create/Edit Modal */}
      <Modal show={showModal} onHide={handleCloseModal} size="lg">
        <Modal.Header closeButton>
          <Modal.Title>
            {editingModel ? 'Edit LLM Model' : 'Add New LLM Model'}
          </Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleSubmit}>
          <Modal.Body>
            <Row>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Model Name *</Form.Label>
                  <Form.Control
                    type="text"
                    name="modelName"
                    value={formData.modelName}
                    onChange={handleInputChange}
                    placeholder="e.g., gpt-4o, gemini-2.0-flash"
                    required
                    disabled={!!editingModel}
                  />
                  <Form.Text className="text-muted">
                    Unique identifier for the model (cannot be changed after creation)
                  </Form.Text>
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Display Name</Form.Label>
                  <Form.Control
                    type="text"
                    name="displayName"
                    value={formData.displayName}
                    onChange={handleInputChange}
                    placeholder="e.g., GPT-4 Optimized"
                  />
                </Form.Group>
              </Col>
            </Row>

            <Row>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Provider *</Form.Label>
                  <Form.Select
                    name="provider"
                    value={formData.provider}
                    onChange={handleInputChange}
                    required
                  >
                    <option value="openai">OpenAI</option>
                    <option value="gemini">Google Gemini</option>
                    <option value="anthropic">Anthropic</option>
                    <option value="other">Other</option>
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Category *</Form.Label>
                  <Form.Select
                    name="category"
                    value={formData.category}
                    onChange={handleInputChange}
                    required
                  >
                    <option value="chat">Chat/Completion</option>
                    <option value="embedding">Embedding</option>
                    <option value="vision">Vision</option>
                    <option value="audio">Audio</option>
                    <option value="other">Other</option>
                  </Form.Select>
                </Form.Group>
              </Col>
            </Row>

            <Row>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Input Price per 1M Tokens (USD) *</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.001"
                    min="0"
                    name="inputPricePer1M"
                    value={formData.inputPricePer1M}
                    onChange={handleInputChange}
                    required
                  />
                  <Form.Text className="text-muted">
                    Price per 1 million input/prompt tokens
                  </Form.Text>
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Output Price per 1M Tokens (USD) *</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.001"
                    min="0"
                    name="outputPricePer1M"
                    value={formData.outputPricePer1M}
                    onChange={handleInputChange}
                    required
                  />
                  <Form.Text className="text-muted">
                    Price per 1 million output/completion tokens
                  </Form.Text>
                </Form.Group>
              </Col>
            </Row>

            <Form.Group className="mb-3">
              <Form.Label>Description</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="description"
                value={formData.description}
                onChange={handleInputChange}
                placeholder="Optional description of the model"
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Check
                type="checkbox"
                name="active"
                label="Active (include in cost calculations)"
                checked={formData.active}
                onChange={handleInputChange}
              />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer className="d-flex justify-content-between">
            <div>
              <Button variant="secondary" onClick={handleCloseModal}>
                Cancel
              </Button>
            </div>
            <div>
              <Button variant="primary" type="submit">
                {editingModel ? 'Update Model' : 'Create Model'}
              </Button>
            </div>
          </Modal.Footer>
        </Form>
      </Modal>
    </Container>
  );
};

export default LlmModels;

