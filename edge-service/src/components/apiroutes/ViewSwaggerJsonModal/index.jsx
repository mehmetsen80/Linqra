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
    }
  }, [show, json]);

  // Check if user can edit
  const canEdit = useMemo(() => {
    return isSuperAdmin(user) || 
           (currentTeam?.roles && currentTeam.roles.includes('ADMIN'));
  }, [user, currentTeam]);

  const handleEditorChange = (event) => {
    if (event.jsObject && !event.error) {
      setJsonValue(JSON.parse(JSON.stringify(event.jsObject)));
    }
  };

  const handleSave = async () => {
    setIsLoading(true);
    try {
      await apiEndpointService.createNewVersion(endpoint.id, {
        ...endpoint,
        swaggerJson: jsonValue
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
    return jsonValue && Object.keys(jsonValue).length > 0;
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
              onClick={() => setIsEditing(!isEditing)}
            >
              <i className={`fas fa-${isEditing ? 'eye' : 'edit'}`}></i>
              {isEditing ? ' Switch to View' : ' Switch to Edit'}
            </button>
          )}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <JSONEditor
          placeholder={jsonValue}
          locale={locale}
          height="500px"
          width="100%"
          onBlur={handleEditorChange}
          viewOnly={!isEditing}
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
      <Modal.Footer>
        {isEditing && (
          <div className="w-100 d-flex justify-content-between align-items-center">
            <div>
              <Button
                variant="secondary"
                onClick={() => {
                  setJsonValue(originalJson);
                  setIsEditing(false);
                }}
              >
                Cancel
              </Button>
            </div>
            <div>
              <Button 
                variant="primary" 
                onClick={handleSave}
                disabled={!isJsonValid() || isLoading}
                loading={isLoading}
              >
                Save Changes
              </Button>
            </div>
          </div>
        )}
      </Modal.Footer>
    </Modal>
  );
};

export default ViewSwaggerJsonModal; 