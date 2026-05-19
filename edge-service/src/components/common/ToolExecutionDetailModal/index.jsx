import React from 'react';
import { Modal, Tabs, Tab, Badge } from 'react-bootstrap';
import { HiCheckCircle, HiXCircle, HiPlay, HiClipboardCopy } from 'react-icons/hi';
import Button from '../Button';
import { showSuccessToast } from '../../../utils/toastConfig';
import './styles.css';

const ToolExecutionDetailModal = ({ show, onHide, execution }) => {
  if (!execution) return null;

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    showSuccessToast("Copied to clipboard!");
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'SUCCESS':
        return <Badge bg="" className="execution-badge success-badge"><HiCheckCircle className="me-1" /> SUCCESS</Badge>;
      case 'FAILED':
        return <Badge bg="" className="execution-badge failed-badge"><HiXCircle className="me-1" /> FAILED</Badge>;
      case 'IN_PROGRESS':
        return <Badge bg="" className="execution-badge progress-badge"><HiPlay className="me-1 pulse-icon" /> RUNNING</Badge>;
      default:
        return <Badge bg="secondary">{status}</Badge>;
    }
  };

  return (
    <Modal
      show={show}
      onHide={onHide}
      centered
      size="xl"
      animation={true}
      className="tool-execution-detail-modal"
    >
      <Modal.Header closeButton>
        <Modal.Title className="d-flex align-items-center gap-3">
          <span className="fw-bold text-dark fs-5">Secure Execution Audit Log</span>
          {getStatusBadge(execution.status)}
        </Modal.Title>
      </Modal.Header>
      
      <Modal.Body className="p-4">
        {/* Metadata Details Bar */}
        <div className="d-flex flex-wrap gap-3 mb-4 bg-light p-3 rounded border align-items-center">
          <div className="small me-3">
            <span className="text-muted fw-semibold">Execution ID:</span> <code className="text-dark font-monospace">{execution.executionId}</code>
          </div>
          <div className="small me-3">
            <span className="text-muted fw-semibold">Triggered By:</span> <code className="text-dark font-monospace">{execution.callerParams?.triggeredBy || 'system'}</code>
          </div>
          <div className="small">
            <span className="text-muted fw-semibold">Latency:</span> <code className="text-dark font-monospace">{execution.durationMs ? `${execution.durationMs}ms` : 'N/A'}</code>
          </div>
        </div>

        {/* JSON Code Tabs */}
        <Tabs defaultActiveKey="request" className="mcp-modal-tabs border-bottom mb-3">
          <Tab eventKey="request" title="📥 Caller Request Payload">
            <div className="position-relative mt-2">
              <Button
                variant="light"
                size="sm"
                className="copy-payload-btn shadow-sm"
                onClick={() => copyToClipboard(JSON.stringify(execution.request, null, 2))}
              >
                <HiClipboardCopy className="me-1" /> Copy Request
              </Button>
              <pre className="json-block p-3 rounded mt-2">
                <code>{JSON.stringify(execution.request, null, 2)}</code>
              </pre>
            </div>
          </Tab>

          <Tab eventKey="response" title="📤 Governed Gateway Response">
            <div className="position-relative mt-2">
              {execution.response ? (
                <>
                  <Button
                    variant="light"
                    size="sm"
                    className="copy-payload-btn shadow-sm"
                    onClick={() => copyToClipboard(JSON.stringify(execution.response, null, 2))}
                  >
                    <HiClipboardCopy className="me-1" /> Copy Response
                  </Button>
                  <pre className="json-block p-3 rounded mt-2">
                    <code>{JSON.stringify(execution.response, null, 2)}</code>
                  </pre>
                </>
              ) : (
                <div className="bg-light border text-center py-4 rounded text-muted mt-2">
                  {execution.errorMessage ? (
                    <div className="p-3 text-start">
                      <h6 className="text-danger fw-bold"><HiXCircle className="me-1" /> Fatal Gatekeeper Error:</h6>
                      <code className="text-danger font-monospace">{execution.errorMessage}</code>
                    </div>
                  ) : (
                    "Execution is currently running - awaiting stream closure."
                  )}
                </div>
              )}
            </div>
          </Tab>
        </Tabs>
      </Modal.Body>

      <Modal.Footer>
        <Button variant="secondary" onClick={onHide} className="px-4 py-2">
          Close Audit Log
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default ToolExecutionDetailModal;
