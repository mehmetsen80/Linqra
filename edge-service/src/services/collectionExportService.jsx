import axiosInstance from './axiosInstance';

export const collectionExportService = {
  // Queue an export job for one or more collections
  queueExport: async (collectionIds) => {
    try {
      const response = await axiosInstance.post('/api/collections/export', {
        collectionIds
      });
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error queueing export job:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to queue export job'
      };
    }
  },

  // Get export job status
  getExportJobStatus: async (jobId) => {
    try {
      const response = await axiosInstance.get(`/api/collections/export/jobs/${jobId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error getting export job status:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to get export job status'
      };
    }
  },

  // Get all export jobs for current team
  getExportJobs: async () => {
    try {
      const response = await axiosInstance.get('/api/collections/export/jobs');
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error getting export jobs:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to get export jobs'
      };
    }
  },

  // Cancel an export job
  cancelExportJob: async (jobId) => {
    try {
      const response = await axiosInstance.delete(`/api/collections/export/jobs/${jobId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error cancelling export job:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to cancel export job'
      };
    }
  }
};

