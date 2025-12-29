
import axiosInstance from './axiosInstance';

const docReviewService = {
    /**
     * Get all contract reviews for a team
     * @param {string} teamId
     * @returns {Promise<{success: boolean, data: any, error: any}>}
     */
    getReviewsByTeam: async (teamId) => {
        try {
            const response = await axiosInstance.get(`/api/doc-reviews/team/${teamId}`);
            return { success: true, data: response.data };
        } catch (error) {
            console.error('Error fetching contract reviews:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch contract reviews'
            };
        }
    },

    /**
     * Create a new document review session
     * @param {Object} reviewData
     * @returns {Promise<{success: boolean, data: any, error: any}>}
     */
    createReview: async (reviewData) => {
        try {
            const response = await axiosInstance.post('/api/doc-reviews', reviewData);
            return { success: true, data: response.data };
        } catch (error) {
            console.error('Error creating doc review:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to create doc review'
            };
        }
    },

    updateReview: async (reviewId, updates) => {
        try {
            const response = await axiosInstance.put(`/api/doc-reviews/${reviewId}`, updates);
            return { success: true, data: response.data };
        } catch (error) {
            console.error('Error updating doc review:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to update doc review'
            };
        }
    },

    getDocReviewAssistant: async () => {
        try {
            const response = await axiosInstance.get('/api/ai-assistants/category/REVIEW_DOC');
            return { success: true, data: response.data };
        } catch (error) {
            console.error('Error fetching doc review assistant:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch doc review assistant'
            };
        }
    },

    startReviewConversation: async (assistantId, message, context) => {
        try {
            const response = await axiosInstance.post(`/api/ai-assistants/${assistantId}/conversations`, {
                message,
                context
            });
            return { success: true, data: response.data };
        } catch (error) {
            console.error('Error starting review conversation:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to start conversation'
            };
        }
    },

    sendReviewMessage: async (conversationId, message) => {
        try {
            const response = await axiosInstance.post(`/api/conversations/${conversationId}/messages`, {
                message
            });
            return { success: true, data: response.data };
        } catch (error) {
            console.error('Error sending review message:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to send message'
            };
        }
    },

    /**
     * Get messages for a conversation
     * @param {string} conversationId
     * @returns {Promise<{success: boolean, data: any, error: any}>}
     */
    getConversationMessages: async (conversationId) => {
        try {
            const response = await axiosInstance.get(`/api/conversations/${conversationId}/messages`);
            return { success: true, data: response.data };
        } catch (error) {
            console.error('Error fetching conversation messages:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch messages'
            };
        }
    }
};

export default docReviewService;
