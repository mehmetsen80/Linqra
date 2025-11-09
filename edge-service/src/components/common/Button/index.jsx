import React from 'react';
import classNames from 'classnames';
import './styles.css';

const Button = React.forwardRef(({ 
  children, 
  variant = 'primary', // 'primary' or 'secondary'
  className, 
  fullWidth,
  loading,
  disabled,
  ...props 
}, ref) => {
  const buttonClasses = classNames(
    'custom-button',
    `custom-button-${variant}`,
    { 'w-100': fullWidth },
    className
  );

  return (
    <button 
      ref={ref}
      className={buttonClasses}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? (
        <>
          <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
          Loading...
        </>
      ) : children}
    </button>
  );
});

Button.displayName = 'Button';

export default Button; 