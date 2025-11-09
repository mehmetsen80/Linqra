import React from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Alert } from 'react-bootstrap';
import Button from '../common/Button';

const EditMilvusCollectionMetadataModal = ({
  show,
  collection = null,
  onHide,
  onChange,
  onSave,
  saving = false
}) => {
  if (!collection) {
    return null;
  }

  const aliasValue = (() => {
    const rawAlias = collection.collectionAlias;
    if (typeof rawAlias === 'string' && rawAlias.trim().length > 0) {
      return rawAlias;
    }
    if (typeof collection.name === 'string') {
      return collection.name;
    }
    return '';
  })();

  const isNameLocked = Boolean(collection.nameLocked) ||
    collection.properties?.collectionNameLocked === 'true';

  const collectionTypeValue = (() => {
    if (typeof collection.collectionType === 'string') return collection.collectionType;
    if (typeof collection.properties?.collectionType === 'string') return collection.properties.collectionType;
    return '';
  })();

  const originalCollectionType = collection.originalCollectionType
    ?? collection.properties?.originalCollectionType
    ?? collectionTypeValue;

  const isOriginalCustom = (originalCollectionType ?? '').toUpperCase() === 'CUSTOM';
  const isCollectionTypeEditable = typeof collection.collectionTypeEditable === 'boolean'
    ? collection.collectionTypeEditable
    : isOriginalCustom;

  const trimmedAlias = aliasValue.trim();
  const aliasError = trimmedAlias.length > 0 && !/^[A-Za-z0-9_-]+$/.test(trimmedAlias)
    ? 'Name can only include letters, numbers, hyphens, or underscores.'
    : null;

  return (
    <Modal show={show} onHide={saving ? undefined : onHide} centered>
      <Modal.Header closeButton={!saving}>
        <Modal.Title>Edit Collection Info</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {isNameLocked && (
          <Alert variant="warning" className="mb-3">
            <strong>Heads up:</strong> This collection is referenced by existing agent workflows. The collection name is immutable; you can still update the description for clarity.
          </Alert>
        )}
        <Form>
          <Form.Group className="mb-3">
            <Form.Label>Name</Form.Label>
            <Form.Control
              type="text"
              value={aliasValue}
              onChange={(e) => {
                if (isNameLocked) return;
                onChange({ collectionAlias: e.target.value });
              }}
              disabled={saving || isNameLocked}
              isInvalid={Boolean(aliasError)}
            />
            <Form.Control.Feedback type="invalid">
              {aliasError}
            </Form.Control.Feedback>
            <Form.Text muted>
              Must be unique across RAG collections. Avoid spaces or special characters.
            </Form.Text>
            <Form.Text muted>
              Local alias to help identify this collection (does not rename upstream).
            </Form.Text>
            {isNameLocked && (
              <Form.Text className="text-warning d-block">
                Agents currently reference <code>{collection.name}</code>. The alias is locked to prevent breaking existing workflows.
              </Form.Text>
            )}
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Collection Type</Form.Label>
            <Form.Control
              type="text"
              value={collectionTypeValue}
              onChange={(e) => {
                if (!isCollectionTypeEditable) return;
                onChange({ collectionType: e.target.value });
              }}
              disabled={saving || !isCollectionTypeEditable}
            />
            <Form.Text muted>
              {isCollectionTypeEditable
                ? 'Update the collection type label used for this custom RAG collection.'
                : 'Only custom collections may change their collection type.'}
            </Form.Text>
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Description</Form.Label>
            <Form.Control
              as="textarea"
              rows={3}
              value={collection.collectionDescription ?? ''}
              onChange={(e) => onChange({ collectionDescription: e.target.value })}
              disabled={saving}
            />
          </Form.Group>
        </Form>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={onHide} disabled={saving}>
          Cancel
        </Button>
        <Button variant="primary" onClick={onSave} disabled={saving || Boolean(aliasError)}>
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

EditMilvusCollectionMetadataModal.propTypes = {
  show: PropTypes.bool.isRequired,
  collection: PropTypes.shape({
    name: PropTypes.string.isRequired,
    collectionAlias: PropTypes.string,
    collectionDescription: PropTypes.string,
    nameLocked: PropTypes.bool,
    properties: PropTypes.object
  }),
  onHide: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
  saving: PropTypes.bool
};

export default EditMilvusCollectionMetadataModal;

