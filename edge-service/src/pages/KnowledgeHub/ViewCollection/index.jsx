import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Container, Card, Table, Breadcrumb, Spinner, Alert, Badge, Form, Row, Col, ListGroup } from 'react-bootstrap';
import { HiDocument, HiArrowLeft, HiBookOpen, HiCloudUpload, HiTrash, HiEye, HiSearch } from 'react-icons/hi';
import { useTeam } from '../../../contexts/TeamContext';
import { knowledgeHubCollectionService } from '../../../services/knowledgeHubCollectionService';
import { knowledgeHubDocumentService } from '../../../services/knowledgeHubDocumentService';
import Button from '../../../components/common/Button';
import axiosInstance from '../../../services/axiosInstance';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { formatDateTime } from '../../../utils/dateUtils';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import { knowledgeHubWebSocketService } from '../../../services/knowledgeHubWebSocketService';
import './styles.css';

const MAX_SNIPPET_LENGTH = 600;

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
  const [searchQuery, setSearchQuery] = useState('');
  const [topK, setTopK] = useState(5);
  const [searchResults, setSearchResults] = useState([]);
  const [searchPerformed, setSearchPerformed] = useState(false);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState(null);
  const [expandedResults, setExpandedResults] = useState({});
  const fileInputRef = useRef(null);

  useEffect(() => {
    if (currentTeam?.id && collectionId) {
      fetchCollection();
      fetchDocuments();
    }
  }, [currentTeam?.id, collectionId]);

  // Subscribe to WebSocket updates for document status changes
  useEffect(() => {
    if (!collectionId) return;

    // Connect to WebSocket if not already connected
    knowledgeHubWebSocketService.connect();

    // Subscribe to document status updates
    const unsubscribe = knowledgeHubWebSocketService.subscribe((statusUpdate) => {
      // Only process updates for documents in this collection
      if (statusUpdate.collectionId === collectionId) {
        console.log('Received document status update for collection:', statusUpdate);

        // Update the document in the list if it exists
        setDocuments(prevDocuments => {
          const documentIndex = prevDocuments.findIndex(
            doc => doc.documentId === statusUpdate.documentId
          );

          if (documentIndex >= 0) {
            // Document exists in list, update it
            const updatedDocuments = [...prevDocuments];
            updatedDocuments[documentIndex] = {
              ...updatedDocuments[documentIndex],
              status: statusUpdate.status,
              processedAt: statusUpdate.processedAt,
              totalChunks: statusUpdate.totalChunks,
              totalTokens: statusUpdate.totalTokens,
              processedS3Key: statusUpdate.processedS3Key,
              errorMessage: statusUpdate.errorMessage
            };
            return updatedDocuments;
          } else {
            // Document doesn't exist yet (might have been uploaded but not yet in list)
            // Don't add it here - let fetchDocuments() handle it after upload completes
            // But if it's a new status update for an existing document that was just uploaded,
            // we might want to fetch the list again to ensure we have all documents
            console.log('Document not found in list, might need to refresh:', statusUpdate.documentId);
            return prevDocuments;
          }
        });
      }
    });

    return () => {
      // Unsubscribe on unmount
      unsubscribe();
      // Note: We don't disconnect the WebSocket here because other components might be using it
      // The WebSocket will be managed globally
    };
  }, [collectionId]);

  const fetchCollection = async () => {
    try {
      const { data, error } = await knowledgeHubCollectionService.getCollectionById(collectionId);
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
      const result = await knowledgeHubDocumentService.getAllDocuments({ collectionId });
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
      const initiateResponse = await axiosInstance.post('/api/documents/upload/initiate', {
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
      await axiosInstance.post(`/api/documents/upload/${documentId}/complete`, {
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
      const result = await knowledgeHubDocumentService.deleteDocument(documentToDelete.documentId);
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
      case 'PROCESSED':
      case 'AI_READY':
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

  const canDeleteDocument = (status) => {
    return status === 'FAILED' || status === 'PENDING_UPLOAD';
  };

  const matchBadgeVariant = (matchType) => {
    switch (matchType) {
      case 'exact':
        return 'success';
      case 'high_similarity':
        return 'primary';
      case 'medium_similarity':
        return 'info';
      case 'low_similarity':
      default:
        return 'secondary';
    }
  };

  const truncateText = (text, maxLength = MAX_SNIPPET_LENGTH) => {
    if (!text) {
      return '';
    }
    if (text.length <= maxLength) {
      return text;
    }
    return `${text.slice(0, maxLength).trimEnd()}…`;
  };

  const toggleResultExpansion = (resultKey) => {
    setExpandedResults((prev) => ({
      ...prev,
      [resultKey]: !prev[resultKey],
    }));
  };

  const extractPageInfo = (result) => {
    return (
      result?.pageNumbers ??
      result?.pageNumber ??
      result?.page_numbers ??
      result?.page ??
      null
    );
  };

  const extractChunkIndex = (result) => {
    const value =
      result?.chunkIndex ??
      result?.chunk_index ??
      result?.chunk_number ??
      null;
    if (value === null || value === undefined) {
      return null;
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : value;
  };

  const handleSemanticSearch = async (event) => {
    event.preventDefault();

    if (!searchQuery.trim()) {
      setSearchError('Enter a sentence or question to search');
      setSearchPerformed(false);
      setSearchResults([]);
      return;
    }

    if (!collection?.milvusCollectionName) {
      setSearchError('This collection is not yet connected to RAG. Assign an embedding collection first.');
      setSearchPerformed(false);
      setSearchResults([]);
      return;
    }

    setSearching(true);
    setSearchError(null);

    try {
      const response = await knowledgeHubCollectionService.searchCollection(collectionId, {
        query: searchQuery.trim(),
        topK,
      });

      if (!response.success) {
        throw new Error(response.error || 'Search failed');
      }

      const payload = response.data || {};
      setSearchResults(payload.results || []);
      setExpandedResults({});
      setSearchPerformed(true);
    } catch (err) {
      console.error('Error running semantic search:', err);
      setSearchError(err.message || 'Failed to run search');
      setSearchResults([]);
      setSearchPerformed(true);
    } finally {
      setSearching(false);
    }
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

          {collection?.milvusCollectionName ? (
            <Card className="search-preview-card mb-4">
              <Card.Body>
                <div className="d-flex flex-column flex-lg-row align-items-lg-center mb-3 gap-2">
                  <div className="flex-grow-1 text-start">
                    <h5 className="mb-1">Semantic Search Preview</h5>
                    <p className="text-muted small mb-0">
                      Run a quick vector search against <strong>{collection.milvusCollectionName}</strong> to confirm embeddings are active.
                    </p>
                  </div>
                  <div className="text-muted small text-lg-end">
                    <div>Embedding model: {collection.embeddingModelName || 'Not configured'}</div>
                    <div>Late chunking: {collection.lateChunkingEnabled ? 'Enabled' : 'Disabled'}</div>
                  </div>
                </div>

                <Form onSubmit={handleSemanticSearch} className="mb-3">
                  <Row className="g-2 align-items-center">
                    <Col lg={8} xs={12}>
                      <Form.Control
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Ask a question or paste a sentence to search your processed chunks..."
                        disabled={searching}
                      />
                    </Col>
                    <Col lg={2} xs={6}>
                      <Form.Select
                        value={topK}
                        onChange={(e) => setTopK(Number(e.target.value))}
                        disabled={searching}
                      >
                        <option value={3}>Top 3</option>
                        <option value={5}>Top 5</option>
                        <option value={10}>Top 10</option>
                      </Form.Select>
                    </Col>
                    <Col lg={2} xs={6} className="d-grid">
                      <Button type="submit" variant="primary" disabled={searching}>
                        {searching ? (
                          <>
                            <Spinner animation="border" size="sm" className="me-2" />
                            Searching...
                          </>
                        ) : (
                          <>
                            <HiSearch className="me-2" />
                            Search
                          </>
                        )}
                      </Button>
                    </Col>
                  </Row>
                </Form>

                {searchError && (
                  <Alert variant="danger" className="mb-3">
                    {searchError}
                  </Alert>
                )}

                {searchPerformed && !searching && !searchError && (
                  <div>
                    {searchResults.length === 0 ? (
                      <Alert variant="info" className="mb-0">
                        No semantic matches found for this query. Try rephrasing or increasing the context window.
                      </Alert>
                    ) : (
                      <ListGroup variant="flush" className="semantic-search-results">
                        {searchResults.map((result, index) => (
                          <ListGroup.Item key={`${result.id || 'result'}-${index}`} className="search-result-item">
                            <div className="d-flex justify-content-between align-items-start mb-2">
                              <div className="fw-semibold">Match #{index + 1}</div>
                              <div className="d-flex gap-2 align-items-center small text-muted">
                                <Badge bg={matchBadgeVariant(result.match_type)} className="text-uppercase">
                                  {result.match_type?.replace('_', ' ') || 'match'}
                                </Badge>
                                <span>
                                  distance:{' '}
                                  {typeof result.distance === 'number'
                                    ? result.distance.toFixed(3)
                                    : 'n/a'}
                                </span>
                              </div>
                            </div>
                            {(() => {
                              const text = result.text || '';
                              const resultKey = result.id || `${index}`;
                              const isExpanded = !!expandedResults[resultKey];
                              const displayText = isExpanded ? text : truncateText(text);
                              const shouldShowToggle = text && text.length > MAX_SNIPPET_LENGTH;

                              return (
                                <>
                                  <div className="search-result-text">
                                    {displayText || 'No text returned for this chunk.'}
                                  </div>
                                  {shouldShowToggle && (
                                    <Button
                                      variant="link"
                                      size="sm"
                                      className="p-0 mt-2 search-result-toggle"
                                      onClick={() => toggleResultExpansion(resultKey)}
                                    >
                                      {isExpanded ? 'Show less' : 'Show more'}
                                    </Button>
                                  )}
                                </>
                              );
                            })()}
                            <div className="search-result-meta text-muted small mt-2">
                              <span className="meta-item document-link">
                                {result.documentId ? (
                                  <Button
                                    variant="link"
                                    size="sm"
                                    className="px-1 py-0"
                                    onClick={() => navigate(`/knowledge-hub/document/${result.documentId}`)}
                                  >
                                    {result.fileName || result.documentId}
                                  </Button>
                                ) : (
                                  result.fileName || 'Unknown document'
                                )}
                              </span>
                              {extractPageInfo(result) && (
                                <span className="meta-item">
                                  Page{String(extractPageInfo(result)).includes(',') ? 's' : ''}:{' '}
                                  {extractPageInfo(result)}
                                </span>
                              )}
                              {extractChunkIndex(result) !== null && (
                                <span className="meta-item">
                                  Chunk:{' '}
                                  {Number.isFinite(Number(extractChunkIndex(result)))
                                    ? Number(extractChunkIndex(result)) + 1
                                    : extractChunkIndex(result)}
                                </span>
                              )}
                            </div>
                            {result.documentId && (
                              <div className="mt-3">
                                <Button
                                  variant="outline-primary"
                                  size="sm"
                                  onClick={() => navigate(`/knowledge-hub/document/${result.documentId}`)}
                                >
                                  <HiEye className="me-1" /> Open Document
                                </Button>
                              </div>
                            )}
                          </ListGroup.Item>
                        ))}
                      </ListGroup>
                    )}
                  </div>
                )}
              </Card.Body>
            </Card>
          ) : (
            <Alert variant="warning" className="mb-4">
              <div className="fw-semibold">RAG collection not assigned</div>
              <div className="small mb-0">Assign an embedding collection to enable semantic search verification.</div>
            </Alert>
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

