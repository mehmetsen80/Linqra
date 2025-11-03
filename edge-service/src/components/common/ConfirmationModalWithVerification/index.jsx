import React, { useState, useEffect } from 'react';
import { Modal, Button, Form } from 'react-bootstrap';
import { HiExclamationCircle } from 'react-icons/hi';
import './styles.css';

const ConfirmationModalWithVerification = ({ 
  show, 
  onHide, 
  onConfirm, 
  title="Confirmation", 
  message="Are you sure you want to confirm?", 
  confirmLabel = 'Yes',
  cancelLabel = 'No',
  variant = 'danger',
  size = 'md'
}) => {
  const [code, setCode] = useState('');
  const [expectedCode, setExpectedCode] = useState('');
  const [codeError, setCodeError] = useState(false);

  useEffect(() => {
    if (show) {
      // Generate a random 6-character code
      const randomCode = Math.random().toString(36).substring(2, 8).toUpperCase();
      setExpectedCode(randomCode);
      setCode('');
      setCodeError(false);
    }
  }, [show]);

  const handleConfirm = () => {
    if (code.toUpperCase() === expectedCode) {
      setCodeError(false);
      onConfirm();
    } else {
      setCodeError(true);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleConfirm();
    }
  };

  return (
    <Modal
      show={show}
      onHide={onHide}
      centered
      size={size}
      animation={true}
      className="confirmation-modal-with-verification"
    >
      <Modal.Header closeButton className="border-0 p-3">
        <Modal.Title className="d-flex align-items-center gap-0">
          {title}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <div className="mb-3">
          {message}
        </div>
        <div className="verification-section">
          <label className="form-label fw-semibold mb-2">
            Enter verification code to confirm:
          </label>
          <div className="text-center mb-3">
            <code className="verification-code">
              {expectedCode}
            </code>
          </div>
          <Form.Group>
            <Form.Control
              type="text"
              placeholder="Enter code"
              value={code}
              onChange={(e) => {
                setCode(e.target.value);
                setCodeError(false);
              }}
              onKeyDown={handleKeyDown}
              isInvalid={codeError}
              className="text-center text-uppercase"
              autoFocus
            />
            {codeError && (
              <Form.Control.Feedback type="invalid">
                Code does not match. Please try again.
              </Form.Control.Feedback>
            )}
          </Form.Group>
        </div>
      </Modal.Body>
      <Modal.Footer className="border-0">
        <div className="d-flex justify-content-end gap-2 w-100">
          <Button 
            variant="light" 
            onClick={onHide}
            className="px-4"
          >
            {cancelLabel}
          </Button>
          <Button 
            variant={variant} 
            onClick={handleConfirm}
            className="px-4"
            disabled={!code || code.toUpperCase() !== expectedCode}
          >
            {confirmLabel}
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
};

export default ConfirmationModalWithVerification;

