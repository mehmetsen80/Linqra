import React, { useState, useEffect } from 'react';
import { Modal } from 'react-bootstrap';
import Button from '../../common/Button';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import './styles.css';
import { apiEndpointService } from '../../../services/apiEndpointService';

const CreateEndpointModal = ({ show, onHide, routeIdentifier, existingSwagger }) => {
  const [loading, setLoading] = useState(false);
  const [validationError, setValidationError] = useState(null);
  const [jsonText, setJsonText] = useState('');
  
  const defaultSwagger = {
    openapi: "3.1.0",
    info: {
      title: "API Endpoint",
      version: "1.0.0",
      description: "API endpoint description",
      contact: {
        name: "API Support",
        email: "support@example.com"
      }
    },
    servers: [
      {
        url: "https://api.example.com",
        description: "Production server"
      }
    ],
    paths: {},
    components: {
      schemas: {},
      securitySchemes: {
        ApiKeyAuth: {
          type: "apiKey",
          in: "header",
          name: "X-API-Key"
        }
      }
    },
    security: [
      {
        ApiKeyAuth: []
      }
    ]
  };

  const [jsonValue, setJsonValue] = useState(existingSwagger || defaultSwagger);

  // Initialize JSON text when modal opens
  useEffect(() => {
    if (show) {
      const initialJson = existingSwagger || defaultSwagger;
      setJsonValue(initialJson);
      setJsonText(JSON.stringify(initialJson, null, 2));
      setValidationError(null);
    }
  }, [show, existingSwagger]);

  const handleTextChange = (event) => {
    const text = event.target.value;
    setJsonText(text);
    
    try {
      // Try to parse the JSON to validate it
      const parsed = JSON.parse(text);
      setJsonValue(parsed);
      setValidationError(null);
    } catch (error) {
      // If parsing fails, don't update the state but don't throw
      console.warn('JSON parsing failed during editing:', error);
      setValidationError(error.message);
    }
  };

  const formatJson = () => {
    try {
      const parsed = JSON.parse(jsonText);
      const formatted = JSON.stringify(parsed, null, 2);
      setJsonText(formatted);
      setValidationError(null);
    } catch (error) {
      setValidationError('Cannot format invalid JSON');
    }
  };

  const isJsonValid = () => {
    try {
      const parsed = JSON.parse(jsonText);
      return parsed && Object.keys(parsed).length > 0;
    } catch (error) {
      return false;
    }
  };

  const handleSubmit = async () => {
    try {
      setLoading(true);
      
      if (!routeIdentifier) {
        showErrorToast('Route identifier is required');
        return;
      }

      // Parse the JSON text to validate it
      const parsedJson = JSON.parse(jsonText);

      // Validate the JSON first
      const isValid = await apiEndpointService.validateSwaggerJson(JSON.stringify(parsedJson));
      if (!isValid) {
        showErrorToast('Invalid OpenAPI/Swagger specification');
        return;
      }

      // Create the endpoint
      await apiEndpointService.createEndpoint(
        routeIdentifier,
        JSON.stringify(parsedJson)
      );

      showSuccessToast('Endpoint created successfully');
      setTimeout(() => {
        onHide();
        window.location.reload();
      }, 1500);
    } catch (error) {
      showErrorToast(`Failed to create endpoint: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      show={show}
      onHide={onHide}
      size="lg"
      centered
      dialogClassName="json-editor-modal"
    >
      <Modal.Header closeButton>
        <Modal.Title>
          Create New Endpoint
          <span className="text-muted ms-2">(OpenAPI/Swagger Specification)</span>
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <div className="mb-3">
          <small className="text-muted">
            Paste your OpenAPI/Swagger JSON specification below. This will be used to generate
            the endpoint documentation and Linq Protocol conversion.
          </small>
        </div>
        
        {validationError && (
          <div className="alert alert-warning mb-3" role="alert">
            <strong>Validation Warning:</strong> {validationError}
            <br />
            <small>You can continue editing, but please fix the JSON format before saving.</small>
          </div>
        )}
        
        <div className="json-editor-container">
          <div className="d-flex justify-content-between align-items-center mb-2">
            <small className="text-muted">Edit OpenAPI/Swagger JSON configuration</small>
            <button
              className="btn btn-sm btn-outline-secondary"
              onClick={formatJson}
              title="Format JSON"
            >
              <i className="fas fa-code"></i> Format
            </button>
          </div>
          <textarea
            className="form-control json-textarea"
            value={jsonText}
            onChange={handleTextChange}
            rows={20}
            style={{
              fontFamily: 'monospace',
              fontSize: '12px',
              lineHeight: '1.4',
              backgroundColor: '#f8f9fa',
              border: '1px solid #dee2e6'
            }}
            placeholder="Enter OpenAPI/Swagger JSON configuration..."
          />
        </div>
      </Modal.Body>

      <Modal.Footer className="d-flex justify-content-between">
        <div>
          <Button 
            variant="secondary" 
            onClick={onHide}
          >
            Cancel
          </Button>
        </div>
        <div>
          <Button 
            variant={validationError ? "warning" : "primary"}
            onClick={handleSubmit}
            loading={loading}
            disabled={!isJsonValid()}
            title={validationError ? 'Save anyway (validation errors detected)' : 'Create endpoint'}
          >
            {validationError ? 'Create Anyway' : 'Create Endpoint'}
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
};

export default CreateEndpointModal; 