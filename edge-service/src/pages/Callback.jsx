import React, { useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const Callback = () => {
  const [searchParams] = useSearchParams();
  const { handleSSOCallback } = useAuth();
  const navigate = useNavigate();
  const processedRef = useRef(false);

  useEffect(() => {
    const processCallback = async () => {
      const code = searchParams.get('code');
      if (code && !processedRef.current) {
        processedRef.current = true;
        try {
          await handleSSOCallback(code);
          // If we get here, it was a successful login
          navigate('/dashboard');
        } catch (error) {
          console.error('SSO callback error:', error);
        }
      }
    };

    processCallback();
  }, [searchParams]);

  return (
    <div className="callback-container">
      <p>Processing login...</p>
    </div>
  );
};

export default Callback; 