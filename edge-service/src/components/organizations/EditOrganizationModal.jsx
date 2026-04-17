import React, { useState, useEffect } from 'react';
import { Modal, Form } from 'react-bootstrap';
import Button from '../common/Button';

function EditOrganizationModal({ show, onHide, onSubmit, loading, organization }) {
  const [formData, setFormData] = useState({
    name: '',
    shortName: '',
    description: ''
  });
  const [error, setError] = useState('');
  const [validated, setValidated] = useState(false);

  useEffect(() => {
    if (show && organization) {
      setFormData({
        name: organization.name,
        shortName: organization.shortName || '',
        description: organization.description || ''
      });
      setError('');
      setValidated(false);
    }
  }, [show, organization]);

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

    if (!formData.name.trim()) {
      setError('Organization name is required');
      return;
    }

    try {
      await onSubmit({
        ...formData,
        shortName: formData.shortName.trim().toUpperCase()
      });
      onHide();
    } catch (err) {
      setError(err.message || 'Failed to update organization');
    }
  };

  return (
    <Modal show={show} onHide={onHide} centered animation={true}>
      <Modal.Header closeButton>
        <Modal.Title>Edit Organization</Modal.Title>
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
          <Modal.Footer>
            <Button 
              variant="secondary" 
              onClick={onHide}
            >
              Cancel
            </Button>
            <Button 
              variant="primary" 
              type="submit" 
              disabled={loading}
              loading={loading}
            >
              {loading ? 'Updating...' : 'Update'}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal.Body>
    </Modal>
  );
}

export default EditOrganizationModal; 