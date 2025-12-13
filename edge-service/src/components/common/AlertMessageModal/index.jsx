import React from 'react';
import { Modal, Alert, Button as BootstrapButton } from 'react-bootstrap';
import { HiInformationCircle, HiExclamationCircle, HiXCircle, HiCheckCircle } from 'react-icons/hi';
import Button from '../Button';
import './styles.css';

const AlertMessageModal = ({ 
  show, 
  onHide, 
  title = "Alert", 
  message,
  variant = 'warning', // 'warning', 'danger', 'info', 'success'
  primaryButton = null, // { label: string, onClick: function }
  secondaryButton = null, // { label: string, onClick: function }
  size = 'md'
}) => {
  const getIcon = () => {
    switch (variant) {
      case 'danger':
        return <HiXCircle className="text-danger" size={24} />;
      case 'success':
        return <HiCheckCircle className="text-success" size={24} />;
      case 'info':
        return <HiInformationCircle className="text-info" size={24} />;
      case 'warning':
      default:
        return <HiInformationCircle className="text-warning" size={24} />;
    }
  };

  return (
    <Modal
      show={show}
      onHide={onHide}
      centered
      size={size}
      animation={true}
      className="alert-message-modal"
    >
      <Modal.Header closeButton className="border-0 p-3">
        <Modal.Title className="d-flex align-items-center gap-2">
          {getIcon()}
          {title}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Alert variant={variant} className="mb-0">
          {message}
        </Alert>
      </Modal.Body>
      {(primaryButton || secondaryButton) && (
        <Modal.Footer className="border-0">
          <div className="d-flex justify-content-end gap-2 w-100">
            {secondaryButton && (
              <BootstrapButton 
                variant="light" 
                onClick={secondaryButton.onClick || onHide}
                className="px-4"
              >
                {secondaryButton.label || 'Close'}
              </BootstrapButton>
            )}
            {primaryButton && (
              <Button 
                variant="primary" 
                onClick={primaryButton.onClick}
                className="px-4"
              >
                {primaryButton.label || 'OK'}
              </Button>
            )}
          </div>
        </Modal.Footer>
      )}
    </Modal>
  );
};

export default AlertMessageModal;

