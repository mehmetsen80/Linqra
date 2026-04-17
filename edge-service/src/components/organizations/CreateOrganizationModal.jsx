import React, { useState, useEffect } from 'react';
import { Modal, Button, Form, Spinner } from 'react-bootstrap';
import './CreateOrganizationModal.css';

function CreateOrganizationModal({ show, onHide, onSubmit, loading }) {
  const [formData, setFormData] = useState({
    name: '',
    shortName: '',
    description: ''
  });
  const [error, setError] = useState('');
  const [validated, setValidated] = useState(false);

  useEffect(() => {
    if (show) {
      setFormData({
        name: '',
        shortName: '',
        description: ''
      });
      setError('');
      setValidated(false);
    }
  }, [show]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: name === 'shortName' ? value.toUpperCase() : value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setValidated(true);

    const form = e.currentTarget;
    if (!form.checkValidity()) {
      e.stopPropagation();
      return;
    }

    const trimmedName = formData.name.trim();
    if (!trimmedName) {
      setError('Organization name is required');
      return;
    }

    try {
      await onSubmit({
        ...formData,
        name: trimmedName,
        shortName: formData.shortName.trim().toUpperCase(),
        description: formData.description?.trim() || ''
      });
      onHide();
    } catch (err) {
      setError(err.message || 'Failed to create organization');
    }
  };

  return (
    <Modal show={show} onHide={onHide} centered animation={true}>
      <Modal.Header closeButton>
        <Modal.Title>Create New Organization</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <div className="alert alert-danger fade show">
            {error}
          </div>
        )}
        <Form noValidate validated={validated} onSubmit={handleSubmit}>
          <Form.Group className="mb-3">
            <Form.Label>Organization Name</Form.Label>
            <Form.Control
              type="text"
              name="name"
              value={formData.name}
              onChange={handleChange}
              placeholder="Enter organization name"
              required
              minLength={3}
              maxLength={50}
            />
            <Form.Control.Feedback type="invalid">
              Please enter an organization name (3-50 characters)
            </Form.Control.Feedback>
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Short Name</Form.Label>
            <Form.Control
              type="text"
              name="shortName"
              value={formData.shortName}
              onChange={handleChange}
              placeholder="Enter short name (e.g. UOM)"
              style={{ textTransform: 'uppercase' }}
              required
              pattern="^[A-Za-z]+$"
              maxLength={20}
            />
            <Form.Text className="text-muted small">
              Only letters are allowed. No spaces or numbers (max 20 characters).
            </Form.Text>
            <Form.Control.Feedback type="invalid">
              Please enter a valid short name (alpha only, max 20 characters)
            </Form.Control.Feedback>
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Description</Form.Label>
            <Form.Control
              as="textarea"
              name="description"
              value={formData.description}
              onChange={handleChange}
              placeholder="Enter organization description"
              rows={3}
              maxLength={500}
            />
            <Form.Text className="text-muted d-block mt-1 small">
              {500 - (formData.description?.length || 0)} characters remaining
            </Form.Text>
          </Form.Group>
        </Form>
      </Modal.Body>
      
      
      <Modal.Footer className="d-flex justify-content-between">
        <div>
       
            <Button 
            variant="outline-secondary" 
            onClick={onHide}
            disabled={loading}
            className="btn-cancel"
          >
            Cancel
          </Button>
          </div>
          <div>
          <Button 
            variant="primary" 
            onClick={handleSubmit}
            disabled={loading}
            className="ms-auto"
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
                Creating...
              </>
            ) : (
              'Create Organization'
            )}
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
}

export default CreateOrganizationModal; 