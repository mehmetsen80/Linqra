import React, { useState, useEffect } from 'react';
import { Card, Breadcrumb, Tabs, Tab, Table, Badge, Form, Row, Col, Alert, Spinner, Modal, Accordion } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../utils/roleUtils';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import Button from '../../components/common/Button';
import resourceService from '../../services/resourceService';
import { apiRouteService } from '../../services/apiRouteService';
import { teamService } from '../../services/teamService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import { formatDate } from '../../utils/dateUtils';
import { 
  HiPlus, 
  HiTrash, 
  HiPencil,
  HiBell, 
  HiTerminal, 
  HiCheck, 
  HiOutlineOfficeBuilding, 
  HiMail, 
  HiGlobeAlt, 
  HiRefresh,
  HiUser,
  HiOutlineDocumentText,
  HiOutlineScale,
  HiOutlineSpeakerphone,
  HiOutlineTrendingUp,
  HiOutlineClock
} from 'react-icons/hi';
import './styles.css';
import Footer from '../../components/common/Footer';

function Resources() {
  const { currentTeam, loading: teamLoading } = useTeam();
  const { user } = useAuth();
  const navigate = useNavigate();

  // Role Gate
  const canEditResource = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);

  // States
  const [activeTab, setActiveTab] = useState('resources');
  const [resources, setResources] = useState([]);
  
  const [teamRoutes, setTeamRoutes] = useState([]);
  
  // Unified Alerts Feed
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  
  // Filters for consolidated Feed
  const [filterCategory, setFilterCategory] = useState('');
  const [filterSeverity, setFilterSeverity] = useState('');
  
  const [loadingResources, setLoadingResources] = useState(true);
  const [loadingNotifications, setLoadingNotifications] = useState(false);
  const [error, setError] = useState(null);

  // Modals Toggle
  const [showCreateResourceModal, setShowCreateResourceModal] = useState(false);
  const [showEditResourceModal, setShowEditResourceModal] = useState(false);
  const [showDispatchModal, setShowDispatchModal] = useState(false);

  // Form Fields for Editing
  const [editingResource, setEditingResource] = useState({
    domain: '',
    category: '',
    resourceId: '',
    displayName: '',
    description: ''
  });
  const [submittingEditResource, setSubmittingEditResource] = useState(false);

  // Form Fields
  const [newResource, setNewResource] = useState({
    domain: 'uscis-sentinel',
    category: 'forms',
    resourceId: '',
    displayName: '',
    description: ''
  });



  const [mockNotification, setMockNotification] = useState({
    domain: 'uscis-sentinel',
    category: 'forms',
    resourceId: '',
    type: 'EDITION_UPDATE',
    severity: 'MEDIUM',
    summary: '',
    details: '',
    directEmail: '',
    reportUrl: '',
    deltaJson: '{\n  "version": "2026.05.28",\n  "status": "active"\n}'
  });

  // Action Pending loaders
  const [submittingResource, setSubmittingResource] = useState(false);
  const [submittingDispatch, setSubmittingDispatch] = useState(false);

  // Initial Data loading
  useEffect(() => {
    if (currentTeam) {
      loadResources();
      loadTeamRoutes();
      loadConsolidatedNotifications();
    }
  }, [currentTeam, user]);

  // Auto-select first route identifier when teamRoutes updates
  useEffect(() => {
    if (teamRoutes.length > 0) {
      setMockNotification(prev => ({
        ...prev,
        appName: teamRoutes[0]
      }));
    }
  }, [teamRoutes]);

  const loadResources = async () => {
    try {
      setLoadingResources(true);
      setError(null);
      const data = await resourceService.getAllResources();
      setResources(data || []);
    } catch (err) {
      console.error('Error loading resources:', err);
      setError('Failed to load registered resources.');
    } finally {
      setLoadingResources(false);
    }
  };

  const loadTeamRoutes = async () => {
    if (currentTeam?.id) {
      try {
        const response = await teamService.getTeamRoutes(currentTeam.id);
        if (response.success) {
          const whitelistedRoutes = (response.data || []).map(r => r.routeIdentifier).filter(Boolean);
          setTeamRoutes(whitelistedRoutes);
        } else {
          console.warn('Failed to load team routes from teamService in Resources page:', response.error);
        }
      } catch (routeErr) {
        console.error('Error loading team routes:', routeErr);
      }
    } else if (isSuperAdmin(user)) {
      try {
        const routesData = await apiRouteService.getAllRoutes();
        const whitelistedRoutes = (routesData || []).map(r => r.routeIdentifier).filter(Boolean);
        setTeamRoutes(whitelistedRoutes);
      } catch (routeErr) {
        console.error('Error loading all routes for superadmin:', routeErr);
      }
    }
  };

  const loadConsolidatedNotifications = async () => {
    const userId = user?.email || user?.username;
    if (!userId) return;

    try {
      setLoadingNotifications(true);
      // Fetch consolidated feed for the active user
      const [notifyLogs, unread] = await Promise.all([
        resourceService.getNotificationsForUser(userId),
        resourceService.countUnread(userId).catch(() => 0)
      ]);
      setNotifications(notifyLogs || []);
      setUnreadCount(unread || 0);
    } catch (err) {
      console.error('Error loading consolidated notifications:', err);
    } finally {
      setLoadingNotifications(false);
    }
  };

  // Metadata Registration Handler
  const handleCreateResource = async (e) => {
    e.preventDefault();
    if (!newResource.domain || !newResource.category || !newResource.resourceId || !newResource.displayName) {
      showErrorToast('Please populate all required fields.');
      return;
    }

    try {
      setSubmittingResource(true);
      await resourceService.upsertResource(newResource);
      showSuccessToast('Resource metadata registered successfully!');
      setShowCreateResourceModal(false);
      setNewResource({
        domain: 'uscis-sentinel',
        category: 'forms',
        resourceId: '',
        displayName: '',
        description: ''
      });
      loadResources();
    } catch (err) {
      console.error('Error upserting resource:', err);
      showErrorToast('Failed to register resource metadata.');
    } finally {
      setSubmittingResource(false);
    }
  };

  // Metadata Deletion Handler
  const handleDeleteResource = async (resource) => {
    if (!window.confirm(`Are you sure you want to delete ${resource.displayName}?`)) {
      return;
    }

    try {
      await resourceService.deleteResource(resource.domain, resource.category, resource.resourceId);
      showSuccessToast('Resource metadata deleted.');
      loadResources();
    } catch (err) {
      console.error('Error deleting resource:', err);
      const errMsg = err.response?.data || 'Failed to delete resource.';
      showErrorToast(typeof errMsg === 'string' ? errMsg : 'Failed to delete resource.');
    }
  };

  // Open Edit Resource Handler
  const handleOpenEditResource = (resource) => {
    setEditingResource({
      domain: resource.domain,
      category: resource.category,
      resourceId: resource.resourceId,
      displayName: resource.displayName || '',
      description: resource.description || ''
    });
    setShowEditResourceModal(true);
  };

  // Submit Edit Resource Handler
  const handleEditResourceSubmit = async (e) => {
    e.preventDefault();
    if (!editingResource.displayName) {
      showErrorToast('Display Name is a mandatory field.');
      return;
    }

    try {
      setSubmittingEditResource(true);
      await resourceService.upsertResource(editingResource);
      showSuccessToast('Resource metadata updated successfully!');
      setShowEditResourceModal(false);
      loadResources();
    } catch (err) {
      console.error('Error updating resource:', err);
      showErrorToast('Failed to update resource metadata.');
    } finally {
      setSubmittingEditResource(false);
    }
  };



  // Mark as Read Handler
  const handleMarkAsRead = async (notificationId) => {
    try {
      await resourceService.markAsRead(notificationId);
      showSuccessToast('Update marked as read.');
      loadConsolidatedNotifications();
    } catch (err) {
      console.error('Error marking as read:', err);
      showErrorToast('Failed to update notification state.');
    }
  };

  // Mock Notification Dispatch Handler
  const handleDispatchNotification = async (e) => {
    e.preventDefault();
    if (!mockNotification.domain || !mockNotification.category || !mockNotification.resourceId || !mockNotification.summary) {
      showErrorToast('Please provide all mandatory details.');
      return;
    }

    let parsedDelta = {};
    if (mockNotification.deltaJson.trim()) {
      try {
        parsedDelta = JSON.parse(mockNotification.deltaJson);
      } catch (err) {
        showErrorToast('Delta changes must be formatted as valid JSON.');
        return;
      }
    }

    const payload = {
      domain: mockNotification.domain,
      category: mockNotification.category,
      resourceId: mockNotification.resourceId,
      type: mockNotification.type,
      severity: mockNotification.severity,
      summary: mockNotification.summary,
      details: mockNotification.details,
      directEmail: mockNotification.directEmail || null,
      reportUrl: mockNotification.reportUrl || null,
      delta: parsedDelta
    };

    try {
      setSubmittingDispatch(true);
      await resourceService.dispatchNotification(payload);
      showSuccessToast('Mock notification update dispatched successfully!');
      setShowDispatchModal(false);
      
      // Reset dispatch state
      setMockNotification({
        domain: 'uscis-sentinel',
        category: 'forms',
        resourceId: '',
        type: 'EDITION_UPDATE',
        severity: 'MEDIUM',
        summary: '',
        details: '',
        directEmail: '',
        reportUrl: '',
        deltaJson: '{\n  "version": "2026.05.28",\n  "status": "active"\n}'
      });

      // Reload notifications consolidated feed
      loadConsolidatedNotifications();
    } catch (err) {
      console.error('Error dispatching notification:', err);
      showErrorToast('Failed to dispatch mock notification update.');
    } finally {
      setSubmittingDispatch(false);
    }
  };



  const handleSelectResourceForDispatch = (resKey) => {
    if (!resKey) return;
    const [domain, category, resourceId] = resKey.split('|');
    setMockNotification(prev => ({
      ...prev,
      domain,
      category,
      resourceId
    }));
  };

  // Helper to categorize registered resources
  const getCategorizedResources = () => {
    const categories = {
      forms: { title: 'Forms Edition Tracking', icon: <HiOutlineDocumentText className="me-2" />, items: [] },
      announcements: { title: 'Newsroom & Announcements Releases', icon: <HiOutlineSpeakerphone className="me-2" />, items: [] },
      'policy-manual': { title: 'Policy Manual Adjudication Updates', icon: <HiOutlineScale className="me-2" />, items: [] },
      'visa-bulletin': { title: 'Visa Bulletin Adjustment Charts', icon: <HiOutlineTrendingUp className="me-2" />, items: [] },
      'processing-times': { title: 'Estimated Processing Times Monitors', icon: <HiOutlineClock className="me-2" />, items: [] }
    };

    resources.forEach(res => {
      const cat = res.category || 'forms';
      if (!categories[cat]) {
        categories[cat] = { title: `${cat.charAt(0).toUpperCase() + cat.slice(1)} Monitors`, icon: <HiOutlineDocumentText className="me-2" />, items: [] };
      }
      categories[cat].items.push(res);
    });

    return categories;
  };

  const filteredNotifications = notifications.filter(notif => {
    if (filterCategory && notif.category !== filterCategory) return false;
    if (filterSeverity && notif.severity !== filterSeverity) return false;
    return true;
  });

  if (teamLoading || loadingResources) {
    return <LoadingSpinner />;
  }

  const categorizedResources = getCategorizedResources();

  return (
    <div className="resources-page">
      {/* Page Header */}
      <Card className="mb-4 mx-1 p-0">
        <Card.Header className="d-flex justify-content-between align-items-center bg-light">
          <Breadcrumb className="bg-light mb-0">
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/' }}>Home</Breadcrumb.Item>
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/organizations' }}>
              {currentTeam?.organization?.name || 'Organization'}
            </Breadcrumb.Item>
            <Breadcrumb.Item
              onClick={() => currentTeam?.id && navigate(`/teams/${currentTeam.id}`)}
              style={{ cursor: currentTeam?.id ? 'pointer' : 'default' }}
            >
              {currentTeam?.name || 'Team'}
            </Breadcrumb.Item>
            <Breadcrumb.Item active>Resources</Breadcrumb.Item>
          </Breadcrumb>

          <div className="d-flex gap-2">
            {canEditResource && (
              <>
                <Button variant="primary" onClick={() => setShowCreateResourceModal(true)}>
                  <HiPlus /> Register Resource
                </Button>
                <Button variant="outline-secondary" onClick={() => setShowDispatchModal(true)}>
                  <HiBell /> Mock Dispatch
                </Button>
              </>
            )}
            <Button variant="outline-primary" onClick={() => navigate('/resource-subscriptions')}>
              <HiOutlineOfficeBuilding className="me-1" /> Manage Subscriptions
            </Button>
          </div>
        </Card.Header>
      </Card>

      {error && (
        <Alert variant="danger" dismissible onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Tabs Layout */}
      <Tabs
        activeKey={activeTab}
        onSelect={(k) => setActiveTab(k)}
        className="mb-4"
      >
        {/* Tab 1: Registered Metadata Grouped Catalog */}
        <Tab eventKey="resources" title="Registered Resources">
          <Card className="resources-table-card border rounded p-3 mb-4">
            <h5 className="fw-semibold text-start mb-3">Registered Resources Registry</h5>
            <p className="text-muted text-start mb-4" style={{fontSize: '0.9rem'}}>
              This catalog lists external tracking resources and Sentinel cases registered globally on Linqra. 
              Toggle the sections below to review whitelisted forms, bulletins, and news releases.
            </p>

            {resources.length === 0 ? (
              <div className="text-center py-5">
                <i className="fas fa-server fa-3x text-muted mb-3"></i>
                <h5 className="text-muted">No resources registered</h5>
                <p className="text-muted">Register resource metadata to track dynamic external services.</p>
              </div>
            ) : (
              <Accordion defaultActiveKey="forms" alwaysOpen>
                {Object.keys(categorizedResources).map(catKey => {
                  const category = categorizedResources[catKey];
                  if (category.items.length === 0) return null;

                  return (
                    <Accordion.Item key={catKey} eventKey={catKey} className="mb-2 border rounded">
                      <Accordion.Header className="fw-semibold text-dark">
                        <div className="d-flex align-items-center">
                          {category.icon}
                          {category.title}
                          <Badge bg="dark" className="ms-2 small">{category.items.length} items</Badge>
                        </div>
                      </Accordion.Header>
                      <Accordion.Body className="p-0">
                        <Table hover responsive className="resources-table mb-0">
                          <thead className="bg-light">
                            <tr>
                              <th>Display Name</th>
                              <th>Domain</th>
                              <th>Resource ID</th>
                              <th>Description</th>
                              {canEditResource && <th>Actions</th>}
                            </tr>
                          </thead>
                          <tbody>
                            {category.items.map((resource, idx) => (
                              <tr key={idx}>
                                <td>
                                  <span className="fw-semibold text-dark">{resource.displayName}</span>
                                </td>
                                <td>
                                  <Badge bg="secondary">{resource.domain}</Badge>
                                </td>
                                <td>
                                  <code>{resource.resourceId}</code>
                                </td>
                                <td>
                                  <span className="text-muted small">{resource.description || 'No description provided'}</span>
                                </td>
                                {canEditResource && (
                                  <td>
                                    <div className="action-buttons d-flex gap-2">
                                      <Button
                                        variant="link"
                                        size="sm"
                                        className="text-primary p-0"
                                        onClick={() => handleOpenEditResource(resource)}
                                        title="Edit Resource"
                                      >
                                        <HiPencil />
                                      </Button>
                                      <Button
                                        variant="link"
                                        size="sm"
                                        className="text-danger p-0"
                                        onClick={() => handleDeleteResource(resource)}
                                        title="Delete Resource"
                                      >
                                        <HiTrash />
                                      </Button>
                                    </div>
                                  </td>
                                )}
                              </tr>
                            ))}
                          </tbody>
                        </Table>
                      </Accordion.Body>
                    </Accordion.Item>
                  );
                })}
              </Accordion>
            )}
          </Card>
        </Tab>



        {/* Tab 3: Update Logs & Notifications Feed */}
        <Tab eventKey="notifications" title={`Updates Feed [${unreadCount} Unread]`}>
          <Row>
            {/* Filter Section */}
            <Col lg={3} className="mb-4">
              <Card className="border rounded p-3 mb-3">
                <h6 className="fw-semibold mb-3">Feed Filters</h6>
                <Form.Group className="mb-3 text-start">
                  <Form.Label className="small fw-semibold text-muted">Category filter</Form.Label>
                  <Form.Select
                    value={filterCategory}
                    onChange={(e) => setFilterCategory(e.target.value)}
                  >
                    <option value="">All Categories</option>
                    <option value="forms">Forms Tracking</option>
                    <option value="announcements">Announcements</option>
                    <option value="policy-manual">Policy Manual</option>
                    <option value="visa-bulletin">Visa Bulletins</option>
                    <option value="processing-times">Processing Times</option>
                  </Form.Select>
                </Form.Group>

                <Form.Group className="mb-3 text-start">
                  <Form.Label className="small fw-semibold text-muted">Severity filter</Form.Label>
                  <Form.Select
                    value={filterSeverity}
                    onChange={(e) => setFilterSeverity(e.target.value)}
                  >
                    <option value="">All Severities</option>
                    <option value="HIGH">HIGH (Action required)</option>
                    <option value="MEDIUM">MEDIUM (Recommended)</option>
                    <option value="LOW">LOW (Informational)</option>
                  </Form.Select>
                </Form.Group>

                <div className="d-grid mt-2">
                  <Button 
                    variant="outline-secondary" 
                    onClick={loadConsolidatedNotifications}
                  >
                    <HiRefresh className="me-1" /> Sync Feed Updates
                  </Button>
                </div>
              </Card>
            </Col>

            {/* Notifications Feed */}
            <Col lg={9}>
              <Card className="border rounded p-3">
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <h6 className="fw-semibold mb-0">Consolidated Notification Alert Logs</h6>
                  <Badge bg="primary">{filteredNotifications.length} updates found</Badge>
                </div>

                {loadingNotifications ? (
                  <div className="text-center py-5">
                    <Spinner animation="border" />
                  </div>
                ) : filteredNotifications.length === 0 ? (
                  <div className="text-center py-5 text-muted">
                    <p className="mb-0">No matching notification alert logs found.</p>
                  </div>
                ) : (
                  <div className="notifications-feed-list">
                    {filteredNotifications.map((notif) => (
                      <Card 
                        key={notif.id} 
                        className={`notification-card p-3 border rounded ${notif.read ? 'read' : ''} severity-${notif.severity?.toLowerCase() || 'medium'}`}
                      >
                        <div className="notification-header">
                          <div className="d-flex align-items-center gap-2">
                            <span className="notification-title fw-bold text-dark">{notif.summary}</span>
                            <Badge bg={
                              notif.severity === 'HIGH' ? 'danger' :
                              notif.severity === 'MEDIUM' ? 'warning' : 'secondary'
                            }>
                              {notif.severity}
                            </Badge>
                            {!notif.read && <Badge bg="success">NEW</Badge>}
                          </div>
                          <span className="notification-meta text-muted small">{formatDate(notif.createdAt)}</span>
                        </div>
                        
                        <div className="notification-details text-start text-secondary mb-3" style={{whiteSpace: 'pre-line'}}>
                          {notif.details || 'No detailed analysis summary provided.'}
                        </div>

                        {/* --- DYNAMIC PREMIUM DELTA RENDERS --- */}

                        {/* Case A: Filing Fees Comparison */}
                        {notif.delta?.filingFees && (
                          <div className="fee-comparison-card p-3 my-2 border rounded bg-light mb-3">
                            <Row className="align-items-center">
                              <Col xs={5} className="text-center">
                                <div className="text-muted small">Previous Filing Fee</div>
                                <div className="fs-5 fw-bold text-secondary text-decoration-line-through">
                                  {notif.delta.filingFees.old || 'N/A'}
                                </div>
                              </Col>
                              <Col xs={2} className="text-center">
                                <span className="fs-3 text-warning">➔</span>
                              </Col>
                              <Col xs={5} className="text-center">
                                <div className="text-muted small">New Adjusted Fee</div>
                                <div className="fs-4 fw-bold text-danger">
                                  {notif.delta.filingFees.new || 'N/A'}
                                </div>
                              </Col>
                            </Row>
                          </div>
                        )}

                        {/* Case B: Structured Sub-Alerts Articles List */}
                        {notif.delta?.alerts && Array.isArray(notif.delta.alerts) && notif.delta.alerts.length > 0 && (
                          <div className="alerts-articles-list my-3 text-start">
                            <div className="text-muted small fw-semibold mb-2">
                              <HiTerminal className="me-1" /> USCIS News Updates Breakdown:
                            </div>
                            {notif.delta.alerts.map((art, idx) => (
                              <div key={idx} className="alert-article-item p-3 mb-2 border-start border-3 border-info bg-light rounded shadow-sm">
                                <div className="fw-semibold text-dark small">{art.title}</div>
                                <div className="text-muted mb-2" style={{fontSize: '0.75rem'}}>{art.date}</div>
                                <p className="mb-2 text-secondary" style={{fontSize: '0.85rem'}}>{art.summary}</p>
                                {art.url && (
                                  <a href={art.url} target="_blank" rel="noreferrer" className="text-decoration-none text-info small fw-bold">
                                    View official USCIS post ↗
                                  </a>
                                )}
                              </div>
                            ))}
                          </div>
                        )}

                        {/* Fallback JSON Delta Payload */}
                        {notif.delta && !notif.delta.filingFees && (!notif.delta.alerts || !Array.isArray(notif.delta.alerts)) && Object.keys(notif.delta).length > 0 && (
                          <div className="delta-viewer-box my-3 text-start">
                            <div className="text-muted small mb-1"><HiTerminal className="me-1" /> Dynamic Delta Changes Payload:</div>
                            <pre className="mb-0 text-light small" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                              {JSON.stringify(notif.delta, null, 2)}
                            </pre>
                          </div>
                        )}

                        <Row className="g-2 text-muted small align-items-center mt-3 pt-2 border-top">
                          <Col sm={6} className="text-start">
                            Type: <Badge bg="dark">{notif.type}</Badge> | App: <code>{notif.appName || 'komunas-app'}</code>
                          </Col>
                          <Col sm={6} className="text-sm-end">
                            {notif.reportUrl && (
                              <a href={notif.reportUrl} target="_blank" rel="noreferrer" className="btn btn-xs btn-outline-info p-1 py-0 me-2" style={{fontSize: '0.75rem'}}>
                                View Custom Report
                              </a>
                            )}
                            {!notif.read && (
                              <Button 
                                size="sm" 
                                variant="link" 
                                className="text-success p-0" 
                                onClick={() => handleMarkAsRead(notif.id)}
                              >
                                <HiCheck className="me-1" /> Mark as read
                              </Button>
                            )}
                          </Col>
                        </Row>
                      </Card>
                    ))}
                  </div>
                )}
              </Card>
            </Col>
          </Row>
        </Tab>
      </Tabs>

      {/* --- MODAL DIALOGS --- */}

      {/* Modal 1: Register Resource Metadata */}
      <Modal show={showCreateResourceModal} onHide={() => setShowCreateResourceModal(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title>Register Resource Metadata</Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleCreateResource}>
          <Modal.Body>
            <Form.Group className="mb-3">
              <Form.Label>Resource Display Name *</Form.Label>
              <Form.Control 
                type="text" 
                placeholder="e.g. Marriage Petition Tracking" 
                required
                value={newResource.displayName}
                onChange={(e) => setNewResource({...newResource, displayName: e.target.value})}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Domain *</Form.Label>
              <Form.Select 
                value={newResource.domain}
                onChange={(e) => setNewResource({...newResource, domain: e.target.value})}
              >
                <option value="uscis-sentinel">USCIS Sentinel (uscis-sentinel)</option>
              </Form.Select>
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Category *</Form.Label>
              <Form.Select 
                value={newResource.category}
                onChange={(e) => setNewResource({...newResource, category: e.target.value})}
              >
                <option value="forms">Forms tracking (forms)</option>
                <option value="announcements">Announcements updates (announcements)</option>
                <option value="policy-manual">Policy manual adjudication (policy-manual)</option>
                <option value="visa-bulletin">Visa bulletin filing charts (visa-bulletin)</option>
                <option value="processing-times">Estimated processing times (processing-times)</option>
              </Form.Select>
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Resource ID *</Form.Label>
              <Form.Control 
                type="text" 
                placeholder="e.g. I-485" 
                required
                value={newResource.resourceId}
                onChange={(e) => setNewResource({...newResource, resourceId: e.target.value})}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Description</Form.Label>
              <Form.Control 
                as="textarea" 
                rows={3}
                placeholder="Details of what this resource covers..." 
                value={newResource.description}
                onChange={(e) => setNewResource({...newResource, description: e.target.value})}
              />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="outline-secondary" onClick={() => setShowCreateResourceModal(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submittingResource}>
              {submittingResource ? 'Registering...' : 'Register Resource'}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>



      {/* Modal 3: Mock/Developer Notification Dispatch */}
      <Modal show={showDispatchModal} onHide={() => setShowDispatchModal(false)} size="lg" centered>
        <Modal.Header closeButton>
          <Modal.Title>Trigger Mock Notification Dispatch</Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleDispatchNotification}>
          <Modal.Body>
            <Alert variant="warning" className="small">
              <strong>Developer Notice:</strong> This triggers an automated dispatch sequence on the backend, pushing real-time notification alerts to all users or teams currently subscribed to this resource details envelope.
            </Alert>

            {resources.length > 0 && (
              <Form.Group className="mb-3">
                <Form.Label>Autofill from Registered Resources</Form.Label>
                <Form.Select 
                  onChange={(e) => handleSelectResourceForDispatch(e.target.value)}
                  defaultValue=""
                >
                  <option value="">-- Or type details manually below --</option>
                  {resources.map((res, index) => (
                    <option key={index} value={`${res.domain}|${res.category}|${res.resourceId}`}>
                      {res.displayName} ({res.domain} - {res.resourceId})
                    </option>
                  ))}
                </Form.Select>
              </Form.Group>
            )}

            <Row>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Domain *</Form.Label>
                  <Form.Select 
                    value={mockNotification.domain}
                    onChange={(e) => setMockNotification({...mockNotification, domain: e.target.value})}
                  >
                    <option value="uscis-sentinel">USCIS Sentinel (uscis-sentinel)</option>
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Category *</Form.Label>
                  <Form.Select 
                    value={mockNotification.category}
                    onChange={(e) => setMockNotification({...mockNotification, category: e.target.value})}
                  >
                    <option value="forms">Forms tracking (forms)</option>
                    <option value="announcements">Announcements updates (announcements)</option>
                    <option value="policy-manual">Policy manual adjudication (policy-manual)</option>
                    <option value="visa-bulletin">Visa bulletin filing charts (visa-bulletin)</option>
                    <option value="processing-times">Estimated processing times (processing-times)</option>
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Resource ID *</Form.Label>
                  <Form.Control 
                    type="text" 
                    placeholder="e.g. I-485" 
                    required
                    value={mockNotification.resourceId}
                    onChange={(e) => setMockNotification({...mockNotification, resourceId: e.target.value})}
                  />
                </Form.Group>
              </Col>
            </Row>

            <Row>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Notification Type *</Form.Label>
                  <Form.Select
                    value={mockNotification.type}
                    onChange={(e) => setMockNotification({...mockNotification, type: e.target.value})}
                  >
                    <option value="EDITION_UPDATE">EDITION_UPDATE (Case file releases)</option>
                    <option value="FEE_CHANGE">FEE_CHANGE (Pricing adjust)</option>
                    <option value="POLICY_ALERT">POLICY_ALERT (Standard changes)</option>
                    <option value="NEWS_UPDATE">NEWS_UPDATE (General details)</option>
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Severity *</Form.Label>
                  <Form.Select
                    value={mockNotification.severity}
                    onChange={(e) => setMockNotification({...mockNotification, severity: e.target.value})}
                  >
                    <option value="LOW">LOW (Informational)</option>
                    <option value="MEDIUM">MEDIUM (Action recommended)</option>
                    <option value="HIGH">HIGH (Immediate audit required)</option>
                  </Form.Select>
                </Form.Group>
              </Col>
            </Row>

            <Form.Group className="mb-3">
              <Form.Label>Summary / Short Headline *</Form.Label>
              <Form.Control 
                type="text" 
                placeholder="e.g. Form I-485 filing fee increased to $1,440" 
                required
                value={mockNotification.summary}
                onChange={(e) => setMockNotification({...mockNotification, summary: e.target.value})}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Detailed Content (Markdown Supported)</Form.Label>
              <Form.Control 
                as="textarea" 
                rows={3}
                placeholder="Describe all key changes and structural impacts in detail..."
                value={mockNotification.details}
                onChange={(e) => setMockNotification({...mockNotification, details: e.target.value})}
              />
            </Form.Group>

            <Row>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Direct Email Target (Optional)</Form.Label>
                  <Form.Control 
                    type="email" 
                    placeholder="e.g. alert-sentinel@company.com" 
                    value={mockNotification.directEmail}
                    onChange={(e) => setMockNotification({...mockNotification, directEmail: e.target.value})}
                  />
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Custom Report URL Link (Optional)</Form.Label>
                  <Form.Control 
                    type="url" 
                    placeholder="e.g. https://uscis.gov/i-485-fee" 
                    value={mockNotification.reportUrl}
                    onChange={(e) => setMockNotification({...mockNotification, reportUrl: e.target.value})}
                  />
                </Form.Group>
              </Col>
            </Row>

            <Form.Group className="mb-3">
              <Form.Label>Dynamic Delta Changes Payload (JSON Format)</Form.Label>
              <Form.Control 
                as="textarea" 
                rows={4}
                style={{fontFamily: 'monospace', fontSize: '0.85rem'}}
                placeholder="{\n  &quot;oldFee&quot;: 1220,\n  &quot;newFee&quot;: 1440\n}"
                value={mockNotification.deltaJson}
                onChange={(e) => setMockNotification({...mockNotification, deltaJson: e.target.value})}
              />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="outline-secondary" onClick={() => setShowDispatchModal(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submittingDispatch}>
              {submittingDispatch ? 'Dispatching...' : 'Dispatch Notification'}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>

      {/* Modal 4: Edit Resource Metadata */}
      <Modal show={showEditResourceModal} onHide={() => setShowEditResourceModal(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title>Edit Resource Metadata</Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleEditResourceSubmit}>
          <Modal.Body>
            <Form.Group className="mb-3">
              <Form.Label>Domain</Form.Label>
              <Form.Control 
                type="text" 
                disabled 
                value={editingResource.domain}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Category</Form.Label>
              <Form.Control 
                type="text" 
                disabled 
                value={editingResource.category}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Resource ID</Form.Label>
              <Form.Control 
                type="text" 
                disabled 
                value={editingResource.resourceId}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Resource Display Name *</Form.Label>
              <Form.Control 
                type="text" 
                placeholder="e.g. Marriage Petition Tracking" 
                required
                value={editingResource.displayName}
                onChange={(e) => setEditingResource({...editingResource, displayName: e.target.value})}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Description</Form.Label>
              <Form.Control 
                as="textarea" 
                rows={3}
                placeholder="Details of what this resource covers..." 
                value={editingResource.description}
                onChange={(e) => setEditingResource({...editingResource, description: e.target.value})}
              />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="outline-secondary" onClick={() => setShowEditResourceModal(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submittingEditResource}>
              {submittingEditResource ? 'Updating...' : 'Update Resource'}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>

      <Footer />
    </div>
  );
}

export default Resources;
