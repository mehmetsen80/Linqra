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
          const result = await handleSSOCallback(code);
          
          if (result?.error) {
            if (result.error === "Code already in use") {
              // Let the AuthContext handle the redirect
              return;
            }
            // Handle other authentication errors
            console.error('Authentication error:', result.error);
            navigate('/login');
            return;
          }
          
          // If we get here, it was a successful login
          navigate('/dashboard');
        } catch (error) {
          console.error('SSO callback error:', error);
          navigate('/login');
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