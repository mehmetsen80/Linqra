import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Spinner, Alert, Badge, Row, Col, OverlayTrigger, Tooltip, Form, Table } from 'react-bootstrap';
import { HiArrowLeft, HiDocument, HiDownload, HiTrash, HiCalendar, HiCube, HiChartBar, HiCheckCircle, HiInformationCircle, HiHashtag, HiLink, HiLockClosed, HiLockOpen } from 'react-icons/hi';
import { useTeam } from '../../../../contexts/TeamContext';
import { useAuth } from '../../../../contexts/AuthContext';
import { knowledgeHubDocumentService } from '../../../../services/knowledgeHubDocumentService';
import { knowledgeHubCollectionService } from '../../../../services/knowledgeHubCollectionService';
import { knowledgeHubWebSocketService } from '../../../../services/knowledgeHubWebSocketService';
import { knowledgeHubGraphService } from '../../../../services/knowledgeHubGraphService';
import { knowledgeHubGraphWebSocketService } from '../../../../services/knowledgeHubGraphWebSocketService';
import vaultHealthService from '../../../../services/vaultHealthService';
import { isSuperAdmin, hasAdminAccess } from '../../../../utils/roleUtils';
import Button from '../../../../components/common/Button';
import { showSuccessToast, showErrorToast } from '../../../../utils/toastConfig';
import { formatDateTime } from '../../../../utils/dateUtils';
import ConfirmationModalWithVerification from '../../../../components/common/ConfirmationModalWithVerification';
import AlertMessageModal from '../../../../components/common/AlertMessageModal';
import PropertiesViewerModal from '../../../../components/common/PropertiesViewerModal';
import EncryptionVersionWarningBanner from '../../../../components/common/EncryptionVersionWarningBanner';
import './styles.css';

