import axiosInstance from './axiosInstance';

export const resourceService = {
  // --- Resource Metadata ---
  async getAllResources() {
    try {
      const response = await axiosInstance.get('/api/resources');
      return response.data;
    } catch (error) {
      console.error('Error fetching resources:', error);
      throw error;
    }
  },

  async upsertResource(metadata) {
    try {
      const response = await axiosInstance.post('/api/resources', metadata);
      return response.data;
    } catch (error) {
      console.error('Error upserting resource:', error);
      throw error;
    }
  },

  async deleteResource(domain, category, resourceId) {
    try {
      await axiosInstance.delete(`/api/resources/${domain}/${category}/${resourceId}`);
      return true;
    } catch (error) {
      console.error('Error deleting resource:', error);
      throw error;
    }
  },

  // --- Resource Subscriptions ---
  async subscribeUser(request) {
    try {
      const response = await axiosInstance.post('/api/subscriptions/subscribe/user', request);
      return response.data;
    } catch (error) {
      console.error('Error subscribing user:', error);
      throw error;
    }
  },

  async subscribeTeam(request) {
    try {
      const response = await axiosInstance.post('/api/subscriptions/subscribe/team', request);
      return response.data;
    } catch (error) {
      console.error('Error subscribing team:', error);
      throw error;
    }
  },

  async getMySubscriptions() {
    try {
      console.log('--- CALLING API: GET /api/subscriptions/user ---');
      const response = await axiosInstance.get('/api/subscriptions/user');
      return response.data;
    } catch (error) {
      console.error('Error fetching user subscriptions:', error);
      throw error;
    }
  },

  async getSubscriptionsByUserId(userId) {
    try {
      const response = await axiosInstance.get(`/api/subscriptions/all/${userId}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching subscriptions for user ${userId}:`, error);
      throw error;
    }
  },

  async getTeamSubscriptions() {
    try {
      console.log('--- CALLING API: GET /api/subscriptions/team ---');
      const response = await axiosInstance.get('/api/subscriptions/team');
      return response.data;
    } catch (error) {
      console.error('Error fetching team subscriptions:', error);
      throw error;
    }
  },

  async unsubscribe(subscriptionId) {
    try {
      await axiosInstance.delete(`/api/subscriptions/${subscriptionId}`);
      return true;
    } catch (error) {
      console.error('Error unsubscribing:', error);
      throw error;
    }
  },

  // --- Resource Notifications ---
  async getNotifications(subscriptionId) {
    try {
      const response = await axiosInstance.get(`/api/notifications/${subscriptionId}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching notifications for subscription ${subscriptionId}:`, error);
      throw error;
    }
  },

  async getNotificationsForUser(userId) {
    try {
      const response = await axiosInstance.get(`/api/notifications/all/${userId}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching notifications for user ${userId}:`, error);
      throw error;
    }
  },

  async countUnread(userId) {
    try {
      const response = await axiosInstance.get(`/api/notifications/count/unread/${userId}`);
      return response.data;
    } catch (error) {
      console.error(`Error counting unread notifications for user ${userId}:`, error);
      throw error;
    }
  },

  async markAsRead(notificationId) {
    try {
      await axiosInstance.post(`/api/notifications/${notificationId}/read`);
      return true;
    } catch (error) {
      console.error(`Error marking notification ${notificationId} as read:`, error);
      throw error;
    }
  },

  async dispatchNotification(dto) {
    try {
      await axiosInstance.post('/api/notifications/dispatch', dto);
      return true;
    } catch (error) {
      console.error('Error dispatching notification:', error);
      throw error;
    }
  }
};

export default resourceService;
