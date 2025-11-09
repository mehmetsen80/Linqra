import React from 'react';
import PropTypes from 'prop-types';
import { Modal, Table, Badge, Alert } from 'react-bootstrap';

const MilvusCollectionDetailsModal = ({ show, onHide, result = null }) => {
  if (!result) {
    return null;
  }

  const { name, teamId, vectorDimension, vectorFieldName, rowCount, description, valid, issues = [], schema = [] } = result;

  return (
    <Modal show={show} onHide={onHide} size="lg" centered>
      <Modal.Header closeButton>
        <Modal.Title className="d-flex align-items-center gap-2">
          Milvus Collection Details
          <Badge bg={valid ? 'success' : 'danger'}>{valid ? 'Valid' : 'Issues Found'}</Badge>
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <div className="mb-3">
          <div className="d-flex justify-content-between align-items-center">
            <div>
              <div className="fw-semibold">{name}</div>
              <div className="text-muted small">Team: {teamId || 'Unknown'}</div>
            </div>
            <div className="text-end small text-muted">
              <div>Vector field: {vectorFieldName || 'Unknown'}</div>
              <div>Dimension: {vectorDimension ?? 'Unknown'}</div>
              <div>Row count: {rowCount ?? 'Unknown'}</div>
            </div>
          </div>
          {description && (
            <div className="mt-2 text-muted small">{description}</div>
          )}
        </div>

        {issues.length > 0 ? (
          <Alert variant="warning">
            <div className="fw-semibold mb-1">Schema Issues</div>
            <ul className="mb-0">
              {issues.map((issue, idx) => (
                <li key={idx}>{issue}</li>
              ))}
            </ul>
          </Alert>
        ) : (
          <Alert variant="success">
            Schema matches the Knowledge Hub requirements.
          </Alert>
        )}

        <Table bordered hover size="sm" className="mt-3">
          <thead>
            <tr>
              <th>Field</th>
              <th>Data Type</th>
              <th>Primary</th>
              <th>Type Params</th>
            </tr>
          </thead>
          <tbody>
            {schema.map(field => (
              <tr key={field.name}>
                <td>{field.name}</td>
                <td>{field.dataType}</td>
                <td>{field.primary ? 'Yes' : 'No'}</td>
                <td>
                  {field.typeParams && Object.keys(field.typeParams).length > 0 ? (
                    <div className="small text-muted">
                      {Object.entries(field.typeParams).map(([key, value]) => (
                        <div key={key}>{key}: {value}</div>
                      ))}
                    </div>
                  ) : (
                    <span className="text-muted small">-</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Modal.Body>
    </Modal>
  );
};

MilvusCollectionDetailsModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onHide: PropTypes.func.isRequired,
  result: PropTypes.shape({
    name: PropTypes.string,
    teamId: PropTypes.string,
    vectorDimension: PropTypes.number,
    vectorFieldName: PropTypes.string,
    rowCount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    description: PropTypes.string,
    valid: PropTypes.bool,
    issues: PropTypes.arrayOf(PropTypes.string),
    schema: PropTypes.arrayOf(PropTypes.shape({
      name: PropTypes.string,
      dataType: PropTypes.string,
      primary: PropTypes.bool,
      typeParams: PropTypes.object,
      maxLength: PropTypes.number
    }))
  })
};

export default MilvusCollectionDetailsModal;


