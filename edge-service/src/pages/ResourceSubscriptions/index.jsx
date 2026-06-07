import React, { useState, useEffect } from 'react';
import { Card, Breadcrumb, Table, Badge, Form, Row, Col, Alert, Spinner, Modal } from 'react-bootstrap';
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
import { 
  HiPlus, 
  HiTrash, 
  HiRefresh,
  HiUser,
  HiOutlineOfficeBuilding, 
  HiMail, 
  HiGlobeAlt
} from 'react-icons/hi';
import './styles.css';
import Footer from '../../components/common/Footer';
import ConfirmationModal from '../../components/common/ConfirmationModal';

function ResourceSubscriptions() {
  const { currentTeam, loading: teamLoading } = useTeam();
  const { user } = useAuth();
  const navigate = useNavigate();

  // Role Gate
  const canEditSubscription = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);

  // States
  const [resources, setResources] = useState([]);
  const [personalSubscriptions, setPersonalSubscriptions] = useState([]);
  const [teamSubscriptions, setTeamSubscriptions] = useState([]);
  const [teamRoutes, setTeamRoutes] = useState([]);
  
  const [loadingSubscriptions, setLoadingSubscriptions] = useState(true);
  const [error, setError] = useState(null);

  // Modal Toggle
  const [showSubscribeModal, setShowSubscribeModal] = useState(false);
  const [subToDelete, setSubToDelete] = useState(null);
  const [deletingSub, setDeletingSub] = useState(false);

  // Form Fields
  const [newSubscription, setNewSubscription] = useState({
    domain: 'uscis-sentinel',
    category: 'forms',
    resourceId: '',
    appName: 'komunas-app',
    type: 'personal', // 'personal' or 'team'
    emailEnabled: true,
    email: user?.email || '',
    webhookEnabled: false,
    webhookUrl: ''
  });

  // Action Pending loaders
  const [submittingSubscription, setSubmittingSubscription] = useState(false);

  // Initial Data loading
  useEffect(() => {
    if (currentTeam) {
      loadResources();
      loadSubscriptions();
    }
  }, [currentTeam, user]);

  // Auto-select first route identifier when teamRoutes updates
  useEffect(() => {
    if (teamRoutes.length > 0) {
      setNewSubscription(prev => ({
        ...prev,
        appName: teamRoutes[0]
      }));
    }
  }, [teamRoutes]);

  const loadResources = async () => {
    try {
      const data = await resourceService.getAllResources();
      setResources(data || []);
    } catch (err) {
      console.error('Error loading resources for autofill:', err);
    }
  };

  const loadSubscriptions = async () => {
    try {
      setLoadingSubscriptions(true);
      setError(null);
      
      let whitelistedRoutes = [];
      if (currentTeam?.id) {
        try {
          console.log('Fetching team routes for currentTeam.id:', currentTeam.id);
          const response = await teamService.getTeamRoutes(currentTeam.id);
          if (response.success) {
            whitelistedRoutes = (response.data || []).map(r => r.routeIdentifier).filter(Boolean);
            console.log('Resolved team route identifiers:', whitelistedRoutes);
            setTeamRoutes(whitelistedRoutes);
          } else {
            console.warn('Failed to load team routes from teamService:', response.error);
          }
        } catch (routeErr) {
          console.error('Error loading team routes for subscriptions filter:', routeErr);
        }
      } else if (isSuperAdmin(user)) {
        try {
          console.log('User is superadmin, fetching all routes...');
          const routesData = await apiRouteService.getAllRoutes();
          whitelistedRoutes = (routesData || []).map(r => r.routeIdentifier).filter(Boolean);
          console.log('Resolved all route identifiers for superadmin:', whitelistedRoutes);
          setTeamRoutes(whitelistedRoutes);
        } catch (routeErr) {
          console.error('Error loading all routes for superadmin:', routeErr);
        }
      }

      // Query both user personal and team wide subscriptions concurrently
      const [personalSubs, teamSubs] = await Promise.all([
        resourceService.getMySubscriptions(),
        resourceService.getTeamSubscriptions()
      ]);
      
      console.log('All fetched personal subscriptions:', personalSubs);
      console.log('All fetched team subscriptions:', teamSubs);

      // Filter subscriptions to show only those belonging to the team's whitelisted route identifiers
      const filteredPersonal = (personalSubs || []).filter(sub => whitelistedRoutes.includes(sub.appName));
      const filteredTeam = (teamSubs || []).filter(sub => whitelistedRoutes.includes(sub.appName));
      
      console.log('Filtered personal subscriptions:', filteredPersonal);
      console.log('Filtered team subscriptions:', filteredTeam);

      setPersonalSubscriptions(filteredPersonal);
      setTeamSubscriptions(filteredTeam);
    } catch (err) {
      console.error('Error loading subscriptions:', err);
      setError('Failed to load active resource subscriptions.');
    } finally {
      setLoadingSubscriptions(false);
    }
  };

  // Subscription Creation Handler
  const handleSubscribe = async (e) => {
    e.preventDefault();
    if (!newSubscription.domain || !newSubscription.category || !newSubscription.resourceId) {
      showErrorToast('Resource details are mandatory fields.');
      return;
    }

    if (newSubscription.emailEnabled && !newSubscription.email) {
      showErrorToast('Please provide a target delivery email.');
      return;
    }

    if (newSubscription.webhookEnabled && !newSubscription.webhookUrl) {
      showErrorToast('Please provide a target webhook delivery URL.');
      return;
    }

    const payload = {
      userId: user?.email || user?.username || 'unknown',
      domain: newSubscription.domain,
      category: newSubscription.category,
      resourceId: newSubscription.resourceId,
      appName: newSubscription.appName,
      delivery: {
        emailEnabled: newSubscription.emailEnabled,
        email: newSubscription.emailEnabled ? newSubscription.email : null,
        webhookEnabled: newSubscription.webhookEnabled,
        webhookUrl: newSubscription.webhookEnabled ? newSubscription.webhookUrl : null
      }
    };

    try {
      setSubmittingSubscription(true);
      if (newSubscription.type === 'team') {
        await resourceService.subscribeTeam(payload);
      } else {
        await resourceService.subscribeUser(payload);
      }
      showSuccessToast('Successfully registered new resource subscription!');
      setShowSubscribeModal(false);
      
      // Reset Subscription state
      setNewSubscription({
        domain: 'uscis-sentinel',
        category: 'forms',
        resourceId: '',
        appName: teamRoutes[0] || 'komunas-app',
        type: 'personal',
        emailEnabled: true,
        email: user?.email || '',
        webhookEnabled: false,
        webhookUrl: ''
      });
      loadSubscriptions();
    } catch (err) {
      console.error('Error subscribing:', err);
      showErrorToast('Failed to create resource subscription.');
    } finally {
      setSubmittingSubscription(false);
    }
  };

  // Unsubscribe handlers
  const handleUnsubscribe = (sub, type) => {
    setSubToDelete({ ...sub, type });
  };

  const handleConfirmUnsubscribe = async () => {
    if (!subToDelete) return;
    try {
      setDeletingSub(true);
      await resourceService.unsubscribe(subToDelete.id);
      showSuccessToast('Resource unsubscribed successfully.');
      loadSubscriptions();
    } catch (err) {
      console.error('Error unsubscribing:', err);
      showErrorToast('Failed to cancel subscription.');
    } finally {
      setSubToDelete(null);
      setDeletingSub(false);
    }
  };

  const getResourceDisplayName = (sub) => {
    if (!sub) return '';
    const meta = resources.find(r => r.domain === sub.domain && r.category === sub.category && r.resourceId === sub.resourceId);
    return meta?.displayName || sub.resourceId;
  };

  // Autofill helpers for subscribing
  const handleSelectResourceForSubscription = (resKey) => {
    if (!resKey) return;
    const [domain, category, resourceId] = resKey.split('|');
    setNewSubscription(prev => ({
      ...prev,
      domain,
      category,
      resourceId
    }));
  };

  if (teamLoading || loadingSubscriptions) {
    return <LoadingSpinner />;
  }

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
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/resources' }}>Resources</Breadcrumb.Item>
            <Breadcrumb.Item active>Subscriptions</Breadcrumb.Item>
          </Breadcrumb>

          <div className="d-flex gap-2">
            <Button variant="outline-secondary" onClick={() => navigate('/resources')}>
              View Registered Resources
            </Button>
            <Button variant="primary" onClick={() => setShowSubscribeModal(true)}>
              <HiPlus /> Create Subscription
            </Button>
          </div>
        </Card.Header>
      </Card>

      {error && (
        <Alert variant="danger">
          {error}
        </Alert>
      )}

      {/* Main Content */}
      <Card className="resources-table-card border rounded p-3 mb-4">
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h5 className="fw-semibold text-start mb-0">Unified Active Subscriptions</h5>
          <Button size="sm" variant="outline-primary" onClick={loadSubscriptions}>
            <HiRefresh className="me-1" /> Refresh Subscriptions
          </Button>
        </div>
        <p className="text-muted text-start mb-4" style={{fontSize: '0.9rem'}}>
          This dashboard lists active subscriptions associated with your team's application routing identifiers. 
          Only subscriptions matching active route configurations (like <code>komunas-app</code>) are displayed.
        </p>

        {personalSubscriptions.length === 0 && teamSubscriptions.length === 0 ? (
          <div className="text-center py-5">
            <i className="fas fa-rss fa-3x text-muted mb-3"></i>
            <h5 className="text-muted">No subscriptions registered</h5>
            <p className="text-muted">Subscribe to resources to trigger automated case logs and notifications.</p>
          </div>
        ) : (
          <Table hover responsive className="resources-table mb-0">
            <thead className="bg-light">
              <tr>
                <th>Resource / Display Name</th>
                <th>Domain</th>
                <th>Category</th>
                <th>Type</th>
                <th>App Listener</th>
                <th>Delivery Configurations</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {/* Personal Subscriptions */}
              {personalSubscriptions.map((sub) => (
                <tr key={sub.id}>
                  <td>
                    {(() => {
                      const meta = resources.find(r => r.domain === sub.domain && r.category === sub.category && r.resourceId === sub.resourceId);
                      return (
                        <div className="d-flex flex-column text-start">
                          <span className="fw-semibold text-dark">{meta?.displayName || sub.resourceId}</span>
                          {meta?.description ? (
                            <small className="text-muted text-truncate" style={{maxWidth: '320px'}} title={meta.description}>
                              {meta.description}
                            </small>
                          ) : (
                            <small className="text-muted"><code>{sub.resourceId}</code></small>
                          )}
                        </div>
                      );
                    })()}
                  </td>
                  <td>
                    <Badge bg="secondary">{sub.domain}</Badge>
                  </td>
                  <td>
                    <Badge bg="info">{sub.category}</Badge>
                  </td>
                  <td>
                    <Badge bg="dark" className="d-flex align-items-center gap-1 w-fit-content" style={{width: 'fit-content'}}>
                      <HiUser /> Personal
                    </Badge>
                  </td>
                  <td>
                    <code>{sub.appName || 'linqra-agent'}</code>
                  </td>
                  <td>
                    <div className="d-flex flex-column gap-1">
                      {sub.delivery?.emailEnabled ? (
                        <small className="text-success d-flex align-items-center gap-1">
                          <HiMail /> Email: <code>{sub.delivery.email || sub.delivery.overrideEmail}</code>
                        </small>
                      ) : (
                        <small className="text-muted d-flex align-items-center gap-1">
                          <HiMail /> Email disabled
                        </small>
                      )}
                      {sub.delivery?.webhookEnabled ? (
                        <small className="text-success d-flex align-items-center gap-1">
                          <HiGlobeAlt /> Webhook: <code>{sub.delivery.webhookUrl}</code>
                        </small>
                      ) : (
                        <small className="text-muted d-flex align-items-center gap-1">
                          <HiGlobeAlt /> Webhook disabled
                        </small>
                      )}
                    </div>
                  </td>
                  <td>
                    <div className="action-buttons">
                      <Button
                        variant="link"
                        size="sm"
                        className="text-danger p-0"
                        onClick={() => handleUnsubscribe(sub, 'personal')}
                        title="Unsubscribe"
                      >
                        <HiTrash /> Cancel
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}

              {/* Team Subscriptions */}
              {teamSubscriptions.map((sub) => (
                <tr key={sub.id}>
                  <td>
                    {(() => {
                      const meta = resources.find(r => r.domain === sub.domain && r.category === sub.category && r.resourceId === sub.resourceId);
                      return (
                        <div className="d-flex flex-column text-start">
                          <span className="fw-semibold text-dark">{meta?.displayName || sub.resourceId}</span>
                          {meta?.description ? (
                            <small className="text-muted text-truncate" style={{maxWidth: '320px'}} title={meta.description}>
                              {meta.description}
                            </small>
                          ) : (
                            <small className="text-muted"><code>{sub.resourceId}</code></small>
                          )}
                        </div>
                      );
                    })()}
                  </td>
                  <td>
                    <Badge bg="secondary">{sub.domain}</Badge>
                  </td>
                  <td>
                    <Badge bg="info">{sub.category}</Badge>
                  </td>
                  <td>
                    <Badge bg="warning" className="d-flex align-items-center gap-1 text-dark" style={{width: 'fit-content'}}>
                      <HiOutlineOfficeBuilding /> Team-wide
                    </Badge>
                  </td>
                  <td>
                    <code>{sub.appName || 'komunas-app'}</code>
                  </td>
                  <td>
                    <div className="d-flex flex-column gap-1">
                      {sub.delivery?.emailEnabled ? (
                        <small className="text-success d-flex align-items-center gap-1">
                          <HiMail /> Email: <code>{sub.delivery.email}</code>
                        </small>
                      ) : (
                        <small className="text-muted d-flex align-items-center gap-1">
                          <HiMail /> Email disabled
                        </small>
                      )}
                      {sub.delivery?.webhookEnabled ? (
                        <small className="text-success d-flex align-items-center gap-1">
                          <HiGlobeAlt /> Webhook: <code>{sub.delivery.webhookUrl}</code>
                        </small>
                      ) : (
                        <small className="text-muted d-flex align-items-center gap-1">
                          <HiGlobeAlt /> Webhook disabled
                        </small>
                      )}
                    </div>
                  </td>
                  <td>
                    <div className="action-buttons">
                      <Button
                        variant="link"
                        size="sm"
                        className="text-danger p-0"
                        onClick={() => handleUnsubscribe(sub, 'team')}
                        title="Unsubscribe"
                      >
                        <HiTrash /> Cancel
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      {/* --- MODAL DIALOGS --- */}

      {/* Modal: Create Personal or Team Subscription */}
      <Modal show={showSubscribeModal} onHide={() => setShowSubscribeModal(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title>Subscribe to Tracking Resource</Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleSubscribe}>
          <Modal.Body>
            <Form.Group className="mb-3">
              <Form.Label>Subscription Scope Type *</Form.Label>
              <Form.Select
                value={newSubscription.type}
                onChange={(e) => setNewSubscription({...newSubscription, type: e.target.value})}
              >
                <option value="personal">Personal subscription (bound to your login email)</option>
                <option value="team">Team-wide subscription (accessible to all members)</option>
              </Form.Select>
            </Form.Group>

            {resources.length > 0 && (
              <Form.Group className="mb-3">
                <Form.Label>Autofill from Registered Resources</Form.Label>
                <Form.Select 
                  onChange={(e) => handleSelectResourceForSubscription(e.target.value)}
                  defaultValue=""
                >
                  <option value="">-- Or type details below --</option>
                  {resources.map((res, index) => (
                    <option key={index} value={`${res.domain}|${res.category}|${res.resourceId}`}>
                      {res.displayName} ({res.domain} - {res.resourceId})
                    </option>
                  ))}
                </Form.Select>
              </Form.Group>
            )}

            <Form.Group className="mb-3">
              <Form.Label>Domain *</Form.Label>
              <Form.Select 
                value={newSubscription.domain}
                onChange={(e) => setNewSubscription({...newSubscription, domain: e.target.value})}
              >
                <option value="uscis-sentinel">USCIS Sentinel (uscis-sentinel)</option>
              </Form.Select>
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Category *</Form.Label>
              <Form.Select 
                value={newSubscription.category}
                onChange={(e) => setNewSubscription({...newSubscription, category: e.target.value})}
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
                value={newSubscription.resourceId}
                onChange={(e) => setNewSubscription({...newSubscription, resourceId: e.target.value})}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>App Listener / App Name *</Form.Label>
              {teamRoutes.length > 0 ? (
                <Form.Select
                  required
                  value={newSubscription.appName}
                  onChange={(e) => setNewSubscription({...newSubscription, appName: e.target.value})}
                >
                  {teamRoutes.map((routeId, idx) => (
                    <option key={idx} value={routeId}>{routeId}</option>
                  ))}
                </Form.Select>
              ) : (
                <Form.Control 
                  type="text" 
                  required
                  placeholder="e.g. komunas-app"
                  value={newSubscription.appName}
                  onChange={(e) => setNewSubscription({...newSubscription, appName: e.target.value})}
                />
              )}
            </Form.Group>

            <hr />
            <h6 className="fw-semibold">Delivery Target Configuration</h6>

            {/* Email configuration */}
            <Form.Group className="mb-3">
              <Form.Check 
                type="checkbox" 
                id="emailEnabled" 
                label="Enable Email Notifications"
                checked={newSubscription.emailEnabled}
                onChange={(e) => setNewSubscription({...newSubscription, emailEnabled: e.target.checked})}
              />
            </Form.Group>
            
            {newSubscription.emailEnabled && (
              <Form.Group className="mb-3">
                <Form.Label>Delivery Target Email *</Form.Label>
                <Form.Control 
                  type="email" 
                  placeholder="e.g. notifications@company.com" 
                  required
                  value={newSubscription.email}
                  onChange={(e) => setNewSubscription({...newSubscription, email: e.target.value})}
                />
              </Form.Group>
            )}

            {/* Webhook configuration */}
            <Form.Group className="mb-3">
              <Form.Check 
                type="checkbox" 
                id="webhookEnabled" 
                label="Enable Webhook Payload Submissions"
                checked={newSubscription.webhookEnabled}
                onChange={(e) => setNewSubscription({...newSubscription, webhookEnabled: e.target.checked})}
              />
            </Form.Group>

            {newSubscription.webhookEnabled && (
              <Form.Group className="mb-3">
                <Form.Label>Target Webhook URL *</Form.Label>
                <Form.Control 
                  type="url" 
                  placeholder="e.g. https://api.linqra.com/webhooks/sentinel" 
                  required
                  value={newSubscription.webhookUrl}
                  onChange={(e) => setNewSubscription({...newSubscription, webhookUrl: e.target.value})}
                />
              </Form.Group>
            )}
          </Modal.Body>
          <Modal.Footer>
            <Button variant="outline-secondary" onClick={() => setShowSubscribeModal(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submittingSubscription}>
              {submittingSubscription ? 'Subscribing...' : 'Register Subscription'}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>

      {/* Confirmation Modal for Unsubscribing */}
      <ConfirmationModal
        show={subToDelete !== null}
        onHide={() => setSubToDelete(null)}
        onConfirm={handleConfirmUnsubscribe}
        title="Cancel Subscription"
        message={subToDelete ? (
          <div>
            <p>Are you sure you want to cancel this resource subscription?</p>
            <ul className="mb-0">
              <li><strong>Resource:</strong> {getResourceDisplayName(subToDelete)}</li>
              <li><strong>Delivery Method:</strong> {(() => {
                const delivery = subToDelete.delivery;
                if (!delivery) return 'None';
                const parts = [];
                if (delivery.emailEnabled) {
                  parts.push(`Email (${delivery.email || delivery.overrideEmail || subToDelete.userId || ''})`);
                }
                if (delivery.webhookEnabled) {
                  parts.push(`Webhook (${delivery.webhookUrl || ''})`);
                }
                return parts.join(', ') || 'None';
              })()}</li>
              <li><strong>App Listener:</strong> <code>{subToDelete.appName || 'linqra-agent'}</code></li>
            </ul>
          </div>
        ) : ''}
        confirmLabel={deletingSub ? "Canceling..." : "Yes, Cancel"}
        cancelLabel="No"
        variant="danger"
        disabled={deletingSub}
      />

      <Footer />
    </div>
  );
}

export default ResourceSubscriptions;
