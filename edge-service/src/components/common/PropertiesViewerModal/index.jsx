import React from 'react';
import { Modal, Table } from 'react-bootstrap';
import Button from '../Button';
import './styles.css';

const PropertiesViewerModal = ({ 
  show, 
  onHide, 
  title = "Properties",
  properties = {},
  entityType = null,
  entityName = null,
  loading = false
}) => {
  // Convert properties object to array of key-value pairs
  // Note: Properties passed to this modal should already be filtered, but we filter again for safety
  const excludedKeys = ['id', 'name', 'type', 'teamId', 'documentId', 'extractedAt', 'createdAt', 'updatedAt'];
  const propertyEntries = Object.entries(properties || {})
    .filter(([key]) => !excludedKeys.includes(key))
    .sort(([keyA], [keyB]) => keyA.localeCompare(keyB));
  
  // Debug: Log if properties seem empty (helps identify extraction issues)
  if (propertyEntries.length === 0 && Object.keys(properties || {}).length > 0) {
    console.debug('PropertiesViewerModal: All properties were filtered out. Original properties:', properties);
  }

  const formatValue = (value) => {
    if (value === null || value === undefined) {
      return <span className="text-muted">null</span>;
    }
    if (typeof value === 'boolean') {
      return value.toString();
    }
    if (typeof value === 'number') {
      return value.toLocaleString();
    }
    if (typeof value === 'object') {
      return <code className="small">{JSON.stringify(value, null, 2)}</code>;
    }
    // Display full string value without truncation
    return <code className="small">{String(value)}</code>;
  };

  return (
    <Modal
      show={show}
      onHide={onHide}
      centered
      size="lg"
      animation={true}
      className="properties-viewer-modal"
    >
      <Modal.Header closeButton className="border-0 p-3">
        <Modal.Title className="d-flex align-items-center gap-2">
          {title}
          {entityType && (
            <span className="badge bg-secondary">{entityType}</span>
          )}
          {entityName && (
            <code className="small ms-2 text-muted">{entityName}</code>
          )}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {loading ? (
          <div className="text-center py-4">
            <div className="spinner-border text-primary" role="status">
              <span className="visually-hidden">Decrypting properties...</span>
            </div>
            <p className="mt-2 text-muted">Decrypting properties...</p>
          </div>
        ) : propertyEntries.length === 0 ? (
          <div className="text-center py-4 text-muted">
            No additional properties available
          </div>
        ) : (
          <div className="table-responsive" style={{ maxHeight: '60vh', overflowY: 'auto' }}>
            <Table striped bordered hover size="sm">
              <thead>
                <tr>
                  <th style={{ width: '30%' }}>Property</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                {propertyEntries.map(([key, value]) => (
                  <tr key={key}>
                    <td>
                      <strong>{key}</strong>
                    </td>
                    <td style={{ wordBreak: 'break-word' }}>
                      {formatValue(value)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        )}
      </Modal.Body>
      <Modal.Footer className="border-0">
        <Button 
          variant="secondary" 
          onClick={onHide}
          className="px-4"
        >
          Close
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default PropertiesViewerModal;

