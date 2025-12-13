import React, { useState, useEffect, useCallback } from 'react';
import { Container, Card, Table, Spinner, Breadcrumb, Badge, Button as BootstrapButton, ProgressBar, Form } from 'react-bootstrap';
import { HiDownload, HiXCircle, HiCheckCircle, HiClock, HiExclamationCircle, HiArrowLeft, HiOutlineCollection } from 'react-icons/hi';
import { useNavigate, Link } from 'react-router-dom';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { hasAdminAccess, isSuperAdmin } from '../../../utils/roleUtils';
import { knowledgeHubCollectionService } from '../../../services/knowledgeHubCollectionService';
import { collectionExportService } from '../../../services/collectionExportService';
import { collectionExportWebSocketService } from '../../../services/collectionExportWebSocketService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { formatDateTime } from '../../../utils/dateUtils';
import ConfirmationModalWithVerification from '../../../components/common/ConfirmationModalWithVerification';
import AdminGuard from '../../../components/guards/AdminGuard';
import Button from '../../../components/common/Button';
import Footer from '../../../components/common/Footer';
import './styles.css';

const STATUS_CONFIG = {
  QUEUED: { variant: 'secondary', icon: HiClock, label: 'Queued' },
  RUNNING: { variant: 'primary', icon: HiClock, label: 'Running' },
  COMPLETED: { variant: 'success', icon: HiCheckCircle, label: 'Completed' },
  FAILED: { variant: 'danger', icon: HiXCircle, label: 'Failed' },
  CANCELLED: { variant: 'warning', icon: HiXCircle, label: 'Cancelled' }
};

