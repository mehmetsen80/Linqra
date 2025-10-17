import axiosInstance from './axiosInstance';

const agentTaskVersionService = {
    createNewVersion: async (taskId, updatedTask, changeDescription = null) => {
        console.log('Creating new version for task:', taskId);
        try {
            const params = changeDescription ? { changeDescription } : {};
            
            const response = await axiosInstance.post(`/api/agent-tasks/${taskId}/versions`, 
                updatedTask, 
                {
                    params,
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json',
                        'X-API-Key': import.meta.env.VITE_API_KEY,
                        'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                    }
                }
            );
            return {
                success: true,
                data: response.data,
                message: 'New version created successfully'
            };
        } catch (error) {
            console.error('Error creating new version:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to create new version'
            };
        }
    },

    rollbackToVersion: async (taskId, version) => {
        console.log('Rolling back task:', taskId, 'to version:', version);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/${taskId}/versions/${version}/rollback`, {}, {
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
                message: 'Successfully rolled back to previous version'
            };
        } catch (error) {
            console.error('Error rolling back to version:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to rollback to version'
            };
        }
    },

    getVersionHistory: async (taskId) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}/versions`, {
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
            console.error('Error fetching version history:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch version history'
            };
        }
    },

    getVersion: async (taskId, version) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}/versions/${version}`, {
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
            console.error('Error fetching version:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch version'
            };
        }
    },

    getLatestVersion: async (taskId) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}/versions/latest`, {
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
            console.error('Error fetching latest version:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch latest version'
            };
        }
    },

    getVersionCount: async (taskId) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/${taskId}/versions/count`, {
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
            console.error('Error fetching version count:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch version count'
            };
        }
    },

    deleteAllVersions: async (taskId, confirmationToken, reason) => {
        console.warn('DANGEROUS: Attempting to delete all versions for task:', taskId);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/${taskId}/versions/delete-all`, null, {
                params: { 
                    confirmationToken,
                    reason 
                },
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
                message: 'All versions deleted successfully'
            };
        } catch (error) {
            console.error('Error deleting all versions:', error);
            return {
                success: false,
                error: error.response?.data?.message || error.response?.data || 'Failed to delete all versions'
            };
        }
    },

    cleanupDuplicateVersions: async (taskId) => {
        console.log('Cleaning up duplicate versions for task:', taskId);
        try {
            const response = await axiosInstance.delete(`/api/agent-tasks/${taskId}/versions/duplicates`, {
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
                message: 'Duplicate versions cleaned up successfully'
            };
        } catch (error) {
            console.error('Error cleaning up duplicate versions:', error);
            return {
                success: false,
                error: error.response?.data?.message || error.response?.data || 'Failed to cleanup duplicate versions'
            };
        }
    }
};

export default agentTaskVersionService;

