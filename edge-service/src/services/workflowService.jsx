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
    }
};

export default workflowService;