function ExportCollection() {
  const navigate = useNavigate();
  const { currentTeam } = useTeam();
  const { user } = useAuth();
  const [collections, setCollections] = useState([]);
  const [selectedCollections, setSelectedCollections] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [exportJobs, setExportJobs] = useState([]);
  const [loadingJobs, setLoadingJobs] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(null);
  const [cancellingJob, setCancellingJob] = useState(null);

  // Check if user has admin access
  const hasAccess = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);

  // Fetch collections on mount
  useEffect(() => {
    if (hasAccess) {
      fetchCollections();
      fetchExportJobs();

      // Connect to WebSocket for progress updates
      collectionExportWebSocketService.connect();
      const unsubscribe = collectionExportWebSocketService.subscribe(handleExportUpdate);

      return () => {
        unsubscribe();
        collectionExportWebSocketService.disconnect();
      };
    }
  }, [hasAccess]);

  const fetchCollections = async () => {
    setLoading(true);
    try {
      const result = await knowledgeHubCollectionService.getAllCollections();
      if (result.success) {
        setCollections(result.data || []);
      } else {
        showErrorToast(result.error || 'Failed to fetch collections');
      }
    } catch (error) {
      console.error('Error fetching collections:', error);
      showErrorToast('Failed to fetch collections');
    } finally {
      setLoading(false);
    }
  };

  const fetchExportJobs = async () => {
    setLoadingJobs(true);
    try {
      const result = await collectionExportService.getExportJobs();
      if (result.success) {
        setExportJobs(result.data || []);
      } else {
        console.error('Failed to fetch export jobs:', result.error);
      }
    } catch (error) {
      console.error('Error fetching export jobs:', error);
    } finally {
      setLoadingJobs(false);
    }
  };

  const handleExportUpdate = useCallback((update) => {
    console.log('📦 Received export update:', update);
    setExportJobs(prevJobs => {
      const existingIndex = prevJobs.findIndex(job => job.jobId === update.jobId);
      if (existingIndex >= 0) {
        const updated = [...prevJobs];
        updated[existingIndex] = { ...updated[existingIndex], ...update };
        return updated;
      } else {
        return [...prevJobs, update];
      }
    });
  }, []);

  const handleCollectionToggle = (collectionId) => {
    setSelectedCollections(prev => {
      const newSet = new Set(prev);
      if (newSet.has(collectionId)) {
        newSet.delete(collectionId);
      } else {
        newSet.add(collectionId);
      }
      return newSet;
    });
  };


  const handleExport = () => {
    if (selectedCollections.size === 0) {
      showErrorToast('Please select at least one collection to export');
      return;
    }
    setShowExportModal(true);
  };

  const handleExportConfirm = async () => {
    setShowExportModal(false);
    setExporting(true);

    try {
      const result = await collectionExportService.queueExport(Array.from(selectedCollections));
      if (result.success) {
        showSuccessToast('Export job queued successfully');
        setSelectedCollections(new Set());
        // Refresh jobs list
        await fetchExportJobs();
      } else {
        showErrorToast(result.error || 'Failed to queue export job');
      }
    } catch (error) {
      console.error('Error queueing export:', error);
      showErrorToast('Failed to queue export job');
    } finally {
      setExporting(false);
    }
  };

  const handleCancelJob = (jobId) => {
    setShowCancelModal(jobId);
  };

  const handleCancelConfirm = async () => {
    if (!showCancelModal) return;

    setCancellingJob(showCancelModal);
    try {
      const result = await collectionExportService.cancelExportJob(showCancelModal);
      if (result.success) {
        showSuccessToast('Export job cancelled successfully');
        await fetchExportJobs();
      } else {
        showErrorToast(result.error || 'Failed to cancel export job');
      }
    } catch (error) {
      console.error('Error cancelling export:', error);
      showErrorToast('Failed to cancel export job');
    } finally {
      setCancellingJob(null);
      setShowCancelModal(null);
    }
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return 'N/A';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    if (bytes === 0) return '0 B';
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return Math.round(bytes / Math.pow(1024, i) * 100) / 100 + ' ' + sizes[i];
  };


  const getJobProgress = (job) => {
    if (!job.totalDocuments || job.totalDocuments === 0) return 0;
    const processed = job.processedDocuments || 0;
    return Math.round((processed / job.totalDocuments) * 100);
  };

  const canCancelJob = (job) => {
    return job.status === 'QUEUED' || job.status === 'RUNNING';
  };

  if (!hasAccess) {
    return (
      <Container fluid className="export-collection-container">
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
                Export Collections
              </Breadcrumb.Item>
            </Breadcrumb>
          </Card.Body>
        </Card>
        <Card className="border-0">
          <Card.Body className="text-center py-5">
            <HiExclamationCircle size={48} className="text-warning mb-3" />
            <h4>Access Denied</h4>
            <p className="text-muted">Only team administrators or SUPER_ADMIN can export collections.</p>
          </Card.Body>
        </Card>
      </Container>
    );
  }

  return (
    <AdminGuard>
      <Container fluid className="export-collection-container">
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
                Export Collections
              </Breadcrumb.Item>
            </Breadcrumb>
          </Card.Body>
        </Card>

        {/* Main Content */}
        <Card className="border-0">
          <Card.Header>
            <div className="d-flex align-items-center">
              <div className="d-flex align-items-center gap-2">
                <HiOutlineCollection className="page-icon" />
                <h4 className="mb-0">Export Collections</h4>
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
            {/* Collection Selection */}
            <div className="mb-4">
              <div className="d-flex justify-content-between align-items-center mb-3">
                <h5>Select Collections to Export</h5>
                <div>
                  <Button
                    variant="primary"
                    onClick={handleExport}
                    disabled={selectedCollections.size === 0 || exporting}
                  >
                    {exporting ? (
                      <>
                        <Spinner as="span" animation="border" size="sm" className="me-2" />
                        Exporting...
                      </>
                    ) : (
                      <>
                        <HiDownload className="me-2" />
                        Export Selected ({selectedCollections.size})
                      </>
                    )}
                  </Button>
                </div>
              </div>
              {loading ? (
                <div className="text-center py-5">
                  <Spinner animation="border" role="status">
                    <span className="visually-hidden">Loading collections...</span>
                  </Spinner>
                </div>
              ) : collections.length === 0 ? (
                <div className="text-center py-4 text-muted">
                  No collections found. Create a collection first.
                </div>
              ) : (
                <Table responsive hover>
                  <thead>
                    <tr>
                      <th style={{ width: '50px' }}></th>
                      <th style={{ width: '30%' }}>Collection Name</th>
                      <th>Description</th>
                      <th>Milvus Collection</th>
                    </tr>
                  </thead>
                  <tbody>
                    {collections.map((collection) => (
                      <tr key={collection.id}>
                        <td>
                          <Form.Check
                            type="checkbox"
                            checked={selectedCollections.has(collection.id)}
                            onChange={() => handleCollectionToggle(collection.id)}
                          />
                        </td>
                        <td>
                          <Link to={`/knowledge-hub/collection/${collection.id}`} className="collection-link">
                            {collection.name}
                          </Link>
                        </td>
                        <td>{collection.description || 'N/A'}</td>
                        <td>
                          {collection.milvusCollectionName ? (
                            <Badge bg="success">{collection.milvusCollectionName}</Badge>
                          ) : (
                            <Badge bg="secondary">Not assigned</Badge>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              )}
            </div>

            {/* Export Jobs Table */}
            <div className="mt-5">
              <div className="d-flex justify-content-between align-items-center mb-3">
                <h5>Export History</h5>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={fetchExportJobs}
                  disabled={loadingJobs}
                >
                  {loadingJobs ? (
                    <>
                      <Spinner as="span" animation="border" size="sm" className="me-2" />
                      Loading...
                    </>
                  ) : (
                    'Refresh'
                  )}
                </Button>
              </div>
              {loadingJobs ? (
                <div className="text-center py-5">
                  <Spinner animation="border" role="status">
                    <span className="visually-hidden">Loading export jobs...</span>
                  </Spinner>
                </div>
              ) : exportJobs.length === 0 ? (
                <div className="text-center py-4 text-muted">
                  No export jobs found.
                </div>
              ) : (
                <div className="table-responsive">
                  <Table hover>
                    <thead>
                      <tr>
                        <th>Job ID</th>
                        <th>Status</th>
                        <th>Collections</th>
                        <th>Progress</th>
                        <th>Exported By</th>
                        <th>Created At</th>
                        <th>Completed At</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {exportJobs.map((job) => {
                        const StatusIcon = STATUS_CONFIG[job.status]?.icon || HiClock;
                        const statusVariant = STATUS_CONFIG[job.status]?.variant || 'secondary';
                        const statusLabel = STATUS_CONFIG[job.status]?.label || job.status;
                        const progress = getJobProgress(job);

                        return (
                          <tr key={job.jobId}>
                            <td>
                              <code className="small">{job.jobId ? job.jobId.substring(0, 8) : 'N/A'}...</code>
                            </td>
                            <td>
                              <Badge bg={statusVariant}>
                                <StatusIcon className="me-1" />
                                {statusLabel}
                              </Badge>
                            </td>
                            <td>
                              {job.collectionIds?.length || 0} collection{job.collectionIds?.length !== 1 ? 's' : ''}
                            </td>
                            <td>
                              {job.status === 'RUNNING' && (
                                <div>
                                  <ProgressBar
                                    now={progress}
                                    label={`${progress}%`}
                                    variant="primary"
                                    className="mb-1"
                                  />
                                  <small className="text-muted">
                                    {job.processedDocuments || 0} / {job.totalDocuments || 0} documents
                                  </small>
                                </div>
                              )}
                              {job.status === 'COMPLETED' && (
                                <Badge bg="success">100%</Badge>
                              )}
                              {!['RUNNING', 'COMPLETED'].includes(job.status) && (
                                <span className="text-muted">-</span>
                              )}
                            </td>
                            <td>{job.exportedBy || 'N/A'}</td>
                            <td>{job.createdAt ? formatDateTime(job.createdAt) : 'N/A'}</td>
                            <td>{job.completedAt ? formatDateTime(job.completedAt) : 'N/A'}</td>
                            <td>
                              <div className="d-flex gap-2">
                                {job.status === 'COMPLETED' && job.exportResults && job.exportResults.length > 0 && (
                                  <>
                                    {job.exportResults.map((result, idx) => (
                                      <Button
                                        key={idx}
                                        variant="outline-primary"
                                        size="sm"
                                        onClick={() => {
                                          if (result.downloadUrl) {
                                            window.open(result.downloadUrl, '_blank');
                                          }
                                        }}
                                        disabled={!result.downloadUrl}
                                        title={`Download ${result.collectionName || result.collectionId}`}
                                      >
                                        <HiDownload className="me-1" />
                                        {result.collectionName || result.collectionId}
                                      </Button>
                                    ))}
                                  </>
                                )}
                                {canCancelJob(job) && (
                                  <Button
                                    variant="outline-danger"
                                    size="sm"
                                    onClick={() => handleCancelJob(job.jobId)}
                                    disabled={cancellingJob === job.jobId}
                                  >
                                    <HiXCircle className="me-1" />
                                    Cancel
                                  </Button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </Table>
                </div>
              )}
            </div>
          </Card.Body>
        </Card>

        {/* Export Confirmation Modal */}
        <ConfirmationModalWithVerification
          show={showExportModal}
          onHide={() => setShowExportModal(false)}
          onConfirm={handleExportConfirm}
          title="Export Collections"
          message={
            <>
              <p>
                <strong>Warning:</strong> You are about to export <strong>{selectedCollections.size}</strong> collection{selectedCollections.size !== 1 ? 's' : ''}
                {' '}containing sensitive data.
              </p>
              <p className="mb-0">
                The exported ZIP files will contain <strong>decrypted</strong> documents, processed JSON files, and knowledge graph data.
                Each collection will be exported as a separate ZIP file. This action cannot be undone.
              </p>
            </>
          }
          variant="warning"
          confirmLabel="Export"
          loading={exporting}
        />

        {/* Cancel Confirmation Modal */}
        <ConfirmationModalWithVerification
          show={!!showCancelModal}
          onHide={() => setShowCancelModal(null)}
          onConfirm={handleCancelConfirm}
          title="Cancel Export Job"
          message="Are you sure you want to cancel this export job? This action cannot be undone."
          variant="danger"
          confirmLabel="Cancel Export"
          loading={!!cancellingJob}
        />
        <Footer />
      </Container>
    </AdminGuard>
  );
}

export default ExportCollection;

