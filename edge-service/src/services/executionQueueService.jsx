import axiosInstance from './axiosInstance';

const executionQueueService = {
    // Get the execution queue for the current team
    async getQueue() {
        try {
            const response = await axiosInstance.get('/api/execution-queue');
            return response.data;
        } catch (error) {
            console.error('Error fetching execution queue:', error);
            throw error;
        }
    },

    // Add an execution to the queue
    async addToQueue(executionData) {
        try {
            const response = await axiosInstance.post('/api/execution-queue', executionData);
            return response.data;
        } catch (error) {
            console.error('Error adding execution to queue:', error);
            throw error;
        }
    },

    // Remove an execution from the queue
    async removeFromQueue(executionId) {
        try {
            await axiosInstance.delete(`/api/execution-queue/${executionId}`);
        } catch (error) {
            console.error('Error removing execution from queue:', error);
            throw error;
        }
    },

    // Get the next execution to start
    async getNextExecution() {
        try {
            const response = await axiosInstance.get('/api/execution-queue/next');
            return response.data;
        } catch (error) {
            console.error('Error getting next execution:', error);
            throw error;
        }
    }
};

export default executionQueueService;
