import React, { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Button, Spinner, Row, Col, InputGroup } from 'react-bootstrap';
import { HiPlus, HiTrash } from 'react-icons/hi';
import './styles.css';

const DATA_TYPE_OPTIONS = [
  { value: 'INT64', label: 'Int64', supportsPrimary: true },
  { value: 'INT32', label: 'Int32', supportsPrimary: true },
  { value: 'INT16', label: 'Int16', supportsPrimary: true },
  { value: 'INT8', label: 'Int8', supportsPrimary: true },
  { value: 'VARCHAR', label: 'VarChar', requiresMaxLength: true },
  { value: 'STRING', label: 'String', requiresMaxLength: false },
  { value: 'FLOAT', label: 'Float', supportsPrimary: false },
  { value: 'DOUBLE', label: 'Double', supportsPrimary: false },
  { value: 'BOOL', label: 'Boolean', supportsPrimary: false },
  { value: 'JSON', label: 'JSON', supportsPrimary: false },
  { value: 'FLOAT_VECTOR', label: 'Float Vector', requiresDimension: true },
  { value: 'BINARY_VECTOR', label: 'Binary Vector', requiresDimension: true }
];

const DEFAULT_FIELDS = [
  { id: 1, name: 'id', dtype: 'INT64', is_primary: true },
  { id: 2, name: 'embedding', dtype: 'FLOAT_VECTOR', dim: 1536 }
];

