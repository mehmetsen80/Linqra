import React, { useState } from 'react';
import { Alert } from 'react-bootstrap';
import { HiInformationCircle, HiExclamationCircle, HiXCircle, HiCheckCircle } from 'react-icons/hi';
import './styles.css';

/**
 * Reusable banner component for displaying encryption version warnings and other alerts.
 * 
 * @param {boolean} show - Whether to show the banner
 * @param {string} variant - Alert variant: 'warning', 'danger', 'info', 'success'
 * @param {string|React.ReactNode} heading - Banner heading text or custom React node
 * @param {string|React.ReactNode} message - Banner message text or custom React node
 * @param {boolean} dismissible - Whether the banner can be dismissed
 * @param {function} onDismiss - Callback when banner is dismissed
 * @param {string} className - Additional CSS classes
 */
const EncryptionVersionWarningBanner = ({
  show = true,
  variant = 'warning',
  heading,
  message,
  dismissible = false,
  onDismiss,
  className = 'mb-3'
}) => {
  const [visible, setVisible] = useState(show);

  const getIcon = () => {
    switch (variant) {
      case 'danger':
        return <HiXCircle className="me-2" />;
      case 'success':
        return <HiCheckCircle className="me-2" />;
      case 'info':
        return <HiInformationCircle className="me-2" />;
      case 'warning':
      default:
        return <HiInformationCircle className="me-2" />;
    }
  };

  const handleDismiss = () => {
    setVisible(false);
    if (onDismiss) {
      onDismiss();
    }
  };

  if (!show || !visible) {
    return null;
  }

  return (
    <Alert variant={variant} dismissible={dismissible} onClose={dismissible ? handleDismiss : undefined} className={className}>
      {heading && (
        <Alert.Heading>
          {getIcon()}
          {heading}
        </Alert.Heading>
      )}
      {message && (
        <p className="mb-0">
          {message}
        </p>
      )}
    </Alert>
  );
};

export default EncryptionVersionWarningBanner;

