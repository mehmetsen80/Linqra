import axiosInstance from './axiosInstance';

const conversationService = {
    // Start a new conversation
    startConversation: async (assistantId, message) => {
        try {
            const response = await axiosInstance.post(`/api/ai-assistants/${assistantId}/conversations`, {
                message
            });
            return {
                success: true,
                data: response.data,
                conversationId: response.data.conversationId
            };
        } catch (error) {
            console.error('Error starting conversation:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to start conversation'
            };
        }
    },

    // Send a message in an existing conversation
    sendMessage: async (conversationId, message) => {
        try {
            const response = await axiosInstance.post(`/api/conversations/${conversationId}/messages`, {
                message
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error sending message:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to send message'
            };
        }
    },

    // Get conversation messages
    getMessages: async (conversationId, page = 0, size = 1000) => {
        try {
            const response = await axiosInstance.get(`/api/conversations/${conversationId}/messages`, {
                params: { page, size }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching messages:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch messages'
            };
        }
    },

    // Get conversation details
    getConversation: async (conversationId) => {
        try {
            const response = await axiosInstance.get(`/api/conversations/${conversationId}`);
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching conversation:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch conversation'
            };
        }
    },

    // List user conversations
    listConversations: async (assistantId = null) => {
        try {
            const params = assistantId ? { assistantId } : {};
            const response = await axiosInstance.get('/api/conversations', { params });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error listing conversations:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to list conversations'
            };
        }
    },

    // Delete conversation
    deleteConversation: async (conversationId) => {
        try {
            await axiosInstance.delete(`/api/conversations/${conversationId}`);
            return {
                success: true
            };
        } catch (error) {
            console.error('Error deleting conversation:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to delete conversation'
            };
        }
    }
};

export default conversationService;

