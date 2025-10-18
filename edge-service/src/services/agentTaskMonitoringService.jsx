import axiosInstance from './axiosInstance';

const agentTaskMonitoringService = {
    getTaskExecutionHistory: async (taskId, limit = 10) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}/execution-history`, {
                params: { limit }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching task execution history:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch task execution history'
            };
        }
    },

    getTaskMetrics: async (taskId, from = null, to = null) => {
        try {
            const params = {};
            if (from) params.from = from;
            if (to) params.to = to;

            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}/metrics`, {
                params
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching task metrics:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch task metrics'
            };
        }
    },

    getTaskStatus: async (taskId) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}/status`);
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching task status:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch task status'
            };
        }
    },

    getTaskStatistics: async (taskId) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}/stats`);
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching task statistics:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch task statistics'
            };
        }
    }
};

export default agentTaskMonitoringService;

