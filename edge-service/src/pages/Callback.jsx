import React, { useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const Callback = () => {
  const [searchParams] = useSearchParams();
  const { handleSSOCallback } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const processCallback = async () => {
      const code = searchParams.get('code');
      if (code) {
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
  }, [searchParams, handleSSOCallback, navigate]);

  return (
    <div className="callback-container">
      <p>Processing login...</p>
    </div>
  );
};

export default Callback; 