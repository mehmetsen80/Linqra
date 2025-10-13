import axiosInstance from './axiosInstance';

const agentTaskService = {
    createAgentTask: async (taskData) => {
        console.log('Creating agent task:', taskData);
        try {
            const response = await axiosInstance.post('/api/agent-tasks', taskData, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent task created successfully'
            };
        } catch (error) {
            console.error('Error creating agent task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to create agent task'
            };
        }
    },

    executeTask: async (taskId) => {
        console.log('Executing agent task:', taskId);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/${taskId}/execute`, {}, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent task executed successfully'
            };
        } catch (error) {
            console.error('Error executing agent task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to execute agent task'
            };
        }
    },

    enableTask: async (taskId) => {
        console.log('Enabling agent task:', taskId);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/${taskId}/enable`, {}, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent task enabled successfully'
            };
        } catch (error) {
            console.error('Error enabling agent task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to enable agent task'
            };
        }
    },

    disableTask: async (taskId) => {
        console.log('Disabling agent task:', taskId);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/${taskId}/disable`, {}, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent task disabled successfully'
            };
        } catch (error) {
            console.error('Error disabling agent task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to disable agent task'
            };
        }
    },

    deleteTask: async (taskId) => {
        console.log('Deleting agent task:', taskId);
        try {
            const response = await axiosInstance.delete(`/api/agent-tasks/${taskId}`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent task deleted successfully'
            };
        } catch (error) {
            console.error('Error deleting agent task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to delete agent task'
            };
        }
    },

    getTask: async (taskId) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching agent task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agent task'
            };
        }
    },

    executeAdhocTask: async (adhocTaskData) => {
        console.log('Executing ad-hoc task:', adhocTaskData);
        try {
            const response = await axiosInstance.post('/api/agent-tasks/execute-adhoc', adhocTaskData, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Ad-hoc task executed successfully'
            };
        } catch (error) {
            console.error('Error executing ad-hoc task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to execute ad-hoc task'
            };
        }
    }
};

export default agentTaskService;

