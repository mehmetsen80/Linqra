import React, { useState, useEffect, useCallback } from 'react';
import { Container, Row, Col, Card, Table, Form, Button, Alert, Spinner, Breadcrumb, Badge, Modal, Pagination, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';
import { format, subDays } from 'date-fns';
import { auditService, AUDIT_EVENT_TYPES, formatEventType, getEventTypeCategory } from '../../services/auditService';
import { formatDateTime } from '../../utils/dateUtils';
import { useTeam } from '../../contexts/TeamContext';
import { HiSearchCircle, HiRefresh, HiFilter, HiEye, HiChevronLeft, HiChevronRight, HiDocumentReport, HiClock, HiDatabase, HiArchive } from 'react-icons/hi';
import Footer from '../../components/common/Footer';
import './styles.css';

const Audits = () => {
    const navigate = useNavigate();
    const { currentTeam } = useTeam();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [auditLogs, setAuditLogs] = useState([]);
    const [pagination, setPagination] = useState({
        page: 0,
        size: 25,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: false
    });
    const [stats, setStats] = useState(null);
    const [statsLoading, setStatsLoading] = useState(false);

    // Filter states
    const [startDate, setStartDate] = useState(format(subDays(new Date(), 7), 'yyyy-MM-dd'));
    const [endDate, setEndDate] = useState(format(new Date(), 'yyyy-MM-dd'));
    const [selectedEventCategory, setSelectedEventCategory] = useState('');
    const [selectedEventTypes, setSelectedEventTypes] = useState([]);
    const [selectedResult, setSelectedResult] = useState('');
    const [userIdFilter, setUserIdFilter] = useState('');
    const [documentIdFilter, setDocumentIdFilter] = useState('');
    const [resourceTypeFilter, setResourceTypeFilter] = useState('');
    const [includeArchived, setIncludeArchived] = useState(false);
    const [showFilters, setShowFilters] = useState(true);

    // Detail modal
    const [selectedLog, setSelectedLog] = useState(null);
    const [showDetailModal, setShowDetailModal] = useState(false);

    // Fetch archival stats
    const fetchStats = useCallback(async () => {
        if (!currentTeam?.id) return;
        setStatsLoading(true);
        try {
            const result = await auditService.getArchivalStats();
            if (result.success) {
                setStats(result.data);
            }
        } catch (err) {
            console.error('Error fetching archival stats:', err);
        } finally {
            setStatsLoading(false);
        }
    }, [currentTeam?.id]);

    // Fetch audit logs
    const fetchAuditLogs = useCallback(async (page = 0) => {
        if (!currentTeam?.id) return;

        setLoading(true);
        setError(null);

        try {
            const request = {
                teamId: currentTeam.id,
                startTime: `${startDate}T00:00:00`,
                endTime: `${endDate}T23:59:59`,
                page: page,
                size: pagination.size
            };

            // Add optional filters
            if (selectedEventTypes.length > 0) {
                request.eventTypes = selectedEventTypes;
            }
            if (selectedResult) {
                request.result = selectedResult;
            }
            if (userIdFilter.trim()) {
                request.userId = userIdFilter.trim();
            }
            if (documentIdFilter.trim()) {
                request.documentId = documentIdFilter.trim();
            }
            if (resourceTypeFilter.trim()) {
                request.resourceType = resourceTypeFilter.trim();
            }
            if (includeArchived) {
                request.includeArchived = true;
            }

            const result = await auditService.queryAuditLogs(request);

            if (result.success) {
                setAuditLogs(result.data.content || []);
                setPagination({
                    page: result.data.page || 0,
                    size: result.data.size || 25,
                    totalElements: result.data.totalElements || 0,
                    totalPages: result.data.totalPages || 0,
                    hasNext: result.data.hasNext || false,
                    hasPrevious: result.data.hasPrevious || false
                });
            } else {
                setError(result.error || 'Failed to fetch audit logs');
            }
        } catch (err) {
            console.error('Error fetching audit logs:', err);
            setError('Failed to fetch audit logs. Please try again.');
        } finally {
            setLoading(false);
        }
    }, [currentTeam?.id, startDate, endDate, selectedEventTypes, selectedResult, userIdFilter, documentIdFilter, resourceTypeFilter, pagination.size]);

    useEffect(() => {
        fetchStats();
        fetchAuditLogs(0);
    }, [currentTeam?.id]);

    // Handle event category selection
    const handleEventCategoryChange = (category) => {
        setSelectedEventCategory(category);
        if (category && AUDIT_EVENT_TYPES[category]) {
            setSelectedEventTypes(AUDIT_EVENT_TYPES[category]);
        } else {
            setSelectedEventTypes([]);
        }
    };

    const handleSearch = () => {
        fetchAuditLogs(0);
    };

    const handleReset = () => {
        setStartDate(format(subDays(new Date(), 7), 'yyyy-MM-dd'));
        setEndDate(format(new Date(), 'yyyy-MM-dd'));
        setSelectedEventCategory('');
        setSelectedEventTypes([]);
        setSelectedResult('');
        setUserIdFilter('');
        setDocumentIdFilter('');
        setResourceTypeFilter('');
        fetchAuditLogs(0);
    };

    const handlePageChange = (newPage) => {
        fetchAuditLogs(newPage);
    };

    const handleViewDetail = (log) => {
        setSelectedLog(log);
        setShowDetailModal(true);
    };

    const getResultBadgeVariant = (result) => {
        switch (result?.toUpperCase()) {
            case 'SUCCESS': return 'success';
            case 'FAILED': return 'danger';
            case 'DENIED': return 'warning';
            default: return 'secondary';
        }
    };

    const getCategoryBadgeColor = (eventType) => {
        const category = getEventTypeCategory(eventType);
        switch (category) {
            case 'DATA_ACCESS': return '#ff9800';      // Orange
            case 'DATA_MODIFICATION': return '#e67e50'; // Coral/Salmon
            case 'EXPORT': return '#9b7dc5';           // Soft purple
            case 'AUTHENTICATION': return '#5cb85c';   // Soft green
            case 'SECURITY': return '#e57373';         // Soft red
            case 'ADMINISTRATIVE': return '#f0ad4e';   // Soft orange
            case 'AGENT_WORKFLOW': return '#8e7cc3';   // Soft violet
            case 'LLM_CHAT': return '#d48fbf';         // Soft pink
            case 'SYSTEM': return '#95a5a6';           // Soft gray
            default: return '#95a5a6';
        }
    };

    if (!currentTeam) {
        return (
            <Container className="audits-container mt-4">
                <Alert variant="warning">Please select a team to view audit logs.</Alert>
                <Footer />
            </Container>
        );
    }

    return (
        <Container fluid className="audits-container">
            {/* Breadcrumb Navigation */}
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
                        <Breadcrumb.Item active>
                            Audit Logs
                        </Breadcrumb.Item>
                    </Breadcrumb>
                </Card.Body>
            </Card>

            {/* Page Header */}
            <div className="page-header mb-4">
                <h3 className="page-title">
                    <HiDocumentReport className="me-2" />
                    Audit Logs
                </h3>
                <p className="page-description text-muted text-start ms-4">
                    View and search audit logs for {currentTeam?.name || 'your team'}. Recent logs (last 90 days) are stored in MongoDB.
                </p>
            </div>

            {/* Stats Cards */}
            <Row className="mb-4">
                <Col md={4}>
                    <Card className="stats-card stats-total">
                        <Card.Body>
                            <div className="stats-icon">
                                <HiDatabase />
                            </div>
                            <div className="stats-content">
                                <p className="stats-label">Total Logs (MongoDB)</p>
                                <h3 className="stats-value">
                                    {statsLoading ? <Spinner animation="border" size="sm" /> : (stats?.totalLogsInMongoDB?.toLocaleString() || '0')}
                                </h3>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={4}>
                    <Card className="stats-card stats-pending">
                        <Card.Body>
                            <div className="stats-icon">
                                <HiClock />
                            </div>
                            <div className="stats-content">
                                <p className="stats-label">Ready for Archival</p>
                                <h3 className="stats-value">
                                    {statsLoading ? <Spinner animation="border" size="sm" /> : (stats?.logsReadyForArchival?.toLocaleString() || '0')}
                                </h3>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={4}>
                    <Card className="stats-card stats-archived">
                        <Card.Body>
                            <div className="stats-icon">
                                <HiArchive />
                            </div>
                            <div className="stats-content">
                                <p className="stats-label">Archived Logs</p>
                                <h3 className="stats-value">
                                    {statsLoading ? <Spinner animation="border" size="sm" /> : (stats?.archivedLogsCount?.toLocaleString() || '0')}
                                </h3>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            {/* Search Filters */}
            <Card className="mb-4 filter-card">
                <Card.Header className="d-flex justify-content-between align-items-center">
                    <h5 className="mb-0">
                        <HiFilter className="me-2" />
                        Search Filters
                    </h5>
                    <Button
                        variant="outline-secondary"
                        size="sm"
                        onClick={() => setShowFilters(!showFilters)}
                    >
                        {showFilters ? 'Hide Filters' : 'Show Filters'}
                    </Button>
                </Card.Header>
                {showFilters && (
                    <Card.Body>
                        <Row className="mb-3">
                            <Col md={3}>
                                <Form.Group>
                                    <Form.Label className="filter-label">
                                        <i className="fas fa-calendar-alt me-2"></i>
                                        Start Date
                                    </Form.Label>
                                    <Form.Control
                                        type="date"
                                        value={startDate}
                                        onChange={(e) => setStartDate(e.target.value)}
                                        max={endDate}
                                    />
                                </Form.Group>
                            </Col>
                            <Col md={3}>
                                <Form.Group>
                                    <Form.Label className="filter-label">
                                        <i className="fas fa-calendar-alt me-2"></i>
                                        End Date
                                    </Form.Label>
                                    <Form.Control
                                        type="date"
                                        value={endDate}
                                        onChange={(e) => setEndDate(e.target.value)}
                                        min={startDate}
                                        max={format(new Date(), 'yyyy-MM-dd')}
                                    />
                                </Form.Group>
                            </Col>
                            <Col md={3}>
                                <Form.Group>
                                    <Form.Label className="filter-label">
                                        <i className="fas fa-tag me-2"></i>
                                        Event Category
                                    </Form.Label>
                                    <Form.Select
                                        value={selectedEventCategory}
                                        onChange={(e) => handleEventCategoryChange(e.target.value)}
                                    >
                                        <option value="">All Categories</option>
                                        {Object.keys(AUDIT_EVENT_TYPES).map(category => (
                                            <option key={category} value={category}>
                                                {category.replace(/_/g, ' ')}
                                            </option>
                                        ))}
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                            <Col md={3}>
                                <Form.Group>
                                    <Form.Label className="filter-label">
                                        <i className="fas fa-check-circle me-2"></i>
                                        Result
                                    </Form.Label>
                                    <Form.Select
                                        value={selectedResult}
                                        onChange={(e) => setSelectedResult(e.target.value)}
                                    >
                                        <option value="">All Results</option>
                                        <option value="SUCCESS">Success</option>
                                        <option value="FAILED">Failed</option>
                                        <option value="DENIED">Denied</option>
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                        </Row>
                        <Row className="mb-3">
                            <Col md={3}>
                                <Form.Group>
                                    <Form.Label className="filter-label">
                                        <i className="fas fa-user me-2"></i>
                                        User ID
                                    </Form.Label>
                                    <Form.Control
                                        type="text"
                                        placeholder="Filter by user..."
                                        value={userIdFilter}
                                        onChange={(e) => setUserIdFilter(e.target.value)}
                                    />
                                </Form.Group>
                            </Col>
                            <Col md={3}>
                                <Form.Group>
                                    <Form.Label className="filter-label">
                                        <i className="fas fa-file-alt me-2"></i>
                                        Document ID
                                    </Form.Label>
                                    <Form.Control
                                        type="text"
                                        placeholder="Filter by document..."
                                        value={documentIdFilter}
                                        onChange={(e) => setDocumentIdFilter(e.target.value)}
                                    />
                                </Form.Group>
                            </Col>
                            <Col md={3}>
                                <Form.Group>
                                    <Form.Label className="filter-label">
                                        <i className="fas fa-cube me-2"></i>
                                        Resource Type
                                    </Form.Label>
                                    <Form.Control
                                        type="text"
                                        placeholder="e.g., DOCUMENT, CHUNK..."
                                        value={resourceTypeFilter}
                                        onChange={(e) => setResourceTypeFilter(e.target.value)}
                                    />
                                </Form.Group>
                            </Col>
                            <Col md={3}>
                                <Form.Group>
                                    <Form.Label className="filter-label">
                                        <i className="fas fa-archive me-2"></i>
                                        Include Archived
                                    </Form.Label>
                                    <Form.Check
                                        type="switch"
                                        id="include-archived-switch"
                                        label={includeArchived ? "Searching S3 + MongoDB" : "MongoDB only (last 90 days)"}
                                        checked={includeArchived}
                                        onChange={(e) => setIncludeArchived(e.target.checked)}
                                        className="mt-2"
                                        style={{ paddingTop: '0.5rem', paddingBottom: '0.5rem' }}
                                    />
                                </Form.Group>
                            </Col>
                        </Row>
                        <Row className="mt-3 justify-content-end">
                            <Col xs="auto" className="d-flex align-items-end gap-2">
                                <Button
                                    variant="primary"
                                    onClick={handleSearch}
                                    disabled={loading}
                                >
                                    <HiSearchCircle className="me-2" />
                                    Search
                                </Button>
                                <Button
                                    variant="outline-secondary"
                                    onClick={handleReset}
                                    disabled={loading}
                                >
                                    <HiRefresh />
                                </Button>
                            </Col>
                        </Row>
                    </Card.Body>
                )}
            </Card>

            {/* Results */}
            <Card className="results-card">
                <Card.Header className="d-flex justify-content-between align-items-center">
                    <h5 className="mb-0">
                        <i className="fas fa-list me-2"></i>
                        Audit Log Results
                        {pagination.totalElements > 0 && (
                            <Badge bg="secondary" className="ms-2">
                                {pagination.totalElements.toLocaleString()} total
                            </Badge>
                        )}
                    </h5>
                    <div className="d-flex align-items-center gap-3">
                        <Form.Group className="d-flex align-items-center mb-0">
                            <Form.Label className="me-2 mb-0 small text-muted">Page Size:</Form.Label>
                            <Form.Select
                                size="sm"
                                style={{ width: 'auto' }}
                                value={pagination.size}
                                onChange={(e) => {
                                    setPagination(prev => ({ ...prev, size: parseInt(e.target.value) }));
                                    fetchAuditLogs(0);
                                }}
                            >
                                <option value="10">10</option>
                                <option value="25">25</option>
                                <option value="50">50</option>
                                <option value="100">100</option>
                            </Form.Select>
                        </Form.Group>
                    </div>
                </Card.Header>
                <Card.Body className="p-0">
                    {loading ? (
                        <div className="text-center py-5">
                            <Spinner animation="border" variant="primary" />
                            <p className="mt-3">Loading audit logs...</p>
                        </div>
                    ) : error ? (
                        <Alert variant="danger" className="m-3">
                            <i className="fas fa-exclamation-triangle me-2"></i>
                            {error}
                        </Alert>
                    ) : auditLogs.length === 0 ? (
                        <Alert variant="info" className="m-3">
                            <i className="fas fa-info-circle me-2"></i>
                            No audit logs found for the selected criteria. Try adjusting your filters.
                        </Alert>
                    ) : (
                        <>
                            <div className="table-responsive">
                                <Table hover className="audit-logs-table mb-0">
                                    <thead>
                                        <tr>
                                            <th>Timestamp</th>
                                            <th>Event Type</th>
                                            <th>User</th>
                                            <th>Action</th>
                                            <th>Result</th>
                                            <th>Resource</th>
                                            <th>Source</th>
                                            <th>Details</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {auditLogs.map((log, index) => (
                                            <tr key={log.eventId || index}>
                                                <td className="timestamp-cell">
                                                    {formatDateTime(log.timestamp)}
                                                </td>
                                                <td>
                                                    <Badge
                                                        bg=""
                                                        style={{
                                                            backgroundColor: getCategoryBadgeColor(log.eventType),
                                                            fontSize: '0.75rem'
                                                        }}
                                                    >
                                                        {formatEventType(log.eventType)}
                                                    </Badge>
                                                </td>
                                                <td>
                                                    <span className="username-cell">
                                                        {log.username || log.userId || '-'}
                                                    </span>
                                                    {log.ipAddress && (
                                                        <small className="d-block text-muted">{log.ipAddress}</small>
                                                    )}
                                                </td>
                                                <td>{log.action || '-'}</td>
                                                <td>
                                                    <Badge bg={getResultBadgeVariant(log.result)}>
                                                        {log.result || '-'}
                                                    </Badge>
                                                </td>
                                                <td>
                                                    <span className="resource-type">{log.resourceType || '-'}</span>
                                                    {log.resourceId && (
                                                        <small className="d-block text-muted text-truncate" style={{ maxWidth: '150px' }} title={log.resourceId}>
                                                            {log.resourceId}
                                                        </small>
                                                    )}
                                                </td>
                                                <td>
                                                    <Badge bg={log.archived ? 'info' : 'secondary'} style={{ fontSize: '0.7rem', minWidth: '75px', display: 'inline-block', textAlign: 'center' }}>
                                                        {log.archived ? (
                                                            <><i className="fas fa-archive me-1"></i>Archived</>
                                                        ) : (
                                                            <><i className="fas fa-database me-1"></i>MongoDB</>
                                                        )}
                                                    </Badge>
                                                </td>
                                                <td>
                                                    <Button
                                                        variant="outline-primary"
                                                        size="sm"
                                                        onClick={() => handleViewDetail(log)}
                                                    >
                                                        <HiEye className="me-2" />
                                                        View
                                                    </Button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </Table>
                            </div>

                            {/* Pagination */}
                            {pagination.totalPages > 1 && (
                                <div className="d-flex justify-content-between align-items-center p-3 border-top">
                                    <span className="text-muted small">
                                        Showing {pagination.page * pagination.size + 1} to {Math.min((pagination.page + 1) * pagination.size, pagination.totalElements)} of {pagination.totalElements.toLocaleString()} entries
                                    </span>
                                    <Pagination className="mb-0">
                                        <Pagination.First
                                            onClick={() => handlePageChange(0)}
                                            disabled={!pagination.hasPrevious}
                                        />
                                        <Pagination.Prev
                                            onClick={() => handlePageChange(pagination.page - 1)}
                                            disabled={!pagination.hasPrevious}
                                        />

                                        {/* Page numbers */}
                                        {[...Array(Math.min(5, pagination.totalPages))].map((_, i) => {
                                            let pageNum;
                                            if (pagination.totalPages <= 5) {
                                                pageNum = i;
                                            } else if (pagination.page < 3) {
                                                pageNum = i;
                                            } else if (pagination.page > pagination.totalPages - 4) {
                                                pageNum = pagination.totalPages - 5 + i;
                                            } else {
                                                pageNum = pagination.page - 2 + i;
                                            }
                                            return (
                                                <Pagination.Item
                                                    key={pageNum}
                                                    active={pageNum === pagination.page}
                                                    onClick={() => handlePageChange(pageNum)}
                                                >
                                                    {pageNum + 1}
                                                </Pagination.Item>
                                            );
                                        })}

                                        <Pagination.Next
                                            onClick={() => handlePageChange(pagination.page + 1)}
                                            disabled={!pagination.hasNext}
                                        />
                                        <Pagination.Last
                                            onClick={() => handlePageChange(pagination.totalPages - 1)}
                                            disabled={!pagination.hasNext}
                                        />
                                    </Pagination>
                                </div>
                            )}
                        </>
                    )}
                </Card.Body>
            </Card>

            {/* Detail Modal */}
            <Modal
                show={showDetailModal}
                onHide={() => setShowDetailModal(false)}
                size="lg"
                className="audit-detail-modal"
            >
                <Modal.Header closeButton>
                    <Modal.Title>
                        <i className="fas fa-info-circle me-2"></i>
                        Audit Log Details
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {selectedLog && (
                        <div className="audit-detail-content">
                            <Row className="mb-3">
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>Event ID</label>
                                        <code>{selectedLog.eventId || '-'}</code>
                                    </div>
                                </Col>
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>Timestamp</label>
                                        <span>{formatDateTime(selectedLog.timestamp)}</span>
                                    </div>
                                </Col>
                            </Row>
                            <Row className="mb-3">
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>Event Type</label>
                                        <Badge style={{ backgroundColor: getCategoryBadgeColor(selectedLog.eventType) }}>
                                            {formatEventType(selectedLog.eventType)}
                                        </Badge>
                                    </div>
                                </Col>
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>Result</label>
                                        <Badge bg={getResultBadgeVariant(selectedLog.result)}>
                                            {selectedLog.result || '-'}
                                        </Badge>
                                    </div>
                                </Col>
                            </Row>
                            <Row className="mb-3">
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>User</label>
                                        <span>{selectedLog.username || selectedLog.userId || '-'}</span>
                                    </div>
                                </Col>
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>Action</label>
                                        <span>{selectedLog.action || '-'}</span>
                                    </div>
                                </Col>
                            </Row>
                            <Row className="mb-3">
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>IP Address</label>
                                        <span>{selectedLog.ipAddress || '-'}</span>
                                    </div>
                                </Col>
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>User Agent</label>
                                        {selectedLog.userAgent ? (
                                            <OverlayTrigger
                                                placement="top"
                                                overlay={
                                                    <Tooltip id="user-agent-tooltip">
                                                        {selectedLog.userAgent}
                                                    </Tooltip>
                                                }
                                            >
                                                <span className="text-truncate d-inline-block" style={{ maxWidth: '300px', cursor: 'pointer' }}>
                                                    {selectedLog.userAgent}
                                                </span>
                                            </OverlayTrigger>
                                        ) : (
                                            <span>-</span>
                                        )}
                                    </div>
                                </Col>
                            </Row>
                            <Row className="mb-3">
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>Resource Type</label>
                                        <span>{selectedLog.resourceType || '-'}</span>
                                    </div>
                                </Col>
                                <Col md={6}>
                                    <div className="detail-field">
                                        <label>Resource ID</label>
                                        <code className="small">{selectedLog.resourceId || '-'}</code>
                                    </div>
                                </Col>
                            </Row>

                            <Row className="mb-3">
                                <Col md={12}>
                                    <div className="detail-field">
                                        <label>Document ID</label>
                                        <code className="small">{selectedLog.documentId || '-'}</code>
                                    </div>
                                </Col>
                            </Row>




                            {/* Metadata */}
                            {selectedLog.metadata && (
                                <div className="metadata-section mt-4">
                                    <h6 className="mb-3">
                                        <i className="fas fa-code me-2"></i>
                                        Metadata
                                    </h6>
                                    {selectedLog.metadata.reason && (
                                        <div className="detail-field mb-2">
                                            <label>Reason</label>
                                            <p className="metadata-reason">{selectedLog.metadata.reason}</p>
                                        </div>
                                    )}
                                    {selectedLog.metadata.durationMs && (
                                        <div className="detail-field mb-2">
                                            <label>Duration</label>
                                            <span>{selectedLog.metadata.durationMs}ms</span>
                                        </div>
                                    )}
                                    {selectedLog.metadata.errorMessage && (
                                        <div className="detail-field mb-2">
                                            <label>Error</label>
                                            <Alert variant="danger" className="p-2 mb-0">
                                                {selectedLog.metadata.errorMessage}
                                            </Alert>
                                        </div>
                                    )}
                                    {selectedLog.metadata.context && Object.keys(selectedLog.metadata.context).length > 0 && (
                                        <div className="detail-field">
                                            <label>Context</label>
                                            <pre className="context-json">
                                                {JSON.stringify(selectedLog.metadata.context, null, 2)}
                                            </pre>
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Archived info */}
                            {selectedLog.archived && (
                                <div className="archived-info mt-3">
                                    <Badge bg="info">
                                        <HiArchive className="me-1" />
                                        Archived
                                    </Badge>
                                    {selectedLog.s3Key && (
                                        <small className="ms-2 text-muted">S3: {selectedLog.s3Key}</small>
                                    )}
                                </div>
                            )}
                        </div>
                    )}
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowDetailModal(false)}>
                        Close
                    </Button>
                </Modal.Footer>
            </Modal>
            <Footer />
        </Container >
    );
};

export default Audits;
