import axiosInstance from './axiosInstance';

export const knowledgeHubDocumentService = {
  // Get all documents, optionally filtered by collection or status
  getAllDocuments: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.collectionId) queryParams.append('collectionId', params.collectionId);
      if (params.status) queryParams.append('status', params.status);
      
      const url = `/api/documents${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
      const response = await axiosInstance.get(url);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching documents:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch documents'
      };
    }
  },

  // Get document by ID
  getDocumentById: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/view/${documentId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching document:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch document'
      };
    }
  },

  // Initiate document upload
  initiateUpload: async (fileData) => {
    try {
      const response = await axiosInstance.post('/api/documents/upload/initiate', fileData);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error initiating upload:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to initiate upload'
      };
    }
  },

  // Complete document upload
  completeUpload: async (documentId, s3Key) => {
    try {
      const response = await axiosInstance.post(`/api/documents/upload/${documentId}/complete`, { s3Key });
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error completing upload:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to complete upload'
      };
    }
  },

  // Get document status
  getDocumentStatus: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/${documentId}/status`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching document status:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch document status'
      };
    }
  },

  // Download document file (streams decrypted file directly)
  downloadDocument: async (documentId, fileName) => {
    try {
      console.log('Starting download for document:', documentId);
      
      const response = await axiosInstance.get(`/api/documents/view/${documentId}/download`, {
        responseType: 'blob', // Important: download as binary blob
        timeout: 300000, // 5 minutes timeout for large files
        headers: {
          'Accept': '*/*' // Override default Accept: application/json to allow blob responses
        }
      });
      
      console.log('Download response received:', {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers,
        hasData: !!response.data,
        dataSize: response.data?.size || response.data?.length || 'unknown'
      });
      
      // Check if response is actually an error (blob responses can contain error JSON)
      if (response.data instanceof Blob && response.data.type === 'application/json') {
        // Response might be an error JSON wrapped in a blob
        const errorText = await response.data.text();
        try {
          const errorJson = JSON.parse(errorText);
          if (errorJson.error || errorJson.message) {
            throw new Error(errorJson.error || errorJson.message || 'Failed to download document');
          }
        } catch (parseError) {
          // If not JSON, continue with download
        }
      }
      
      // Check response status
      if (response.status < 200 || response.status >= 300) {
        throw new Error(`Download failed with status: ${response.status}`);
      }
      
      // Create a blob URL and trigger download
      const blob = response.data instanceof Blob 
        ? response.data 
        : new Blob([response.data], { type: response.headers['content-type'] || 'application/octet-stream' });
      
      // Validate blob is not empty
      if (!blob || blob.size === 0) {
        throw new Error('Downloaded file is empty or invalid');
      }
      
      console.log('Download successful:', {
        blobSize: blob.size,
        contentType: blob.type || response.headers['content-type'],
        contentDisposition: response.headers['content-disposition']
      });
      
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.style.display = 'none';
      
      // Get filename from Content-Disposition header or use provided/default name
      const contentDisposition = response.headers['content-disposition'];
      let downloadFileName = fileName || 'document';
      if (contentDisposition) {
        // Try multiple patterns for filename extraction
        const patterns = [
          /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/,
          /filename\*=UTF-8''([^;\n]+)/,
          /filename=([^;\n]+)/
        ];
        
        for (const pattern of patterns) {
          const fileNameMatch = contentDisposition.match(pattern);
          if (fileNameMatch && fileNameMatch[1]) {
            downloadFileName = fileNameMatch[1].replace(/['"]/g, '');
            // Handle URL-encoded filenames
            try {
              downloadFileName = decodeURIComponent(downloadFileName);
            } catch (e) {
              // If decoding fails, use as-is
            }
            break;
          }
        }
      }
      
      link.setAttribute('download', downloadFileName);
      document.body.appendChild(link);
      link.click();
      
      // Clean up after a short delay
      setTimeout(() => {
        if (document.body.contains(link)) {
          document.body.removeChild(link);
        }
        window.URL.revokeObjectURL(url);
      }, 100);
      
      return {
        success: true
      };
    } catch (error) {
      console.error('Error downloading document:', error);
      console.error('Error details:', {
        message: error.message,
        status: error.response?.status,
        statusText: error.response?.statusText,
        headers: error.response?.headers,
        hasData: !!error.response?.data,
        dataType: error.response?.data ? typeof error.response.data : 'none'
      });
      
      // Handle blob error responses
      let errorMessage = error.message || 'Failed to download document';
      
      // Check if axios actually threw an error for non-2xx status
      if (error.response?.status) {
        if (error.response.status === 404) {
          errorMessage = 'Document not found';
        } else if (error.response.status === 403) {
          errorMessage = 'Access denied';
        } else if (error.response.status === 401) {
          errorMessage = 'Authentication required';
        } else if (error.response.status >= 500) {
          errorMessage = 'Server error occurred while downloading';
        }
        
        // Try to parse error message from blob response
        if (error.response?.data instanceof Blob) {
          try {
            const errorText = await error.response.data.text();
            if (errorText) {
              try {
                const errorJson = JSON.parse(errorText);
                errorMessage = errorJson.error || errorJson.message || errorMessage;
              } catch (parseError) {
                // If not JSON, use the text if it looks like an error message
                if (errorText.length < 500) {
                  errorMessage = errorText || errorMessage;
                }
              }
            }
          } catch (readError) {
            console.warn('Could not read error blob:', readError);
          }
        } else if (error.response?.data?.message) {
          errorMessage = error.response.data.message;
        } else if (error.response?.statusText) {
          errorMessage = error.response.statusText;
        }
      } else if (!error.response && error.message) {
        // Network error or timeout
        if (error.message.includes('timeout')) {
          errorMessage = 'Download timeout - file may be too large';
        } else if (error.message.includes('Network Error')) {
          errorMessage = 'Network error - please check your connection';
        }
      }
      
      return {
        success: false,
        error: errorMessage
      };
    }
  },

  // Generate download URL (deprecated - kept for backward compatibility)
  // Note: This now calls downloadDocument which streams the file directly
  generateDownloadUrl: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/view/${documentId}/download`, {
        responseType: 'blob'
      });
      
      // Extract filename from headers
      const contentDisposition = response.headers['content-disposition'];
      let fileName = 'document';
      if (contentDisposition) {
        const fileNameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
        if (fileNameMatch && fileNameMatch[1]) {
          fileName = fileNameMatch[1].replace(/['"]/g, '');
          try {
            fileName = decodeURIComponent(fileName);
          } catch (e) {
            // If decoding fails, use as-is
          }
        }
      }
      
      // Create blob URL for download
      const blob = new Blob([response.data], { type: response.headers['content-type'] || 'application/octet-stream' });
      const downloadUrl = window.URL.createObjectURL(blob);
      
      return {
        success: true,
        data: {
          downloadUrl: downloadUrl,
          fileName: fileName
        }
      };
    } catch (error) {
      console.error('Error generating download URL:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Failed to generate download URL'
      };
    }
  },

  // Generate download URL for processed JSON
  generateProcessedJsonDownloadUrl: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/view/${documentId}/processed/download`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error generating processed JSON download URL:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to generate processed JSON download URL'
      };
    }
  },

  // Delete document (soft delete - only if S3 file doesn't exist)
  deleteDocument: async (documentId) => {
    try {
      await axiosInstance.delete(`/api/documents/${documentId}`);
      return {
        success: true
      };
    } catch (error) {
      console.error('Error deleting document:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || 'Failed to delete document'
      };
    }
  },

  // Hard delete document (deletes everything including S3 files)
  hardDeleteDocument: async (documentId) => {
    try {
      await axiosInstance.delete(`/api/documents/${documentId}/hard`);
      return {
        success: true
      };
    } catch (error) {
      console.error('Error hard deleting document:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || 'Failed to hard delete document'
      };
    }
  },

  // Delete downstream artifacts for a document (processed data, metadata, embeddings)
  deleteDocumentArtifacts: async (documentId, scope) => {
    try {
      await axiosInstance.delete(`/api/documents/${documentId}/artifacts/${scope}`);
      return { success: true };
    } catch (error) {
      console.error(`Error deleting document artifacts (${scope}):`, error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || `Failed to delete document ${scope}`
      };
    }
  },

  // Get document metadata
  getMetadata: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/metadata/${documentId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching metadata:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to fetch metadata',
        statusCode: error.response?.status // Include status code for retry logic
      };
    }
  },

  // Extract metadata from a processed document
  extractMetadata: async (documentId) => {
    try {
      const response = await axiosInstance.post(`/api/documents/metadata/extract/${documentId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error extracting metadata:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to extract metadata'
      };
    }
  }
};

