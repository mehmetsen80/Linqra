import axios from 'axios';
import authService from './authService';
import { jwtDecode } from 'jwt-decode';

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_GATEWAY_URL || 'https://localhost:7777',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  }
});

// Request interceptor - proactive token refresh
axiosInstance.interceptors.request.use(
  async (config) => {
    const authData = JSON.parse(localStorage.getItem('authState') || '{}');
    if (authData.token) {
      try {
        const decoded = jwtDecode(authData.token);
        const currentTime = Date.now() / 1000;
        const timeUntilExpiry = decoded.exp - currentTime;
        
        // If token will expire in less than 30 seconds, refresh it
        if (timeUntilExpiry < 30 && !config.url.includes('/api/auth/')) {
          try {
            // The refreshToken call was not properly handling the response
            const { success, data, error } = await authService.refreshToken(authData.refreshToken);
            if (success && data?.token) {
              const newAuthState = {
                user: data.user,
                token: data.token,
                refreshToken: data.refreshToken,
                isAuthenticated: true
              };
              localStorage.setItem('authState', JSON.stringify(newAuthState));
              config.headers.Authorization = `Bearer ${data.token}`;
            } else {
              throw new Error(error || 'Token refresh failed');
            }
          } catch (refreshError) {
            console.error('Token refresh failed:', refreshError);
            authService.logout();
            return Promise.reject('Token refresh failed');
          }
        } else if (decoded.exp < currentTime) {
          // Token is already expired
          console.error('Token expired');
          authService.logout();
          return Promise.reject('Token expired');
        } else {
          // Token is still valid
          config.headers.Authorization = `Bearer ${authData.token}`;
        }
      } catch (error) {
        // Invalid token
        console.error('Invalid token:', error);
        authService.logout();
        return Promise.reject('Invalid token');
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor - handles 401s as fallback
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    
    if (error.response?.status === 401 && 
        !originalRequest._retry && 
        !originalRequest.url.includes('/api/auth/')) {
      
      originalRequest._retry = true;
      
      try {
        const authData = JSON.parse(localStorage.getItem('authState') || '{}');
        if (!authData.refreshToken) {
          throw new Error('No refresh token available');
        }

        const { success, data, error: refreshError } = await authService.refreshToken(authData.refreshToken);
        if (success && data?.token) {
          const newAuthState = {
            user: data.user,
            token: data.token,
            refreshToken: data.refreshToken,
            isAuthenticated: true
          };
          localStorage.setItem('authState', JSON.stringify(newAuthState));
          
          // Update headers for the retry
          axiosInstance.defaults.headers.Authorization = `Bearer ${data.token}`;
          originalRequest.headers.Authorization = `Bearer ${data.token}`;
          
          // Retry the original request
          return axiosInstance(originalRequest);
        } else {
          throw new Error(refreshError || 'Token refresh failed');
        }
      } catch (refreshError) {
        console.error('Token refresh failed:', refreshError);
        authService.logout();
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);

export default axiosInstance; 