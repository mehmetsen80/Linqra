import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Spinner, Alert, Badge, Row, Col, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { HiArrowLeft, HiDocument, HiDownload, HiTrash, HiCalendar, HiCube } from 'react-icons/hi';
import { useTeam } from '../../../../contexts/TeamContext';
import { documentService } from '../../../../services/documentService';
import { knowledgeCollectionService } from '../../../../services/knowledgeCollectionService';
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
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [fileExists, setFileExists] = useState(null);

  useEffect(() => {
    if (currentTeam?.id && documentId) {
      fetchDocument();
    }
  }, [currentTeam?.id, documentId]);

  const fetchDocument = async () => {
    try {
      setLoading(true);
      const { data, error } = await documentService.getDocumentById(documentId);
      if (error) throw new Error(error);
      setDocument(data);
      
      // Default fileExists based on status
      setFileExists(data.status === 'UPLOADED' || data.status === 'READY');
      
      // Fetch collection details
      if (data.collectionId) {
        const collectionResult = await knowledgeCollectionService.getCollectionById(data.collectionId);
        if (collectionResult.data) {
          setCollection(collectionResult.data);
        }
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
      const { data, error } = await documentService.generateDownloadUrl(documentId);
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

  const handleHardDelete = async () => {
    try {
      const { error } = await documentService.deleteDocument(documentId);
      if (error) throw new Error(error);
      
      showSuccessToast('Document deleted successfully');
      navigate(`/knowledge-hub/collection/${document.collectionId}`);
    } catch (err) {
      console.error('Error deleting document:', err);
      showErrorToast(err.response?.data?.error || err.message || 'Failed to delete document');
    }
  };

  const getStatusBadgeVariant = (status) => {
    switch (status) {
      case 'READY':
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
              <Badge bg={getStatusBadgeVariant(document.status)}>
                {document.status}
              </Badge>
            </div>
            <div className="d-flex gap-2">
              {document.status === 'UPLOADED' || document.status === 'READY' ? (
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
              >
                <HiTrash /> Hard Delete
              </Button>
            </div>
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
                <Badge bg={getStatusBadgeVariant(document.status)}>
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
      </Row>

      <ConfirmationModalWithVerification
        show={showDeleteModal}
        onHide={() => setShowDeleteModal(false)}
        onConfirm={handleHardDelete}
        title="Hard Delete Document"
        message={`Are you sure you want to permanently delete "${document.fileName}"? This action will delete the file from S3 and cannot be undone.`}
        variant="danger"
        confirmLabel="Delete"
      />
    </div>
  );
}

export default ViewDocument;

