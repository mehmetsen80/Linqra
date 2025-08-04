import React, { useState, useMemo, useEffect } from 'react';
import { Modal } from 'react-bootstrap';
import JSONEditor from 'react-json-editor-ajrm';
import locale from 'react-json-editor-ajrm/locale/en';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { useAuth } from '../../../contexts/AuthContext';
import { useTeam } from '../../../contexts/TeamContext';
import { isSuperAdmin } from '../../../utils/roleUtils';
import './styles.css';
import { apiEndpointService } from '../../../services/apiEndpointService';
import Button from '../../common/Button';

const ViewSwaggerJsonModal = ({ show, onHide, json, onSave, endpoint, onVersionCreated }) => {
  const { user } = useAuth();
  const { currentTeam } = useTeam();
  const [isEditing, setIsEditing] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [validationError, setValidationError] = useState(null);
  const [jsonText, setJsonText] = useState('');
  
  // Parse the JSON if it's a string, otherwise use as is
  const initialJson = useMemo(() => {
    if (typeof json === 'string') {
      try {
        return JSON.parse(json);
      } catch (error) {
        console.error('Failed to parse JSON:', error);
        return {};
      }
    }
    return json;
  }, [json]);

  const [jsonValue, setJsonValue] = useState(initialJson);
  const [originalJson] = useState(initialJson);

  // Update jsonValue when the modal is shown with new json
  useEffect(() => {
    if (show) {
      const parsedJson = typeof json === 'string' ? JSON.parse(json) : json;
      setJsonValue(parsedJson);
      setJsonText(JSON.stringify(parsedJson, null, 2));
      setValidationError(null);
    }
  }, [show, json]);

  // Check if user can edit
  const canEdit = useMemo(() => {
    return isSuperAdmin(user) || 
           (currentTeam?.roles && currentTeam.roles.includes('ADMIN'));
  }, [user, currentTeam]);

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

  const handleSave = async () => {
    setIsLoading(true);
    try {
      // Parse the JSON text to validate it
      const parsedJson = JSON.parse(jsonText);
      
      await apiEndpointService.createNewVersion(endpoint.id, {
        ...endpoint,
        swaggerJson: parsedJson
      });
      
      showSuccessToast('New version created successfully');
      
      if (onVersionCreated) {
        await onVersionCreated();
      }
      
      onHide();
    } catch (error) {
      console.error('Error saving new version:', error);
      showErrorToast(error.response?.data?.message || 'Failed to create new version');
    } finally {
      setIsLoading(false);
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
          Swagger Configuration
          <span className="mode-indicator">
            {isEditing ? '(Edit Mode)' : '(View Mode)'}
          </span>
          {canEdit && (
            <button
              className={`edit-toggle-btn ${isEditing ? 'active' : ''}`}
              onClick={() => {
                setIsEditing(!isEditing);
                if (!isEditing) {
                  setValidationError(null);
                }
              }}
            >
              <i className={`fas fa-${isEditing ? 'eye' : 'edit'}`}></i>
              {isEditing ? ' Switch to View' : ' Switch to Edit'}
            </button>
          )}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {validationError && isEditing && (
          <div className="alert alert-warning mb-3" role="alert">
            <strong>Validation Warning:</strong> {validationError}
            <br />
            <small>You can continue editing, but please fix the JSON format before saving.</small>
          </div>
        )}
        
        {!isEditing && (
          <div className="alert alert-info mb-3" role="alert">
            <i className="fas fa-eye"></i> <strong>View Mode:</strong> JSON is read-only. Click "Switch to Edit" to modify the configuration.
          </div>
        )}
        
        {isEditing ? (
          <div className="json-editor-container">
            <div className="d-flex justify-content-between align-items-center mb-2">
              <small className="text-muted">Edit Swagger JSON configuration</small>
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
              placeholder="Enter Swagger JSON configuration..."
            />
          </div>
        ) : (
          <div className="json-viewer-container">
            <JSONEditor
              placeholder={jsonValue}
              locale={locale}
              height="500px"
              width="100%"
              viewOnly={true}
              theme={{
                background: '#f8f9fa',
                default: '#333',
                string: '#ce9178',
                number: '#b5cea8',
                colon: '#49b4bb',
                keys: '#9cdcfe',
                keys_whiteSpace: '#af74a5',
                primitive: '#6b9955'
              }}
            />
          </div>
        )}
      </Modal.Body>
      <Modal.Footer>
        {isEditing && (
          <div className="w-100 d-flex justify-content-between align-items-center">
            <div>
              <Button
                variant="secondary"
                onClick={() => {
                  setJsonValue(originalJson);
                  setJsonText(JSON.stringify(originalJson, null, 2));
                  setIsEditing(false);
                  setValidationError(null);
                }}
              >
                Cancel
              </Button>
            </div>
            <div>
              <Button 
                variant={validationError ? "warning" : "primary"}
                onClick={handleSave}
                disabled={!isJsonValid() || isLoading}
                loading={isLoading}
                title={validationError ? 'Save anyway (validation errors detected)' : 'Save changes'}
              >
                {validationError ? 'Save Anyway' : 'Save Changes'}
              </Button>
            </div>
          </div>
        )}
      </Modal.Footer>
    </Modal>
  );
};

export default ViewSwaggerJsonModal; 