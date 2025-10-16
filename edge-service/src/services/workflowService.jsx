import axiosInstance from './axiosInstance';

const workflowService = {
    getAllTeamWorkflows: async () => {
        try {
            const response = await axiosInstance.get('/linq/workflows', {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data  // The API is returning the array directly
            };
        } catch (error) {
            console.error('Error fetching all team workflows:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch all team workflows'
            };
        }
    },

    getWorkflowById: async (workflowId) => {
        try {
            const response = await axiosInstance.get(`/linq/workflows/${workflowId}`, {
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
            console.error('Error fetching workflow:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch workflow'
            };
        }
    },

    getWorkflowVersions: async (workflowId) => {
        try {
            const response = await axiosInstance.get(`/linq/workflows/${workflowId}/versions`, {
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
            console.error('Error fetching workflow versions:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch workflow versions'
            };
        }
    },

    updateWorkflow: async (workflowId, workflowData) => {
        try {
            const response = await axiosInstance.put(`/linq/workflows/${workflowId}`, workflowData, {
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
                message: 'Workflow updated successfully'
            };
        } catch (error) {
            console.error('Error updating workflow:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to update workflow'
            };
        }
    },

    createNewVersion: async (workflowId, workflowData) => {
        try {
            const response = await axiosInstance.post(`/linq/workflows/${workflowId}/versions`, workflowData, {
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

    rollbackToVersion: async (workflowId, versionId) => {
        try {
            const response = await axiosInstance.post(`/linq/workflows/${workflowId}/versions/${versionId}/rollback`, {}, {
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
            console.error('Error rolling back workflow version:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to rollback workflow version'
            };
        }
    },

    getWorkflowExecutions: async (workflowId) => {
        try {
            const response = await axiosInstance.get(`/linq/workflows/${workflowId}/executions`, {
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
            console.error('Error fetching workflow executions:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch workflow executions'
            };
        }
    },

    executeWorkflow: async (workflowId) => {
        try {
            const response = await axiosInstance.post(`/linq/workflows/${workflowId}/execute`, {}, {
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
                message: 'Workflow executed successfully'
            };
        } catch (error) {
            console.error('Error executing workflow:', error);
            
            // Get the actual error message
            const actualError = error.response?.data?.message || 
                              error.response?.data?.error || 
                              error.message || 
                              'Unknown error';

            // If we have a step failure, format it nicely
            if (actualError.includes('Workflow step')) {
                const stepMatch = actualError.match(/Workflow step (\d+) failed: (.*)/);
                if (stepMatch) {
                    const [_, stepNumber, stepError] = stepMatch;
                    return {
                        success: false,
                        error: `Step ${stepNumber} failed`,
                        details: stepError || 'No error details available'
                    };
                }
            }
            
            return {
                success: false,
                error: 'Failed to execute workflow',
                details: actualError
            };
        }
    },

    getWorkflowStats: async (workflowId) => {
        try {
            const response = await axiosInstance.get(`/linq/workflows/${workflowId}/stats`, {
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
            console.error('Error fetching workflow stats:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch workflow stats'
            };
        }
    },

    getAgentTaskWorkflowStats: async (agentTaskId) => {
        try {
            const response = await axiosInstance.get(`/linq/workflows/agent-task/${agentTaskId}/stats`, {
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
            console.error('Error fetching agent task workflow stats:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agent task workflow stats'
            };
        }
    },

    validateRequest: async (request) => {
        try {
            console.log('Sending validation request:', request);
            const response = await axiosInstance.post('/linq/workflows/validate', request, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            console.log('Validation response:', response.data);
            
            // Check if the response indicates validation failure
            if (response.data && response.data.valid === false) {
                return {
                    success: true, // The request was successful, but validation failed
                    data: response.data
                };
            }
            
            return { success: true, data: response.data };
        } catch (error) {
            console.error('Validation error:', error.response?.data || error);
            return { 
                success: false, 
                error: error.response?.data?.errors || ['Failed to validate request'],
                status: error.response?.status
            };
        }
    },

    createWorkflow: async (workflowData) => {
        console.log('Creating workflow:', workflowData);
        try {
            const response = await axiosInstance.post('/linq/workflows', workflowData, {
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
                message: 'Workflow created successfully'
            };
        } catch (error) {
            console.error('Error creating workflow:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to create workflow'
            };
        }
    },

    deleteWorkflow: async (workflowId) => {
        try {
            const response = await axiosInstance.delete(`/linq/workflows/${workflowId}`, {
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
                message: 'Workflow deleted successfully'
            };
        } catch (error) {
            console.error('Error deleting workflow:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to delete workflow'
            };
        }
    },

    getTeamStats: async () => {
        try {
            const response = await axiosInstance.get('/linq/workflows/team/stats', {
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
            console.error('Error fetching team workflow statistics:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch team workflow statistics'
            };
        }
    },

    getExecutionByAgentExecutionId: async (agentExecutionId) => {
        try {
            const response = await axiosInstance.get(`/linq/workflows/executions/${agentExecutionId}`, {
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
            console.error('Error fetching execution by agent execution ID:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch execution'
            };
        }
    }
};

export default workflowService;