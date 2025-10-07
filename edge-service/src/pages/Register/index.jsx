import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Form, Alert } from 'react-bootstrap';
import { HiHome } from 'react-icons/hi';
import { useAuth } from '../../contexts/AuthContext';
import Button from '../../components/common/Button';
import authService from '../../services/authService';
import './styles.css';

function Register() {
  const { register } = useAuth();
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [passwordStrength, setPasswordStrength] = useState({
    isStrong: false,
    error: null
  });
  const [usernameError, setUsernameError] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Username validation
  useEffect(() => {
    if (formData.username) {
      setUsernameError(
        formData.username.length < 6 
          ? 'Username must be at least 6 characters long'
          : ''
      );
    } else {
      setUsernameError('');
    }
  }, [formData.username]);

  // Password validation
  useEffect(() => {
    const validatePassword = async () => {
      if (formData.password) {
        try {
          const { data, error } = await authService.validatePassword(formData.password);
          if (error) {
            setPasswordStrength({ isStrong: false, error });
            return;
          }
          setPasswordStrength(data);
        } catch (err) {
          setPasswordStrength({ 
            isStrong: false, 
            error: 'Password validation failed' 
          });
        }
      } else {
        setPasswordStrength({ isStrong: false, error: null });
      }
    };
    
    const timeoutId = setTimeout(validatePassword, 500);
    return () => clearTimeout(timeoutId);
  }, [formData.password]);

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

    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match');
      setLoading(false);
      return;
    }

    if (!passwordStrength.isStrong) {
      setError('Please ensure your password meets all requirements');
      setLoading(false);
      return;
    }

    try {
      const { error } = await register(
        formData.username,
        formData.email,
        formData.password
      );
      
      if (error) {
        setError(error);
        return;
      }
    } catch (err) {
      setError(err.message || 'Registration failed');
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
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
            <polyline points="9,22 9,12 15,12 15,22"/>
        </svg>
        Home
      </Link>
      <div className="auth-form-container">
        <div className="auth-card">
          <div className={loading ? 'opacity-50' : ''}>
            <div className="auth-header">
              <h2>Create Account</h2>
              <p className="text-muted">Join us to monitor your microservices</p>
            </div>

            {error && (
              <Alert variant="danger">{error}</Alert>
            )}

            <Form onSubmit={handleSubmit}>
              <div className="kc-form-group mb-3">
                <Form.Control
                  type="text"
                  name="username"
                  value={formData.username}
                  onChange={handleChange}
                  required
                  disabled={loading}
                  className="kc-input"
                  autoFocus
                />
                <Form.Label className="kc-label">Username</Form.Label>
                {usernameError && (
                  <Form.Text className="text-danger">
                    {usernameError}
                  </Form.Text>
                )}
              </div>

              <div className="kc-form-group mb-3">
                <Form.Control
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  required
                  disabled={loading}
                  className="kc-input"
                />
                <Form.Label className="kc-label">Email</Form.Label>
              </div>

              <div className="kc-form-group mb-3">
                <Form.Control
                  type="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  required
                  disabled={loading}
                  className="kc-input"
                />
                <Form.Label className="kc-label">Password</Form.Label>
                {formData.password && (
                  <div className="password-strength-container">
                    <div className={`password-strength ${passwordStrength.isStrong ? 'strong' : 'weak'}`}>
                      {passwordStrength.error || (passwordStrength.isStrong ? 'Password is strong' : 'Password is weak')}
                    </div>
                  </div>
                )}
              </div>

              <div className="kc-form-group mb-4">
                <Form.Control
                  type="password"
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  required
                  disabled={loading}
                  className="kc-input"
                />
                <Form.Label className="kc-label">Confirm Password</Form.Label>
              </div>

              <Button 
                type="submit"
                variant="primary"
                fullWidth
                disabled={loading || usernameError || !passwordStrength.isStrong}
              >
                {loading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
                    Creating Account...
                  </>
                ) : (
                  'Create Account'
                )}
              </Button>
            </Form>

            <div className="mt-4 text-center">
              <p className="mb-0">
                Already have an account? <Link to="/login" className="primary-link">Login</Link>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Register;