import React, { useState } from 'react';
import { Modal } from 'react-bootstrap';
import JSONEditor from 'react-json-editor-ajrm';
import locale from 'react-json-editor-ajrm/locale/en';
import Button from '../../common/Button';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import './styles.css';
import { apiEndpointService } from '../../../services/apiEndpointService';

const CreateEndpointModal = ({ show, onHide, routeIdentifier, existingSwagger }) => {
  const [loading, setLoading] = useState(false);
  const [jsonValue, setJsonValue] = useState(existingSwagger || {
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
  });

  const handleEditorChange = (event) => {
    if (event.jsObject && !event.error) {
      setJsonValue(JSON.parse(JSON.stringify(event.jsObject)));
    }
  };

  const isJsonValid = () => {
    return jsonValue && Object.keys(jsonValue).length > 0;
  };

  const handleSubmit = async () => {
    try {
      setLoading(true);
      
      if (!routeIdentifier) {
        showErrorToast('Route identifier is required');
        return;
      }

      // Validate the JSON first
      const isValid = await apiEndpointService.validateSwaggerJson(JSON.stringify(jsonValue));
      if (!isValid) {
        showErrorToast('Invalid OpenAPI/Swagger specification');
        return;
      }

      // Create the endpoint
      await apiEndpointService.createEndpoint(
        routeIdentifier,
        JSON.stringify(jsonValue)
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
        <JSONEditor
          placeholder={jsonValue}
          locale={locale}
          height="500px"
          width="100%"
          onBlur={handleEditorChange}
          theme={{
            background: '#f8f9fa',
            default: '#1e1e1e',
            string: '#ce9178',
            number: '#b5cea8',
            colon: '#49b4bb',
            keys: '#9cdcfe',
            keys_whiteSpace: '#af74a5',
            primitive: '#6b9955'
          }}
        />
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
            variant="primary" 
            onClick={handleSubmit}
            loading={loading}
            disabled={!isJsonValid()}
          >
            Create Endpoint
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
};

export default CreateEndpointModal; 