import React from 'react';
import { Modal, Button } from 'react-bootstrap';
import { HiExclamationCircle } from 'react-icons/hi';
import './styles.css';

const ConfirmationModal = ({ 
  show, 
  onHide, 
  onConfirm, 
  title="Confirmation", 
  message="Are you sure you want to confirm?", 
  confirmLabel = 'Yes',
  cancelLabel = 'No',
  variant = 'primary',
  disabled = false
}) => {
  return (
    <Modal
      show={show}
      onHide={onHide}
      centered
      size="lg"
      animation={true}
      className="confirmation-modal"
    >
      <Modal.Header closeButton className="border-0 p-3">
        <Modal.Title className="d-flex align-items-center gap-0">
          {title}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {message}
      </Modal.Body>
      <Modal.Footer className="border-0">
      <div className="d-flex justify-content-end gap-2 w-100">
        <Button 
          variant="light" 
          onClick={onHide}
          className="px-4"
          disabled={disabled}
        >
          {cancelLabel}
        </Button>
        <Button 
          variant={variant} 
          onClick={onConfirm}
          className="px-4"
          disabled={disabled}
        >
          {confirmLabel}
        </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
};

export default ConfirmationModal; 