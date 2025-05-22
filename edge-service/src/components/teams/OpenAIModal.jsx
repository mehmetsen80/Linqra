import React from 'react';
import { Modal, Card, Row, Col } from 'react-bootstrap';
import { HiKey } from 'react-icons/hi';

function OpenAIModal({ show, onHide, team }) {
  if (!team) return null;

  return (
    <Modal show={show} onHide={onHide} size="lg">
      <Modal.Header closeButton>
        <Modal.Title>
          OpenAI Configuration
          <span className="ms-2 text-muted">- {team.name}</span>
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Row className="g-4">
          <Col md={12}>
            <Card className="border-0 bg-light p-2">
              <Card.Body>
                <div className="d-flex align-items-center mb-3">
                  <HiKey className="text-primary me-2" size={24} />
                  <h5 className="mb-0">API Key</h5>
                </div>
                <div className="d-flex align-items-center">
                  <code className="bg-white px-2 py-1 rounded me-2">
                    {team.openaiKey || 'No API key configured'}
                  </code>
                  <button 
                    className="btn btn-sm btn-outline-primary"
                    onClick={() => {
                      if (team.openaiKey) {
                        navigator.clipboard.writeText(team.openaiKey);
                        showSuccessToast('API key copied to clipboard');
                      }
                    }}
                    disabled={!team.openaiKey}
                  >
                    <HiKey size={16} className="me-1" />
                    Copy
                  </button>
                </div>
              </Card.Body>
            </Card>
          </Col>
        </Row>
      </Modal.Body>
    </Modal>
  );
}

export default OpenAIModal;