function ViewDocument() {
  const { documentId } = useParams();
  const navigate = useNavigate();
  const { currentTeam } = useTeam();
  const { user } = useAuth();
  const [document, setDocument] = useState(null);
  const [collection, setCollection] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [downloading, setDownloading] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [fileExists, setFileExists] = useState(null);
  const [chunkStats, setChunkStats] = useState(null);
  const [loadingChunkStats, setLoadingChunkStats] = useState(false);
  const [metadata, setMetadata] = useState(null);
  const [loadingMetadata, setLoadingMetadata] = useState(false);
  const [hardDeleting, setHardDeleting] = useState(false);
  const [embedding, setEmbedding] = useState({
    running: false,
    progress: null
  });
  const [stageDeleteModal, setStageDeleteModal] = useState({ show: false, scope: null });
  const [stageDeleting, setStageDeleting] = useState(false);
  const [extracting, setExtracting] = useState({
    entities: false,
    relationships: false,
    all: false
  });
  const [extractionConfirmModal, setExtractionConfirmModal] = useState({
    show: false,
    type: null, // 'entities', 'relationships', or 'all'
    force: false // Whether to force re-extraction
  });
  const [alreadyExtractedModal, setAlreadyExtractedModal] = useState({
    show: false,
    message: null,
    extractionType: null // 'entities', 'relationships', or 'all'
  });
  const [currentJob, setCurrentJob] = useState(null); // Current extraction job
  const [graphEntityCount, setGraphEntityCount] = useState(null);
  const [graphRelationshipCount, setGraphRelationshipCount] = useState(null);
  const [loadingGraphCounts, setLoadingGraphCounts] = useState(false);
  const [documentEntities, setDocumentEntities] = useState([]);
  const [selectedDocumentEntityType, setSelectedDocumentEntityType] = useState('All');
  const [loadingDocumentEntities, setLoadingDocumentEntities] = useState(false);
  const [documentRelationships, setDocumentRelationships] = useState([]);
  const [selectedDocumentRelationshipType, setSelectedDocumentRelationshipType] = useState('All');
  const [loadingDocumentRelationships, setLoadingDocumentRelationships] = useState(false);
  const [propertiesModal, setPropertiesModal] = useState({
    show: false,
    title: 'Properties',
    entityType: null,
    entityName: null,
    properties: {},
    loading: false
  });
  const [currentEncryptionKeyVersion, setCurrentEncryptionKeyVersion] = useState(null);
  const [vaultHealth, setVaultHealth] = useState({ healthy: true, checked: false });

  // Helper function to truncate encrypted names for display
  const truncateEncryptedName = (name, maxLength = 60) => {
    if (!name || typeof name !== 'string') return name || 'N/A';
    if (name.length <= maxLength) return name;
    return name.substring(0, maxLength) + '...';
  };

  // Helper function to compare version numbers (e.g., "v1" < "v2")
  const compareVersion = (v1, v2) => {
    if (!v1 || !v2) return false;
    const num1 = parseInt(v1.replace('v', '')) || 0;
    const num2 = parseInt(v2.replace('v', '')) || 0;
    return num1 < num2;
  };

  // Helper function to get entity encryption version
  const getEntityEncryptionVersion = (entity) => {
    // Prefer property-level version, fallback to entity-level version
    return entity.name_encryption_version || entity.encryptionKeyVersion || null;
  };

  // Helper function to check if entity is using outdated encryption key
  const isOutdatedEncryption = (entity) => {
    if (!currentEncryptionKeyVersion) return false;
    const entityVersion = getEntityEncryptionVersion(entity);
    if (!entityVersion) return true; // Legacy/unencrypted entities are considered outdated
    return compareVersion(entityVersion, currentEncryptionKeyVersion);
  };

  const handleOpenPropertiesModal = async (title, entityType, entityName, properties, fromEntity = null, toEntity = null) => {
    const isAdmin = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    
    if (isAdmin) {
      // For admin users, decrypt properties and entity names before showing
      setPropertiesModal({
        show: true,
        title,
        entityType,
        entityName,
        properties: {},
        loading: true
      });

      try {
        // Build a combined properties map that includes entity names and encryption version markers for decryption
        const propertiesToDecrypt = { ...(properties || {}) };
        
        // Build entity name properties with encryption version markers
        const fromEntityProps = {};
        if (fromEntity?.name) {
          fromEntityProps['name'] = fromEntity.name;
          // Include encryption version markers if present
          if (fromEntity.name_encryption_version) {
            fromEntityProps['name_encryption_version'] = fromEntity.name_encryption_version;
          } else if (fromEntity.encryptionKeyVersion) {
            fromEntityProps['encryptionKeyVersion'] = fromEntity.encryptionKeyVersion;
          }
        }
        
        const toEntityProps = {};
        if (toEntity?.name) {
          toEntityProps['name'] = toEntity.name;
          // Include encryption version markers if present
          if (toEntity.name_encryption_version) {
            toEntityProps['name_encryption_version'] = toEntity.name_encryption_version;
          } else if (toEntity.encryptionKeyVersion) {
            toEntityProps['encryptionKeyVersion'] = toEntity.encryptionKeyVersion;
          }
        }
        
        // Determine if this is a relationship (has fromEntity and toEntity) or an entity
        const isRelationship = fromEntity !== null && toEntity !== null;
        
        let updatedEntityName = entityName; // Default to the passed entityName
        
        if (isRelationship) {
          // Decrypt relationship entity names separately if they exist
          let decryptedFromName = fromEntity?.name || fromEntity?.id || 'N/A';
          let decryptedToName = toEntity?.name || toEntity?.id || 'N/A';
          
          if (Object.keys(fromEntityProps).length > 0) {
            const fromNameResult = await knowledgeHubGraphService.decryptProperties(fromEntityProps);
            if (fromNameResult.success && fromNameResult.data?.name) {
              decryptedFromName = fromNameResult.data.name;
            }
          }
          
          if (Object.keys(toEntityProps).length > 0) {
            const toNameResult = await knowledgeHubGraphService.decryptProperties(toEntityProps);
            if (toNameResult.success && toNameResult.data?.name) {
              decryptedToName = toNameResult.data.name;
            }
          }
          
          // Update subtitle with decrypted names for relationships
          const fromType = fromEntity?.type || 'Unknown';
          const toType = toEntity?.type || 'Unknown';
          updatedEntityName = `${fromType}:${decryptedFromName} → ${toType}:${decryptedToName}`;
        } else {
          // For entities, decrypt the entity name if it exists in properties
          if (properties?.name) {
            const entityNameProps = {
              name: properties.name
            };
            // Include encryption version markers if present
            if (properties.name_encryption_version) {
              entityNameProps['name_encryption_version'] = properties.name_encryption_version;
            } else if (properties.encryptionKeyVersion) {
              entityNameProps['encryptionKeyVersion'] = properties.encryptionKeyVersion;
            }
            
            if (Object.keys(entityNameProps).length > 1) { // Has name + encryption version
              const entityNameResult = await knowledgeHubGraphService.decryptProperties(entityNameProps);
              if (entityNameResult.success && entityNameResult.data?.name) {
                updatedEntityName = entityNameResult.data.name;
              }
            } else {
              // No encryption version, use the name as-is
              updatedEntityName = properties.name;
            }
          }
        }
        
        // Decrypt relationship properties if they exist
        if (Object.keys(propertiesToDecrypt).length > 0) {
          const result = await knowledgeHubGraphService.decryptProperties(propertiesToDecrypt);
          if (result.success) {
            setPropertiesModal(prev => ({
              ...prev,
              entityName: updatedEntityName,
              properties: result.data || {},
              loading: false
            }));
          } else {
            // If decryption fails, show original properties but with decrypted entity names
            console.warn('Failed to decrypt properties:', result.error);
            setPropertiesModal(prev => ({
              ...prev,
              entityName: updatedEntityName,
              properties: properties || {},
              loading: false
            }));
          }
        } else {
          // No properties to decrypt, but still update entity name
          setPropertiesModal(prev => ({
            ...prev,
            entityName: updatedEntityName,
            properties: properties || {},
            loading: false
          }));
        }
      } catch (error) {
        console.error('Error decrypting properties:', error);
        // On error, show original properties
        setPropertiesModal(prev => ({
          ...prev,
          properties: properties || {},
          loading: false
        }));
      }
    } else {
      // For non-admin users, show as-is
      setPropertiesModal({
        show: true,
        title,
        entityType,
        entityName,
        properties: properties || {},
        loading: false
      });
    }
  };

  const stageDeletionConfig = {
    embedding: {
      title: 'Delete Embedding Data',
      message: 'This will remove all embedding vectors for this document from the assigned Milvus collection. Metadata and processed chunks will remain so you can rerun embedding when ready.',
      success: 'Embedding vectors removed. You can rerun embedding whenever you are ready.'
    },
    metadata: {
      title: 'Delete Extracted Metadata',
      message: 'This will delete the extracted metadata JSON for this document and also remove any existing embeddings. Processed chunks will remain so you can rerun metadata extraction afterwards.',
      success: 'Extracted metadata cleared. Re-run metadata extraction to continue.'
    },
    processed: {
      title: 'Delete Processed Information',
      message: 'This will remove processed chunks, extracted metadata, and embeddings while keeping the original file. The document will return to the uploaded state so you can reprocess from parsing onwards.',
      success: 'Processed information removed. Re-run parsing to regenerate downstream artifacts.'
    }
  };

  useEffect(() => {
    if (currentTeam?.id && documentId) {
      fetchDocument();
    }
    
    // Connect to WebSocket for document processing
    knowledgeHubWebSocketService.connect();
    
    // Connect to WebSocket for graph extraction updates
    knowledgeHubGraphWebSocketService.connect();
    
    // Subscribe to graph extraction updates
    const unsubscribeGraphExtraction = knowledgeHubGraphWebSocketService.subscribe((update) => {
      // Only process updates for the current document
      if (update.documentId === documentId) {
        console.log('Received graph extraction update:', update);
        
        setCurrentJob({
          jobId: update.jobId,
          documentId: update.documentId,
          teamId: update.teamId,
          extractionType: update.extractionType,
          status: update.status,
          totalBatches: update.totalBatches,
          processedBatches: update.processedBatches,
          totalEntities: update.totalEntities,
          totalRelationships: update.totalRelationships,
          totalCostUsd: update.totalCostUsd,
          errorMessage: update.errorMessage,
          queuedAt: update.queuedAt,
          startedAt: update.startedAt,
          completedAt: update.completedAt
        });
        
        // Update extracting state based on job status
        const status = update.status;
        if (status === 'QUEUED' || status === 'RUNNING') {
          // Still processing
          if (update.extractionType === 'entities') {
            setExtracting(prev => ({ ...prev, entities: true }));
          } else if (update.extractionType === 'relationships') {
            setExtracting(prev => ({ ...prev, relationships: true }));
          } else if (update.extractionType === 'all') {
            setExtracting(prev => ({ ...prev, all: true }));
          }
        } else {
          // Job completed, failed, or cancelled
          setExtracting({ entities: false, relationships: false, all: false });
          
          if (status === 'COMPLETED') {
            showSuccessToast(
              `Extraction completed: ${update.totalEntities || 0} entities, ${update.totalRelationships || 0} relationships`
            );
            fetchMetadata();
            fetchGraphCounts();
            fetchDocumentEntities(); // Refresh document entities after extraction
            fetchDocumentRelationships(); // Refresh document relationships after extraction
            // Reset force option after successful extraction
            setExtractionConfirmModal(prev => ({ ...prev, force: false }));
          } else if (status === 'FAILED') {
            showErrorToast(update.errorMessage || 'Extraction failed');
          } else if (status === 'CANCELLED') {
            showErrorToast('Extraction cancelled');
          }
        }
      }
    });
    
    // Subscribe to document status updates
    const unsubscribe = knowledgeHubWebSocketService.subscribe((statusUpdate) => {
      // Only process updates for the current document
      if (statusUpdate.documentId === documentId) {
        console.log('Received document status update:', statusUpdate);
        
        // Update document state with the new status
        setDocument(prevDoc => {
          if (!prevDoc) return prevDoc;
          
          return {
            ...prevDoc,
            status: statusUpdate.status,
            processedAt: statusUpdate.processedAt,
            totalChunks: statusUpdate.totalChunks,
            totalTokens: statusUpdate.totalTokens,
            totalEmbeddings: statusUpdate.totalEmbeddings,
            processedS3Key: statusUpdate.processedS3Key,
            errorMessage: statusUpdate.errorMessage
          };
        });
        
        setEmbedding(prev => ({
          running: statusUpdate.status === 'EMBEDDING',
          progress: statusUpdate.totalEmbeddings !== undefined ? statusUpdate.totalEmbeddings : (prev?.progress ?? null)
        }));
        
        // Update fileExists based on status (file exists if status is not PENDING_UPLOAD)
        // FAILED status could still have the file if upload succeeded but processing failed
        setFileExists(statusUpdate.status !== 'PENDING_UPLOAD');
        
        // Fetch chunk statistics if document is now processed
        if (statusUpdate.status === 'PROCESSED' && statusUpdate.processedS3Key) {
          fetchChunkStatistics(statusUpdate.processedS3Key);
        }
        
      // Fetch metadata if document status is METADATA_EXTRACTION or beyond
      if (['METADATA_EXTRACTION', 'EMBEDDING', 'AI_READY'].includes(statusUpdate.status)) {
        fetchMetadata();
      }
      
      // Reset extraction states if extraction completes
      if (statusUpdate.status === 'AI_READY') {
        setExtracting({ entities: false, relationships: false, all: false });
      }
      }
    });
    
    return () => {
      // Unsubscribe and disconnect on unmount
      unsubscribe();
      unsubscribeGraphExtraction();
      knowledgeHubWebSocketService.disconnect();
      knowledgeHubGraphWebSocketService.disconnect();
    };
  }, [currentTeam?.id, documentId]);

  // Fetch current encryption key version on component mount
  useEffect(() => {
    const fetchCurrentEncryptionVersion = async () => {
      try {
        const result = await knowledgeHubGraphService.getCurrentEncryptionKeyVersion();
        if (result.success && result.data?.currentKeyVersion) {
          setCurrentEncryptionKeyVersion(result.data.currentKeyVersion);
        }
      } catch (err) {
        console.error('Error fetching current encryption key version:', err);
        // Don't show error toast - this is optional information
      }
    };
    
    if (currentTeam?.id) {
      fetchCurrentEncryptionVersion();
    }
  }, [currentTeam?.id]);

  // Check vault health on component mount
  useEffect(() => {
    const checkVaultHealth = async () => {
      try {
        const result = await vaultHealthService.checkVaultHealth();
        setVaultHealth({
          healthy: result.data?.healthy ?? false,
          checked: true,
          message: result.data?.message || null
        });
      } catch (err) {
        console.error('Error checking vault health:', err);
        setVaultHealth({
          healthy: false,
          checked: true,
          message: 'Failed to check vault health. The VAULT_MASTER_KEY may have changed.'
        });
      }
    };
    
    checkVaultHealth();
  }, []);

  // Fetch document entities when entity type selection changes
  useEffect(() => {
    if (currentTeam?.id && documentId && document && 
        (document.status === 'PROCESSED' || document.status === 'AI_READY')) {
      fetchDocumentEntities();
      fetchDocumentRelationships();
    }
  }, [currentTeam?.id, documentId, selectedDocumentEntityType, selectedDocumentRelationshipType, document?.status]);

  const fetchDocument = async () => {
    try {
      setLoading(true);
      const { data, error } = await knowledgeHubDocumentService.getDocumentById(documentId);
      if (error) throw new Error(error);
      setDocument(data);
      
      // Default fileExists based on status - file exists if status is not PENDING_UPLOAD
      // (FAILED status could still have the file if upload succeeded but processing failed)
      setFileExists(data.status !== 'PENDING_UPLOAD');
      
      // Fetch collection details
      if (data.collectionId) {
        const collectionResult = await knowledgeHubCollectionService.getCollectionById(data.collectionId);
        if (collectionResult.data) {
          setCollection(collectionResult.data);
        }
      }
      
      // Fetch chunk statistics if document is processed or AI ready
      if ((data.status === 'PROCESSED' || data.status === 'AI_READY') && data.processedS3Key) {
        fetchChunkStatistics(data.processedS3Key);
      }
      
      // Fetch metadata if document has metadata extraction status or beyond
      if (['METADATA_EXTRACTION', 'EMBEDDING', 'AI_READY'].includes(data.status)) {
        fetchMetadata();
      }
      
      // Fetch graph entity/relationship counts if document is processed or AI ready
      if (data.status === 'PROCESSED' || data.status === 'AI_READY') {
        fetchGraphCounts();
        // Load document entities if entity type is selected
        if (selectedDocumentEntityType !== 'All') {
          fetchDocumentEntities();
        }
        // Load document relationships if relationship type is selected
        if (selectedDocumentRelationshipType !== 'All') {
          fetchDocumentRelationships();
        }
      }
      
      if (data.totalEmbeddings !== undefined) {
        setEmbedding({
          running: data.status === 'EMBEDDING',
          progress: data.totalEmbeddings
        });
      }
    } catch (err) {
      console.error('Error fetching document:', err);
      setError('Failed to load document');
      showErrorToast(err.message || 'Failed to load document');
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async () => {
    try {
      setDownloading(true);
      const result = await knowledgeHubDocumentService.downloadDocument(documentId, document?.fileName);
      
      if (!result.success) {
        throw new Error(result.error || 'Failed to download document');
      }
      
      // File download triggered automatically via blob URL
      // Note: Browser's download dialog will appear, so we don't need a success toast
    } catch (err) {
      console.error('Error downloading document:', err);
      showErrorToast(err.message || 'Failed to download document');
    } finally {
      setDownloading(false);
    }
  };

  const fetchChunkStatistics = async (processedS3Key) => {
    if (!processedS3Key) return;
    
    try {
      setLoadingChunkStats(true);
      const { data, error } = await knowledgeHubDocumentService.generateProcessedJsonDownloadUrl(documentId);
      if (error) throw new Error(error);
      
      // Download and parse the processed JSON to get statistics
      const response = await fetch(data.downloadUrl);
      if (!response.ok) throw new Error('Failed to download processed JSON');
      
      const processedJson = await response.json();
      if (processedJson.statistics) {
        setChunkStats({
          avgQualityScore: processedJson.statistics.avgQualityScore,
          encryptionKeyVersion: processedJson.encryptionKeyVersion,
          // We already have totalChunks and totalTokens from document
        });
      }
    } catch (err) {
      console.error('Error fetching chunk statistics:', err);
      // Don't show error toast - just silently fail, stats are optional
    } finally {
      setLoadingChunkStats(false);
    }
  };


  const fetchMetadata = async () => {
    if (!documentId) return;
    
    try {
      setLoadingMetadata(true);
      const { data, error } = await knowledgeHubDocumentService.getMetadata(documentId);
      if (error) throw new Error(error);
      
      if (data) {
        // Debug: Log metadata to check if it's encrypted
        console.log('Metadata received from API:', {
          title: data.title?.substring(0, 50),
          author: data.author?.substring(0, 50),
          subject: data.subject?.substring(0, 50),
          keywords: data.keywords?.substring(0, 50),
          encryptionKeyVersion: data.encryptionKeyVersion
        });
        setMetadata(data);
      }
    } catch (err) {
      console.error('Error fetching metadata:', err);
      // Don't show error toast - just silently fail, metadata is optional
    } finally {
      setLoadingMetadata(false);
    }
  };

  const handleHardDelete = async () => {
    try {
      setHardDeleting(true);
      const { error } = await knowledgeHubDocumentService.hardDeleteDocument(documentId);
      if (error) throw new Error(error);
      
      showSuccessToast('Document deleted successfully');
      navigate(`/knowledge-hub/collection/${document.collectionId}`);
    } catch (err) {
      console.error('Error hard deleting document:', err);
      showErrorToast(err.response?.data?.error || err.message || 'Failed to hard delete document');
      setHardDeleting(false); // Reset on error so modal can be closed
    }
  };

  const openStageDeleteModal = (scope) => {
    setStageDeleteModal({ show: true, scope });
  };

  const closeStageDeleteModal = () => {
    if (!stageDeleting) {
      setStageDeleteModal({ show: false, scope: null });
    }
  };

  const handleStageDeleteConfirm = async () => {
    if (!stageDeleteModal.scope) {
      return;
    }

    const scopeKey = stageDeleteModal.scope;
    try {
      setStageDeleting(true);
      const { success, error } = await knowledgeHubDocumentService.deleteDocumentArtifacts(documentId, scopeKey);
      if (!success) {
        throw new Error(error || 'Failed to delete document artifacts');
      }

      if (scopeKey !== 'embedding') {
        setMetadata(null);
      }
      if (scopeKey === 'processed') {
        setChunkStats(null);
      }

      setEmbedding({ running: false, progress: null });
      await fetchDocument();

      const successMessage = stageDeletionConfig[scopeKey]?.success || 'Document artifacts deleted.';
      showSuccessToast(successMessage);
      setStageDeleteModal({ show: false, scope: null });
    } catch (err) {
      console.error(`Error deleting document artifacts (${scopeKey}):`, err);
      showErrorToast(err.message || 'Failed to delete document artifacts');
    } finally {
      setStageDeleting(false);
    }
  };

  const getStatusBadgeVariant = (status) => {
    switch (status) {
      case 'AI_READY':
      case 'PROCESSED':
        return 'success';
      case 'UPLOADED':
      case 'PARSING':
      case 'PARSED':
      case 'METADATA_EXTRACTION':
      case 'EMBEDDING':
        return 'info';
      case 'PENDING_UPLOAD':
        return 'warning';
      case 'FAILED':
        return 'danger';
      default:
        return 'secondary';
    }
  };

  // Check if status is in a processing state (should glow)
  const isProcessingStatus = (status) => {
    const processingStates = ['UPLOADED', 'PARSING', 'PARSED', 'PROCESSED', 'METADATA_EXTRACTION', 'EMBEDDING'];
    return processingStates.includes(status);
  };

  const getEmbeddingProviderLabel = (modelCategory) => {
    if (!modelCategory) return 'N/A';
    const providerKey = modelCategory.split('-')[0];
    const providerLabels = {
      openai: 'OpenAI',
      gemini: 'Gemini',
      cohere: 'Cohere'
    };
    return providerLabels[providerKey] || providerKey;
  };

  const getStatusFlowSteps = () => {
    const steps = [
      { key: 'PENDING_UPLOAD', label: 'Pending Upload' },
      { key: 'UPLOADED', label: 'Uploaded' },
      { key: 'PARSING', label: 'Parsing' },
      { key: 'PROCESSED', label: 'Processed' },
      { key: 'METADATA_EXTRACTION', label: 'Metadata Extraction' },
      { key: 'EMBEDDING', label: 'Embedding' },
      { key: 'AI_READY', label: 'AI Ready' }
    ];

    const statusOrder = ['PENDING_UPLOAD', 'UPLOADED', 'PARSING', 'PROCESSED', 'METADATA_EXTRACTION', 'EMBEDDING', 'AI_READY'];
    const currentStatusIndex = statusOrder.indexOf(document.status);

    // If status is FAILED, determine the last successful step and allow clicking next step
    if (document.status === 'FAILED') {
      let lastSuccessfulIndex = -1;
      
      // Determine last successful status based on document state
      if (document.processedAt) {
        // Got processed, so at least PROCESSED was reached
        lastSuccessfulIndex = statusOrder.indexOf('PROCESSED');
      } else if (document.uploadedAt && !document.processedAt) {
        // Got uploaded but not processed
        lastSuccessfulIndex = statusOrder.indexOf('UPLOADED');
      } else {
        // Only created, not uploaded
        lastSuccessfulIndex = statusOrder.indexOf('PENDING_UPLOAD');
      }
      
      return steps.map((step, index) => {
        const stepIndex = statusOrder.indexOf(step.key);
        const isCompleted = index <= lastSuccessfulIndex;
        const isCurrent = step.key === document.status;
        // Allow clicking the next step after the last successful one (not last step)
        const isClickable = !isCompleted && index < steps.length - 1 && 
                           stepIndex === lastSuccessfulIndex + 1;
        
        return {
          ...step,
          completed: isCompleted,
          current: isCurrent,
          failed: true,
          clickable: isClickable
        };
      });
    }

    // For non-failed statuses, mark steps as completed if we've reached or passed them
    // PROCESSED status means processing is complete, so it should show as completed
    return steps.map((step, index) => {
      const stepIndex = statusOrder.indexOf(step.key);
      // If current status index is >= step index, it's completed (including the current status itself)
      const isCompleted = currentStatusIndex >= stepIndex;
      // Check if this step is the current status
      const isCurrent = step.key === document.status;
      // A step is clickable if it's the next step after the current one (not completed, not current, not last)
      const isClickable = !isCompleted && !isCurrent && index < steps.length - 1 && 
                         (currentStatusIndex < 0 || stepIndex === currentStatusIndex + 1);
      
      return {
        ...step,
        completed: isCompleted,
        current: isCurrent,
        failed: false,
        clickable: isClickable
      };
    });
  };
  
  const handleNodeClick = (step) => {
    if (!step.clickable || !document || !currentTeam?.id) {
      return;
    }
    
    // Route to appropriate WebSocket method based on status
    if (step.key === 'PARSING' || step.key === 'PROCESSED') {
      // Send document processing command for parsing and processing steps
      knowledgeHubWebSocketService.sendDocumentProcessingCommand(
        document.documentId,
        step.key,
        currentTeam.id
      );
    } else if (step.key === 'METADATA_EXTRACTION') {
      // Send metadata extraction command
      knowledgeHubWebSocketService.sendMetadataExtractionCommand(
        document.documentId,
        step.key,
        currentTeam.id
      );
    } else if (step.key === 'EMBEDDING') {
      knowledgeHubWebSocketService.sendDocumentEmbeddingCommand(
        document.documentId,
        step.key,
        currentTeam.id
      );
      setEmbedding({ running: true, progress: document.totalEmbeddings ?? null });
    } else {
      // Default fallback for other statuses
      console.warn(`Unknown step key: ${step.key}, using document processing command as fallback`);
      knowledgeHubWebSocketService.sendDocumentProcessingCommand(
        document.documentId,
        step.key,
        currentTeam.id
      );
    }
    
    // Show feedback
    showSuccessToast(`Triggering ${step.label}...`);
    
    // Refresh document after a short delay to show status change
    setTimeout(() => {
      fetchDocument();
    }, 1000);
  };

  const handleEmbeddingTrigger = () => {
    if (!document || !currentTeam?.id) return;
    knowledgeHubWebSocketService.sendDocumentEmbeddingCommand(
      document.documentId,
      'EMBEDDING',
      currentTeam.id
    );
    setEmbedding({ running: true, progress: document.totalEmbeddings ?? null });
    showSuccessToast('Embedding triggered...');
  };

  const canExtractGraph = () => {
    return document.status === 'READY' || document.status === 'PROCESSED' || document.status === 'AI_READY';
  };

  const showExtractionConfirmModal = (type) => {
    if (!document || !canExtractGraph()) return;
    setExtractionConfirmModal(prev => ({ 
      ...prev, 
      show: true, 
      type,
      // Preserve force value when opening modal
      force: prev.force || false
    }));
  };

  const closeExtractionConfirmModal = () => {
    setExtractionConfirmModal(prev => ({ 
      ...prev, 
      show: false, 
      type: null,
      // Don't reset force - user might want to keep it selected
    }));
  };

  const handleForceToggle = (checked) => {
    setExtractionConfirmModal(prev => ({ ...prev, force: checked }));
  };

  const handleExtractEntitiesConfirm = async () => {
    if (!document || !canExtractGraph()) return;
    
    try {
      const { force } = extractionConfirmModal;
      closeExtractionConfirmModal();
      
      const { data, error } = await knowledgeHubGraphService.extractEntitiesFromDocument(document.documentId, force);
      if (error) {
        // Check if it's an idempotency error (entities already extracted)
        if (error.includes('already extracted') && !force) {
          // Show modal instead of toast
          setAlreadyExtractedModal({
            show: true,
            message: error,
            extractionType: 'entities'
          });
          return;
        }
        throw new Error(error);
      }
      
      if (data?.jobId) {
        // Set initial job state - WebSocket will update it
        setCurrentJob({
          jobId: data.jobId,
          documentId: data.documentId,
          teamId: currentTeam.id,
          extractionType: 'entities',
          status: 'QUEUED'
        });
        setExtracting(prev => ({ ...prev, entities: true }));
        showSuccessToast('Entity extraction job queued. Processing in background...');
      }
    } catch (err) {
      console.error('Error queueing entity extraction:', err);
      // Check if it's an idempotency error (entities already extracted)
      if (err.message && err.message.includes('already extracted') && !extractionConfirmModal.force) {
        setAlreadyExtractedModal({
          show: true,
          message: err.message,
          extractionType: 'entities'
        });
      } else {
        showErrorToast(err.message || 'Failed to queue entity extraction');
      }
    }
  };

  const handleExtractRelationshipsConfirm = async () => {
    if (!document || !canExtractGraph()) return;
    
    try {
      const { force } = extractionConfirmModal;
      closeExtractionConfirmModal();
      
      const { data, error } = await knowledgeHubGraphService.extractRelationshipsFromDocument(document.documentId, force);
      if (error) {
        if (error.includes('already extracted') && !force) {
          // Show modal instead of toast
          setAlreadyExtractedModal({
            show: true,
            message: error,
            extractionType: 'relationships'
          });
          return;
        }
        throw new Error(error);
      }
      
      if (data?.jobId) {
        // Set initial job state - WebSocket will update it
        setCurrentJob({
          jobId: data.jobId,
          documentId: data.documentId,
          teamId: currentTeam.id,
          extractionType: 'relationships',
          status: 'QUEUED'
        });
        setExtracting(prev => ({ ...prev, relationships: true }));
        showSuccessToast('Relationship extraction job queued. Processing in background...');
      }
    } catch (err) {
      console.error('Error queueing relationship extraction:', err);
      // Check if it's an idempotency error (relationships already extracted)
      if (err.message && err.message.includes('already extracted') && !extractionConfirmModal.force) {
        setAlreadyExtractedModal({
          show: true,
          message: err.message,
          extractionType: 'relationships'
        });
      } else {
        showErrorToast(err.message || 'Failed to queue relationship extraction');
      }
    }
  };

  const handleExtractAllConfirm = async () => {
    if (!document || !canExtractGraph()) return;
    
    try {
      const { force } = extractionConfirmModal;
      closeExtractionConfirmModal();
      
      const { data, error } = await knowledgeHubGraphService.extractAllFromDocument(document.documentId, force);
      if (error) {
        if (error.includes('already extracted') && !force) {
          // Show modal instead of toast
          setAlreadyExtractedModal({
            show: true,
            message: error,
            extractionType: 'all'
          });
          return;
        }
        throw new Error(error);
      }
      
      if (data?.jobId) {
        // Set initial job state - WebSocket will update it
        setCurrentJob({
          jobId: data.jobId,
          documentId: data.documentId,
          teamId: currentTeam.id,
          extractionType: 'all',
          status: 'QUEUED'
        });
        setExtracting(prev => ({ ...prev, all: true }));
        showSuccessToast('Full extraction job queued. Processing in background...');
      }
    } catch (err) {
      console.error('Error queueing full extraction:', err);
      // Check if it's an idempotency error (already extracted)
      if (err.message && err.message.includes('already extracted') && !extractionConfirmModal.force) {
        setAlreadyExtractedModal({
          show: true,
          message: err.message,
          extractionType: 'all'
        });
      } else {
        showErrorToast(err.message || 'Failed to queue full extraction');
      }
    }
  };
  
  const handleAlreadyExtractedModalClose = () => {
    setAlreadyExtractedModal({ show: false, message: null, extractionType: null });
  };
  
  const handleAlreadyExtractedForceRetry = () => {
    const { extractionType } = alreadyExtractedModal;
    handleAlreadyExtractedModalClose();
    
    // Enable force and retry the extraction
    setExtractionConfirmModal(prev => ({
      ...prev,
      show: true,
      type: extractionType,
      force: true
    }));
  };

  const handleCancelJob = async () => {
    if (!currentJob?.jobId) return;
    
    try {
      const { data, error } = await knowledgeHubGraphService.cancelJob(currentJob.jobId);
      if (error) throw new Error(error);
      
      if (data?.cancelled) {
        showSuccessToast('Extraction job cancelled');
        setCurrentJob(null);
        setExtracting({ entities: false, relationships: false, all: false });
      }
    } catch (err) {
      console.error('Error cancelling job:', err);
      showErrorToast(err.message || 'Failed to cancel job');
    }
  };

  const handleExtractConfirm = () => {
    const { type } = extractionConfirmModal;
    if (type === 'entities') {
      handleExtractEntitiesConfirm();
    } else if (type === 'relationships') {
      handleExtractRelationshipsConfirm();
    } else if (type === 'all') {
      handleExtractAllConfirm();
    }
  };

  const getExtractionModalConfig = () => {
    const { type, force } = extractionConfirmModal;
    const configs = {
      entities: {
        title: 'Extract Entities',
        message: force 
          ? 'This will re-extract entities from this document using AI. This will incur additional LLM costs. Continue?'
          : 'This will extract entities (Forms, Organizations, People, Dates, Locations, etc.) from this document using AI. This may take several minutes and will incur LLM costs. Continue?',
        confirmLabel: 'Extract Entities'
      },
      relationships: {
        title: 'Extract Relationships',
        message: force
          ? 'This will re-extract relationships from this document using AI. This will incur additional LLM costs. Continue?'
          : 'This will extract relationships between entities from this document using AI. Entities must be extracted first. This may take several minutes and will incur LLM costs. Continue?',
        confirmLabel: 'Extract Relationships'
      },
      all: {
        title: 'Extract All',
        message: force
          ? 'This will re-extract both entities and relationships from this document using AI. This will incur additional LLM costs. Continue?'
          : 'This will extract both entities and relationships from this document using AI. This may take several minutes and will incur LLM costs. Continue?',
        confirmLabel: 'Extract All'
      }
    };
    return configs[type] || configs.entities;
  };


  const fetchGraphCounts = async () => {
    if (!documentId || !currentTeam?.id) return;
    
    try {
      setLoadingGraphCounts(true);
      const entityTypes = ['Form', 'Organization', 'Person', 'Date', 'Location', 'Document'];
      
      // Fetch entities for each type filtered by documentId
        const entityPromises = entityTypes.map(type => 
          knowledgeHubGraphService.findEntities(type, { documentId, teamId: currentTeam.id })
        );
      
      const entityResults = await Promise.all(entityPromises);
      const totalEntities = entityResults.reduce((sum, result) => {
        return sum + (result.success && Array.isArray(result.data) ? result.data.length : 0);
      }, 0);
      
      setGraphEntityCount(totalEntities);
      
      // Fetch relationships filtered by documentId
      const relationshipResult = await knowledgeHubGraphService.findRelationships({ 
        documentId, 
        teamId: currentTeam.id 
      });
      
      const totalRelationships = relationshipResult.success && Array.isArray(relationshipResult.data) 
        ? relationshipResult.data.length 
        : 0;
      
      setGraphRelationshipCount(totalRelationships);
    } catch (err) {
      console.error('Error fetching graph counts:', err);
      // Don't show error toast - counts are optional
    } finally {
      setLoadingGraphCounts(false);
    }
  };

  const fetchDocumentEntities = async () => {
    if (!documentId || !currentTeam?.id) {
      setDocumentEntities([]);
      return;
    }
    
    try {
      setLoadingDocumentEntities(true);
      
      if (selectedDocumentEntityType === 'All') {
        // Fetch all entity types and combine them
        const entityTypes = ['Form', 'Organization', 'Person', 'Date', 'Location', 'Document'];
        const entityPromises = entityTypes.map(type => 
          knowledgeHubGraphService.findEntities(type, { documentId, teamId: currentTeam.id })
        );
        
        const entityResults = await Promise.all(entityPromises);
        const allEntities = entityResults.reduce((acc, result, index) => {
          if (result.success && Array.isArray(result.data)) {
            // Add type to each entity if not present
            const entityType = entityTypes[index];
            const typedEntities = result.data.map(entity => ({
              ...entity,
              type: entity.type || entityType
            }));
            return [...acc, ...typedEntities];
          }
          return acc;
        }, []);
        
        setDocumentEntities(allEntities);
      } else {
        // Fetch entities for specific type
        const { data, error } = await knowledgeHubGraphService.findEntities(
          selectedDocumentEntityType, 
          { documentId, teamId: currentTeam.id }
        );
        if (error) throw new Error(error);
        const entities = Array.isArray(data) ? data : [];
        // Ensure type is set
        const typedEntities = entities.map(entity => ({
          ...entity,
          type: entity.type || selectedDocumentEntityType
        }));
        setDocumentEntities(typedEntities);
      }
    } catch (err) {
      console.error('Error fetching document entities:', err);
      showErrorToast(err.message || 'Failed to fetch document entities');
      setDocumentEntities([]);
    } finally {
      setLoadingDocumentEntities(false);
    }
  };

  const fetchDocumentRelationships = async () => {
    if (!documentId || !currentTeam?.id) {
      setDocumentRelationships([]);
      return;
    }

    try {
      setLoadingDocumentRelationships(true);
      const filters = { 
        teamId: currentTeam.id,
        documentId: documentId
      };
      
      if (selectedDocumentRelationshipType && selectedDocumentRelationshipType !== 'All') {
        filters.relationshipType = selectedDocumentRelationshipType;
      }
      
      const { data, error } = await knowledgeHubGraphService.findRelationships(filters);
      if (error) throw new Error(error);
      setDocumentRelationships(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Error fetching document relationships:', err);
      showErrorToast(err.message || 'Failed to fetch document relationships');
      setDocumentRelationships([]);
    } finally {
      setLoadingDocumentRelationships(false);
    }
  };

  const getGraphExtractionInfo = () => {
    if (!metadata?.customMetadata?.graphExtraction && graphEntityCount === null) return null;
    
    const graphExtraction = metadata?.customMetadata?.graphExtraction;
    const entityExtraction = graphExtraction?.entityExtraction;
    const relationshipExtraction = graphExtraction?.relationshipExtraction;
    
    // Debug logging to help troubleshoot model info display
    if (graphExtraction) {
      console.log('Graph Extraction Metadata:', {
        entityExtraction: entityExtraction ? {
          modelName: entityExtraction.modelName,
          modelCategory: entityExtraction.modelCategory,
          provider: entityExtraction.provider,
          hasModelInfo: !!(entityExtraction.modelName || entityExtraction.provider)
        } : null,
        relationshipExtraction: relationshipExtraction ? {
          modelName: relationshipExtraction.modelName,
          modelCategory: relationshipExtraction.modelCategory,
          provider: relationshipExtraction.provider,
          hasModelInfo: !!(relationshipExtraction.modelName || relationshipExtraction.provider)
        } : null
      });
    }
    
    // Use graphEntityCount from Neo4j query if available, otherwise 0
    const entityCount = graphEntityCount !== null ? graphEntityCount : 0;
    const relationshipCount = graphRelationshipCount !== null ? graphRelationshipCount : 0;
    const entityCost = entityExtraction?.costUsd || 0;
    const relationshipCost = relationshipExtraction?.costUsd || 0;
    const totalCost = entityCost + relationshipCost;
    
    const hasData = entityCount > 0 || relationshipCount > 0 || entityExtraction || relationshipExtraction;
    
    if (!hasData) return null;
    
    return {
      entityCount,
      relationshipCount,
      entityCost,
      relationshipCost,
      totalCost,
      extractedAt: entityExtraction?.extractedAt || relationshipExtraction?.extractedAt,
      // Timing information - separate for entities and relationships
      entityStartedAt: entityExtraction?.startedAt,
      entityCompletedAt: entityExtraction?.completedAt,
      entityDurationMs: entityExtraction?.durationMs,
      relationshipStartedAt: relationshipExtraction?.startedAt,
      relationshipCompletedAt: relationshipExtraction?.completedAt,
      relationshipDurationMs: relationshipExtraction?.durationMs,
      // Model information - separate for entities and relationships
      entityModelName: entityExtraction?.modelName,
      entityModelCategory: entityExtraction?.modelCategory,
      entityProvider: entityExtraction?.provider,
      entityExtraction: entityExtraction, // Keep full object to check if extraction exists
      relationshipModelName: relationshipExtraction?.modelName,
      relationshipModelCategory: relationshipExtraction?.modelCategory,
      relationshipProvider: relationshipExtraction?.provider,
      relationshipExtraction: relationshipExtraction, // Keep full object to check if extraction exists
      // For backwards compatibility - use entity model if available, otherwise relationship model
      modelName: entityExtraction?.modelName || relationshipExtraction?.modelName,
      modelCategory: entityExtraction?.modelCategory || relationshipExtraction?.modelCategory,
      provider: entityExtraction?.provider || relationshipExtraction?.provider
    };
  };

  const calculateUploadDuration = () => {
    if (!document.createdAt || !document.uploadedAt) return null;
    
    try {
      // Handle array format from LocalDateTime serialization
      let createdDate, uploadedDate;
      
      if (Array.isArray(document.createdAt)) {
        const [year, month, day, hour, minute, second, nano] = document.createdAt;
        createdDate = new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
      } else {
        createdDate = new Date(document.createdAt);
      }
      
      if (Array.isArray(document.uploadedAt)) {
        const [year, month, day, hour, minute, second, nano] = document.uploadedAt;
        uploadedDate = new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
      } else {
        uploadedDate = new Date(document.uploadedAt);
      }
      
      const diffMs = uploadedDate.getTime() - createdDate.getTime();
      const diffSeconds = Math.floor(diffMs / 1000);
      const diffMinutes = Math.floor(diffSeconds / 60);
      
      if (diffSeconds < 60) {
        return `${diffSeconds} second${diffSeconds !== 1 ? 's' : ''}`;
      } else if (diffMinutes < 60) {
        return `${diffMinutes} minute${diffMinutes !== 1 ? 's' : ''}`;
      } else {
        const hours = Math.floor(diffMinutes / 60);
        const remainingMinutes = diffMinutes % 60;
        return `${hours} hour${hours !== 1 ? 's' : ''} ${remainingMinutes} minute${remainingMinutes !== 1 ? 's' : ''}`;
      }
    } catch (error) {
      console.error('Error calculating upload duration:', error);
      return null;
    }
  };

  if (loading) {
    return (
      <div className="text-center py-5">
        <Spinner animation="border" role="status">
          <span className="visually-hidden">Loading...</span>
        </Spinner>
      </div>
    );
  }

  if (error || !document) {
    return (
      <div className="alert alert-danger" role="alert">
        {error || 'Document not found'}
      </div>
    );
  }

  return (
    <div className="view-document-container">
      {/* Vault Health Warning Banner */}
      {vaultHealth.checked && !vaultHealth.healthy && (
        <EncryptionVersionWarningBanner
          show={true}
          variant="danger"
          heading="Vault Decryption Failed"
          message={
            <>
              <strong>Warning:</strong> The vault file cannot be decrypted. This usually means the <code>VAULT_MASTER_KEY</code> has changed. 
              The vault file needs to be re-encrypted with the new key. Until this is fixed, secrets cannot be read from the vault and many features may not work.
              <br /><br />
              <strong>To fix:</strong>
              <ol style={{ marginBottom: 0, paddingLeft: '20px' }}>
                <li>Re-encrypt the vault file using the vault-reader CLI with the new <code>VAULT_MASTER_KEY</code></li>
                <li><strong>Restart the api-gateway-service</strong> so it picks up the newly encrypted vault file</li>
              </ol>
            </>
          }
        />
      )}
      
      {/* Header */}
      <Card className="mb-4 border-0">
        <Card.Body>
          <div className="d-flex align-items-center justify-content-between">
            <div className="d-flex align-items-center gap-2">
              <Button 
                variant="link" 
                className="p-0"
                onClick={() => navigate(`/knowledge-hub/collection/${document.collectionId}`)}
              >
                <HiArrowLeft className="text-primary" size={24} />
              </Button>
              <h4 className="mb-0">{document.fileName}</h4>
              <Badge 
                bg={getStatusBadgeVariant(document.status)}
                className={isProcessingStatus(document.status) ? 'status-processing' : ''}
              >
                {document.status}
              </Badge>
            </div>
            <div className="d-flex gap-2">
              {/* Show Download button for documents that have been uploaded (UPLOADED and beyond) */}
              {/* Note: FAILED status might still have the file if upload succeeded but processing failed */}
              {document.status !== 'PENDING_UPLOAD' ? (
                <OverlayTrigger
                  placement="bottom"
                  overlay={<Tooltip id="download-tooltip">The file could not be uploaded</Tooltip>}
                  show={fileExists === false ? true : false}
                >
                  <span>
                    <Button 
                      variant="primary"
                      onClick={handleDownload}
                      disabled={downloading || fileExists === false}
                    >
                      <HiDownload /> {downloading ? 'Downloading...' : 'Download'}
                    </Button>
                  </span>
                </OverlayTrigger>
              ) : null}
              <Button 
                variant="outline-danger"
                onClick={() => setShowDeleteModal(true)}
                disabled={hardDeleting}
              >
                {hardDeleting ? (
                  <>
                    <Spinner animation="border" size="sm" className="me-2" />
                    Deleting...
                  </>
                ) : (
                  <>
                    <HiTrash /> Hard Delete
                  </>
                )}
              </Button>
            </div>
          </div>
        </Card.Body>
      </Card>

      {/* Status Flow Bar */}
      <Card className="mb-4 border-0">
        <Card.Body style={{ padding: '20px' }}>
          <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
            {getStatusFlowSteps().map((step, index, array) => {
              const isLast = index === array.length - 1;
              const nextStepCompleted = index < array.length - 1 && array[index + 1]?.completed;
              const connectorCompleted = step.completed;
              
              return (
                <React.Fragment key={step.key}>
                  <div 
                    className={step.current && isProcessingStatus(document.status) ? 'status-step-processing' : ''}
                    style={{ 
                      display: 'flex', 
                      flexDirection: 'column', 
                      alignItems: 'center',
                      flex: 1,
                      minWidth: '100px',
                      position: 'relative',
                      zIndex: 2,
                      cursor: step.clickable ? 'pointer' : 'default',
                      padding: step.current && isProcessingStatus(document.status) ? '8px' : '0',
                      margin: step.current && isProcessingStatus(document.status) ? '-8px' : '0'
                    }}
                    onClick={() => handleNodeClick(step)}
                  >
                    {/* Step Icon */}
                    <div style={{ 
                      position: 'relative',
                      marginBottom: '8px',
                      zIndex: 3,
                      opacity: step.clickable ? 1 : (step.completed ? 1 : 0.6),
                      transition: 'opacity 0.2s'
                    }}>
                      {step.completed ? (
                        <HiCheckCircle 
                          className="text-success" 
                          size={36} 
                          style={{ 
                            backgroundColor: 'white', 
                            borderRadius: '50%',
                            padding: '2px'
                          }} 
                        />
                      ) : step.failed ? (
                        <div 
                          className="rounded-circle bg-danger d-flex align-items-center justify-content-center text-white"
                          style={{ 
                            width: '36px', 
                            height: '36px', 
                            fontSize: '22px', 
                            fontWeight: 'bold',
                            lineHeight: '36px'
                          }}
                        >
                          ✕
                        </div>
                      ) : (
                        <div 
                          className={`rounded-circle border d-flex align-items-center justify-content-center`}
                          style={{ 
                            width: '36px', 
                            height: '36px', 
                            fontSize: '14px',
                            borderWidth: '2px',
                            borderColor: '#dee2e6',
                            color: '#6c757d',
                            backgroundColor: 'white',
                            fontWeight: '500'
                          }}
                        >
                          {index + 1}
                        </div>
                      )}
                    </div>
                    
                    {/* Step Label */}
                    <div style={{ textAlign: 'center' }}>
                      <span 
                        className={`${
                          step.completed 
                            ? 'text-success fw-semibold' 
                            : step.clickable 
                              ? 'text-primary fw-semibold' 
                              : step.failed && step.current
                                ? 'text-danger fw-semibold' 
                                : 'text-muted'
                        }`}
                        style={{ fontSize: '12px', display: 'block', lineHeight: '1.4' }}
                      >
                        {step.label}
                      </span>
                      {step.current && (
                        <span className="badge bg-secondary mt-1" style={{ fontSize: '9px', padding: '2px 6px' }}>
                          Current
                        </span>
                      )}
                      {step.clickable && (
                        <span className="badge bg-primary mt-1" style={{ fontSize: '9px', padding: '2px 6px' }}>
                          Click to start
                        </span>
                      )}
                    </div>
                  </div>
                  
                  {/* Connector Line */}
                  {!isLast && (
                    <div 
                      style={{
                        flex: 1,
                        height: '3px',
                        backgroundColor: connectorCompleted ? '#28a745' : step.failed ? '#dc3545' : '#dee2e6',
                        marginLeft: '10px',
                        marginRight: '10px',
                        position: 'relative',
                        zIndex: 1,
                        opacity: connectorCompleted ? 1 : 0.4,
                        alignSelf: 'flex-start',
                        marginTop: '18px'
                      }}
                    />
                  )}
                </React.Fragment>
              );
            })}
          </div>
        </Card.Body>
      </Card>

      {/* Error Message */}
      {document.errorMessage && (
        <Alert variant="danger" className="mb-4">
          <strong>Error:</strong> {document.errorMessage}
        </Alert>
      )}

      {/* Main Content */}
      <Row className="g-4">
        {/* File Information */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiDocument className="text-primary me-2" size={24} />
                <h5 className="mb-0 fw-semibold">File Information</h5>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">File Name</span>
                <span className="text-secondary">{document.fileName}</span>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">File Size</span>
                <span className="text-secondary">
                  {document.fileSize ? `${(document.fileSize / 1024 / 1024).toFixed(2)} MB` : 'N/A'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Encryption Status</span>
                <div className="d-flex align-items-center gap-2">
                  {document.encrypted ? (
                    <>
                      <HiLockClosed className="text-success" size={18} />
                      <Badge bg="success">Encrypted</Badge>
                    </>
                  ) : (
                    <>
                      <HiLockOpen className="text-warning" size={18} />
                      <Badge bg="warning">Not Encrypted</Badge>
                    </>
                  )}
                </div>
              </div>
              {document.encrypted && (
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <span className="text-muted h6">Encryption Key Version</span>
                  <div className="d-flex align-items-center gap-2">
                    {document.encryptionKeyVersion ? (
                      <>
                        <Badge 
                          bg={
                            currentEncryptionKeyVersion && 
                            compareVersion(document.encryptionKeyVersion, currentEncryptionKeyVersion)
                              ? "warning" 
                              : "info"
                          }
                          title={
                            currentEncryptionKeyVersion && 
                            compareVersion(document.encryptionKeyVersion, currentEncryptionKeyVersion)
                              ? `File encrypted with ${document.encryptionKeyVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`
                              : `File encrypted with key version ${document.encryptionKeyVersion}`
                          }
                        >
                          {document.encryptionKeyVersion}
                        </Badge>
                        {currentEncryptionKeyVersion && 
                         compareVersion(document.encryptionKeyVersion, currentEncryptionKeyVersion) && (
                          <OverlayTrigger
                            placement="top"
                            overlay={
                              <Tooltip>
                                This file is encrypted with an older key version ({document.encryptionKeyVersion}). 
                                Current version is {currentEncryptionKeyVersion}. Re-upload the file to encrypt with the latest version.
                              </Tooltip>
                            }
                          >
                            <span><HiInformationCircle className="text-warning" size={16} /></span>
                          </OverlayTrigger>
                        )}
                      </>
                    ) : (
                      <Badge bg="secondary" title="Legacy encrypted file - encryption key version not tracked">
                        Unknown
                      </Badge>
                    )}
                  </div>
                </div>
              )}
              <div className="d-flex justify-content-between align-items-center">
                <span className="text-muted h6">Status</span>
                <Badge 
                  bg={getStatusBadgeVariant(document.status)}
                  className={isProcessingStatus(document.status) ? 'status-processing' : ''}
                >
                  {document.status}
                </Badge>
              </div>
            </Card.Body>
          </Card>
        </Col>

        {/* Timestamps */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiCalendar className="text-primary me-2" size={24} />
                <h5 className="mb-0 fw-semibold">Timestamps</h5>
              </div>
              {document.uploadedAt && (
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <span className="text-muted h6 mb-0">Uploaded</span>
                  <span className="text-secondary">
                    {formatDateTime(document.uploadedAt)}
                  </span>
                </div>
              )}
              {calculateUploadDuration() && (
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <span className="text-muted h6 mb-0">Upload Duration</span>
                  <span className="text-secondary">
                    {calculateUploadDuration()}
                  </span>
                </div>
              )}
              <div className="d-flex justify-content-between align-items-center">
                <span className="text-muted h6 mb-0">Processed</span>
                <span className="text-secondary">
                  {document.processedAt ? formatDateTime(document.processedAt) : 'The process has not started yet'}
                </span>
              </div>
            </Card.Body>
          </Card>
        </Col>

        {/* Chunking Configuration */}
        {(document.chunkSize || document.overlapTokens || document.chunkStrategy) && (
          <Col md={6}>
            <Card className="border-0 bg-light h-100">
              <Card.Body>
                <div className="d-flex align-items-center mb-3">
                  <HiCube className="text-primary me-2" size={24} />
                  <h5 className="mb-0 fw-semibold">Chunking Configuration</h5>
                </div>
                {document.chunkSize && (
                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <span className="text-muted h6">Chunk Size</span>
                    <span className="text-secondary">{document.chunkSize} tokens</span>
                  </div>
                )}
                {document.overlapTokens && (
                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <span className="text-muted h6">Overlap</span>
                    <span className="text-secondary">{document.overlapTokens} tokens</span>
                  </div>
                )}
                {document.chunkStrategy && (
                  <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted h6">Chunk Strategy</span>
                    <span className="text-secondary">{document.chunkStrategy}</span>
                  </div>
                )}
              </Card.Body>
            </Card>
          </Col>
        )}

        {/* RAG Information */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiDocument className="text-primary me-2" size={24} />
                <h5 className="mb-0 fw-semibold">RAG Information</h5>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">RAG Collection</span>
                <span className="text-secondary">
                  {collection?.milvusCollectionName || 'Not assigned'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Embedding Model</span>
                <span className="text-secondary">
                  {collection?.embeddingModelName || 'Not configured'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Embedding Category</span>
                <span className="text-secondary">
                  {collection?.embeddingModel || 'N/A'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Embedding Provider</span>
                <span className="text-secondary">
                  {getEmbeddingProviderLabel(collection?.embeddingModel)}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Embedding Dimension</span>
                <span className="text-secondary">
                  {collection?.embeddingDimension || 'N/A'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Late Chunking</span>
                <span className="text-secondary">
                  {collection?.lateChunkingEnabled ? 'Enabled' : 'Disabled'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Embeddings Stored</span>
                <span className="text-secondary">
                  {document.totalEmbeddings !== undefined ? document.totalEmbeddings : 'N/A'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center">
                <span className="text-muted h6">Embedding Status</span>
                <span className="text-secondary">
                  {embedding.running ? 'In progress' : (document.status === 'AI_READY' ? 'Completed' : 'Not started')}
                </span>
              </div>
              {collection?.milvusCollectionName ? (
                <div className="d-flex justify-content-end gap-2 mt-3">
                  {((document.totalEmbeddings ?? 0) > 0 || document.status === 'AI_READY') && (
                    <Button
                      variant="outline-danger"
                      size="sm"
                      onClick={() => openStageDeleteModal('embedding')}
                      disabled={embedding.running || stageDeleting || stageDeleteModal.show}
                    >
                      Delete Embedding Data
                    </Button>
                  )}
                  <Button
                    variant="outline-primary"
                    size="sm"
                    onClick={handleEmbeddingTrigger}
                    disabled={embedding.running}
                  >
                    {embedding.running ? (
                      <>
                        <Spinner animation="border" size="sm" className="me-2" />
                        Embedding...
                      </>
                    ) : (
                      'Run Embedding'
                    )}
                  </Button>
                </div>
              ) : (
                <div className="mt-3 text-muted small">
                  Assign a RAG collection to enable embedding for this document.
                </div>
              )}
            </Card.Body>
          </Card>
        </Col>

        {/* Processed Chunks Information */}
        {['PROCESSED', 'METADATA_EXTRACTION', 'EMBEDDING', 'AI_READY'].includes(document.status) && (
          <Col md={6}>
            <Card className="border-0 bg-light h-100">
              <Card.Body>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <div className="d-flex align-items-center">
                    <HiChartBar className="text-primary me-2" size={24} />
                    <h5 className="mb-0 fw-semibold">Processed Chunks</h5>
                  </div>
                  <div className="d-flex align-items-center gap-2">
                    {(document.processedS3Key || (document.totalChunks ?? 0) > 0) && (
                      <Button
                        variant="outline-danger"
                        size="sm"
                        onClick={() => openStageDeleteModal('processed')}
                        disabled={stageDeleting || stageDeleteModal.show}
                      >
                        Delete Processed Data
                      </Button>
                    )}
                  </div>
                </div>
                {document.totalChunks !== null && document.totalChunks !== undefined && (
                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <span className="text-muted h6">Total Chunks</span>
                    <span className="text-secondary">{document.totalChunks.toLocaleString()}</span>
                  </div>
                )}
                {document.totalTokens !== null && document.totalTokens !== undefined && (
                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <span className="text-muted h6">Total Tokens</span>
                    <span className="text-secondary">{document.totalTokens.toLocaleString()}</span>
                  </div>
                )}
                {loadingChunkStats ? (
                  <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted h6">Avg Quality Score</span>
                    <Spinner animation="border" size="sm" />
                  </div>
                ) : chunkStats?.avgQualityScore !== null && chunkStats?.avgQualityScore !== undefined ? (
                  <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted h6">Avg Quality Score</span>
                    <span className="text-secondary">{(chunkStats.avgQualityScore * 100).toFixed(1)}%</span>
                  </div>
                ) : (
                  <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted h6">Avg Quality Score</span>
                    <span className="text-secondary">N/A</span>
                  </div>
                )}
                {document.processedS3Key && (
                  <>
                    <hr className="my-3" />
                    <div className="d-flex justify-content-between align-items-center mb-3">
                      <span className="text-muted h6">Encryption Status</span>
                      <div className="d-flex align-items-center gap-2">
                        <HiLockClosed className="text-success" size={18} />
                        <Badge bg="success">Encrypted</Badge>
                      </div>
                    </div>
                    {chunkStats?.encryptionKeyVersion && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Encryption Key Version</span>
                        <div className="d-flex align-items-center gap-2">
                          <Badge 
                            bg={
                              currentEncryptionKeyVersion && 
                              compareVersion(chunkStats.encryptionKeyVersion, currentEncryptionKeyVersion)
                                ? "warning" 
                                : "info"
                            }
                            title={
                              currentEncryptionKeyVersion && 
                              compareVersion(chunkStats.encryptionKeyVersion, currentEncryptionKeyVersion)
                                ? `Processed data encrypted with ${chunkStats.encryptionKeyVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`
                                : `Processed data encrypted with key version ${chunkStats.encryptionKeyVersion}`
                            }
                          >
                            {chunkStats.encryptionKeyVersion}
                          </Badge>
                          {currentEncryptionKeyVersion && 
                           compareVersion(chunkStats.encryptionKeyVersion, currentEncryptionKeyVersion) && (
                            <OverlayTrigger
                              placement="top"
                              overlay={
                                <Tooltip>
                                  This processed data is encrypted with an older key version ({chunkStats.encryptionKeyVersion}). 
                                  Current version is {currentEncryptionKeyVersion}. Re-process the document to encrypt with the latest version.
                                </Tooltip>
                              }
                            >
                              <span><HiInformationCircle className="text-warning" size={16} /></span>
                            </OverlayTrigger>
                          )}
                        </div>
                      </div>
                    )}
                  </>
                )}
              </Card.Body>
            </Card>
          </Col>
        )}

        {/* Metadata Information */}
        {(document.status === 'METADATA_EXTRACTION' || document.status === 'EMBEDDING' || document.status === 'AI_READY') && (
          <Col md={6}>
            <Card className="border-0 bg-light h-100">
              <Card.Body>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <div className="d-flex align-items-center">
                    <HiInformationCircle className="text-primary me-2" size={24} />
                    <h5 className="mb-0 fw-semibold">Document Metadata</h5>
                  </div>
                  {metadata && (
                    <Button
                      variant="outline-danger"
                      size="sm"
                      onClick={() => openStageDeleteModal('metadata')}
                      disabled={stageDeleting || stageDeleteModal.show}
                    >
                      Delete Metadata
                    </Button>
                  )}
                </div>
                {loadingMetadata ? (
                  <div className="text-center py-3">
                    <Spinner animation="border" size="sm" />
                    <span className="ms-2 text-muted">Loading metadata...</span>
                  </div>
                ) : metadata ? (
                  <div>
                    {/* Document Information */}
                    {metadata.title && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Title</span>
                        <span className="text-secondary fw-medium">{metadata.title}</span>
                      </div>
                    )}
                    {metadata.author && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Author</span>
                        <span className="text-secondary">{metadata.author}</span>
                      </div>
                    )}
                    {metadata.subject && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Subject</span>
                        <span className="text-secondary">{metadata.subject}</span>
                      </div>
                    )}
                    {metadata.keywords && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Keywords</span>
                        <span className="text-secondary">{metadata.keywords}</span>
                      </div>
                    )}
                    
                    {/* Document Statistics */}
                    <hr className="my-3" />
                    {metadata.documentType && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Document Type</span>
                        <span className="text-secondary">{metadata.documentType}</span>
                      </div>
                    )}
                    {metadata.mimeType && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">MIME Type</span>
                        <span className="text-secondary small">{metadata.mimeType}</span>
                      </div>
                    )}
                    {metadata.language && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Language</span>
                        <span className="text-secondary text-uppercase">{metadata.language}</span>
                      </div>
                    )}
                    {metadata.pageCount !== null && metadata.pageCount !== undefined && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Pages</span>
                        <span className="text-secondary">{metadata.pageCount}</span>
                      </div>
                    )}
                    {metadata.wordCount !== null && metadata.wordCount !== undefined && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Words</span>
                        <span className="text-secondary">{metadata.wordCount.toLocaleString()}</span>
                      </div>
                    )}
                    {metadata.characterCount !== null && metadata.characterCount !== undefined && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Characters</span>
                        <span className="text-secondary">{metadata.characterCount.toLocaleString()}</span>
                      </div>
                    )}
                    
                    {/* Additional Metadata */}
                    {(metadata.creator || metadata.producer) && (
                      <>
                        <hr className="my-3" />
                        {metadata.creator && (
                          <div className="d-flex justify-content-between align-items-center mb-3">
                            <span className="text-muted h6">Creator</span>
                            <span className="text-secondary small">{metadata.creator}</span>
                          </div>
                        )}
                        {metadata.producer && (
                          <div className="d-flex justify-content-between align-items-center mb-3">
                            <span className="text-muted h6">Producer</span>
                            <span className="text-secondary small">{metadata.producer}</span>
                          </div>
                        )}
                      </>
                    )}
                    
                    {/* Extraction Info */}
                    <hr className="my-3" />
                    {metadata.extractedAt && (
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <span className="text-muted h6">Extracted At</span>
                        <span className="text-secondary small">{formatDateTime(metadata.extractedAt)}</span>
                      </div>
                    )}
                    
                    {/* Encryption Info */}
                    <hr className="my-3" />
                    <div className="d-flex justify-content-between align-items-center mb-3">
                      <span className="text-muted h6">Encryption Status</span>
                      <div className="d-flex align-items-center gap-2">
                        {metadata.encryptionKeyVersion ? (
                          <>
                            <HiLockClosed className="text-success" size={18} />
                            <Badge bg="success">Encrypted</Badge>
                          </>
                        ) : (
                          <>
                            <HiLockOpen className="text-warning" size={18} />
                            <Badge bg="warning">Not Encrypted</Badge>
                          </>
                        )}
                      </div>
                    </div>
                    {metadata.encryptionKeyVersion && (
                      <div className="d-flex justify-content-between align-items-center">
                        <span className="text-muted h6">Encryption Key Version</span>
                        <div className="d-flex align-items-center gap-2">
                          <Badge 
                            bg={
                              currentEncryptionKeyVersion && 
                              compareVersion(metadata.encryptionKeyVersion, currentEncryptionKeyVersion)
                                ? "warning" 
                                : "info"
                            }
                            title={
                              currentEncryptionKeyVersion && 
                              compareVersion(metadata.encryptionKeyVersion, currentEncryptionKeyVersion)
                                ? `Metadata encrypted with ${metadata.encryptionKeyVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`
                                : `Metadata encrypted with key version ${metadata.encryptionKeyVersion}`
                            }
                          >
                            {metadata.encryptionKeyVersion}
                          </Badge>
                          {currentEncryptionKeyVersion && 
                           compareVersion(metadata.encryptionKeyVersion, currentEncryptionKeyVersion) && (
                            <OverlayTrigger
                              placement="top"
                              overlay={
                                <Tooltip>
                                  This metadata is encrypted with an older key version ({metadata.encryptionKeyVersion}). 
                                  Current version is {currentEncryptionKeyVersion}. Re-extract metadata to encrypt with the latest version.
                                </Tooltip>
                              }
                            >
                              <span><HiInformationCircle className="text-warning" size={16} /></span>
                            </OverlayTrigger>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="text-center py-3 text-muted">
                    <p className="mb-0">No metadata available</p>
                  </div>
                )}
              </Card.Body>
            </Card>
          </Col>
        )}

        {/* Knowledge Graph Extraction */}
        {canExtractGraph() && (
          <Col md={12}>
            <Card className="border-0 bg-light h-100">
              <Card.Body>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <div className="d-flex align-items-center">
                    <HiHashtag className="text-primary me-2" size={24} />
                    <h5 className="mb-0 fw-semibold">Knowledge Graph</h5>
                  </div>
                  <Button
                    variant="outline-secondary"
                    size="sm"
                    onClick={() => {
                      // In development, Neo4j Browser is accessible directly on localhost:7474
                      // In production, it's proxied through Nginx at /neo4j/
                      const neo4jUrl = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
                        ? 'http://localhost:7474'
                        : '/neo4j/';
                      window.open(neo4jUrl, '_blank');
                    }}
                  >
                    <HiCube className="me-1" /> View in Neo4j
                  </Button>
                </div>
                
                {loadingGraphCounts ? (
                  <div className="text-center py-3 mb-3">
                    <Spinner animation="border" size="sm" />
                    <span className="ms-2 text-muted">Loading graph info...</span>
                  </div>
                ) : (() => {
                  const graphInfo = getGraphExtractionInfo();
                  return graphInfo ? (
                    <div className="mb-3">
                      <div className="d-flex justify-content-between align-items-center mb-2">
                        <span className="text-muted h6">Status</span>
                        <Badge bg="success">Extracted</Badge>
                      </div>
                      {graphInfo.totalCost > 0 && (
                        <div className="d-flex justify-content-between align-items-center mb-3">
                          <span className="text-muted h6 text-start">Total Extraction Cost</span>
                          <span className="text-secondary fw-semibold">${graphInfo.totalCost.toFixed(6)}</span>
                        </div>
                      )}
                      
                      {/* Entities Section */}
                      {(graphInfo.entityCount > 0 || graphInfo.entityStartedAt || graphInfo.entityModelName || graphInfo.entityProvider || graphInfo.entityExtraction) && (
                        <div className="mb-3 pb-3 border-bottom">
                          <h6 className="text-muted mb-2 fw-semibold text-start">Entities</h6>
                          {graphInfo.entityCount > 0 && (
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <span className="text-muted small">Count</span>
                              <span className="text-secondary">{graphInfo.entityCount}</span>
                            </div>
                          )}
                          {graphInfo.entityStartedAt && (
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <span className="text-muted small">Started</span>
                              <span className="text-secondary small">
                                {formatDateTime(new Date(graphInfo.entityStartedAt))}
                              </span>
                            </div>
                          )}
                          {graphInfo.entityCompletedAt && (
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <span className="text-muted small">Completed</span>
                              <span className="text-secondary small">
                                {formatDateTime(new Date(graphInfo.entityCompletedAt))}
                                {graphInfo.entityDurationMs && (
                                  <span className="text-muted ms-2">
                                    ({Math.round(graphInfo.entityDurationMs / 1000)}s)
                                  </span>
                                )}
                              </span>
                            </div>
                          )}
                          {(graphInfo.entityModelName || graphInfo.entityProvider || graphInfo.entityExtraction) && (
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <span className="text-muted small">Model</span>
                              <span className="text-secondary small">
                                {graphInfo.entityModelName || 'Unknown'}
                                {graphInfo.entityProvider && (
                                  <span className="text-muted ms-1">({graphInfo.entityProvider})</span>
                                )}
                              </span>
                            </div>
                          )}
                          {graphInfo.entityCost > 0 && (
                            <div className="d-flex justify-content-between align-items-center">
                              <span className="text-muted small">Cost</span>
                              <span className="text-secondary small">${graphInfo.entityCost.toFixed(6)}</span>
                            </div>
                          )}
                        </div>
                      )}
                      
                      {/* Relationships Section */}
                      {(graphInfo.relationshipCount > 0 || graphInfo.relationshipStartedAt || graphInfo.relationshipModelName || graphInfo.relationshipProvider || graphInfo.relationshipExtraction) && (
                        <div className="mb-3">
                          <h6 className="text-muted mb-2 fw-semibold text-start">Relationships</h6>
                          {graphInfo.relationshipCount > 0 && (
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <span className="text-muted small">Count</span>
                              <span className="text-secondary">{graphInfo.relationshipCount}</span>
                            </div>
                          )}
                          {graphInfo.relationshipStartedAt && (
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <span className="text-muted small">Started</span>
                              <span className="text-secondary small">
                                {formatDateTime(new Date(graphInfo.relationshipStartedAt))}
                              </span>
                            </div>
                          )}
                          {graphInfo.relationshipCompletedAt && (
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <span className="text-muted small">Completed</span>
                              <span className="text-secondary small">
                                {formatDateTime(new Date(graphInfo.relationshipCompletedAt))}
                                {graphInfo.relationshipDurationMs && (
                                  <span className="text-muted ms-2">
                                    ({Math.round(graphInfo.relationshipDurationMs / 1000)}s)
                                  </span>
                                )}
                              </span>
                            </div>
                          )}
                          {(graphInfo.relationshipModelName || graphInfo.relationshipProvider || graphInfo.relationshipExtraction) && (
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <span className="text-muted small">Model</span>
                              <span className="text-secondary small">
                                {graphInfo.relationshipModelName || 'Unknown'}
                                {graphInfo.relationshipProvider && (
                                  <span className="text-muted ms-1">({graphInfo.relationshipProvider})</span>
                                )}
                              </span>
                            </div>
                          )}
                          {graphInfo.relationshipCost > 0 && (
                            <div className="d-flex justify-content-between align-items-center">
                              <span className="text-muted small">Cost</span>
                              <span className="text-secondary small">${graphInfo.relationshipCost.toFixed(6)}</span>
                            </div>
                          )}
                        </div>
                      )}
                      
                      {/* Fallback to old extractedAt for backwards compatibility */}
                      {!graphInfo.entityStartedAt && !graphInfo.relationshipStartedAt && graphInfo.extractedAt && (
                        <div className="d-flex justify-content-between align-items-center mb-2">
                          <span className="text-muted h6">Extracted At</span>
                          <span className="text-secondary small">
                            {formatDateTime(new Date(graphInfo.extractedAt))}
                          </span>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="text-center py-3 text-muted mb-3">
                      <p className="mb-0">No graph data extracted yet</p>
                    </div>
                  );
                })()}

                <div className="mb-2">
                  <Form.Check
                    type="checkbox"
                    id="force-extraction-checkbox"
                    label="Force re-extraction (will re-extract even if already extracted, incurring additional costs)"
                    checked={extractionConfirmModal.force}
                    onChange={(e) => handleForceToggle(e.target.checked)}
                    disabled={extracting.entities || extracting.relationships || extracting.all}
                    className="text-start"
                  />
                </div>
                <div className="d-flex gap-2 align-items-start">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => showExtractionConfirmModal('entities')}
                    disabled={extracting.entities || extracting.all}
                  >
                    {extracting.entities || extracting.all ? (
                      <>
                        <Spinner animation="border" size="sm" className="me-2" />
                        Extracting...
                      </>
                    ) : (
                      <>
                        <HiCube className="me-0" /> Extract Entities
                      </>
                    )}
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => showExtractionConfirmModal('relationships')}
                    disabled={extracting.relationships || extracting.all}
                  >
                    {extracting.relationships || extracting.all ? (
                      <>
                        <Spinner animation="border" size="sm" className="me-2" />
                        Extracting...
                      </>
                    ) : (
                      <>
                        <HiLink className="me-0" /> Extract Relationships
                      </>
                    )}
                  </Button>
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => showExtractionConfirmModal('all')}
                    disabled={extracting.entities || extracting.relationships || extracting.all}
                    className="ms-auto"
                  >
                    {extracting.all ? (
                      <>
                        <Spinner animation="border" size="sm" className="me-2" />
                        Extracting...
                      </>
                    ) : (
                      <>
                        <HiHashtag className="me-2" /> Extract All
                      </>
                    )}
                  </Button>
                </div>

                {/* Job Status Display */}
                {currentJob && (currentJob.status === 'QUEUED' || currentJob.status === 'RUNNING') && (
                  <div className="mt-3 p-3 bg-light rounded border">
                    <div className="d-flex justify-content-between align-items-center mb-2">
                      <div>
                        <strong>Extraction Job Status</strong>
                        <Badge bg={currentJob.status === 'QUEUED' ? 'secondary' : 'primary'} className="ms-2">
                          {currentJob.status}
                        </Badge>
                      </div>
                      <Button
                        variant="outline-danger"
                        size="sm"
                        onClick={handleCancelJob}
                      >
                        Cancel
                      </Button>
                    </div>
                    <div className="mt-2">
                      {currentJob.status === 'QUEUED' ? (
                        <small className="text-muted d-block">
                          Waiting to start extraction...
                        </small>
                      ) : (
                        <>
                          {currentJob.processedBatches !== null && currentJob.totalBatches !== null && (
                            <div className="mb-2">
                              <small className="text-muted">
                                Processing batch {currentJob.processedBatches} of {currentJob.totalBatches}
                              </small>
                              <div className="progress mt-1" style={{ height: '6px' }}>
                                <div
                                  className="progress-bar"
                                  role="progressbar"
                                  style={{
                                    width: `${(currentJob.processedBatches / currentJob.totalBatches) * 100}%`
                                  }}
                                />
                              </div>
                            </div>
                          )}
                          {currentJob.totalEntities !== null && (
                            <small className="text-muted d-block">
                              Entities extracted: {currentJob.totalEntities}
                            </small>
                          )}
                          {currentJob.totalRelationships !== null && (
                            <small className="text-muted d-block">
                              Relationships extracted: {currentJob.totalRelationships}
                            </small>
                          )}
                          {currentJob.processedBatches === null && currentJob.totalBatches === null && 
                           currentJob.totalEntities === null && currentJob.totalRelationships === null && (
                            <small className="text-muted d-block">
                              Starting extraction...
                            </small>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                )}
              </Card.Body>
            </Card>
          </Col>
        )}
      </Row>

      {/* Document Entities Browser */}
      {document && (document.status === 'PROCESSED' || document.status === 'AI_READY') && (
        <Row className="mt-4">
          <Col md={12}>
            <Card className="border-0 bg-light h-100">
              <Card.Body>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <h5 className="mb-0 fw-semibold">Document Entities</h5>
                  <div className="d-flex align-items-center gap-2">
                    <select
                      className="form-select form-select-sm"
                      style={{ width: 'auto' }}
                      value={selectedDocumentEntityType}
                      onChange={(e) => setSelectedDocumentEntityType(e.target.value)}
                      disabled={loadingDocumentEntities}
                    >
                      <option value="All">All Types</option>
                      {['Form', 'Organization', 'Person', 'Date', 'Location', 'Document'].map((type) => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </select>
                  </div>
                </div>

                {(() => {
                  const outdatedCount = documentEntities.filter(e => isOutdatedEncryption(e)).length;
                  return (
                    <EncryptionVersionWarningBanner
                      show={outdatedCount > 0 && currentEncryptionKeyVersion}
                      variant="warning"
                      heading={
                        <>
                          {outdatedCount} {outdatedCount === 1 ? 'entity' : 'entities'} {outdatedCount === 1 ? 'is' : 'are'} encrypted with an outdated key version
                        </>
                      }
                      message={
                        <>
                          These entities are encrypted with an older encryption key version and need to be re-encrypted.
                          To fix this, you should: <strong>1) Hard delete all files</strong>, <strong>2) Re-upload the documents</strong>, and <strong>3) Re-extract the entities</strong>.
                          This will ensure all data is encrypted with the current version ({currentEncryptionKeyVersion}).
                        </>
                      }
                    />
                  );
                })()}

                {loadingDocumentEntities ? (
                  <div className="text-center py-5">
                    <Spinner animation="border" role="status">
                      <span className="visually-hidden">Loading entities...</span>
                    </Spinner>
                  </div>
                ) : documentEntities.length === 0 ? (
                  <div className="text-center py-4 text-muted">
                    {selectedDocumentEntityType === 'All' 
                      ? 'No entities found for this document'
                      : `No ${selectedDocumentEntityType} entities found for this document`}
                  </div>
                ) : (
                  <>
                    <Table responsive hover>
                      <thead>
                        <tr>
                          <th>ID</th>
                          <th>Name</th>
                          <th>Type</th>
                          <th>Encryption Version</th>
                          <th>Properties</th>
                        </tr>
                      </thead>
                      <tbody>
                        {documentEntities.slice(0, 100).map((entity, idx) => {
                          // Filter out system fields - same list used in both count and extraction
                          const excludedKeys = ['id', 'name', 'type', 'teamId', 'documentId', 'extractedAt', 'createdAt', 'updatedAt'];
                          const propertyCount = Object.keys(entity).filter(k => !excludedKeys.includes(k)).length;
                          const entityProperties = Object.entries(entity)
                            .filter(([key]) => !excludedKeys.includes(key))
                            .reduce((acc, [key, value]) => {
                              acc[key] = value;
                              return acc;
                            }, {});
                          
                          // Include name with encryption info for decryption in the modal
                          const propertiesWithName = {
                            ...entityProperties,
                            name: entity.name,
                            ...(entity.name_encryption_version && { name_encryption_version: entity.name_encryption_version }),
                            ...(entity.encryptionKeyVersion && { encryptionKeyVersion: entity.encryptionKeyVersion })
                          };
                          
                          return (
                            <tr 
                              key={entity.id || idx}
                              style={{ cursor: 'pointer' }}
                              onClick={() => handleOpenPropertiesModal(
                                'Entity Properties',
                                entity.type || 'Unknown',
                                entity.name || entity.id || 'Unnamed',
                                propertiesWithName,
                                null, // fromEntity - not a relationship
                                null  // toEntity - not a relationship
                              )}
                            >
                              <td>
                                <code className="small">{entity.id || 'N/A'}</code>
                              </td>
                              <td>
                                <code className="small" title={entity.name || entity.id || 'Unnamed'}>
                                  {truncateEncryptedName(entity.name || entity.id || 'Unnamed')}
                                </code>
                              </td>
                              <td>
                                <Badge bg="secondary">{entity.type || 'Unknown'}</Badge>
                              </td>
                              <td>
                                {(() => {
                                  const entityVersion = getEntityEncryptionVersion(entity);
                                  const isOutdated = isOutdatedEncryption(entity);
                                  const displayVersion = entityVersion || 'none';
                                  
                                  if (isOutdated && currentEncryptionKeyVersion) {
                                    return (
                                      <div>
                                        <Badge bg="warning" className="me-1" title={`Entity encrypted with ${displayVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`}>
                                          {displayVersion}
                                        </Badge>
                                        <small className="text-warning" title={`Needs re-encryption. Current: ${currentEncryptionKeyVersion}`}>
                                          ⚠️ Outdated
                                        </small>
                                      </div>
                                    );
                                  }
                                  
                                  return (
                                    <Badge bg={entityVersion ? "success" : "secondary"} title={entityVersion ? `Encrypted with ${displayVersion}` : 'Not encrypted (legacy)'}>
                                      {displayVersion}
                                    </Badge>
                                  );
                                })()}
                              </td>
                              <td
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleOpenPropertiesModal(
                                    'Entity Properties',
                                    entity.type || 'Unknown',
                                    entity.name || entity.id || 'Unnamed',
                                    propertiesWithName,
                                    null, // fromEntity - not a relationship
                                    null  // toEntity - not a relationship
                                  );
                                }}
                                style={{ cursor: 'pointer' }}
                              >
                                <small className="text-muted">
                                  {propertyCount} properties
                                </small>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </Table>
                    {documentEntities.length > 100 && (
                      <div className="text-center mt-3 text-muted">
                        <small>Showing first 100 of {documentEntities.length} entities</small>
                      </div>
                    )}
                  </>
                )}
              </Card.Body>
            </Card>
          </Col>
        </Row>
      )}

      {/* Document Relationships Browser */}
      {document && (document.status === 'PROCESSED' || document.status === 'AI_READY') && (
        <Row className="mt-4">
          <Col md={12}>
            <Card className="border-0 bg-light h-100">
              <Card.Body>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <h5 className="mb-0 fw-semibold">Document Relationships</h5>
                  <div className="d-flex align-items-center gap-2">
                    <select
                      className="form-select form-select-sm"
                      style={{ width: 'auto' }}
                      value={selectedDocumentRelationshipType}
                      onChange={(e) => setSelectedDocumentRelationshipType(e.target.value)}
                      disabled={loadingDocumentRelationships}
                    >
                      <option value="All">All Types</option>
                      {Array.from(new Set(documentRelationships.map(r => r.relationshipType).filter(Boolean))).sort().map((type) => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </select>
                  </div>
                </div>

                {loadingDocumentRelationships ? (
                  <div className="text-center py-5">
                    <Spinner animation="border" role="status">
                      <span className="visually-hidden">Loading relationships...</span>
                    </Spinner>
                  </div>
                ) : documentRelationships.length === 0 ? (
                  <div className="text-center py-4 text-muted">
                    {selectedDocumentRelationshipType === 'All' 
                      ? 'No relationships found for this document'
                      : `No ${selectedDocumentRelationshipType} relationships found for this document`}
                  </div>
                ) : (
                  <>
                    <Table responsive hover>
                      <thead>
                        <tr>
                          <th>Type</th>
                          <th>From Entity</th>
                          <th>From Entity Name</th>
                          <th>From Encryption</th>
                          <th>To Entity</th>
                          <th>To Entity Name</th>
                          <th>To Encryption</th>
                          <th>Properties</th>
                        </tr>
                      </thead>
                      <tbody>
                        {documentRelationships.slice(0, 100).map((relationship, idx) => {
                          const propertyCount = Object.keys(relationship.properties || {}).length;
                          const fromEntityName = relationship.fromEntity?.name || relationship.fromEntity?.id || 'N/A';
                          const toEntityName = relationship.toEntity?.name || relationship.toEntity?.id || 'N/A';
                          const relationshipTitle = `${relationship.relationshipType || 'Unknown'} Relationship`;
                          const relationshipSubtitle = `${relationship.fromEntity?.type || 'Unknown'}:${fromEntityName} → ${relationship.toEntity?.type || 'Unknown'}:${toEntityName}`;
                          
                          return (
                            <tr 
                              key={idx}
                              style={{ cursor: 'pointer' }}
                              onClick={() => handleOpenPropertiesModal(
                                relationshipTitle,
                                relationship.relationshipType || 'Unknown',
                                relationshipSubtitle,
                                relationship.properties || {},
                                relationship.fromEntity,
                                relationship.toEntity
                              )}
                            >
                              <td>
                                <Badge bg="info">{relationship.relationshipType || 'Unknown'}</Badge>
                              </td>
                              <td>
                                <Badge bg="secondary">{relationship.fromEntity?.type || 'Unknown'}</Badge>
                              </td>
                              <td>
                                <code className="small" title={fromEntityName}>
                                  {truncateEncryptedName(fromEntityName)}
                                </code>
                              </td>
                              <td>
                                {(() => {
                                  const fromVersion = getEntityEncryptionVersion(relationship.fromEntity);
                                  const fromOutdated = relationship.fromEntity && isOutdatedEncryption(relationship.fromEntity);
                                  const fromDisplayVersion = fromVersion || 'none';
                                  
                                  if (fromOutdated && currentEncryptionKeyVersion) {
                                    return (
                                      <div>
                                        <Badge bg="warning" className="me-1" title={`Entity encrypted with ${fromDisplayVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`}>
                                          {fromDisplayVersion}
                                        </Badge>
                                        <small className="text-warning">⚠️</small>
                                      </div>
                                    );
                                  }
                                  
                                  return (
                                    <Badge bg={fromVersion ? "success" : "secondary"} title={fromVersion ? `Encrypted with ${fromDisplayVersion}` : 'Not encrypted (legacy)'}>
                                      {fromDisplayVersion}
                                    </Badge>
                                  );
                                })()}
                              </td>
                              <td>
                                <Badge bg="secondary">{relationship.toEntity?.type || 'Unknown'}</Badge>
                              </td>
                              <td>
                                <code className="small" title={toEntityName}>
                                  {truncateEncryptedName(toEntityName)}
                                </code>
                              </td>
                              <td>
                                {(() => {
                                  const toVersion = getEntityEncryptionVersion(relationship.toEntity);
                                  const toOutdated = relationship.toEntity && isOutdatedEncryption(relationship.toEntity);
                                  const toDisplayVersion = toVersion || 'none';
                                  
                                  if (toOutdated && currentEncryptionKeyVersion) {
                                    return (
                                      <div>
                                        <Badge bg="warning" className="me-1" title={`Entity encrypted with ${toDisplayVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`}>
                                          {toDisplayVersion}
                                        </Badge>
                                        <small className="text-warning">⚠️</small>
                                      </div>
                                    );
                                  }
                                  
                                  return (
                                    <Badge bg={toVersion ? "success" : "secondary"} title={toVersion ? `Encrypted with ${toDisplayVersion}` : 'Not encrypted (legacy)'}>
                                      {toDisplayVersion}
                                    </Badge>
                                  );
                                })()}
                              </td>
                              <td
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleOpenPropertiesModal(
                                    relationshipTitle,
                                    relationship.relationshipType || 'Unknown',
                                    relationshipSubtitle,
                                    relationship.properties || {},
                                    relationship.fromEntity,
                                    relationship.toEntity
                                  );
                                }}
                                style={{ cursor: 'pointer' }}
                              >
                                <small className="text-muted">
                                  {propertyCount} properties
                                </small>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </Table>
                    {documentRelationships.length > 100 && (
                      <div className="text-center mt-3 text-muted">
                        <small>Showing first 100 of {documentRelationships.length} relationships</small>
                      </div>
                    )}
                  </>
                )}
              </Card.Body>
            </Card>
          </Col>
        </Row>
      )}

      <ConfirmationModalWithVerification
        show={stageDeleteModal.show}
        onHide={closeStageDeleteModal}
        onConfirm={handleStageDeleteConfirm}
        title={stageDeletionConfig[stageDeleteModal.scope]?.title || 'Delete Artifacts'}
        message={stageDeletionConfig[stageDeleteModal.scope]?.message || 'This will delete the selected derived artifacts for this document.'}
        variant="danger"
        confirmLabel="Delete"
        loading={stageDeleting}
      />
      <ConfirmationModalWithVerification
        show={showDeleteModal}
        onHide={() => setShowDeleteModal(false)}
        onConfirm={handleHardDelete}
        title="Hard Delete Document"
        message={`Are you sure you want to permanently delete "${document.fileName}"? This action will delete the file from S3 and cannot be undone.`}
        variant="danger"
        confirmLabel="Delete"
        loading={hardDeleting}
      />
      {extractionConfirmModal.type && (
        <ConfirmationModalWithVerification
          show={extractionConfirmModal.show}
          onHide={closeExtractionConfirmModal}
          onConfirm={handleExtractConfirm}
          title={getExtractionModalConfig().title}
          message={getExtractionModalConfig().message}
          variant="primary"
          confirmLabel={getExtractionModalConfig().confirmLabel}
          loading={extracting.entities || extracting.relationships || extracting.all}
        />
      )}
      
      {/* Already Extracted Modal */}
      <AlertMessageModal
        show={alreadyExtractedModal.show}
        onHide={handleAlreadyExtractedModalClose}
        title="Already Extracted"
        message={
          <>
            {alreadyExtractedModal.message}
            <div className="mt-3">
              <p className="mb-0 text-muted small">
                To re-extract and incur additional costs, check the "Force re-extraction" checkbox and try again.
              </p>
            </div>
          </>
        }
        variant="warning"
        primaryButton={{
          label: 'Force Re-extract',
          onClick: handleAlreadyExtractedForceRetry
        }}
        secondaryButton={{
          label: 'Close',
          onClick: handleAlreadyExtractedModalClose
        }}
      />

      <PropertiesViewerModal
        show={propertiesModal.show}
        onHide={() => setPropertiesModal(prev => ({ ...prev, show: false }))}
        title={propertiesModal.title}
        entityType={propertiesModal.entityType}
        entityName={propertiesModal.entityName}
        properties={propertiesModal.properties}
        loading={propertiesModal.loading}
      />
    </div>
  );
}

export default ViewDocument;