const CreateCustomMilvusCollectionModal = ({
  show,
  onHide,
  onCreate,
  teamId,
  existingCollectionNames = [],
  creating = false
}) => {
  const [collectionName, setCollectionName] = useState('');
  const [description, setDescription] = useState('');
  const [fields, setFields] = useState(() => DEFAULT_FIELDS.map(field => ({ ...field })));
  const [properties, setProperties] = useState([{ key: 'collectionType', value: 'CUSTOM' }]);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!show) {
      setCollectionName('');
      setDescription('');
      setFields(DEFAULT_FIELDS.map(field => ({ ...field })));
      setProperties([{ key: 'collectionType', value: 'CUSTOM' }]);
      setError(null);
    }
  }, [show]);

  const addField = () => {
    setFields(prev => ([
      ...prev,
      {
        id: Date.now(),
        name: '',
        dtype: 'VARCHAR',
        max_length: 256,
        is_primary: false
      }
    ]));
  };

  const removeField = (id) => {
    if (fields.length <= 1) return;
    setFields(prev => prev.filter(field => field.id !== id));
  };

  const updateField = (id, updates) => {
    setFields(prev => prev.map(field => field.id === id ? { ...field, ...updates } : field));
  };

  const addProperty = () => {
    setProperties(prev => ([...prev, { key: '', value: '' }]));
  };

  const updateProperty = (index, updates) => {
    setProperties(prev => prev.map((prop, idx) => idx === index ? { ...prop, ...updates } : prop));
  };

  const removeProperty = (index) => {
    setProperties(prev => prev.filter((_, idx) => idx !== index));
  };

  const validate = () => {
    if (!collectionName.trim()) {
      return 'Collection name is required.';
    }

    if (existingCollectionNames.includes(collectionName.trim())) {
      return 'A collection with this name already exists.';
    }

    const hasPrimary = fields.some(field => field.is_primary);
    if (!hasPrimary) {
      return 'Define at least one primary key field.';
    }

    const vectorFields = fields.filter(field => field.dtype === 'FLOAT_VECTOR' || field.dtype === 'BINARY_VECTOR');
    if (vectorFields.length === 0) {
      return 'At least one vector field (FLOAT_VECTOR or BINARY_VECTOR) is required.';
    }

    for (const field of fields) {
      if (!field.name || !field.name.trim()) {
        return 'All fields must have a name.';
      }
      if (field.dtype === 'VARCHAR' && (!field.max_length || field.max_length <= 0)) {
        return `Field "${field.name}" requires a maximum length.`;
      }
      if ((field.dtype === 'FLOAT_VECTOR' || field.dtype === 'BINARY_VECTOR') && (!field.dim || field.dim <= 0)) {
        return `Field "${field.name}" requires a vector dimension.`;
      }
    }

    return null;
  };

  const handleSubmit = () => {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const schemaFields = fields.map(({ id, ...rest }) => ({ ...rest }));
    const filteredProperties = properties
      .filter(prop => prop.key && prop.key.trim())
      .reduce((acc, prop) => {
        acc[prop.key.trim()] = prop.value ?? '';
        return acc;
      }, {});

    const payload = {
      collectionName: collectionName.trim(),
      description: description.trim(),
      schemaFields,
      teamId,
      collectionType: 'CUSTOM',
      properties: filteredProperties
    };

    onCreate(payload);
  };

  const renderFieldControls = (field) => {
    const dataTypeMeta = DATA_TYPE_OPTIONS.find(option => option.value === field.dtype) || {};

    return (
      <Row className="g-2" key={field.id}>
        <Col lg={4}>
          <Form.Control
            type="text"
            placeholder="Field name"
            value={field.name}
            onChange={(e) => updateField(field.id, { name: e.target.value })}
            disabled={creating}
          />
        </Col>
        <Col lg={4}>
          <Form.Select
            value={field.dtype}
            onChange={(e) => updateField(field.id, { dtype: e.target.value })}
            disabled={creating}
          >
            {DATA_TYPE_OPTIONS.map(option => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </Form.Select>
        </Col>
        <Col lg={2} className="dimension-column">
          {dataTypeMeta.requiresMaxLength ? (
            <Form.Control
              type="number"
              value={field.max_length ?? ''}
              placeholder="Max length"
              min={1}
              onChange={(e) => updateField(field.id, { max_length: Number(e.target.value) })}
              disabled={creating}
            />
          ) : dataTypeMeta.requiresDimension ? (
            <Form.Control
              type="number"
              value={field.dim ?? ''}
              placeholder="Dimension"
              min={1}
              onChange={(e) => updateField(field.id, { dim: Number(e.target.value) })}
              disabled={creating}
            />
          ) : (
            <Form.Control plaintext readOnly value="—" />
          )}
        </Col>
        <Col lg={1} className="d-flex align-items-center justify-content-center">
          <Form.Check
            type="switch"
            id={`primary-${field.id}`}
            label="Primary"
            className="primary-switch"
            checked={Boolean(field.is_primary)}
            onChange={(e) => updateField(field.id, { is_primary: e.target.checked })}
            disabled={creating || !dataTypeMeta.supportsPrimary}
          />
        </Col>
        <Col lg={1} className="d-flex align-items-center justify-content-end">
          <Button
            variant="outline-danger"
            size="sm"
            onClick={() => removeField(field.id)}
            disabled={creating || fields.length <= 1}
          >
            <HiTrash />
          </Button>
        </Col>
      </Row>
    );
  };

  return (
    <Modal show={show} onHide={creating ? undefined : onHide} size="lg" centered>
      <Modal.Header closeButton={!creating}>
        <Modal.Title>Create Custom Milvus Collection</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <Form.Text className="text-danger d-block mb-3">{error}</Form.Text>
        )}
        <Form>
          <Form.Group className="mb-3">
            <Form.Label>Collection Name</Form.Label>
            <Form.Control
              type="text"
              value={collectionName}
              onChange={(e) => setCollectionName(e.target.value)}
              placeholder="e.g., my_team_custom_collection"
              disabled={creating}
              autoFocus
            />
            <Form.Text muted>Must be unique in Milvus. Avoid spaces or special characters.</Form.Text>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Description</Form.Label>
            <Form.Control
              as="textarea"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={creating}
              placeholder="Optional description for this collection"
            />
          </Form.Group>

          <div className="d-flex align-items-center justify-content-between mb-2">
          <Form.Label className="mb-0">Schema Fields</Form.Label>
          <Button
            variant="link"
            className="px-0 add-field-link"
            style={{ minWidth: '100px', color: 'var(--primary-color)' }}
            onClick={addField}
            disabled={creating}
          >
            <HiPlus className="me-1" /> Add Field
          </Button>
          </div>

          <div className="d-flex flex-column gap-3 mb-4">
            {fields.map(renderFieldControls)}
          </div>

          <Form.Label>Collection Properties</Form.Label>
          <div className="d-flex flex-column gap-2">
            {properties.map((property, index) => (
              <InputGroup key={`${property.key}-${index}`}>
                <Form.Control
                  placeholder="Property key"
                  value={property.key}
                  onChange={(e) => updateProperty(index, { key: e.target.value })}
                  disabled={creating}
                />
                <Form.Control
                  placeholder="Property value"
                  value={property.value}
                  onChange={(e) => updateProperty(index, { value: e.target.value })}
                  disabled={creating}
                />
                <Button
                  variant="outline-danger"
                  onClick={() => removeProperty(index)}
                  disabled={creating || properties.length <= 1}
                >
                  <HiTrash />
                </Button>
              </InputGroup>
            ))}
          </div>
          <Button
            variant="link"
            className="px-0 mt-2 add-property-link"
            style={{ minWidth: '100px', color: 'var(--primary-color)' }}
            onClick={addProperty}
            disabled={creating}
          >
            <HiPlus className="me-1" /> Add Property
          </Button>
        </Form>
      </Modal.Body>
      <Modal.Footer className="d-flex justify-content-between">
        <div>
          <Button variant="secondary" onClick={onHide} disabled={creating}>
            Cancel
          </Button>
        </div>
        <div>
          <Button variant="primary" onClick={handleSubmit} disabled={creating}>
            {creating ? (
              <>
                <Spinner as="span" animation="border" size="sm" className="me-2" />
                Creating...
              </>
            ) : (
              'Create Collection'
            )}
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
};

CreateCustomMilvusCollectionModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onHide: PropTypes.func.isRequired,
  onCreate: PropTypes.func.isRequired,
  teamId: PropTypes.string,
  existingCollectionNames: PropTypes.arrayOf(PropTypes.string),
  creating: PropTypes.bool
};

export default CreateCustomMilvusCollectionModal;

