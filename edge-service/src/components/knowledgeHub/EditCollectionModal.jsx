import React, { useState, useEffect } from 'react';
import { Modal, Form, Spinner } from 'react-bootstrap';
import Select from 'react-select';
import Button from '../common/Button';
import './styles.css';

const KNOWLEDGE_CATEGORIES = [
  { value: 'TECHNICAL_DOCS', label: 'Technical Documentation', description: 'API docs, SDKs, system architecture' },
  { value: 'CODE_REFERENCE', label: 'Code Reference', description: 'Code snippets, templates, examples' },
  { value: 'TROUBLESHOOTING', label: 'Troubleshooting Guides', description: 'Debug guides, issue resolution, FAQs' },
  { value: 'BUSINESS_DOCS', label: 'Business Documents', description: 'Reports, presentations, analysis' },
  { value: 'RESEARCH_PAPERS', label: 'Research Papers', description: 'Academic papers, industry research' },
  { value: 'MARKET_INTELLIGENCE', label: 'Market Intelligence', description: 'Competitive analysis, trends' },
  { value: 'IMMIGRATION_DOCS', label: 'Immigration Documents', description: 'Visa applications, permits, residency documents' },
  { value: 'TRAINING_MATERIALS', label: 'Training Materials', description: 'Onboarding docs, courses, tutorials' },
  { value: 'BEST_PRACTICES', label: 'Best Practices', description: 'Guidelines, standards, methodologies' },
  { value: 'PRODUCT_DOCS', label: 'Product Documentation', description: 'Specs, requirements, roadmaps' },
  { value: 'USER_GUIDES', label: 'User Guides', description: 'Manuals, how-tos, tutorials' },
  { value: 'POLICIES', label: 'Policies', description: 'Company policies, procedures, governance' },
  { value: 'SOP', label: 'Standard Operating Procedures', description: 'Processes, workflows, checklists' },
  { value: 'ARCHIVES', label: 'Archives', description: 'Historical documents, old files' },
  { value: 'TEMPLATES', label: 'Templates', description: 'Document templates, forms' },
  { value: 'REFERENCE', label: 'Reference Material', description: 'Glossaries, indexes, directories' },
  { value: 'CUSTOM', label: 'Custom', description: 'Custom category' }
];

function EditCollectionModal({ show, onHide, onSave, collection, loading, size = 'lg' }) {
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    categories: []
  });
  const [error, setError] = useState('');
  const [validated, setValidated] = useState(false);

  useEffect(() => {
    if (show && collection) {
      // Convert categories from backend format to react-select format
      const categoryOptions = collection.categories?.map(cat => {
        if (typeof cat === 'string') {
          // If it's just a string, find the matching option from KNOWLEDGE_CATEGORIES
          return KNOWLEDGE_CATEGORIES.find(c => c.value === cat) || { value: cat, label: cat };
        } else {
          // If it's an object, find the matching option
          const categoryValue = cat.name || cat;
          return KNOWLEDGE_CATEGORIES.find(c => c.value === categoryValue) || { value: categoryValue, label: categoryValue };
        }
      }).filter(Boolean) || [];
      
      setFormData({
        name: collection.name || '',
        description: collection.description || '',
        categories: categoryOptions
      });
      setError('');
      setValidated(false);
    }
  }, [show, collection]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleCategoriesChange = (selectedOptions) => {
    setFormData(prev => ({
      ...prev,
      categories: selectedOptions || []
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
      setError('Collection name is required');
      return;
    }

    if (!formData.categories || formData.categories.length === 0) {
      setError('At least one category is required');
      return;
    }

    try {
      // Extract just the category values for submission
      const submissionData = {
        name: formData.name.trim(),
        description: formData.description?.trim() || '',
        categories: formData.categories.map(cat => cat.value)
      };
      await onSave(submissionData);
    } catch (err) {
      setError(err.message || 'Failed to update collection');
    }
  };

  return (
    <Modal show={show} onHide={onHide} centered size={size}>
      <Modal.Header closeButton>
        <Modal.Title>Edit Knowledge Collection</Modal.Title>
      </Modal.Header>
      <Form noValidate validated={validated} onSubmit={handleSubmit}>
        <Modal.Body>
          {error && (
            <div className="alert alert-danger fade show">
              {error}
            </div>
          )}
          
          <Form.Group className="mb-3">
            <Form.Label>Collection Name <span className="text-danger">*</span></Form.Label>
            <Form.Control
              type="text"
              name="name"
              value={formData.name}
              onChange={handleChange}
              required
              placeholder="Enter collection name"
            />
            <Form.Control.Feedback type="invalid">
              Please provide a collection name.
            </Form.Control.Feedback>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Description</Form.Label>
            <Form.Control
              as="textarea"
              rows={3}
              name="description"
              value={formData.description}
              onChange={handleChange}
              placeholder="Enter collection description (optional)"
            />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Categories <span className="text-danger">*</span></Form.Label>
            <Select
              isMulti
              name="categories"
              options={KNOWLEDGE_CATEGORIES}
              value={formData.categories}
              onChange={handleCategoriesChange}
              className="basic-multi-select"
              classNamePrefix="select"
              placeholder="Select categories..."
              closeMenuOnSelect={true}
            />
            {formData.categories.length === 0 && validated && (
              <div className="text-danger small mt-1">
                Please select at least one category
              </div>
            )}
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={onHide} disabled={loading} type="button">
            Cancel
          </Button>
          <Button variant="primary" type="submit" disabled={loading}>
            {loading ? (
              <>
                <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" />
                <span className="ms-2">Updating...</span>
              </>
            ) : (
              'Update Collection'
            )}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}

export default EditCollectionModal;

