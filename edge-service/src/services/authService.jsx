import axiosInstance from './axiosInstance';

const authService = {
  registerUser: async (username, email, password) => {
    try {
      // Clear existing auth state before registration
      localStorage.removeItem('authState');
      
      const response = await axiosInstance.post('/api/auth/register', {
        username,
        email,
        password
      });
      const data = response.data;

      if (!data.token || !data.user?.username) {
        console.error('Invalid registration response:', data);
        return { error: 'Invalid response from server' };
      }

      // Create and store auth state, just like in loginUser
      const authState = {
        user: data.user,
        token: data.token,
        refreshToken: data.refreshToken,
        isAuthenticated: true
      };

      // Store auth state as single source of truth
      localStorage.setItem('authState', JSON.stringify(authState));

      return { data: authState };
    } catch (error) {
      console.error('Registration error:', error);
      if (error.response) {
        return { error: error.response.data.message || 'Registration failed' };
      }
      return { error: error.message };
    }
  },

  loginUser: async (username, password) => {
    try {
      // Clear existing auth state before login attempt
      localStorage.removeItem('authState');
      
      const response = await axiosInstance.post('/api/auth/login', {
        username,
        password
      });
      
      const data = response.data;
      
      if (data.token) {
        const authState = {
          user: data.user,
          token: data.token,
          refreshToken: data.refreshToken,
          isAuthenticated: true
        };

        // Store auth state as single source of truth
        localStorage.setItem('authState', JSON.stringify(authState));

        return { data: authState };
      }
      return { error: 'Invalid response from server' };
    } catch (error) {
      console.error('Login error:', error);
      return { error: error.message };
    }
  },

  validatePassword: async (password) => {
    try {
      const response = await axiosInstance.post('/api/users/validate-password', { password });
      const data = response.data;

      return { data };
    } catch (error) {
      console.error('Password validation error:', error);
      if (error.response) {
        return { error: error.response.data.message || 'Password validation failed' };
      }
      return { error: error.message };
    }
  },

  logout: () => {
    // Clear auth state and team selection
    localStorage.removeItem('authState');
    localStorage.removeItem('currentTeamId');
    sessionStorage.clear();
    
    // Force reload to clear any in-memory state
    window.location.href = '/login';
  },

  handleSSOCallback: async (code) => {
    try {
        console.log('Making SSO callback request with code:', code);
        console.log('Current redirect URI:', process.env.REACT_APP_OAUTH2_REDIRECT_URI);
        
        const response = await axiosInstance.post('/api/auth/sso/callback', { code });
        console.log('SSO callback response:', response.data);
        
        return response.data;
    } catch (error) {
        console.error('SSO callback error details:', {
            message: error.message,
            response: error.response?.data,
            status: error.response?.status,
            headers: error.response?.headers,
            config: {
                url: error.config?.url,
                method: error.config?.method,
                headers: error.config?.headers
            }
        });

        if (error.response?.data) {
            console.error('Full error response:', error.response.data);
        }

        if (error.response?.status === 400 && 
            error.response?.data?.message?.includes("Code already in use")) {
            return { 
                error: "Code already in use",
                type: "INVALID_AUTHENTICATION"
            };
        }

        if (error.code === 'ECONNABORTED') {
            return {
                error: "Request timed out. Please try again.",
                type: "TIMEOUT_ERROR"
            };
        }

        throw error;
    }
  },

  refreshToken: async (refreshToken) => {
    try {
      const response = await axiosInstance.post('/api/auth/refresh', {
        refresh_token: refreshToken
      });

      if (response.data?.token) {
        return {
          success: true,
          data: {
            user: response.data.user,
            token: response.data.token,
            refreshToken: response.data.refreshToken
          }
        };
      }
      
      return {
        success: false,
        error: 'Invalid response from server'
      };
    } catch (error) {
      console.error('Token refresh error:', error);
      // If the refresh token is invalid or expired
      if (error.response?.status === 401) {
        authService.logout();
      }
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Failed to refresh token'
      };
    }
  },

  setupAuthInterceptors: () => {} // Empty function or remove entirely
};

export default authService;