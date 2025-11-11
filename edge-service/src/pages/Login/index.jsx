import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Form, Alert } from 'react-bootstrap';
import { HiLockClosed, HiHome } from 'react-icons/hi';
import { useAuth } from '../../contexts/AuthContext';
import Button from '../../components/common/Button';
import './styles.css';

function Login() {
  const { login, handleSSOLogin } = useAuth();
  const [formData, setFormData] = useState({
    email: '',
    password: ''
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
    setError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const { error } = await login(formData.email, formData.password);
      
      if (error) {
        setError(error);
        return;
      }
    } catch (err) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="logo-container">
        <img src="/images/noBgWhiteOnlyLogo.png" alt="Logo" />
      </div>
      <Link to="/" className="home-link">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
            <polyline points="9,22 9,12 15,12 15,22"/>
        </svg>
        Home
      </Link>      
      <div className="auth-form-container">
        <div className="auth-card">
          <div className={loading ? 'opacity-50' : ''}>
            <div className="auth-header">
              <h2>Welcome Back</h2>
                        <p className="text-muted lead "> — Agentic AI Orchestration Platform — design, deploy, and monitor enterprise-grade AI workflows with built-in observability and security.</p>
            </div>
            
            <Button 
              variant="secondary"
              fullWidth
              onClick={handleSSOLogin}
              disabled={loading}
            >
              {loading ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
                  Signing in with SSO...
                </>
              ) : (
                <>
                  <HiLockClosed />
                  Sign in with SSO
                </>
              )}
            </Button>
            
            {/* <div className="separator my-3">
              <span className="separator-text">OR</span>
            </div>

            {error && (
              <Alert variant="danger">{error}</Alert>
            )}

            <Form onSubmit={handleSubmit}>
              <div className="kc-form-group mb-3">
                <Form.Control
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  required
                  autoComplete="email"
                  disabled={loading}
                  className="kc-input"
                  autoFocus
                />
                <Form.Label className="kc-label">Email</Form.Label>
              </div>

              <div className="kc-form-group mb-4">
                <Form.Control
                  type="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  required
                  autoComplete="current-password"
                  disabled={loading}
                  className="kc-input"
                />
                <Form.Label className="kc-label">Password</Form.Label>
              </div>

              <Button 
                type="submit"
                variant="primary"
                fullWidth
                disabled={loading}
              >
                {loading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
                    Signing in...
                  </>
                ) : (
                  'Sign In'
                )}
              </Button>
            </Form> */}


            {/* TODO: Uncomment this when we decide for the registration */}
            {/* <div className="mt-4 text-center">
              <p className="mb-0">
                Don't have an account? <Link to="/register" className="primary-link">Register</Link>
              </p>
            </div> */}
          </div>
        </div>
      </div>
    </div>
  );
}

export default Login;