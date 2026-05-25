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

  const renderSafeContent = (payload, filename = "data.json") => {
    if (!payload) return null;
    const jsonStr = typeof payload === "string" ? payload : JSON.stringify(payload, null, 2);
    const isLarge = jsonStr.length > 100000;
    const sizeInKb = (jsonStr.length / 1024).toFixed(1);
    const sizeDisplay = jsonStr.length > 1024 * 1024 
      ? `${(jsonStr.length / (1024 * 1024)).toFixed(2)} MB` 
      : `${sizeInKb} KB`;

    let contentNode;
    if (isLarge) {
      const previewText = jsonStr.substring(0, 5000) + '\n\n... [TRUNCATED - Payload is too large to render dynamically (' + sizeDisplay + ')]';
      contentNode = (
        <pre className="text-dark m-0 font-monospace" style={{ fontSize: '0.8rem', maxHeight: '300px', overflow: 'auto', whiteSpace: 'pre-wrap' }}>
          {previewText}
        </pre>
      );
    } else {
      contentNode = (
        <pre className="m-0 font-monospace text-light" style={{ fontSize: '0.8rem', maxHeight: '480px', overflow: 'auto', whiteSpace: 'pre' }}>
          <code>{jsonStr}</code>
        </pre>
      );
    }

    return (
      <div className={`payload-container rounded mt-2 border ${isLarge ? 'border-warning' : 'border-secondary'}`} style={{ background: isLarge ? '#f8fafc' : '#0f172a' }}>
        <div className={`d-flex justify-content-between align-items-center p-2 px-3 border-bottom ${isLarge ? 'border-warning-subtle bg-warning-subtle' : 'border-secondary bg-dark bg-opacity-70'}`}>
          <span className={`fw-bold small d-flex align-items-center gap-1 ${isLarge ? 'text-warning' : 'text-muted'}`}>
            {isLarge ? '⚠️ Large Payload Bypassed' : '✨ JSON Payload'} ({sizeDisplay})
          </span>
          <div className="d-flex gap-2">
            <button 
              className={`btn btn-sm py-1 font-monospace text-decoration-none ${isLarge ? 'btn-outline-warning' : 'btn-outline-light'}`}
              style={{ fontSize: '0.72rem' }}
              onClick={(e) => {
                copyToClipboard(jsonStr);
                const origText = e.target.innerHTML;
                e.target.innerHTML = "Copied! ✓";
                setTimeout(() => { e.target.innerHTML = origText; }, 1500);
              }}
            >
              <HiClipboardCopy className="me-1" /> Copy Full JSON
            </button>
            <button 
              className={`btn btn-sm py-1 font-monospace text-decoration-none ${isLarge ? 'btn-warning' : 'btn-outline-info'}`}
              style={{ fontSize: '0.72rem' }}
              onClick={() => {
                const blob = new Blob([jsonStr], { type: 'application/json' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                a.click();
                URL.revokeObjectURL(url);
              }}
            >
              💾 Download Full JSON File
            </button>
          </div>
        </div>
        <div className="p-3">
          {contentNode}
        </div>
      </div>
    );
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
              {renderSafeContent(execution.request, "mcp_request_payload.json")}
            </div>
          </Tab>

          <Tab eventKey="response" title="📤 Governed Gateway Response">
            <div className="position-relative mt-2">
              {execution.response ? (
                renderSafeContent(execution.response, "mcp_governed_response.json")
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
