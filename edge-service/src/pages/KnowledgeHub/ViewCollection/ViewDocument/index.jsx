import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Spinner, Alert, Badge, Row, Col, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { HiArrowLeft, HiDocument, HiDownload, HiTrash, HiCalendar, HiCube, HiChartBar, HiCheckCircle, HiInformationCircle } from 'react-icons/hi';
import { useTeam } from '../../../../contexts/TeamContext';
import { knowledgeHubDocumentService } from '../../../../services/knowledgeHubDocumentService';
import { knowledgeHubCollectionService } from '../../../../services/knowledgeHubCollectionService';
import { knowledgeHubWebSocketService } from '../../../../services/knowledgeHubWebSocketService';
import Button from '../../../../components/common/Button';
import { showSuccessToast, showErrorToast } from '../../../../utils/toastConfig';
import { formatDateTime } from '../../../../utils/dateUtils';
import ConfirmationModalWithVerification from '../../../../components/common/ConfirmationModalWithVerification';
import './styles.css';

function ViewDocument() {
  const { documentId } = useParams();
  const navigate = useNavigate();
  const { currentTeam } = useTeam();
  const [document, setDocument] = useState(null);
  const [collection, setCollection] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [downloading, setDownloading] = useState(false);
  const [downloadingProcessedJson, setDownloadingProcessedJson] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [fileExists, setFileExists] = useState(null);
  const [chunkStats, setChunkStats] = useState(null);
  const [loadingChunkStats, setLoadingChunkStats] = useState(false);
  const [metadata, setMetadata] = useState(null);
  const [loadingMetadata, setLoadingMetadata] = useState(false);
  const [hardDeleting, setHardDeleting] = useState(false);

  useEffect(() => {
    if (currentTeam?.id && documentId) {
      fetchDocument();
    }
    
    // Connect to WebSocket for document processing
    knowledgeHubWebSocketService.connect();
    
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
            processedS3Key: statusUpdate.processedS3Key,
            errorMessage: statusUpdate.errorMessage
          };
        });
        
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
      }
    });
    
    return () => {
      // Unsubscribe and disconnect on unmount
      unsubscribe();
      knowledgeHubWebSocketService.disconnect();
    };
  }, [currentTeam?.id, documentId]);

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
      
      // Fetch chunk statistics if document is processed
      if (data.status === 'PROCESSED' && data.processedS3Key) {
        fetchChunkStatistics(data.processedS3Key);
      }
      
      // Fetch metadata if document has metadata extraction status or beyond
      if (['METADATA_EXTRACTION', 'EMBEDDING', 'AI_READY'].includes(data.status)) {
        fetchMetadata();
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
      const { data, error } = await knowledgeHubDocumentService.generateDownloadUrl(documentId);
      if (error) throw new Error(error);
      
      // Open the presigned URL in a new window to download
      if (data.downloadUrl) {
        window.open(data.downloadUrl, '_blank');
        showSuccessToast('Download initiated');
      }
    } catch (err) {
      console.error('Error downloading document:', err);
      showErrorToast(err.response?.data?.error || err.message || 'Failed to download document');
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

  const handleDownloadProcessedJson = async () => {
    try {
      setDownloadingProcessedJson(true);
      const { data, error } = await knowledgeHubDocumentService.generateProcessedJsonDownloadUrl(documentId);
      if (error) throw new Error(error);
      
      // Open the presigned URL in a new window to download
      if (data.downloadUrl) {
        window.open(data.downloadUrl, '_blank');
        showSuccessToast('Processed JSON download initiated');
      }
    } catch (err) {
      console.error('Error downloading processed JSON:', err);
      showErrorToast(err.response?.data?.error || err.message || 'Failed to download processed JSON');
    } finally {
      setDownloadingProcessedJson(false);
    }
  };

  const fetchMetadata = async () => {
    if (!documentId) return;
    
    try {
      setLoadingMetadata(true);
      const { data, error } = await knowledgeHubDocumentService.getMetadata(documentId);
      if (error) throw new Error(error);
      
      if (data) {
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

  const getStatusBadgeVariant = (status) => {
    switch (status) {
      case 'READY':
      case 'PROCESSED':
        return 'success';
      case 'UPLOADED':
      case 'PARSING':
      case 'PARSED':
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
      // TODO: Add embedding command handler
      // For now, using document processing command
      knowledgeHubWebSocketService.sendDocumentProcessingCommand(
        document.documentId,
        step.key,
        currentTeam.id
      );
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

        {/* Additional Information */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiDocument className="text-primary me-2" size={24} />
                <h5 className="mb-0 fw-semibold">Additional Information</h5>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Document ID</span>
                <code className="text-secondary">{document.documentId}</code>
              </div>
              {document.contentType && (
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <span className="text-muted h6">Content Type</span>
                  <span className="text-secondary">{document.contentType}</span>
                </div>
              )}
              <div className="d-flex justify-content-between align-items-center">
                <span className="text-muted h6">Collection</span>
                <span className="text-secondary">{collection?.name || 'N/A'}</span>
              </div>
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
                  {document.processedS3Key && (
                    <Button
                      variant="outline-primary"
                      size="sm"
                      onClick={handleDownloadProcessedJson}
                      disabled={downloadingProcessedJson}
                    >
                      <HiDownload /> {downloadingProcessedJson ? 'Downloading...' : 'Download JSON'}
                    </Button>
                  )}
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
              </Card.Body>
            </Card>
          </Col>
        )}

        {/* Metadata Information */}
        {(document.status === 'METADATA_EXTRACTION' || document.status === 'EMBEDDING' || document.status === 'AI_READY') && (
          <Col md={6}>
            <Card className="border-0 bg-light h-100">
              <Card.Body>
                <div className="d-flex align-items-center mb-3">
                  <HiInformationCircle className="text-primary me-2" size={24} />
                  <h5 className="mb-0 fw-semibold">Document Metadata</h5>
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
                      <div className="d-flex justify-content-between align-items-center">
                        <span className="text-muted h6">Extracted At</span>
                        <span className="text-secondary small">{formatDateTime(metadata.extractedAt)}</span>
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
      </Row>

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
    </div>
  );
}

export default ViewDocument;

