import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../../utils/roleUtils';
import workflowService from '../../../services/workflowService';
import { teamService } from '../../../services/teamService';
import './styles.css';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import ExecuteConfirmationModal from '../../../components/workflows/ExecuteConfirmationModal';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { Form, Card, Spinner, Badge, Modal, Row, Col, OverlayTrigger, Tooltip } from 'react-bootstrap';
import Button from '../../../components/common/Button';
import { format } from 'date-fns';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, Legend, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import ExecutionDetailsModal from '../../../components/workflows/ExecutionDetailsModal';
import { HiPlay } from 'react-icons/hi';

function EditWorkflow() {
    const { workflowId } = useParams();
    const navigate = useNavigate();
    const { currentTeam, loading: teamLoading, selectedTeam } = useTeam();
    const { user } = useAuth();
    const [workflow, setWorkflow] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [versions, setVersions] = useState([]);
    const [showConfirmModal, setShowConfirmModal] = useState(false);
    const [showRollbackModal, setShowRollbackModal] = useState(false);
    const [showCompareModal, setShowCompareModal] = useState(false);
    const [selectedVersion, setSelectedVersion] = useState(null);
    const [compareVersions, setCompareVersions] = useState({ version1: '', version2: '' });
    const [saving, setSaving] = useState(false);
    const [showMetadataModal, setShowMetadataModal] = useState(false);
    const [teamDetails, setTeamDetails] = useState(null);
    const [stats, setStats] = useState(null);
    const [loadingExecutions, setLoadingExecutions] = useState(true);
    const [executions, setExecutions] = useState([]);
    const [showExecutionModal, setShowExecutionModal] = useState(false);
    const [selectedExecution, setSelectedExecution] = useState(null);
    const [showExecuteConfirm, setShowExecuteConfirm] = useState(false);
    const [executing, setExecuting] = useState(false);

    console.log('Debug - User:', user);
    console.log('Debug - Current Team:', currentTeam);
    console.log('Debug - Selected Team:', selectedTeam);
    console.log('Debug - Is Super Admin:', isSuperAdmin(user));
    console.log('Debug - Has Admin Access:', hasAdminAccess(user, currentTeam));

    const canEditWorkflow = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    console.log('Debug - Can Edit Workflow:', canEditWorkflow);

    const canAccessWorkflow = 
        // If workflow is private
        (!workflow?.public && (isSuperAdmin(user) || hasAdminAccess(user, currentTeam))) ||
        // If workflow is public
        (workflow?.public && (isSuperAdmin(user) || hasAdminAccess(user, currentTeam) || currentTeam?.id === workflow?.teamId));

    const canExecuteWorkflow = workflow?.public || currentTeam?.id === workflow?.teamId;

    useEffect(() => {
        if (currentTeam) {
            loadWorkflow();
            loadVersions();
            loadStats();
            loadExecutions();
        }
    }, [currentTeam]);

    useEffect(() => {
        if (workflow?.teamId) {
            loadTeamDetails();
        }
    }, [workflow?.teamId]);

    useEffect(() => {
        if (workflow && !canAccessWorkflow) {
            navigate('/workflows');
            showErrorToast('You do not have access to this workflow');
        }
    }, [workflow, canAccessWorkflow]);

    const loadWorkflow = async () => {
        try {
            setLoading(true);
            const response = await workflowService.getWorkflowById(workflowId);
            if (response.success) {
                setWorkflow(response.data);
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load workflow');
            console.error('Error loading workflow:', err);
        } finally {
            setLoading(false);
        }
    };

    const loadVersions = async () => {
        try {
            const response = await workflowService.getWorkflowVersions(workflowId);
            if (response.success) {
                setVersions(response.data);
            }
        } catch (err) {
            console.error('Error loading versions:', err);
        }
    };

    const loadTeamDetails = async () => {
        try {
            const response = await teamService.getTeam(workflow.teamId);
            if (response.success) {
                setTeamDetails(response.data);
            }
        } catch (err) {
            console.error('Error loading team details:', err);
        }
    };

    const loadStats = async () => {
        try {
            const response = await workflowService.getWorkflowStats(workflowId);
            if (response.success) {
                setStats(response.data);
            }
        } catch (err) {
            console.error('Error loading stats:', err);
        }
    };

    const loadExecutions = async () => {
        try {
            setLoadingExecutions(true);
            const response = await workflowService.getWorkflowExecutions(workflowId);
            if (response.success) {
                setExecutions(response.data);
            }
        } catch (err) {
            console.error('Error loading executions:', err);
        } finally {
            setLoadingExecutions(false);
        }
    };

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        
        if (name === 'request') {
            try {
                // Try to parse the JSON input
                const parsedJson = JSON.parse(value);
                setWorkflow(prev => ({
                    ...prev,
                    request: parsedJson
                }));
            } catch (err) {
                // If parsing fails, just update the raw value
                setWorkflow(prev => ({
                    ...prev,
                    request: value
                }));
            }
        } else {
            setWorkflow(prev => ({
                ...prev,
                [name]: value
            }));
        }
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            // Ensure request is properly stringified if it's an object
            const workflowToSave = {
                ...workflow,
                request: typeof workflow.request === 'object' ? workflow.request : JSON.parse(workflow.request)
            };
            const response = await workflowService.createNewVersion(workflowId, workflowToSave);
            if (response.success) {
                showSuccessToast('Workflow updated successfully');
                loadWorkflow();
                loadVersions();
            } else {
                showErrorToast(response.error || 'Failed to update workflow');
            }
        } catch (err) {
            showErrorToast('Failed to update workflow');
            console.error('Error updating workflow:', err);
        } finally {
            setSaving(false);
            setShowConfirmModal(false);
        }
    };

    const handleRollbackClick = (version) => {
        setSelectedVersion(version);
        setShowRollbackModal(true);
    };

    const handleRollback = async () => {
        if (!selectedVersion) return;

        try {
            setSaving(true);
            const response = await workflowService.rollbackToVersion(workflowId, selectedVersion.id);
            if (response.success) {
                showSuccessToast('Rolled back to previous version');
                loadWorkflow();
                loadVersions();
            } else {
                showErrorToast(response.error || 'Failed to rollback version');
            }
        } catch (err) {
            showErrorToast('Failed to rollback version');
            console.error('Error rolling back version:', err);
        } finally {
            setSaving(false);
            setShowRollbackModal(false);
            setSelectedVersion(null);
        }
    };

    const handleCompareClick = (version) => {
        setCompareVersions({
            version1: workflow?.version,
            version2: version.version
        });
        setShowCompareModal(true);
    };

    const formatDate = (date) => {
        if (!date) return 'N/A';
        try {
            let dateObj;
            if (Array.isArray(date)) {
                // Handle array format [year, month, day, hour, minute, second, nanoseconds]
                dateObj = new Date(date[0], date[1] - 1, date[2], date[3], date[4], date[5]);
            } else {
                dateObj = new Date(date);
            }
            if (isNaN(dateObj.getTime())) return 'N/A';
            return format(dateObj, 'MMM d, yyyy HH:mm');
        } catch (err) {
            console.error('Error formatting date:', err);
            return 'N/A';
        }
    };

    const highlightDifferences = (obj1, obj2, path = '') => {
        if (!obj1 || !obj2) return {};
        
        const result = {};
        
        // Handle arrays
        if (Array.isArray(obj1) && Array.isArray(obj2)) {
            obj1.forEach((item, index) => {
                if (index < obj2.length) {
                    const nestedDiffs = highlightDifferences(item, obj2[index], `${path}[${index}]`);
                    Object.assign(result, nestedDiffs);
                }
            });
            return result;
        }
        
        // Handle objects
        if (typeof obj1 === 'object' && typeof obj2 === 'object') {
            const allKeys = new Set([...Object.keys(obj1), ...Object.keys(obj2)]);
            
            allKeys.forEach(key => {
                const currentPath = path ? `${path}.${key}` : key;
                
                // If both values are objects, compare them recursively
                if (obj1[key] && obj2[key] && 
                    typeof obj1[key] === 'object' && typeof obj2[key] === 'object') {
                    const nestedDiffs = highlightDifferences(obj1[key], obj2[key], currentPath);
                    Object.assign(result, nestedDiffs);
                } 
                // If values are different, mark this path as different
                else if (JSON.stringify(obj1[key]) !== JSON.stringify(obj2[key])) {
                    result[currentPath] = true;
                }
            });
            
            return result;
        }
        
        // For primitive values, compare directly
        if (JSON.stringify(obj1) !== JSON.stringify(obj2)) {
            result[path] = true;
        }
        
        return result;
    };

    const renderJsonWithHighlights = (obj, differences, path = '') => {
        if (!obj) return null;
        
        if (Array.isArray(obj)) {
            return (
                <div>
                    [
                    <div style={{ marginLeft: '20px' }}>
                        {obj.map((item, index) => (
                            <div key={index}>
                                {renderJsonWithHighlights(item, differences, `${path}[${index}]`)}
                                {index < obj.length - 1 && ','}
                            </div>
                        ))}
                    </div>
                    ]
                </div>
            );
        }
        
        if (typeof obj === 'object' && obj !== null) {
            return (
                <div>
                    {'{'}
                    <div style={{ marginLeft: '20px' }}>
                        {Object.entries(obj).map(([key, value], index, array) => {
                            const currentPath = path ? `${path}.${key}` : key;
                            const isDifferent = differences[currentPath];
                            
                            return (
                                <div key={key} className={isDifferent ? 'diff-changed' : ''}>
                                    <span className="json-key">"{key}"</span>: {renderJsonWithHighlights(value, differences, currentPath)}
                                    {index < array.length - 1 && ','}
                                </div>
                            );
                        })}
                    </div>
                    {'}'}
                </div>
            );
        }
        
        return (
            <span className="json-value">
                {typeof obj === 'string' ? `"${obj}"` : obj}
            </span>
        );
    };

    const handleMetadataSave = async () => {
        try {
            await workflowService.updateWorkflow(workflowId, workflow);
            showSuccessToast('Workflow details updated successfully');
            setShowMetadataModal(false);
        } catch (err) {
            showErrorToast('Failed to update workflow details');
        }
    };

    const handleValidate = async () => {
        try {
            const requestToValidate = typeof workflow.request === 'object' ? 
                workflow.request : 
                JSON.parse(workflow.request);

            console.log('Sending request for validation:', requestToValidate);
            
            const response = await workflowService.validateRequest(requestToValidate);
            console.log('Validation response:', response);

            if (response.success) {
                if (response.data && response.data.errors && response.data.errors.length > 0) {
                    // If the backend returned validation errors
                    const errorMessage = Array.isArray(response.data.errors) ? 
                        response.data.errors.join('\n') : 
                        response.data.errors;
                    showErrorToast(errorMessage);
                } else {
                    showSuccessToast('Request validation successful');
                }
            } else {
                const errorMessage = Array.isArray(response.error) ? 
                    response.error.join('\n') : 
                    response.error;
                showErrorToast(errorMessage);
            }
        } catch (err) {
            console.error('Validation error:', err);
            showErrorToast('Failed to validate request: ' + err.message);
        }
    };

    const handleExecutionClick = (execution) => {
        setSelectedExecution(execution);
        setShowExecutionModal(true);
    };

    const handleExecute = async () => {
        if (!workflow || !canExecuteWorkflow) return;

        try {
            setExecuting(true);
            const response = await workflowService.executeWorkflow(workflowId);
            if (response.success) {
                showSuccessToast('Workflow execution started successfully');
                loadExecutions();
            } else {
                showErrorToast(response.error || 'Failed to execute workflow');
            }
        } catch (err) {
            console.error('Error executing workflow:', err);
            showErrorToast('Failed to execute workflow');
        } finally {
            setExecuting(false);
            setShowExecuteConfirm(false);
        }
    };

    if (loading) {
        return (
            <div className="d-flex justify-content-center align-items-center" style={{ height: '200px' }}>
                <Spinner animation="border" role="status">
                    <span className="visually-hidden">Loading...</span>
                </Spinner>
            </div>
        );
    }

    if (error) {
        return (
            <div className="alert alert-danger" role="alert">
                {error}
            </div>
        );
    }

    return (
        <div className="edit-workflow-container">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h2>Edit Workflow</h2>
                <div className="d-flex gap-2">
                    <OverlayTrigger
                        placement="top"
                        overlay={
                            <Tooltip id="execute-tooltip">
                                {canExecuteWorkflow ? 'Execute this workflow' : 'You do not have permission to execute this workflow'}
                            </Tooltip>
                        }
                    >
                        <div>
                            <Button 
                                variant="success" 
                                onClick={() => setShowExecuteConfirm(true)}
                                disabled={!canExecuteWorkflow}
                            >
                                <HiPlay className="me-1" /> Execute
                            </Button>
                        </div>
                    </OverlayTrigger>
                    <OverlayTrigger
                        placement="top"
                        overlay={
                            <Tooltip id="edit-details-tooltip">
                                {canEditWorkflow ? 'Edit workflow name and description' : 'Only team admins can edit workflow details'}
                            </Tooltip>
                        }
                    >
                        <div>
                            <Button 
                                variant="outline-primary" 
                                onClick={() => setShowMetadataModal(true)}
                                disabled={!canEditWorkflow}
                            >
                                Edit Workflow Details
                            </Button>
                        </div>
                    </OverlayTrigger>
                    <Button 
                        variant="outline-secondary" 
                        onClick={() => navigate('/workflows')}
                    >
                        Back to Workflows
                    </Button>
                </div>
            </div>

            <div className="row">
                <div className="col-md-8">
                    <Card className="mb-4">
                        <Card.Body>
                            <Form onSubmit={(e) => e.preventDefault()}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Request</Form.Label>
                                    <Form.Control
                                        as="textarea"
                                        name="request"
                                        value={typeof workflow?.request === 'object' ? JSON.stringify(workflow.request, null, 2) : workflow?.request || ''}
                                        onChange={handleInputChange}
                                        rows={25}
                                        className="font-monospace"
                                    />
                                </Form.Group>

                                <div className="d-flex justify-content-between">
                                    <Button 
                                        variant="outline-secondary" 
                                        onClick={handleValidate}
                                    >
                                        Validate
                                    </Button>
                                    <OverlayTrigger
                                        placement="top"
                                        overlay={
                                            <Tooltip id="save-tooltip">
                                                {canEditWorkflow ? 'Save changes to create a new version' : 'Only team admins can save changes'}
                                            </Tooltip>
                                        }
                                    >
                                        <div>
                                            <Button 
                                                variant="primary" 
                                                onClick={() => setShowConfirmModal(true)}
                                                disabled={saving || !canEditWorkflow}
                                            >
                                                {saving ? (
                                                    <>
                                                        <Spinner
                                                            as="span"
                                                            animation="border"
                                                            size="sm"
                                                            role="status"
                                                            aria-hidden="true"
                                                            className="me-2"
                                                        />
                                                        Saving...
                                                    </>
                                                ) : 'Save Changes'}
                                            </Button>
                                        </div>
                                    </OverlayTrigger>
                                </div>
                            </Form>
                        </Card.Body>
                    </Card>

                    <Card className="mb-4">
                        <Card.Header>
                            <h5 className="mb-0">Workflow Statistics</h5>
                        </Card.Header>
                        <Card.Body>
                            {stats ? (
                                <div>
                                    <Row className="mb-4">
                                        <Col md={3}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Total</h6>
                                                    <h3>{stats.totalExecutions}</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                        <Col md={3}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Success</h6>
                                                    <h3 className="text-success">{stats.successfulExecutions}</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                        <Col md={3}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Failed</h6>
                                                    <h3 className="text-danger">{stats.failedExecutions}</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                        <Col md={3}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Avg Time</h6>
                                                    <h3>{stats.averageExecutionTime.toFixed(2)}ms</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    </Row>

                                    <Row className="mb-4">
                                        <Col md={6}>
                                            <Card>
                                                <Card.Body>
                                                    <h6>Step Performance</h6>
                                                    <ResponsiveContainer width="100%" height={300}>
                                                        <BarChart data={Object.entries(stats.stepStats).map(([step, data]) => ({
                                                            step: `Step ${step}`,
                                                            duration: data.averageDurationMs,
                                                            executions: data.totalExecutions
                                                        }))}>
                                                            <CartesianGrid strokeDasharray="3 3" />
                                                            <XAxis dataKey="step" />
                                                            <YAxis />
                                                            <RechartsTooltip 
                                                                formatter={(value, name) => {
                                                                    if (name === 'duration') {
                                                                        return [`${value.toFixed(2)}ms`, 'Avg. Duration'];
                                                                    }
                                                                    return [value, 'Total Executions'];
                                                                }}
                                                                contentStyle={{
                                                                    backgroundColor: 'rgba(255, 255, 255, 0.9)',
                                                                    border: '1px solid #ccc',
                                                                    borderRadius: '4px',
                                                                    padding: '10px'
                                                                }}
                                                            />
                                                            <Legend />
                                                            <Bar dataKey="duration" name="Avg. Duration (ms)" fill="#8884d8" />
                                                            <Bar dataKey="executions" name="Total Executions" fill="#82ca9d" />
                                                        </BarChart>
                                                    </ResponsiveContainer>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                        <Col md={6}>
                                            <Card>
                                                <Card.Body>
                                                    <h6>Target Distribution</h6>
                                                    <ResponsiveContainer width="100%" height={300}>
                                                        <PieChart>
                                                            <Pie
                                                                data={Object.entries(stats.targetStats).map(([target, data]) => ({
                                                                    name: target,
                                                                    value: data.totalExecutions
                                                                }))}
                                                                dataKey="value"
                                                                nameKey="name"
                                                                cx="50%"
                                                                cy="50%"
                                                                outerRadius={100}
                                                                label
                                                            >
                                                                {Object.entries(stats.targetStats).map((entry, index) => (
                                                                    <Cell key={`cell-${index}`} fill={['#0088FE', '#00C49F', '#FFBB28', '#FF8042'][index % 4]} />
                                                                ))}
                                                            </Pie>
                                                            <RechartsTooltip 
                                                                formatter={(value, name) => {
                                                                    const total = Object.values(stats.targetStats)
                                                                        .reduce((sum, data) => sum + data.totalExecutions, 0);
                                                                    const percentage = ((value / total) * 100).toFixed(1);
                                                                    return [`${value} executions (${percentage}%)`, name];
                                                                }}
                                                                contentStyle={{
                                                                    backgroundColor: 'rgba(255, 255, 255, 0.9)',
                                                                    border: '1px solid #ccc',
                                                                    borderRadius: '4px',
                                                                    padding: '10px'
                                                                }}
                                                            />
                                                            <Legend />
                                                        </PieChart>
                                                    </ResponsiveContainer>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    </Row>

                                    <Row>
                                        <Col md={12}>
                                            <Card>
                                                <Card.Body>
                                                    <h6>Hourly Executions</h6>
                                                    <ResponsiveContainer width="100%" height={400}>
                                                        <BarChart data={Object.entries(stats.hourlyExecutions)
                                                            .reduce((acc, [hour, count]) => {
                                                                // Find the corresponding date from dailyExecutions
                                                                const date = Object.keys(stats.dailyExecutions)[0]; // Get the first date since we have only one day
                                                                const hourWithDate = `${date} ${hour}`;
                                                                
                                                                // Deduplicate entries by hour
                                                                const existingEntry = acc.find(item => item.hour === hourWithDate);
                                                                if (existingEntry) {
                                                                    existingEntry.executions += count;
                                                                } else {
                                                                    acc.push({ 
                                                                        hour: hourWithDate,
                                                                        executions: count 
                                                                    });
                                                                }
                                                                return acc;
                                                            }, [])
                                                            .sort((a, b) => {
                                                                // Sort by hour
                                                                const [aDate, aTime] = a.hour.split(' ');
                                                                const [bDate, bTime] = b.hour.split(' ');
                                                                const [aHours] = aTime.split(':').map(Number);
                                                                const [bHours] = bTime.split(':').map(Number);
                                                                return aHours - bHours;
                                                            })}>
                                                            <CartesianGrid strokeDasharray="3 3" />
                                                            <XAxis 
                                                                dataKey="hour" 
                                                                angle={-45}
                                                                textAnchor="end"
                                                                height={80}
                                                                interval={0}
                                                                tick={{ fontSize: 12 }}
                                                            />
                                                            <YAxis />
                                                            <RechartsTooltip 
                                                                formatter={(value) => [`${value} executions`, 'Executions']}
                                                                labelFormatter={(label) => {
                                                                    const [date, time] = label.split(' ');
                                                                    return `${date}\n${time}`;
                                                                }}
                                                                contentStyle={{
                                                                    backgroundColor: 'rgba(255, 255, 255, 0.9)',
                                                                    border: '1px solid #ccc',
                                                                    borderRadius: '4px',
                                                                    padding: '10px'
                                                                }}
                                                            />
                                                            <Legend />
                                                            <Bar dataKey="executions" name="Executions" fill="#8884d8" />
                                                        </BarChart>
                                                    </ResponsiveContainer>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    </Row>
                                </div>
                            ) : (
                                <div className="text-center">
                                    <Spinner animation="border" role="status">
                                        <span className="visually-hidden">Loading stats...</span>
                                    </Spinner>
                                </div>
                            )}
                        </Card.Body>
                    </Card>
                </div>

                <div className="col-md-4">
                    <Card className="mb-4">
                        <Card.Body className="workflow-details">
                            <h5>Workflow Details</h5>
                            <div className="detail-item">
                                <div className="detail-label">ID</div>
                                <div className="detail-value">{workflow?.id}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Version</div>
                                <div className="detail-value">v{workflow?.version || 'N/A'}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Name</div>
                                <div className="detail-value">{workflow?.name}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Description</div>
                                <div className="detail-value">{workflow?.description}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Team</div>
                                <div className="detail-value">
                                    {teamDetails ? (
                                        <div>
                                            <div>{teamDetails.name}</div>
                                            <small className="text-muted">ID: {workflow?.teamId}</small>
                                        </div>
                                    ) : (
                                        workflow?.teamId
                                    )}
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Created At</div>
                                <div className="detail-value">{formatDate(workflow?.createdAt)}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Updated At</div>
                                <div className="detail-value">{formatDate(workflow?.updatedAt)}</div>
                            </div>
                        </Card.Body>
                    </Card>

                    <Card className="mb-4">
                        <Card.Header>
                            <h5 className="mb-0">Version History</h5>
                        </Card.Header>
                        <Card.Body className="version-history">
                            {versions.map((version) => (
                                <div key={version.id} className="version-item">
                                    <div className="d-flex justify-content-between align-items-center mb-2">
                                        <div className="d-flex align-items-center">
                                            <Badge bg="secondary" className="me-2">v{version.version}</Badge>
                                            {version.version === workflow.version && (
                                                <Badge bg="success">Current</Badge>
                                            )}
                                        </div>
                                        <small className="text-muted">
                                            {formatDate(version.createdAt)}
                                        </small>
                                    </div>
                                    <p className="version-description mb-2">
                                        {version.changeDescription.replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d+/, (match) => format(new Date(match), 'MMM d, yyyy HH:mm'))}
                                    </p>
                                    <div className="d-flex justify-content-between align-items-center">
                                        <small className="text-muted">
                                            By {version.createdBy || 'System'}
                                        </small>
                                        {version.version !== workflow?.version && (
                                            <div className="d-flex gap-2">
                                                <Button
                                                    variant="outline-info"
                                                    size="sm"
                                                    onClick={() => handleCompareClick(version)}
                                                >
                                                    Compare
                                                </Button>
                                                <OverlayTrigger
                                                    placement="top"
                                                    overlay={
                                                        <Tooltip id="rollback-tooltip">
                                                            {canEditWorkflow ? 'Rollback to this version' : 'Only team admins can rollback versions'}
                                                        </Tooltip>
                                                    }
                                                >
                                                    <div>
                                                        <Button
                                                            variant="outline-primary"
                                                            size="sm"
                                                            onClick={() => handleRollbackClick(version)}
                                                            disabled={!canEditWorkflow}
                                                        >
                                                            Rollback
                                                        </Button>
                                                    </div>
                                                </OverlayTrigger>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </Card.Body>
                    </Card>

                    <Card>
                        <Card.Header>
                            <h5 className="mb-0">Execution Pipeline</h5>
                        </Card.Header>
                        <Card.Body>
                            {loadingExecutions ? (
                                <div className="text-center">
                                    <Spinner animation="border" role="status">
                                        <span className="visually-hidden">Loading executions...</span>
                                    </Spinner>
                                </div>
                            ) : (
                                <div className="edit-workflow-execution-pipeline">
                                    {executions.map((execution, index) => (
                                        <OverlayTrigger
                                            key={execution._id?.$oid || execution.id || execution._id}
                                            placement="top"
                                            overlay={
                                                <Tooltip id={`tooltip-${execution._id?.$oid || execution.id || execution._id}`}>
                                                    Executed at: {formatDate(execution.executedAt?.$date || execution.executedAt)}
                                                </Tooltip>
                                            }
                                        >
                                            <div 
                                                className="edit-workflow-execution-item"
                                                onClick={() => handleExecutionClick(execution)}
                                            >
                                                <div className="edit-workflow-execution-header">
                                                    <div className="edit-workflow-execution-number">#{executions.length - index}</div>
                                                    <div className="edit-workflow-execution-status">
                                                        <Badge bg={execution.status === 'SUCCESS' ? 'success' : 'danger'}>
                                                            {execution.status}
                                                        </Badge>
                                                    </div>
                                                </div>
                                                <div className="edit-workflow-execution-duration">
                                                    {execution.durationMs?.$numberLong || execution.durationMs}ms
                                                </div>
                                                <div className="edit-workflow-execution-result">
                                                    {execution.response?.result?.finalResult}
                                                </div>
                                            </div>
                                        </OverlayTrigger>
                                    ))}
                                </div>
                            )}
                        </Card.Body>
                    </Card>
                </div>
            </div>

            <Modal
                show={showCompareModal}
                onHide={() => setShowCompareModal(false)}
                fullscreen
            >
                <Modal.Header closeButton>
                    <Modal.Title>Compare Versions</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <div className="compare-form">
                        <div className="version-selectors mb-4">
                            <div className="row g-3 align-items-center">
                                <div className="col-5">
                                    <div className="version-content p-3 bg-light rounded">
                                        <h6 className="mb-3">Version {compareVersions.version1}</h6>
                                        <div className="diff-view">
                                            <div className="diff-section">
                                                <h6 className="diff-title">Name</h6>
                                                <div className="diff-content">
                                                    {workflow?.name}
                                                </div>
                                            </div>
                                            <div className="diff-section">
                                                <h6 className="diff-title">Description</h6>
                                                <div className="diff-content">
                                                    {workflow?.description}
                                                </div>
                                            </div>
                                            <div className="diff-section">
                                                <h6 className="diff-title">Request</h6>
                                                <pre className="diff-content">
                                                    {renderJsonWithHighlights(
                                                        workflow?.request || {},
                                                        highlightDifferences(
                                                            workflow?.request || {},
                                                            versions.find(v => v.version === compareVersions.version2)?.request || {}
                                                        )
                                                    )}
                                                </pre>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-2 text-center">
                                    <span className="vs-badge">VS</span>
                                </div>
                                <div className="col-5">
                                    <div className="version-content p-3 bg-light rounded">
                                        <h6 className="mb-3">Version {compareVersions.version2}</h6>
                                        <div className="diff-view">
                                            <div className="diff-section">
                                                <h6 className="diff-title">Name</h6>
                                                <div className="diff-content">
                                                    {workflow?.name}
                                                </div>
                                            </div>
                                            <div className="diff-section">
                                                <h6 className="diff-title">Description</h6>
                                                <div className="diff-content">
                                                    {workflow?.description}
                                                </div>
                                            </div>
                                            <div className="diff-section">
                                                <h6 className="diff-title">Request</h6>
                                                <pre className="diff-content">
                                                    {renderJsonWithHighlights(
                                                        versions.find(v => v.version === compareVersions.version2)?.request || {},
                                                        highlightDifferences(
                                                            workflow?.request || {},
                                                            versions.find(v => v.version === compareVersions.version2)?.request || {}
                                                        )
                                                    )}
                                                </pre>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowCompareModal(false)}>
                        Close
                    </Button>
                </Modal.Footer>
            </Modal>

            <ConfirmationModal
                show={showRollbackModal}
                onHide={() => {
                    setShowRollbackModal(false);
                    setSelectedVersion(null);
                }}
                onConfirm={handleRollback}
                title="Rollback Version"
                message={`Are you sure you want to rollback from version ${workflow?.version} to version ${selectedVersion?.version}?`}
                confirmLabel={saving ? (
                    <>
                        <Spinner
                            as="span"
                            animation="border"
                            size="sm"
                            role="status"
                            aria-hidden="true"
                            className="me-2"
                        />
                        Rolling back...
                    </>
                ) : "Rollback"}
                variant="warning"
                disabled={saving}
            />

            <ConfirmationModal
                show={showConfirmModal}
                onHide={() => setShowConfirmModal(false)}
                onConfirm={handleSave}
                title="Save Changes"
                message="Are you sure you want to save these changes? This will create a new version of the workflow."
            />

            <Modal
                show={showMetadataModal}
                onHide={() => setShowMetadataModal(false)}
                centered
            >
                <Modal.Header closeButton>
                    <Modal.Title>Edit Workflow Details</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label>Name</Form.Label>
                            <Form.Control
                                type="text"
                                name="name"
                                value={workflow?.name || ''}
                                onChange={handleInputChange}
                                placeholder="Enter workflow name"
                            />
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Description</Form.Label>
                            <Form.Control
                                as="textarea"
                                name="description"
                                value={workflow?.description || ''}
                                onChange={handleInputChange}
                                placeholder="Enter workflow description"
                                rows={3}
                            />
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Visibility</Form.Label>
                            <div>
                                <Form.Check
                                    type="switch"
                                    id="visibility-switch"
                                    label={workflow?.public ? "Public - Accessible by all teams" : "Private - Only accessible by your team"}
                                    checked={workflow?.public || false}
                                    onChange={(e) => setWorkflow(prev => ({
                                        ...prev,
                                        public: e.target.checked
                                    }))}
                                    disabled={!canEditWorkflow}
                                />
                                <Form.Text className="text-muted">
                                    {workflow?.public 
                                        ? "Public workflows can be viewed and executed by all teams, but only your team's admins can edit them."
                                        : "Private workflows are only accessible to your team members."}
                                </Form.Text>
                            </div>
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowMetadataModal(false)}>
                        Cancel
                    </Button>
                    <OverlayTrigger
                        placement="top"
                        overlay={
                            <Tooltip id="metadata-save-tooltip">
                                {canEditWorkflow ? 'Save workflow details' : 'Only team admins can save workflow details'}
                            </Tooltip>
                        }
                    >
                        <div>
                            <Button 
                                variant="primary" 
                                onClick={handleMetadataSave}
                                disabled={!canEditWorkflow}
                            >
                                Save Changes
                            </Button>
                        </div>
                    </OverlayTrigger>
                </Modal.Footer>
            </Modal>

            {/* Execution Details Modal */}
            <ExecutionDetailsModal
                show={showExecutionModal}
                onHide={() => setShowExecutionModal(false)}
                execution={selectedExecution}
            />

            {/* Execute Confirmation Modal */}
            <ExecuteConfirmationModal
                show={showExecuteConfirm}
                onHide={() => setShowExecuteConfirm(false)}
                onConfirm={handleExecute}
                workflow={workflow}
                executing={executing}
            />
        </div>
    );
}

export default EditWorkflow;