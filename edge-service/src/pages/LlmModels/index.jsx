import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Table, Form, Alert, Spinner, Badge, Breadcrumb, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';
import llmModelService from '../../services/llmModelService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import { useTeam } from '../../contexts/TeamContext';
import Button from '../../components/common/Button';
import './styles.css';
import CreateEditLlmModelModal from '../../components/llmmodels/CreateEditLlmModelModal';

const LlmModels = () => {
  const navigate = useNavigate();
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
    endpoint: '',
    dimensions: null,
    contextWindowTokens: null,
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
        endpoint: model.endpoint || '',
        dimensions: model.dimensions || null,
        contextWindowTokens: model.contextWindowTokens || null,
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
        endpoint: '',
        dimensions: null,
        contextWindowTokens: null,
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
      const payload = {
        ...formData,
        dimensions: formData.dimensions ? Number(formData.dimensions) : null,
        contextWindowTokens: formData.contextWindowTokens ? Number(formData.contextWindowTokens) : null,
        inputPricePer1M: Number(formData.inputPricePer1M),
        outputPricePer1M: Number(formData.outputPricePer1M)
      };

      if (editingModel) {
        await llmModelService.updateModel(editingModel.id, payload);
        showSuccessToast('Model updated successfully');
      } else {
        await llmModelService.createModel(payload);
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
      minimumFractionDigits: 5,
      maximumFractionDigits: 5
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
            <Breadcrumb.Item 
              onClick={() => currentTeam?.id && navigate(`/teams/${currentTeam.id}`)}
              style={{ cursor: currentTeam?.id ? 'pointer' : 'default' }}
            >
              <i className="fas fa-users me-1"></i>
              {currentTeam?.name || 'Team'}
            </Breadcrumb.Item>
            <Breadcrumb.Item active>
              <i className="fas fa-robot me-1"></i>
              LLM Models
            </Breadcrumb.Item>
          </Breadcrumb>
          <div>
            <Button variant="primary" onClick={() => handleShowModal()} className="add-model-btn">
              <i className="fas fa-plus me-2"></i>
              Add Model
            </Button>
            {models.length === 0 && (
              <Button variant="secondary" onClick={handleInitializeDefaults} className="ms-2">
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
                    <th>Endpoint</th>
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
                        <OverlayTrigger
                          placement="top"
                          overlay={
                            <Tooltip>
                              {model.endpoint || 'No endpoint configured'}
                            </Tooltip>
                          }
                        >
                          <code className="model-name-code" style={{ cursor: 'help' }}>
                            {model.modelName}
                          </code>
                        </OverlayTrigger>
                      </td>
                      <td>
                        <OverlayTrigger
                          placement="top"
                          overlay={
                            <Tooltip>
                              {model.endpoint || 'No endpoint configured'}
                            </Tooltip>
                          }
                        >
                          <span style={{ cursor: 'help' }}>
                            {model.displayName || '-'}
                          </span>
                        </OverlayTrigger>
                      </td>
                      <td>
                        <Badge bg={
                          model.provider === 'openai' ? 'primary' :
                          model.provider === 'gemini' ? 'warning' :
                          model.provider === 'anthropic' ? 'info' :
                          model.provider === 'cohere' ? 'success' : 'secondary'
                        }>
                          {model.provider}
                        </Badge>
                      </td>
                      <td>
                        <span className="category-badge">{model.category || 'chat'}</span>
                      </td>
                      <td className="endpoint-cell">
                        <OverlayTrigger
                          placement="top"
                          overlay={
                            <Tooltip>
                              {model.endpoint || 'No endpoint configured'}
                            </Tooltip>
                          }
                        >
                          <small className="text-muted endpoint-text" style={{ cursor: 'help' }}>
                            {model.endpoint ? (
                              model.endpoint.length > 10 ? 
                                `${model.endpoint.slice(0,15)}` : 
                                model.endpoint
                            ) : '-'}
                          </small>
                        </OverlayTrigger>
                      </td>
                      <td className="text-end price-cell">{formatCurrency(model.inputPricePer1M)}</td>
                      <td className="text-end price-cell">{formatCurrency(model.outputPricePer1M)}</td>
                      <td className="text-center">
                        <Badge bg={model.active ? 'success' : 'secondary'}>
                          {model.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className="text-center">
                        <div className="d-flex justify-content-center gap-1">
                          <Button 
                            variant="secondary" 
                            onClick={() => handleShowModal(model)}
                            className="btn-sm"
                          >
                            <i className="fas fa-edit"></i>
                          </Button>
                          <Button 
                            variant="secondary" 
                            onClick={() => handleDelete(model.id, model.modelName)}
                            className="btn-sm"
                          >
                            <i className="fas fa-trash"></i>
                          </Button>
                        </div>
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

      <CreateEditLlmModelModal
        show={showModal}
        onClose={handleCloseModal}
        onSubmit={handleSubmit}
        onChange={handleInputChange}
        formData={formData}
        editingModel={editingModel}
      />
    </Container>
  );
};

export default LlmModels;

