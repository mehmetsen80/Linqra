import axiosInstance from './axiosInstance';

const agentMonitoringService = {
    // ==================== AGENT HEALTH MONITORING ====================
    
    getAgentHealth: async (agentId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/monitoring/${agentId}/health`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching agent health:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agent health'
            };
        }
    },

    getTeamAgentsHealth: async (teamId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/monitoring/team/${teamId}/health`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching team agents health:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch team agents health'
            };
        }
    },

    getAgentsWithErrors: async (teamId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/monitoring/team/${teamId}/errors`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching agents with errors:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agents with errors'
            };
        }
    },

    // ==================== PERFORMANCE MONITORING ====================

    getAgentPerformance: async (agentId, from = null, to = null) => {
        try {
            const params = {};
            if (from) params.from = from;
            if (to) params.to = to;

            const response = await axiosInstance.get(`/api/agents/monitoring/${agentId}/performance`, {
                params,
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching agent performance:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agent performance'
            };
        }
    },

    getTeamExecutionStats: async (teamId, from = null, to = null, agentId = null) => {
        try {
            const params = {};
            if (from) params.from = from;
            if (to) params.to = to;
            if (agentId) params.agentId = agentId;

            const response = await axiosInstance.get(`/api/agents/monitoring/team/${teamId}/execution-stats`, {
                params,
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching team execution stats:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch team execution stats'
            };
        }
    },

    // ==================== RESOURCE MONITORING ====================

    getTeamResourceUsage: async (teamId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/monitoring/team/${teamId}/resource-usage`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching team resource usage:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch team resource usage'
            };
        }
    },

    getAgentCapabilitiesSummary: async (teamId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/monitoring/team/${teamId}/capabilities`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching agent capabilities summary:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agent capabilities summary'
            };
        }
    },

    // ==================== EXECUTION MONITORING ====================

    getFailedExecutions: async (teamId, limit = 10) => {
        try {
            const response = await axiosInstance.get(`/api/agents/monitoring/team/${teamId}/failed-executions`, {
                params: { limit },
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching failed executions:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch failed executions'
            };
        }
    },

    getWorkflowExecutionStatus: async (workflowExecutionId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/monitoring/workflow/${workflowExecutionId}/status`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching workflow execution status:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch workflow execution status'
            };
        }
    }
};

export default agentMonitoringService;

