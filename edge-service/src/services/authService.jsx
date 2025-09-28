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
    // Get the current auth state before clearing it
    const authState = JSON.parse(localStorage.getItem('authState') || '{}');
    const wasSSO = authState.isSSO;
    
    // Get the id_token from session storage before clearing anything
    const idToken = sessionStorage.getItem('id_token');
    
    // Clear auth state and team selection
    localStorage.removeItem('authState');
    localStorage.removeItem('currentTeamId');
    localStorage.removeItem('lastLoginTime');
    
    // Clear all session storage
    sessionStorage.clear();
    
    if (wasSSO) {
      // If we have an id_token, use it for logout
      if (idToken) {
        // TODO: Multi-application logout scenarios
        // In a multi-application setup (e.g., Linqra Web, Linqra Mobile, Linqra Admin):
        // 1. Each application would have its own client_id in Keycloak
        // 2. Each application would define its name in environment variables:
        //    REACT_APP_APPLICATION_NAME=Linqra Web
        //    REACT_APP_APPLICATION_ID=linqra-web
        // 3. The logout_hint parameter would be used to identify which app initiated the logout
        // 4. Keycloak would show a confirmation screen listing all active applications
        // 5. Users could choose to log out of all apps or just the current one
        // 6. Other applications would be notified via their backchannel_logout_uri
        // Example enhanced URL for multi-app scenario:
        // const keycloakLogoutUrl = `${process.env.REACT_APP_KEYCLOAK_URL}/realms/${process.env.REACT_APP_KEYCLOAK_REALM}/protocol/openid-connect/logout?client_id=${process.env.REACT_APP_KEYCLOAK_CLIENT_ID}&post_logout_redirect_uri=${encodeURIComponent(window.location.origin + '/login')}&id_token_hint=${idToken}&logout_hint=${process.env.REACT_APP_APPLICATION_NAME}`;

        // Current single-application logout flow:
        // 1. Construct logout URL with id_token_hint and post_logout_redirect_uri
        // 2. Keycloak validates the id_token_hint
        // 3. If valid, Keycloak silently logs out the user (no confirmation screen)
        // 4. Keycloak redirects to our login page
        // Note: We don't see a logout screen because:
        // - id_token_hint is valid
        // - post_logout_redirect_uri is valid
        // - No other applications need to be logged out
        const keycloakLogoutUrl = `${process.env.REACT_APP_KEYCLOAK_URL}/realms/${process.env.REACT_APP_KEYCLOAK_REALM}/protocol/openid-connect/logout?post_logout_redirect_uri=${encodeURIComponent(window.location.origin + '/login')}&id_token_hint=${idToken}`;
        window.location.href = keycloakLogoutUrl;
      } else {
        // If no id_token, just redirect to login
        window.location.href = '/login';
      }
    } else {
      // For regular form login, just redirect to login page
      window.location.href = '/login';
    }
  },

  handleSSOCallback: async (code) => {
    try {
        console.log('Making SSO callback request with code:', code);
        console.log('Current redirect URI:', process.env.REACT_APP_OAUTH2_REDIRECT_URI);
        
        const response = await axiosInstance.post('/api/auth/sso/callback', { code });
        // console.log('SSO callback full response:', response);
        // console.log('SSO callback response data:', response.data);
        
        // Store the idToken if it exists in the response
        if (response.data.idToken) {
            // console.log('Storing idToken from backend response:', response.data.idToken);
            sessionStorage.setItem('id_token', response.data.idToken);
        } else {
            console.log('No idToken found in response. Response data:', response.data);
        }
        
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