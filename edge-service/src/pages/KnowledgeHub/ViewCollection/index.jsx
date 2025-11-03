import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Container, Card, Table, Breadcrumb, Spinner, Alert, Badge } from 'react-bootstrap';
import { HiDocument, HiArrowLeft, HiBookOpen, HiCloudUpload, HiTrash, HiEye } from 'react-icons/hi';
import { useTeam } from '../../../contexts/TeamContext';
import { knowledgeCollectionService } from '../../../services/knowledgeCollectionService';
import { documentService } from '../../../services/documentService';
import Button from '../../../components/common/Button';
import axiosInstance from '../../../services/axiosInstance';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { formatDateTime } from '../../../utils/dateUtils';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import './styles.css';

function ViewCollection() {
  const { collectionId } = useParams();
  const navigate = useNavigate();
  const { currentTeam } = useTeam();
  const [collection, setCollection] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [dragover, setDragover] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [documentToDelete, setDocumentToDelete] = useState(null);
  const fileInputRef = useRef(null);

  useEffect(() => {
    if (currentTeam?.id && collectionId) {
      fetchCollection();
      fetchDocuments();
    }
  }, [currentTeam?.id, collectionId]);

  const fetchCollection = async () => {
    try {
      const { data, error } = await knowledgeCollectionService.getCollectionById(collectionId);
      if (error) throw new Error(error);
      setCollection(data);
    } catch (err) {
      console.error('Error fetching collection:', err);
      setError('Failed to load collection');
    }
  };

  const fetchDocuments = async () => {
    try {
      setLoading(true);
      const result = await documentService.getAllDocuments({ collectionId });
      if (result.error) throw new Error(result.error);
      setDocuments(result.data || []);
      setLoading(false);
    } catch (err) {
      console.error('Error fetching documents:', err);
      setError('Failed to load documents');
      setLoading(false);
    }
  };

  const handleFileSelect = (files) => {
    if (!files || files.length === 0) return;
    
    Array.from(files).forEach(file => {
      uploadFile(file);
    });
  };

  const uploadFile = async (file) => {
    try {
      setUploading(true);
      
      // Initiate upload
      const initiateResponse = await axiosInstance.post('/api/v1/documents/upload/initiate', {
        fileName: file.name,
        collectionId: collectionId,
        fileSize: file.size,
        contentType: file.type || 'application/octet-stream',
        chunkSize: 400,
        overlapTokens: 50,
        chunkStrategy: 'sentence'
      });

      if (initiateResponse.data.error) {
        throw new Error(initiateResponse.data.error);
      }

      const { uploadUrl, s3Key, documentId, requiredHeaders } = initiateResponse.data;

      // Upload file to S3 using presigned URL with fetch
      // Using fetch to avoid axios adding default headers (Accept, etc.) to the request
      const uploadResponse = await fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: requiredHeaders || {
          'Content-Type': file.type || 'application/octet-stream'
        }
      });

      if (!uploadResponse.ok) {
        throw new Error(`S3 upload failed: ${uploadResponse.status} ${uploadResponse.statusText}`);
      }

      // Complete upload
      await axiosInstance.post(`/api/v1/documents/upload/${documentId}/complete`, {
        s3Key: s3Key
      });

      showSuccessToast(`"${file.name}" uploaded successfully`);
      
      // Refresh documents list
      fetchDocuments();
    } catch (err) {
      console.error('Error uploading file:', err);
      showErrorToast(err.response?.data?.error || err.message || 'Failed to upload file');
    } finally {
      setUploading(false);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setDragover(true);
  };

  const handleDragLeave = () => {
    setDragover(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragover(false);
    handleFileSelect(e.dataTransfer.files);
  };

  const handleDeleteClick = (doc) => {
    setDocumentToDelete(doc);
    setShowDeleteModal(true);
  };

  const handleDeleteConfirm = async () => {
    if (!documentToDelete) return;

    try {
      const result = await documentService.deleteDocument(documentToDelete.documentId);
      if (result.error) throw new Error(result.error);
      
      showSuccessToast('Document deleted successfully');
      setShowDeleteModal(false);
      setDocumentToDelete(null);
      fetchDocuments();
    } catch (err) {
      console.error('Error deleting document:', err);
      showErrorToast(err.message || 'Failed to delete document');
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

  const canDeleteDocument = (status) => {
    return status === 'FAILED' || status === 'PENDING_UPLOAD';
  };

  return (
    <Container fluid className="view-collection-container">
      {/* Breadcrumb */}
      <Card className="breadcrumb-card mb-3">
        <Card.Body>
          <Breadcrumb>
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/dashboard' }}>
              Home
            </Breadcrumb.Item>
            <Breadcrumb.Item 
              onClick={() => navigate(`/teams/${currentTeam?.id}`)}
              style={{ cursor: 'pointer' }}
            >
              {currentTeam?.name || 'Team'}
            </Breadcrumb.Item>
            <Breadcrumb.Item 
              onClick={() => navigate('/knowledge-hub')}
              style={{ cursor: 'pointer' }}
            >
              Knowledge Hub
            </Breadcrumb.Item>
            <Breadcrumb.Item active>
              {collection?.name || 'Collection'}
            </Breadcrumb.Item>
          </Breadcrumb>
        </Card.Body>
      </Card>

      {/* Main Content */}
      <Card className="border-0">
        <Card.Header>
          <div className="d-flex align-items-center">
            <div className="d-flex align-items-center gap-2">
              <HiBookOpen className="page-icon" />
              <h4 className="mb-0">{collection?.name || 'Collection'}</h4>
            </div>
            <div className="ms-auto">
              <Button 
                variant="secondary"
                onClick={() => navigate('/knowledge-hub')}
              >
                <HiArrowLeft /> Back to Collections
              </Button>
            </div>
          </div>
        </Card.Header>
        <Card.Body>
          {error && (
            <Alert variant="danger">{error}</Alert>
          )}
          
          {/* Upload Area */}
          <div
            className={`upload-area ${dragover ? 'dragover' : ''}`}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
          >
            <HiCloudUpload className="upload-icon" />
            <div className="upload-text">
              Drag and drop files here or click to browse
            </div>
            <div className="upload-subtext">
              Supports PDF, DOCX, TXT, and other text-based documents
            </div>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              style={{ display: 'none' }}
              onChange={(e) => handleFileSelect(e.target.files)}
            />
          </div>
          
          {uploading && (
            <div className="d-flex align-items-center justify-content-center py-3 mt-3">
              <Spinner size="sm" animation="border" role="status" />
              <span className="ms-2">Uploading files...</span>
            </div>
          )}
          
          {loading ? (
            <div className="text-center py-5 mt-5">
              <Spinner animation="border" role="status" />
            </div>
          ) : documents.length === 0 ? (
            <div className="text-center py-5">
              <HiDocument className="empty-state-icon" />
              <h5 className="mt-3">No Documents Yet</h5>
              <p className="text-muted">Upload your first document to get started.</p>
            </div>
          ) : (
            <Table responsive hover className="mt-5">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Size</th>
                  <th>Status</th>
                  <th>Uploaded</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((doc) => (
                  <tr 
                    key={doc.id}
                    onClick={() => navigate(`/knowledge-hub/document/${doc.documentId}`)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td>
                      <div className="d-flex align-items-center gap-2">
                        <HiDocument className="document-icon" />
                        <span className="document-name">{doc.fileName}</span>
                      </div>
                    </td>
                    <td>
                      <span>{doc.fileSize ? `${(doc.fileSize / 1024 / 1024).toFixed(2)} MB` : 'N/A'}</span>
                    </td>
                    <td>
                      <Badge bg={getStatusBadgeVariant(doc.status)}>{doc.status}</Badge>
                    </td>
                    <td>
                      <span>{doc.uploadedAt ? formatDateTime(doc.uploadedAt) : 'N/A'}</span>
                    </td>
                    <td className="text-end">
                      <div className="d-flex gap-2 justify-content-end" onClick={(e) => e.stopPropagation()}>
                        <Button
                          variant="outline-primary"
                          size="sm"
                          onClick={() => navigate(`/knowledge-hub/document/${doc.documentId}`)}
                        >
                          <HiEye /> View
                        </Button>
                        {canDeleteDocument(doc.status) && (
                          <Button
                            variant="outline-danger"
                            size="sm"
                            onClick={() => handleDeleteClick(doc)}
                          >
                            <HiTrash /> Delete
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card.Body>
      </Card>

      <ConfirmationModal
        show={showDeleteModal}
        onHide={() => {
          setShowDeleteModal(false);
          setDocumentToDelete(null);
        }}
        onConfirm={handleDeleteConfirm}
        title="Delete Document"
        message={`Are you sure you want to delete "${documentToDelete?.fileName}"? This action cannot be undone.`}
        variant="danger"
        confirmLabel="Delete"
      />
    </Container>
  );
}

export default ViewCollection;

