import axiosInstance from './axiosInstance';

const agentSchedulingService = {
    // ==================== TASK SCHEDULING OPERATIONS ====================
    
    scheduleTask: async (taskId, cronExpression) => {
        console.log('Scheduling task:', taskId, 'with cron:', cronExpression);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/scheduling/${taskId}/schedule`, 
                { cronExpression }, 
                {
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    }
                }
            );
            return {
                success: true,
                data: response.data,
                message: 'Task scheduled successfully'
            };
        } catch (error) {
            console.error('Error scheduling task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to schedule task'
            };
        }
    },

    unscheduleTask: async (taskId) => {
        console.log('Unscheduling task:', taskId);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/scheduling/${taskId}/unschedule`, {}, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Task unscheduled successfully'
            };
        } catch (error) {
            console.error('Error unscheduling task:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to unschedule task'
            };
        }
    },

    // ==================== SCHEDULING QUERIES ====================

    getTasksReadyToRun: async () => {
        try {
            const response = await axiosInstance.get('/api/agent-tasks/scheduling/ready-to-run', {
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
            console.error('Error fetching tasks ready to run:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch tasks ready to run'
            };
        }
    },

    getTasksReadyToRunByAgent: async (agentId) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/scheduling/agent/${agentId}/ready-to-run`, {
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
            console.error('Error fetching tasks ready to run for agent:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch tasks ready to run for agent'
            };
        }
    },

    getTasksReadyToRunByTeam: async (teamId) => {
        try {
            const response = await axiosInstance.get(`/api/agent-tasks/scheduling/team/${teamId}/ready-to-run`, {
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
            console.error('Error fetching tasks ready to run for team:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch tasks ready to run for team'
            };
        }
    },

    // ==================== SCHEDULING MANAGEMENT ====================

    updateTaskNextRunTime: async (taskId, nextRun) => {
        console.log('Updating next run time for task:', taskId, 'to:', nextRun);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/scheduling/${taskId}/next-run`, 
                { nextRun }, 
                {
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    }
                }
            );
            return {
                success: true,
                data: response.data,
                message: 'Next run time updated successfully'
            };
        } catch (error) {
            console.error('Error updating next run time:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to update next run time'
            };
        }
    },

    updateTaskLastRunTime: async (taskId, lastRun) => {
        console.log('Updating last run time for task:', taskId, 'to:', lastRun);
        try {
            const response = await axiosInstance.post(`/api/agent-tasks/scheduling/${taskId}/last-run`, 
                { lastRun }, 
                {
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    }
                }
            );
            return {
                success: true,
                data: response.data,
                message: 'Last run time updated successfully'
            };
        } catch (error) {
            console.error('Error updating last run time:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to update last run time'
            };
        }
    }
};

export default agentSchedulingService;

