import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../../utils/roleUtils';
import agentTaskService from '../../../services/agentTaskService';
import agentTaskVersionService from '../../../services/agentTaskVersionService';
import agentTaskMonitoringService from '../../../services/agentTaskMonitoringService';
import agentSchedulingService from '../../../services/agentSchedulingService';
import agentService from '../../../services/agentService';
import workflowService from '../../../services/workflowService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { Form, Card, Spinner, Badge, Modal, Row, Col, OverlayTrigger, Tooltip, Accordion } from 'react-bootstrap';
import Button from '../../../components/common/Button';
import { format } from 'date-fns';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import ExecutionDetailsModal from '../../../components/workflows/ExecutionDetailsModal';
import StepDescriptions from '../../../components/workflows/StepDescriptions';
import { HiPlay, HiArrowLeft } from 'react-icons/hi';
import { BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, Legend, ResponsiveContainer } from 'recharts';
import { 
    Table, 
    TableBody, 
    TableCell, 
    TableContainer, 
    TableHead, 
    TableRow, 
    Paper,
    TextField,
    Box,
    InputAdornment 
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import './styles.css';

function ViewAgentTask() {
    const { taskId } = useParams();
    const navigate = useNavigate();
    const { currentTeam } = useTeam();
    const { user } = useAuth();
    const [task, setTask] = useState(null);
    const [agent, setAgent] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [versions, setVersions] = useState([]);
    const [showRollbackModal, setShowRollbackModal] = useState(false);
    const [showCompareModal, setShowCompareModal] = useState(false);
    const [selectedVersion, setSelectedVersion] = useState(null);
    const [compareVersions, setCompareVersions] = useState({ version1: '', version2: '' });
    const [saving, setSaving] = useState(false);
    const [showMetadataModal, setShowMetadataModal] = useState(false);
    const [showExecuteConfirm, setShowExecuteConfirm] = useState(false);
    const [executing, setExecuting] = useState(false);
    const [configText, setConfigText] = useState('');
    const [validationError, setValidationError] = useState(null);
    const [isEditingConfig, setIsEditingConfig] = useState(false);
    const [showConfirmModal, setShowConfirmModal] = useState(false);
    const [stats, setStats] = useState(null);
    const [metrics, setMetrics] = useState(null);
    const [executionHistory, setExecutionHistory] = useState([]);
    const [workflowStats, setWorkflowStats] = useState(null);
    const [loadingWorkflowStats, setLoadingWorkflowStats] = useState(false);
    const [loadingStats, setLoadingStats] = useState(true);
    const [selectedExecution, setSelectedExecution] = useState(null);
    const [showExecutionModal, setShowExecutionModal] = useState(false);
    const [loadingExecutionDetails, setLoadingExecutionDetails] = useState(false);
    const [executionFilters, setExecutionFilters] = useState({
        executionId: '',
        startedAt: '',
        completedAt: '',
        duration: '',
        status: '',
        result: ''
    });
    const [linkedWorkflow, setLinkedWorkflow] = useState(null);
    const [loadingWorkflow, setLoadingWorkflow] = useState(false);
    const [generatingCronDescription, setGeneratingCronDescription] = useState(false);
    const [showSchedulingModal, setShowSchedulingModal] = useState(false);
    const [showUnscheduleModal, setShowUnscheduleModal] = useState(false);
    const [unscheduling, setUnscheduling] = useState(false);
    
    // Timezone conversion utilities
    const getUserTimezone = () => Intl.DateTimeFormat().resolvedOptions().timeZone;
    
    const convertUTCToLocal = (utcHour, utcMinute) => {
        const utcDate = new Date();
        utcDate.setUTCHours(utcHour, utcMinute, 0, 0);
        return {
            hour: utcDate.getHours(),
            minute: utcDate.getMinutes()
        };
    };
    
    const convertLocalToUTC = (localHour, localMinute) => {
        const localDate = new Date();
        localDate.setHours(localHour, localMinute, 0, 0);
        return {
            hour: localDate.getUTCHours(),
            minute: localDate.getUTCMinutes()
        };
    };
    
    const [cronFields, setCronFields] = useState({
        seconds: '',
        minutes: '',
        hours: '',
        dayOfMonth: '',
        month: '',
        dayOfWeek: ''
    });
    const [cronValidationError, setCronValidationError] = useState('');
    const [cronDescriptionError, setCronDescriptionError] = useState('');
    const [isValidatingCron, setIsValidatingCron] = useState(false);
    const [isCronValidated, setIsCronValidated] = useState(false);

    const canEditTask = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);

    useEffect(() => {
        if (currentTeam) {
            loadTask();
            loadVersions();
            loadStats();
            loadMetrics();
            loadExecutionHistory();
        }
    }, [currentTeam, taskId]);

    useEffect(() => {
        if (task?.linq_config && !isEditingConfig) {
            const configString = typeof task.linq_config === 'object' 
                ? JSON.stringify(task.linq_config, null, 2) 
                : task.linq_config || '';
            setConfigText(configString);
            setValidationError(null);
        }
    }, [task, isEditingConfig]);

    useEffect(() => {
        if (task) {
            loadWorkflowStats();
        }
    }, [task]);

    useEffect(() => {
        if (task?.agentId && currentTeam) {
            loadAgent();
        }
    }, [task?.agentId, currentTeam]);

    useEffect(() => {
        if (task?.linq_config?.query?.workflowId && currentTeam) {
            loadLinkedWorkflow();
        }
    }, [task?.linq_config?.query?.workflowId, currentTeam]);

    useEffect(() => {
        if (showSchedulingModal && task?.cronExpression) {
            setCronFields(parseCronExpressionToUTC(task.cronExpression));
            // If there's already a description, mark as validated
            setIsCronValidated(!!task.cronDescription);
        } else if (showSchedulingModal) {
            // Reset validation state when opening modal without existing cron
            setIsCronValidated(false);
        }
    }, [showSchedulingModal, task?.cronExpression, task?.cronDescription]);

    // Clear errors and reset validation when cron expression changes
    useEffect(() => {
        const currentExpression = buildCronExpression(cronFields);
        const previousExpression = task?.cronExpression || '';
        
        // Only reset validation if the expression actually changed
        if (currentExpression !== previousExpression) {
            setCronValidationError('');
            setCronDescriptionError('');
            setIsCronValidated(false);
        }
    }, [cronFields, task?.cronExpression]);


    const loadTask = async () => {
        try {
            setLoading(true);
            const response = await agentTaskService.getTask(taskId);
            if (response.success) {
                setTask(response.data);
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load task');
            console.error('Error loading task:', err);
        } finally {
            setLoading(false);
        }
    };

    const loadAgent = async () => {
        try {
            const response = await agentService.getAgent(currentTeam.id, task.agentId);
            if (response.success) {
                setAgent(response.data);
            }
        } catch (err) {
            console.error('Error loading agent:', err);
        }
    };

    const loadLinkedWorkflow = async () => {
        try {
            setLoadingWorkflow(true);
            const workflowId = task?.linq_config?.query?.workflowId;
            if (workflowId && currentTeam) {
                const response = await workflowService.getWorkflowById(workflowId);
                if (response.success) {
                    setLinkedWorkflow(response.data);
                }
            }
        } catch (err) {
            console.error('Error loading linked workflow:', err);
        } finally {
            setLoadingWorkflow(false);
        }
    };

    const loadVersions = async () => {
        try {
            const response = await agentTaskVersionService.getVersionHistory(taskId);
            if (response.success) {
                setVersions(response.data);
            }
        } catch (err) {
            console.error('Error loading versions:', err);
        }
    };

    const loadStats = async () => {
        try {
            setLoadingStats(true);
            const response = await agentTaskMonitoringService.getTaskStatistics(taskId);
            if (response.success) {
                setStats(response.data);
            }
        } catch (err) {
            console.error('Error loading stats:', err);
        } finally {
            setLoadingStats(false);
        }
    };

    const loadWorkflowStats = async () => {
        if (!task) return;
        
        try {
            setLoadingWorkflowStats(true);
            
            // For WORKFLOW_TRIGGER tasks, fetch stats by workflowId
            if (task.taskType === 'WORKFLOW_TRIGGER' && task.linq_config?.query?.workflowId) {
                const workflowId = task.linq_config.query.workflowId;
                const response = await workflowService.getWorkflowStats(workflowId);
                if (response.success) {
                    setWorkflowStats(response.data);
                }
            }
            // For WORKFLOW_EMBEDDED tasks, fetch stats by agentTaskId
            else if (task.taskType === 'WORKFLOW_EMBEDDED' || task.taskType === 'WORKFLOW_EMBEDDED_ADHOC') {
                const response = await workflowService.getAgentTaskWorkflowStats(taskId);
                if (response.success) {
                    setWorkflowStats(response.data);
                }
            }
            
        } catch (err) {
            console.error('Error loading workflow stats:', err);
        } finally {
            setLoadingWorkflowStats(false);
        }
    };

    const loadMetrics = async () => {
        try {
            const response = await agentTaskMonitoringService.getTaskMetrics(taskId);
            if (response.success) {
                setMetrics(response.data);
            }
        } catch (err) {
            console.error('Error loading metrics:', err);
        }
    };

    const loadExecutionHistory = async () => {
        try {
            // Fetch all executions by passing a large limit or removing the limit parameter
            const response = await agentTaskMonitoringService.getTaskExecutionHistory(taskId, 1000);
            if (response.success) {
                setExecutionHistory(response.data);
            }
        } catch (err) {
            console.error('Error loading execution history:', err);
        }
    };

    const handleInputChange = (e) => {
        const { name, value, type, checked } = e.target;
        
        setTask(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value
        }));

        // Auto-generate cron description when cron expression changes
        if (name === 'cronExpression' && value.trim()) {
            generateCronDescription(value.trim());
        }
    };

    const generateCronDescription = async (cronExpression) => {
        if (!cronExpression || cronExpression.length < 5) {
            return;
        }

        try {
            setGeneratingCronDescription(true);
            const response = await agentTaskService.getCronDescription(cronExpression);
            if (response.success && response.description) {
                setTask(prev => ({
                    ...prev,
                    cronDescription: response.description
                }));
            } else {
                // If API fails, clear the description
                setTask(prev => ({
                    ...prev,
                    cronDescription: ''
                }));
            }
        } catch (err) {
            console.error('Error generating cron description:', err);
            setTask(prev => ({
                ...prev,
                cronDescription: ''
            }));
        } finally {
            setGeneratingCronDescription(false);
        }
    };

    const validateAndGenerateCronDescription = async () => {
        const cronExpression = buildCronExpression(cronFields);
        
        // Clear previous errors
        setCronValidationError('');
        setCronDescriptionError('');
        
        // Basic validation - check if all fields are filled
        const parts = cronExpression.trim().split(/\s+/).filter(part => part.trim());
        if (parts.length !== 6) {
            setCronValidationError('All cron fields must be filled');
            return;
        }
        
        // Check for empty fields
        const hasEmptyFields = Object.values(cronFields).some(field => !field.trim());
        if (hasEmptyFields) {
            setCronValidationError('All cron fields must be filled');
            return;
        }
        
        // Quartz-specific validation: Cannot use * for both dayOfMonth and dayOfWeek
        if (cronFields.dayOfMonth === '*' && cronFields.dayOfWeek === '*') {
            setCronValidationError('Cannot use * for both Day of Month and Day of Week. Use ? for one of them.');
            return;
        }
        
        try {
            setIsValidatingCron(true);
            const response = await agentTaskService.getCronDescription(cronExpression);
            
            if (response.success && response.description) {
                setTask(prev => ({
                    ...prev,
                    cronExpression: cronExpression,
                    cronDescription: response.description
                }));
                setCronDescriptionError('');
                setIsCronValidated(true);
            } else {
                setCronDescriptionError(response.error || 'Failed to validate cron expression');
                setTask(prev => ({
                    ...prev,
                    cronDescription: ''
                }));
                setIsCronValidated(false);
            }
        } catch (err) {
            console.error('Error validating cron expression:', err);
            setCronDescriptionError(err.response?.data?.message || 'Invalid cron expression');
            setTask(prev => ({
                ...prev,
                cronDescription: ''
            }));
            setIsCronValidated(false);
        } finally {
            setIsValidatingCron(false);
        }
    };

    const parseCronExpression = (cronExpression) => {
        if (!cronExpression) {
            return { seconds: '', minutes: '', hours: '', dayOfMonth: '', month: '', dayOfWeek: '' };
        }
        
        const parts = cronExpression.trim().split(/\s+/);
        return {
            seconds: parts[0] || '',
            minutes: parts[1] || '',
            hours: parts[2] || '',
            dayOfMonth: parts[3] || '',
            month: parts[4] || '',
            dayOfWeek: parts[5] || ''
        };
    };

    const buildCronExpression = (fields) => {
        // For Quartz cron expressions, we cannot use * for both dayOfMonth and dayOfWeek
        // If dayOfMonth is *, then dayOfWeek should be ?
        const dayOfWeek = fields.dayOfMonth === '*' ? '?' : fields.dayOfWeek;
        
        // The fields already contain UTC time (what user enters), so use them directly
        return `${fields.seconds} ${fields.minutes} ${fields.hours} ${fields.dayOfMonth} ${fields.month} ${dayOfWeek}`.trim();
    };
    
    const parseCronExpressionToUTC = (cronExpression) => {
        if (!cronExpression) return { seconds: '', minutes: '', hours: '', dayOfMonth: '', month: '', dayOfWeek: '' };
        
        const parts = cronExpression.split(' ');
        if (parts.length !== 6) return { seconds: '', minutes: '', hours: '', dayOfMonth: '', month: '', dayOfWeek: '' };
        
        const [seconds, utcMinutes, utcHours, dayOfMonth, month, dayOfWeek] = parts;
        
        // Return UTC time directly (what gets stored)
        return {
            seconds: seconds || '',
            minutes: utcMinutes || '',
            hours: utcHours || '',
            dayOfMonth: dayOfMonth || '',
            month: month || '',
            dayOfWeek: dayOfWeek || ''
        };
    };

    const handleCronFieldChange = (field, value) => {
        // Limit to 6 characters and only allow valid cron characters
        const sanitizedValue = value.replace(/[^0-9*,\-\/\s]/g, '').substring(0, 6);
        
        setCronFields(prev => {
            const newFields = { ...prev, [field]: sanitizedValue };
            const cronExpression = buildCronExpression(newFields);
            
            // Update the task's cron expression
            setTask(prevTask => ({
                ...prevTask,
                cronExpression: cronExpression
            }));
            
            // Clear any previous validation errors when user starts typing
            setCronValidationError('');
            setCronDescriptionError('');
            
            return newFields;
        });
    };

    const isCronExpressionValid = () => {
        const cronExpression = buildCronExpression(cronFields);
        
        if (!cronExpression || cronExpression.trim() === '') {
            return false;
        }

        const parts = cronExpression.trim().split(/\s+/);
        if (parts.length !== 6) {
            return false;
        }

        // Check if all fields are filled
        const hasEmptyFields = Object.values(cronFields).some(field => !field.trim());
        if (hasEmptyFields) {
            return false;
        }

        return true;
    };

    const handleConfigTextChange = (event) => {
        const text = event.target.value;
        setIsEditingConfig(true);
        setConfigText(text);
        
        try {
            const parsed = JSON.parse(text);
            setTask(prev => ({
                ...prev,
                linq_config: parsed
            }));
            setValidationError(null);
        } catch (error) {
            console.warn('JSON parsing failed during editing:', error);
            setValidationError(error.message);
        }
    };

    const formatJson = () => {
        try {
            const parsed = JSON.parse(configText);
            const formatted = JSON.stringify(parsed, null, 2);
            setConfigText(formatted);
            setValidationError(null);
        } catch (error) {
            setValidationError('Cannot format invalid JSON');
        }
    };

    const handleConfigFocus = () => {
        setIsEditingConfig(true);
    };

    const handleConfigBlur = () => {
        setTimeout(() => {
            setIsEditingConfig(false);
        }, 100);
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            const taskToSave = {
                ...task,
                linq_config: typeof task.linq_config === 'object' ? task.linq_config : JSON.parse(task.linq_config)
            };
            const response = await agentTaskVersionService.createNewVersion(taskId, taskToSave);
            if (response.success) {
                showSuccessToast('Task updated successfully');
                loadTask();
                loadVersions();
            } else {
                showErrorToast(response.error || 'Failed to update task');
            }
        } catch (err) {
            showErrorToast('Failed to update task');
            console.error('Error updating task:', err);
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
            const response = await agentTaskVersionService.rollbackToVersion(taskId, selectedVersion.version);
            if (response.success) {
                showSuccessToast('Rolled back to previous version');
                loadTask();
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
            version1: task?.version,
            version2: version.version
        });
        setShowCompareModal(true);
    };

    const handleMetadataSave = async () => {
        try {
            setSaving(true);
            
            // Create a new version with updated metadata
            // Note: Backend will auto-detect and update task type based on linq_config structure
            const response = await agentTaskVersionService.createNewVersion(taskId, task);
            if (response.success) {
                showSuccessToast('Task details updated successfully');
                setShowMetadataModal(false);
                loadTask();
                loadVersions();
            } else {
                showErrorToast(response.error || 'Failed to update task details');
            }
        } catch (err) {
            showErrorToast(err.response?.data?.message || 'Failed to update task details');
            console.error('Error updating task details:', err);
        } finally {
            setSaving(false);
        }
    };

    const handleExecute = async () => {
        if (!task) return;

        try {
            setExecuting(true);
            
            // Show success message and redirect immediately
            showSuccessToast('Task execution started! Redirecting to monitoring...');
            navigate('/execution-monitoring');
            
            // Execute the task in the background
            const response = await agentTaskService.executeTask(taskId);
            console.log('Execute task response:', response);
            
            if (!response.success) {
                showErrorToast(response.error || 'Failed to execute task');
            }
            
        } catch (err) {
            console.error('Error executing task:', err);
            const errorMessage = err.response?.data?.message || err.message || 'Failed to execute task';
            showErrorToast(errorMessage);
        } finally {
            setExecuting(false);
            setShowExecuteConfirm(false);
        }
    };

    const handleEnableDisable = async () => {
        try {
            const response = task.enabled 
                ? await agentTaskService.disableTask(taskId)
                : await agentTaskService.enableTask(taskId);
            
            if (response.success) {
                showSuccessToast(`Task ${task.enabled ? 'disabled' : 'enabled'} successfully`);
                loadTask();
            } else {
                showErrorToast(response.error || `Failed to ${task.enabled ? 'disable' : 'enable'} task`);
            }
        } catch (err) {
            showErrorToast(`Failed to ${task.enabled ? 'disable' : 'enable'} task`);
        }
    };

    const handleExecutionClick = async (execution) => {
        try {
            setLoadingExecutionDetails(true);
            
            // Fetch the full execution details by agentExecutionId
            // This works for both embedded workflows and workflow triggers
            const response = await workflowService.getExecutionByAgentExecutionId(execution.executionId);
            
            if (response.success && response.data) {
                setSelectedExecution(response.data);
                setShowExecutionModal(true);
            } else {
                showErrorToast(response.error || 'Execution details not found');
            }
        } catch (err) {
            console.error('Error fetching execution details:', err);
            showErrorToast('Failed to load execution details');
        } finally {
            setLoadingExecutionDetails(false);
        }
    };

    const handleExecutionFilterChange = (column) => (event) => {
        setExecutionFilters(prev => ({
            ...prev,
            [column]: event.target.value
        }));
    };

    const filteredExecutions = executionHistory.filter(execution => {
        return Object.keys(executionFilters).every(key => {
            const filterValue = executionFilters[key].toLowerCase();
            if (!filterValue) return true;

            if (key === 'executionId') {
                return execution.executionId.toLowerCase().includes(filterValue);
            }
            if (key === 'startedAt' || key === 'completedAt') {
                return formatDate(execution[key]).toLowerCase().includes(filterValue);
            }
            if (key === 'duration') {
                return String(execution.executionDurationMs || '').includes(filterValue);
            }
            if (key === 'status' || key === 'result') {
                return String(execution[key] || '').toLowerCase().includes(filterValue);
            }

            return true;
        });
    });

    const formatDate = (date) => {
        if (!date) return 'N/A';
        try {
            let dateObj;
            if (Array.isArray(date)) {
                // Handle array format [year, month, day, hour, minute, second?, nanosecond?]
                // Since these are stored in UTC, we need to create a UTC date
                const year = date[0];
                const month = date[1] - 1; // JavaScript months are 0-based
                const day = date[2];
                const hour = date[3];
                const minute = date[4];
                const second = date[5] || 0;
                
                // Create UTC date
                dateObj = new Date(Date.UTC(year, month, day, hour, minute, second));
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
        
        if (Array.isArray(obj1) && Array.isArray(obj2)) {
            obj1.forEach((item, index) => {
                if (index < obj2.length) {
                    const nestedDiffs = highlightDifferences(item, obj2[index], `${path}[${index}]`);
                    Object.assign(result, nestedDiffs);
                }
            });
            return result;
        }
        
        if (typeof obj1 === 'object' && typeof obj2 === 'object') {
            const allKeys = new Set([...Object.keys(obj1), ...Object.keys(obj2)]);
            
            allKeys.forEach(key => {
                const currentPath = path ? `${path}.${key}` : key;
                
                if (obj1[key] && obj2[key] && 
                    typeof obj1[key] === 'object' && typeof obj2[key] === 'object') {
                    const nestedDiffs = highlightDifferences(obj1[key], obj2[key], currentPath);
                    Object.assign(result, nestedDiffs);
                } 
                else if (JSON.stringify(obj1[key]) !== JSON.stringify(obj2[key])) {
                    result[currentPath] = true;
                }
            });
            
            return result;
        }
        
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
                {typeof obj === 'string' ? `"${obj}"` : String(obj)}
            </span>
        );
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
        <div className="view-agent-task-container">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h4 className="page-header">
                    <i className="fas fa-tasks me-2"></i>
                    Agent Task Details
                </h4>
                <div className="d-flex gap-2">
                    <Button 
                        variant="outline-secondary" 
                        onClick={() => navigate(`/agents/${task?.agentId}`)}
                    >
                        <HiArrowLeft className="me-1" /> Back to Agent
                    </Button>
                    <OverlayTrigger
                        placement="top"
                        overlay={
                            <Tooltip id="execute-tooltip">
                                {task?.enabled ? 'Execute this task' : 'Task must be enabled to execute'}
                            </Tooltip>
                        }
                    >
                        <div>
                            <Button 
                                variant="primary" 
                                onClick={() => setShowExecuteConfirm(true)}
                                disabled={!task?.enabled || executing}
                            >
                                <HiPlay className="me-1" /> Execute
                            </Button>
                        </div>
                    </OverlayTrigger>
                    <OverlayTrigger
                        placement="top"
                        overlay={
                            <Tooltip id="toggle-tooltip">
                                {canEditTask ? `${task?.enabled ? 'Disable' : 'Enable'} this task` : 'Only team admins can modify tasks'}
                            </Tooltip>
                        }
                    >
                        <div>
                            <Button 
                                variant={task?.enabled ? 'warning' : 'success'}
                                onClick={handleEnableDisable}
                                disabled={!canEditTask}
                            >
                                {task?.enabled ? 'Disable Task' : 'Enable Task'}
                            </Button>
                        </div>
                    </OverlayTrigger>
                    <OverlayTrigger
                        placement="top"
                        overlay={
                            <Tooltip id="edit-details-tooltip">
                                {canEditTask ? 'Edit task details' : 'Only team admins can edit task details'}
                            </Tooltip>
                        }
                    >
                        <div>
                            <Button 
                                variant="outline-primary" 
                                onClick={() => setShowMetadataModal(true)}
                                disabled={!canEditTask}
                            >
                                Edit Task Details
                            </Button>
                        </div>
                    </OverlayTrigger>
                </div>
            </div>

            <div className="task-title-section mb-4">
                <h4 className="task-title text-start mb-2">
                    {task?.name}
                </h4>
                <div className="task-meta text-muted small">
                    <span className="me-4">
                        <i className="fas fa-calendar-plus me-1"></i>
                        Created: {formatDate(task?.createdAt)}
                    </span>
                    <span className="me-4">
                        <i className="fas fa-user-plus me-1"></i>
                        Created By: {task?.createdBy || 'Unknown'}
                    </span>
                    <span className="me-4">
                        <i className="fas fa-calendar-check me-1"></i>
                        Updated: {formatDate(task?.updatedAt)}
                    </span>
                    <span className="me-4">
                        <i className="fas fa-user-edit me-1"></i>
                        Updated By: {task?.updatedBy || 'Unknown'}
                    </span>
                    {task?.version && (
                        <span>
                            <i className="fas fa-code-branch me-1"></i>
                            v{task.version}
                        </span>
                    )}
                </div>
                {task?.description && (
                    <div className="task-description mt-3">
                        <div className="description-label mb-1">
                            <i className="fas fa-align-left me-1 text-muted"></i>
                            <small className="text-muted fw-semibold">Description</small>
                        </div>
                        <p className="description-text mb-0">
                            {task.description}
                        </p>
                    </div>
                )}
            </div>

            {/* Schedule Information Section */}
            <Card className="mb-4">
                <Card.Header className="bg-light">
                    <div className="d-flex justify-content-between align-items-center">
                        <h5 className="mb-0">
                            <i className="fas fa-clock me-2"></i>
                            Schedule Information
                        </h5>
                        {canEditTask && task?.executionTrigger !== 'MANUAL' && (
                            <div className="d-flex gap-2">
                                <Button 
                                    variant="outline-danger" 
                                    size="sm"
                                    onClick={() => setShowUnscheduleModal(true)}
                                >
                                    <i className="fas fa-calendar-times me-1"></i>
                                    Unschedule
                                </Button>
                                <Button 
                                    variant="outline-primary" 
                                    size="sm"
                                    onClick={() => setShowSchedulingModal(true)}
                                >
                                    <i className="fas fa-edit me-1"></i>
                                    Edit
                                </Button>
                            </div>
                        )}
                    </div>
                </Card.Header>
                <Card.Body>
                    {task?.executionTrigger === 'MANUAL' ? (
                        <div className="text-center py-3">
                            <i className="fas fa-hand-pointer text-muted fa-3x mb-3"></i>
                            <h6 className="text-muted">Manual Execution Only</h6>
                            <p className="text-muted mb-3">
                                This task is set to manual execution. It will not run automatically.
                            </p>
                            {canEditTask && (
                                <Button 
                                    variant="outline-primary" 
                                    size="sm"
                                    onClick={() => setShowSchedulingModal(true)}
                                >
                                    <i className="fas fa-clock me-1"></i>
                                    Configure Scheduling
                                </Button>
                            )}
                        </div>
                    ) : (
                        <div>
                            {task?.cronExpression && (
                                <div className="mb-3">
                                    <div className="mb-2">
                                        <strong>{task.cronDescription || 'No description available'} (UTC)</strong>
                                    </div>
                                    <div className="mb-2">
                                        <small className="text-muted">
                                            <i className="fas fa-map-marker-alt me-1"></i>
                                            Local Time ({Intl.DateTimeFormat().resolvedOptions().timeZone}): 
                                            <strong className="ms-1">
                                                {(() => {
                                                    const parts = task.cronExpression.split(' ');
                                                    if (parts.length >= 3) {
                                                        const utcHour = parseInt(parts[2]) || 0;
                                                        const utcMinute = parseInt(parts[1]) || 0;
                                                        const localTime = convertUTCToLocal(utcHour, utcMinute);
                                                        const localHour12 = localTime.hour === 0 ? 12 : localTime.hour > 12 ? localTime.hour - 12 : localTime.hour;
                                                        const localAmPm = localTime.hour >= 12 ? 'PM' : 'AM';
                                                        return `${localHour12}:${localTime.minute.toString().padStart(2, '0')} ${localAmPm}`;
                                                    }
                                                    return 'Invalid';
                                                })()}
                                            </strong>
                                        </small>
                                    </div>
                                    <div className="d-flex gap-3 text-muted small">
                                        <span>
                                            <i className="fas fa-globe me-1"></i>
                                            {(() => {
                                                const parts = task.cronExpression.split(' ');
                                                if (parts.length >= 3) {
                                                    const utcHour = parseInt(parts[2]) || 0;
                                                    const utcMinute = parseInt(parts[1]) || 0;
                                                    return `${utcHour.toString().padStart(2, '0')}:${utcMinute.toString().padStart(2, '0')} UTC`;
                                                }
                                                return 'Invalid';
                                            })()}
                                        </span>
                                        <span>
                                            <code className="text-muted">{task.cronExpression}</code>
                                        </span>
                                    </div>
                                </div>
                            )}
                            
                            <Row className="mt-3">
                                <Col md={6}>
                                    <div className="p-3 border rounded">
                                        <small className="text-muted d-block mb-2">
                                            <i className="fas fa-history me-1"></i>
                                            Last Run
                                            <span className="ms-1">({Intl.DateTimeFormat().resolvedOptions().timeZone})</span>
                                        </small>
                                        <div>
                                            {task?.lastRun ? (
                                                <strong className="text-success">
                                                    {formatDate(task.lastRun)}
                                                </strong>
                                            ) : (
                                                <span className="text-muted">Never executed</span>
                                            )}
                                        </div>
                                    </div>
                                </Col>
                                <Col md={6}>
                                    <div className="p-3 border rounded">
                                        <small className="text-muted d-block mb-2">
                                            <i className="fas fa-clock me-1"></i>
                                            Next Run
                                            <span className="ms-1">({Intl.DateTimeFormat().resolvedOptions().timeZone})</span>
                                        </small>
                                        <div>
                                            {task?.nextRun ? (
                                                <strong style={{ color: '#ff6b35' }}>
                                                    {formatDate(task.nextRun)}
                                                </strong>
                                            ) : (
                                                <span className="text-muted">Not scheduled</span>
                                            )}
                                        </div>
                                    </div>
                                </Col>
                            </Row>
                        </div>
                    )}
                </Card.Body>
            </Card>

            <div className="row">
                <div className="col-md-8">
                    <Card className="mb-4">
                        <Card.Body>
                            <Form onSubmit={(e) => e.preventDefault()}>
                                <Form.Group className="mb-3">
                                    <div className="d-flex justify-content-between align-items-center mb-2">
                                        <Form.Label>Linq Configuration</Form.Label>
                                        <button
                                            className="btn btn-sm btn-outline-secondary"
                                            onClick={formatJson}
                                            title="Format JSON"
                                            type="button"
                                        >
                                            <i className="fas fa-code"></i> Format
                                        </button>
                                    </div>
                                    {validationError && (
                                        <div className="alert alert-warning alert-sm mb-2">
                                            <small><strong>JSON Error:</strong> {validationError}</small>
                                        </div>
                                    )}
                                    <Form.Control
                                        as="textarea"
                                        name="config"
                                        value={configText}
                                        onChange={handleConfigTextChange}
                                        onFocus={handleConfigFocus}
                                        onBlur={handleConfigBlur}
                                        rows={25}
                                        className="font-monospace"
                                        style={{
                                            fontSize: '12px',
                                            lineHeight: '1.4',
                                            backgroundColor: '#f8f9fa',
                                            border: validationError ? '1px solid #ffc107' : '1px solid #dee2e6'
                                        }}
                                        placeholder="Enter JSON configuration..."
                                    />
                                </Form.Group>

                                <div className="d-flex justify-content-end">
                                    <OverlayTrigger
                                        placement="top"
                                        overlay={
                                            <Tooltip id="save-tooltip">
                                                {canEditTask ? 'Save changes to create a new version' : 'Only team admins can save changes'}
                                            </Tooltip>
                                        }
                                    >
                                        <div>
                                            <Button 
                                                variant="primary" 
                                                onClick={() => setShowConfirmModal(true)}
                                                disabled={saving || !canEditTask || validationError}
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

                    {/* Workflow Steps Visualization */}
                    {task?.linq_config && (() => {
                        // For WORKFLOW_TRIGGER: Use the linked workflow
                        if (task.linq_config.query?.workflowId && linkedWorkflow) {
                            return <StepDescriptions workflow={linkedWorkflow.request} />;
                        }
                        // For WORKFLOW_EMBEDDED: Use the embedded workflow from linq_config
                        if (task.linq_config.query?.workflow) {
                            return <StepDescriptions workflow={task.linq_config} />;
                        }
                        // Show loading if we're fetching a linked workflow
                        if (task.linq_config.query?.workflowId && loadingWorkflow) {
                            return (
                                <div className="text-center my-3">
                                    <Spinner animation="border" size="sm" />
                                    <span className="ms-2">Loading workflow steps...</span>
                                </div>
                            );
                        }
                        return null;
                    })()}

                    {/* Task Statistics Section */}
                    <Card className="mb-4">
                        <Card.Header>
                            <h5 className="mb-0">Task Statistics</h5>
                        </Card.Header>
                        <Card.Body>
                            {loadingStats ? (
                                <div className="text-center">
                                    <Spinner animation="border" role="status">
                                        <span className="visually-hidden">Loading stats...</span>
                                    </Spinner>
                                </div>
                            ) : stats ? (
                                <div>
                                    <Row className="mb-4">
                                        <Col md={6}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Total Executions</h6>
                                                    <h3>{stats.totalExecutions}</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                        <Col md={6}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Success</h6>
                                                    <h3 className="text-success">{stats.successfulExecutions}</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    </Row>

                                    <Row className="mb-4">
                                        <Col md={6}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Failed</h6>
                                                    <h3 className="text-danger">{stats.failedExecutions}</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                        <Col md={6}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Success Rate</h6>
                                                    <h3 className="text-info">{stats.successRate.toFixed(1)}%</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    </Row>

                                    <Row className="mb-4">
                                        <Col md={12}>
                                            <Card className="stats-card">
                                                <Card.Body>
                                                    <h6>Avg Execution Time</h6>
                                                    <h3>{stats.averageExecutionTime.toFixed(2)}ms</h3>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    </Row>
                                </div>
                            ) : (
                                <div className="text-center text-muted">
                                    <p>No execution data available</p>
                                </div>
                            )}
                        </Card.Body>
                    </Card>
                </div>

                <div className="col-md-4">
                    <Card className="mb-4">
                        <Card.Body className="task-details">
                            <h5>Task Details</h5>
                            <div className="detail-item">
                                <div className="detail-label">ID</div>
                                <div className="detail-value">{task?.id}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Version</div>
                                <div className="detail-value">v{task?.version || 'N/A'}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Name</div>
                                <div className="detail-value">{task?.name}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Agent</div>
                                <div className="detail-value">
                                    {agent ? (
                                        <div>
                                            <div>{agent.name}</div>
                                            <small className="text-muted">ID: {task?.agentId}</small>
                                        </div>
                                    ) : (
                                        task?.agentId
                                    )}
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Task Type</div>
                                <div className="detail-value">
                                    <Badge bg={
                                        task?.taskType === 'WORKFLOW_TRIGGER' ? 'primary' :
                                        task?.taskType === 'WORKFLOW_EMBEDDED' ? 'info' :
                                        task?.taskType === 'API_CALL' ? 'warning' :
                                        'secondary'
                                    }>
                                        {task?.taskType?.replace(/_/g, ' ')}
                                    </Badge>
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Status</div>
                                <div className="detail-value">
                                    <Badge bg={task?.enabled ? 'success' : 'secondary'}>
                                        {task?.enabled ? 'Active' : 'Inactive'}
                                    </Badge>
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Execution Trigger</div>
                                <div className="detail-value">
                                    <Badge bg={
                                        task?.executionTrigger === 'MANUAL' ? 'secondary' :
                                        task?.executionTrigger === 'CRON' ? 'primary' :
                                        task?.executionTrigger === 'EVENT_DRIVEN' ? 'warning' :
                                        'info'
                                    }>
                                        {task?.executionTrigger}
                                    </Badge>
                                </div>
                            </div>
                            {task?.cronExpression && (
                                <>
                                    <div className="detail-item">
                                        <div className="detail-label">Cron Expression</div>
                                        <div className="detail-value">
                                            <code>{task.cronExpression}</code>
                                        </div>
                                    </div>
                                    <div className="detail-item">
                                        <div className="detail-label">Cron Description</div>
                                        <div className="detail-value">{task.cronDescription || 'N/A'}</div>
                                    </div>
                                </>
                            )}
                            <div className="detail-item">
                                <div className="detail-label">Priority</div>
                                <div className="detail-value">{task?.priority || 'N/A'}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Max Retries</div>
                                <div className="detail-value">{task?.maxRetries || 0}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Timeout</div>
                                <div className="detail-value">{task?.timeoutMinutes || 0} minutes</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Schedule on Startup</div>
                                <div className="detail-value">
                                    <Badge bg={task?.scheduleOnStartup ? 'success' : 'secondary'}>
                                        {task?.scheduleOnStartup ? 'Yes' : 'No'}
                                    </Badge>
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Ready to Execute</div>
                                <div className="detail-value">
                                    <Badge bg={task?.readyToExecute ? 'success' : 'warning'}>
                                        {task?.readyToExecute ? 'Yes' : 'No'}
                                    </Badge>
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Auto Execute</div>
                                <div className="detail-value">
                                    <Badge bg={task?.autoExecute ? 'success' : 'secondary'}>
                                        {task?.autoExecute ? 'Yes' : 'No'}
                                    </Badge>
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Linq Target</div>
                                <div className="detail-value">{task?.linqTarget || 'N/A'}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Linq Action</div>
                                <div className="detail-value">{task?.linqAction || 'N/A'}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Linq Intent</div>
                                <div className="detail-value">{task?.linqIntent || 'N/A'}</div>
                            </div>
                            {task?.linqParams && Object.keys(task.linqParams).length > 0 && (
                                <div className="detail-item">
                                    <div className="detail-label">Linq Params</div>
                                    <div className="detail-value">
                                        <pre style={{ fontSize: '0.8rem', marginBottom: 0, textAlign: 'left' }}>
                                            {JSON.stringify(task.linqParams, null, 2)}
                                        </pre>
                                    </div>
                                </div>
                            )}
                            {task?.scheduled && (
                                <div className="detail-item">
                                    <div className="detail-label">Scheduled</div>
                                    <div className="detail-value">
                                        <Badge bg="info">
                                            <i className="fas fa-calendar-check me-1"></i>
                                            Yes
                                        </Badge>
                                    </div>
                                </div>
                            )}
                            {task?.cronTrigger && (
                                <div className="detail-item">
                                    <div className="detail-label">Cron Trigger</div>
                                    <div className="detail-value">
                                        <Badge bg="primary">
                                            <i className="fas fa-clock me-1"></i>
                                            Enabled
                                        </Badge>
                                    </div>
                                </div>
                            )}
                            {task?.manualTrigger && (
                                <div className="detail-item">
                                    <div className="detail-label">Manual Trigger</div>
                                    <div className="detail-value">
                                        <Badge bg="secondary">
                                            <i className="fas fa-hand-pointer me-1"></i>
                                            Enabled
                                        </Badge>
                                    </div>
                                </div>
                            )}
                            {task?.eventDrivenTrigger && (
                                <div className="detail-item">
                                    <div className="detail-label">Event Driven</div>
                                    <div className="detail-value">
                                        <Badge bg="warning">
                                            <i className="fas fa-bolt me-1"></i>
                                            Enabled
                                        </Badge>
                                    </div>
                                </div>
                            )}
                            {task?.workflowTrigger && (
                                <div className="detail-item">
                                    <div className="detail-label">Workflow Trigger</div>
                                    <div className="detail-value">
                                        <Badge bg="success">
                                            <i className="fas fa-project-diagram me-1"></i>
                                            Enabled
                                        </Badge>
                                    </div>
                                </div>
                            )}
                            {task?.workflowTriggerTask && (
                                <div className="detail-item">
                                    <div className="detail-label">Workflow Trigger Task</div>
                                    <div className="detail-value">
                                        <Badge bg="primary">
                                            <i className="fas fa-tasks me-1"></i>
                                            Yes
                                        </Badge>
                                    </div>
                                </div>
                            )}
                            {task?.workflowEmbeddedTask && (
                                <div className="detail-item">
                                    <div className="detail-label">Workflow Embedded Task</div>
                                    <div className="detail-value">
                                        <Badge bg="info">
                                            <i className="fas fa-code-branch me-1"></i>
                                            Yes
                                        </Badge>
                                    </div>
                                </div>
                            )}
                            {task?.autoExecute && (
                                <div className="detail-item">
                                    <div className="detail-label">Auto Execute</div>
                                    <div className="detail-value">
                                        <Badge bg="warning">
                                            <i className="fas fa-bolt me-1"></i>
                                            Enabled
                                        </Badge>
                                    </div>
                                </div>
                            )}
                            <div className="detail-item">
                                <div className="detail-label">Execution Trigger Valid</div>
                                <div className="detail-value">
                                    <Badge bg={task?.executionTriggerValid ? 'success' : 'danger'}>
                                        <i className={`fas fa-${task?.executionTriggerValid ? 'check-circle' : 'times-circle'} me-1`}></i>
                                        {task?.executionTriggerValid ? 'Valid' : 'Invalid'}
                                    </Badge>
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Task Configuration Valid</div>
                                <div className="detail-value">
                                    <Badge bg={task?.taskTypeConfigurationValid ? 'success' : 'danger'}>
                                        <i className={`fas fa-${task?.taskTypeConfigurationValid ? 'check-circle' : 'times-circle'} me-1`}></i>
                                        {task?.taskTypeConfigurationValid ? 'Valid' : 'Invalid'}
                                    </Badge>
                                </div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Last Run</div>
                                <div className="detail-value">{formatDate(task?.lastRun)}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Next Run</div>
                                <div className="detail-value">{formatDate(task?.nextRun)}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Created At</div>
                                <div className="detail-value">{formatDate(task?.createdAt)}</div>
                            </div>
                            <div className="detail-item">
                                <div className="detail-label">Updated At</div>
                                <div className="detail-value">{formatDate(task?.updatedAt)}</div>
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
                                            {version.version === task.version && (
                                                <Badge bg="success">Current</Badge>
                                            )}
                                        </div>
                                        <small className="text-muted">
                                            {formatDate(version.createdAt)}
                                        </small>
                                    </div>
                                    <p className="version-description mb-2">
                                        Version {version.version} - {formatDate(version.createdAt)}
                                    </p>
                                    <div className="d-flex justify-content-between align-items-center">
                                        <small className="text-muted">
                                            By {version.createdBy || 'System'}
                                        </small>
                                        {version.version !== task?.version && (
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
                                                            {canEditTask ? 'Rollback to this version' : 'Only team admins can rollback versions'}
                                                        </Tooltip>
                                                    }
                                                >
                                                    <div>
                                                        <Button
                                                            variant="outline-primary"
                                                            size="sm"
                                                            onClick={() => handleRollbackClick(version)}
                                                            disabled={!canEditTask}
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
                </div>
            </div>

            {/* Execution History */}
            {executionHistory && executionHistory.length > 0 && (
                <Card className="mb-4">
                    <Card.Header>
                        <h5 className="mb-0">Execution History ({executionHistory.length} total)</h5>
                    </Card.Header>
                    <Card.Body className="p-0">
                        <TableContainer 
                            component={Paper} 
                            sx={{ 
                                maxHeight: 600,
                                boxShadow: 'none',
                                '& .MuiTableHead-root': {
                                    position: 'sticky',
                                    top: 0,
                                    backgroundColor: '#f8f9fa',
                                    zIndex: 10
                                }
                            }}
                        >
                            <Table stickyHeader>
                                <TableHead>
                                    <TableRow>
                                        <TableCell sx={{ fontWeight: 'bold', backgroundColor: '#f8f9fa', whiteSpace: 'nowrap' }}>
                                            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                                                Execution ID
                                                <TextField
                                                    size="small"
                                                    variant="outlined"
                                                    placeholder="Filter"
                                                    value={executionFilters.executionId}
                                                    onChange={handleExecutionFilterChange('executionId')}
                                                    sx={{ mt: 1 }}
                                                    slotProps={{
                                                        input: {
                                                            startAdornment: (
                                                                <InputAdornment position="start">
                                                                    <SearchIcon fontSize="small" />
                                                                </InputAdornment>
                                                            )
                                                        }
                                                    }}
                                                />
                                            </Box>
                                        </TableCell>
                                        <TableCell sx={{ fontWeight: 'bold', backgroundColor: '#f8f9fa', minWidth: 150 }}>
                                            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                                                Started At
                                                <TextField
                                                    size="small"
                                                    variant="outlined"
                                                    placeholder="Filter"
                                                    value={executionFilters.startedAt}
                                                    onChange={handleExecutionFilterChange('startedAt')}
                                                    sx={{ mt: 1 }}
                                                    slotProps={{
                                                        input: {
                                                            startAdornment: (
                                                                <InputAdornment position="start">
                                                                    <SearchIcon fontSize="small" />
                                                                </InputAdornment>
                                                            )
                                                        }
                                                    }}
                                                />
                                            </Box>
                                        </TableCell>
                                        <TableCell sx={{ fontWeight: 'bold', backgroundColor: '#f8f9fa', minWidth: 150 }}>
                                            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                                                Completed At
                                                <TextField
                                                    size="small"
                                                    variant="outlined"
                                                    placeholder="Filter"
                                                    value={executionFilters.completedAt}
                                                    onChange={handleExecutionFilterChange('completedAt')}
                                                    sx={{ mt: 1 }}
                                                    slotProps={{
                                                        input: {
                                                            startAdornment: (
                                                                <InputAdornment position="start">
                                                                    <SearchIcon fontSize="small" />
                                                                </InputAdornment>
                                                            )
                                                        }
                                                    }}
                                                />
                                            </Box>
                                        </TableCell>
                                        <TableCell sx={{ fontWeight: 'bold', backgroundColor: '#f8f9fa', minWidth: 120 }}>
                                            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                                                Duration (ms)
                                                <TextField
                                                    size="small"
                                                    variant="outlined"
                                                    placeholder="Filter"
                                                    value={executionFilters.duration}
                                                    onChange={handleExecutionFilterChange('duration')}
                                                    sx={{ mt: 1 }}
                                                    slotProps={{
                                                        input: {
                                                            startAdornment: (
                                                                <InputAdornment position="start">
                                                                    <SearchIcon fontSize="small" />
                                                                </InputAdornment>
                                                            )
                                                        }
                                                    }}
                                                />
                                            </Box>
                                        </TableCell>
                                        <TableCell sx={{ fontWeight: 'bold', backgroundColor: '#f8f9fa', minWidth: 120 }}>
                                            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                                                Status
                                                <TextField
                                                    size="small"
                                                    variant="outlined"
                                                    placeholder="Filter"
                                                    value={executionFilters.status}
                                                    onChange={handleExecutionFilterChange('status')}
                                                    sx={{ mt: 1 }}
                                                    slotProps={{
                                                        input: {
                                                            startAdornment: (
                                                                <InputAdornment position="start">
                                                                    <SearchIcon fontSize="small" />
                                                                </InputAdornment>
                                                            )
                                                        }
                                                    }}
                                                />
                                            </Box>
                                        </TableCell>
                                        <TableCell sx={{ fontWeight: 'bold', backgroundColor: '#f8f9fa', minWidth: 120 }}>
                                            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                                                Result
                                                <TextField
                                                    size="small"
                                                    variant="outlined"
                                                    placeholder="Filter"
                                                    value={executionFilters.result}
                                                    onChange={handleExecutionFilterChange('result')}
                                                    sx={{ mt: 1 }}
                                                    slotProps={{
                                                        input: {
                                                            startAdornment: (
                                                                <InputAdornment position="start">
                                                                    <SearchIcon fontSize="small" />
                                                                </InputAdornment>
                                                            )
                                                        }
                                                    }}
                                                />
                                            </Box>
                                        </TableCell>
                                        <TableCell sx={{ fontWeight: 'bold', backgroundColor: '#f8f9fa', width: 80 }}>
                                            Error
                                        </TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {filteredExecutions.map((execution) => (
                                        <TableRow 
                                            key={execution.executionId}
                                            onClick={() => handleExecutionClick(execution)}
                                            sx={{ 
                                                cursor: 'pointer',
                                                '&:hover': {
                                                    backgroundColor: '#f5f5f5'
                                                }
                                            }}
                                        >
                                            <TableCell sx={{ whiteSpace: 'nowrap' }}>
                                                <small style={{ fontFamily: 'monospace' }}>
                                                    {execution.executionId}
                                                </small>
                                            </TableCell>
                                            <TableCell>{formatDate(execution.startedAt)}</TableCell>
                                            <TableCell>{formatDate(execution.completedAt)}</TableCell>
                                            <TableCell>{execution.executionDurationMs}</TableCell>
                                            <TableCell>
                                                <Badge bg={
                                                    execution.status === 'COMPLETED' ? 'success' :
                                                    execution.status === 'RUNNING' ? 'primary' :
                                                    execution.status === 'FAILED' ? 'danger' :
                                                    'secondary'
                                                }>
                                                    {execution.status}
                                                </Badge>
                                            </TableCell>
                                            <TableCell>
                                                <Badge bg={
                                                    execution.result === 'SUCCESS' ? 'success' :
                                                    execution.result === 'FAILURE' ? 'danger' :
                                                    execution.result === 'PARTIAL_SUCCESS' ? 'warning' :
                                                    'secondary'
                                                }>
                                                    {execution.result}
                                                </Badge>
                                            </TableCell>
                                            <TableCell>
                                                {execution.errorMessage ? (
                                                    <OverlayTrigger
                                                        placement="left"
                                                        overlay={
                                                            <Tooltip id={`error-${execution.executionId}`}>
                                                                {execution.errorMessage}
                                                            </Tooltip>
                                                        }
                                                    >
                                                        <i className="fas fa-exclamation-circle text-danger"></i>
                                                    </OverlayTrigger>
                                                ) : (
                                                    <span className="text-muted">-</span>
                                                )}
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </Card.Body>
                </Card>
            )}

            {/* Workflow Step & Target Analysis */}
            {workflowStats && workflowStats.stepStats && Object.keys(workflowStats.stepStats).length > 0 && (
                <Card className="mb-4">
                    <Card.Header>
                        <h5 className="mb-0">Workflow Analysis</h5>
                    </Card.Header>
                    <Card.Body>
                        <Row className="mb-4">
                            <Col md={6}>
                                <Card>
                                    <Card.Body>
                                        <h6>Step Performance</h6>
                                        <ResponsiveContainer width="100%" height={300}>
                                            <BarChart data={Object.entries(workflowStats.stepStats).map(([step, data]) => ({
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
                                                    data={Object.entries(workflowStats.targetStats).map(([target, data]) => ({
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
                                                    {Object.entries(workflowStats.targetStats).map((entry, index) => (
                                                        <Cell key={`cell-${index}`} fill={['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8', '#82ca9d'][index % 6]} />
                                                    ))}
                                                </Pie>
                                                <RechartsTooltip 
                                                    formatter={(value, name) => {
                                                        const total = Object.values(workflowStats.targetStats)
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
                    </Card.Body>
                </Card>
            )}

            {/* Performance Visualizations */}
            {executionHistory && executionHistory.length > 0 && (
                <Card className="mb-4">
                    <Card.Header>
                        <h5 className="mb-0">Performance Insights</h5>
                    </Card.Header>
                    <Card.Body>
                        <Row className="mb-4">
                            <Col md={6}>
                                <Card>
                                    <Card.Body>
                                        <h6>Execution Status Distribution</h6>
                                        <ResponsiveContainer width="100%" height={300}>
                                            <PieChart>
                                                <Pie
                                                    data={(() => {
                                                        const statusCounts = executionHistory.reduce((acc, exec) => {
                                                            acc[exec.status] = (acc[exec.status] || 0) + 1;
                                                            return acc;
                                                        }, {});
                                                        return Object.entries(statusCounts).map(([status, count]) => ({
                                                            name: status,
                                                            value: count
                                                        }));
                                                    })()}
                                                    dataKey="value"
                                                    nameKey="name"
                                                    cx="50%"
                                                    cy="50%"
                                                    outerRadius={100}
                                                    label
                                                >
                                                    {Object.keys(executionHistory.reduce((acc, exec) => {
                                                        acc[exec.status] = true;
                                                        return acc;
                                                    }, {})).map((entry, index) => (
                                                        <Cell key={`cell-${index}`} fill={['#28a745', '#007bff', '#dc3545', '#6c757d'][index % 4]} />
                                                    ))}
                                                </Pie>
                                                <RechartsTooltip 
                                                    formatter={(value, name) => {
                                                        const total = executionHistory.length;
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
                            <Col md={6}>
                                <Card>
                                    <Card.Body>
                                        <h6>Result Distribution</h6>
                                        <ResponsiveContainer width="100%" height={300}>
                                            <PieChart>
                                                <Pie
                                                    data={(() => {
                                                        const resultCounts = executionHistory.reduce((acc, exec) => {
                                                            acc[exec.result] = (acc[exec.result] || 0) + 1;
                                                            return acc;
                                                        }, {});
                                                        return Object.entries(resultCounts).map(([result, count]) => ({
                                                            name: result,
                                                            value: count
                                                        }));
                                                    })()}
                                                    dataKey="value"
                                                    nameKey="name"
                                                    cx="50%"
                                                    cy="50%"
                                                    outerRadius={100}
                                                    label
                                                >
                                                    {Object.keys(executionHistory.reduce((acc, exec) => {
                                                        acc[exec.result] = true;
                                                        return acc;
                                                    }, {})).map((entry, index) => (
                                                        <Cell key={`cell-${index}`} fill={['#28a745', '#dc3545', '#ffc107', '#6c757d'][index % 4]} />
                                                    ))}
                                                </Pie>
                                                <RechartsTooltip 
                                                    formatter={(value, name) => {
                                                        const total = executionHistory.length;
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
                                        <h6>Execution Duration Over Time</h6>
                                        <ResponsiveContainer width="100%" height={400}>
                                            <BarChart data={executionHistory.map((exec, index) => ({
                                                execution: `#${executionHistory.length - index}`,
                                                duration: exec.executionDurationMs,
                                                time: formatDate(exec.startedAt)
                                            })).reverse()}>
                                                <CartesianGrid strokeDasharray="3 3" />
                                                <XAxis 
                                                    dataKey="execution" 
                                                    angle={-45}
                                                    textAnchor="end"
                                                    height={80}
                                                    interval={0}
                                                    tick={{ fontSize: 12 }}
                                                />
                                                <YAxis label={{ value: 'Duration (ms)', angle: -90, position: 'insideLeft' }} />
                                                <RechartsTooltip 
                                                    formatter={(value) => [`${value}ms`, 'Duration']}
                                                    labelFormatter={(label) => `Execution ${label}`}
                                                    contentStyle={{
                                                        backgroundColor: 'rgba(255, 255, 255, 0.9)',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        padding: '10px'
                                                    }}
                                                />
                                                <Legend />
                                                <Bar dataKey="duration" name="Execution Duration (ms)" fill="#8884d8" />
                                            </BarChart>
                                        </ResponsiveContainer>
                                    </Card.Body>
                                </Card>
                            </Col>
                        </Row>
                    </Card.Body>
                </Card>
            )}

            {/* Compare Modal */}
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
                            <div className="row g-3 align-items-start">
                                <div className="col-5">
                                    <div className="version-content p-3 bg-light rounded">
                                        <h6 className="mb-3">Version {compareVersions.version1} (Current)</h6>
                                        <div className="diff-view">
                                            {/* Basic Information */}
                                            <div className="diff-section mb-3">
                                                <h6 className="diff-title">Basic Information</h6>
                                                <pre className="diff-content">
                                                    {renderJsonWithHighlights(
                                                        {
                                                            name: task?.name,
                                                            description: task?.description,
                                                            taskType: task?.taskType,
                                                            priority: task?.priority,
                                                            enabled: task?.enabled,
                                                            maxRetries: task?.maxRetries,
                                                            timeoutMinutes: task?.timeoutMinutes
                                                        },
                                                        highlightDifferences(
                                                            {
                                                                name: task?.name,
                                                                description: task?.description,
                                                                taskType: task?.taskType,
                                                                priority: task?.priority,
                                                                enabled: task?.enabled,
                                                                maxRetries: task?.maxRetries,
                                                                timeoutMinutes: task?.timeoutMinutes
                                                            },
                                                            (() => {
                                                                const v = versions.find(v => v.version === compareVersions.version2);
                                                                return {
                                                                    name: v?.name,
                                                                    description: v?.description,
                                                                    taskType: v?.taskType,
                                                                    priority: v?.priority,
                                                                    enabled: v?.enabled,
                                                                    maxRetries: v?.maxRetries,
                                                                    timeoutMinutes: v?.timeoutMinutes
                                                                };
                                                            })()
                                                        )
                                                    )}
                                                </pre>
                                            </div>
                                            
                                            {/* Scheduling Information */}
                                            <div className="diff-section mb-3">
                                                <h6 className="diff-title">Scheduling</h6>
                                                <pre className="diff-content">
                                                    {renderJsonWithHighlights(
                                                        {
                                                            executionTrigger: task?.executionTrigger,
                                                            cronExpression: task?.cronExpression,
                                                            cronDescription: task?.cronDescription,
                                                            scheduleOnStartup: task?.scheduleOnStartup
                                                        },
                                                        highlightDifferences(
                                                            {
                                                                executionTrigger: task?.executionTrigger,
                                                                cronExpression: task?.cronExpression,
                                                                cronDescription: task?.cronDescription,
                                                                scheduleOnStartup: task?.scheduleOnStartup
                                                            },
                                                            (() => {
                                                                const v = versions.find(v => v.version === compareVersions.version2);
                                                                return {
                                                                    executionTrigger: v?.executionTrigger,
                                                                    cronExpression: v?.cronExpression,
                                                                    cronDescription: v?.cronDescription,
                                                                    scheduleOnStartup: v?.scheduleOnStartup
                                                                };
                                                            })()
                                                        )
                                                    )}
                                                </pre>
                                            </div>
                                            
                                            {/* Linq Configuration */}
                                            <div className="diff-section">
                                                <h6 className="diff-title">Linq Configuration</h6>
                                                <pre className="diff-content">
                                                    {renderJsonWithHighlights(
                                                        task?.linq_config || {},
                                                        highlightDifferences(
                                                            task?.linq_config || {},
                                                            versions.find(v => v.version === compareVersions.version2)?.linq_config || {}
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
                                            {(() => {
                                                const v = versions.find(v => v.version === compareVersions.version2);
                                                return (
                                                    <>
                                                        {/* Basic Information */}
                                                        <div className="diff-section mb-3">
                                                            <h6 className="diff-title">Basic Information</h6>
                                                            <pre className="diff-content">
                                                                {renderJsonWithHighlights(
                                                                    {
                                                                        name: v?.name,
                                                                        description: v?.description,
                                                                        taskType: v?.taskType,
                                                                        priority: v?.priority,
                                                                        enabled: v?.enabled,
                                                                        maxRetries: v?.maxRetries,
                                                                        timeoutMinutes: v?.timeoutMinutes
                                                                    },
                                                                    highlightDifferences(
                                                                        {
                                                                            name: task?.name,
                                                                            description: task?.description,
                                                                            taskType: task?.taskType,
                                                                            priority: task?.priority,
                                                                            enabled: task?.enabled,
                                                                            maxRetries: task?.maxRetries,
                                                                            timeoutMinutes: task?.timeoutMinutes
                                                                        },
                                                                        {
                                                                            name: v?.name,
                                                                            description: v?.description,
                                                                            taskType: v?.taskType,
                                                                            priority: v?.priority,
                                                                            enabled: v?.enabled,
                                                                            maxRetries: v?.maxRetries,
                                                                            timeoutMinutes: v?.timeoutMinutes
                                                                        }
                                                                    )
                                                                )}
                                                            </pre>
                                                        </div>
                                                        
                                                        {/* Scheduling Information */}
                                                        <div className="diff-section mb-3">
                                                            <h6 className="diff-title">Scheduling</h6>
                                                            <pre className="diff-content">
                                                                {renderJsonWithHighlights(
                                                                    {
                                                                        executionTrigger: v?.executionTrigger,
                                                                        cronExpression: v?.cronExpression,
                                                                        cronDescription: v?.cronDescription,
                                                                        scheduleOnStartup: v?.scheduleOnStartup
                                                                    },
                                                                    highlightDifferences(
                                                                        {
                                                                            executionTrigger: task?.executionTrigger,
                                                                            cronExpression: task?.cronExpression,
                                                                            cronDescription: task?.cronDescription,
                                                                            scheduleOnStartup: task?.scheduleOnStartup
                                                                        },
                                                                        {
                                                                            executionTrigger: v?.executionTrigger,
                                                                            cronExpression: v?.cronExpression,
                                                                            cronDescription: v?.cronDescription,
                                                                            scheduleOnStartup: v?.scheduleOnStartup
                                                                        }
                                                                    )
                                                                )}
                                                            </pre>
                                                        </div>
                                                        
                                                        {/* Linq Configuration */}
                                                        <div className="diff-section">
                                                            <h6 className="diff-title">Linq Configuration</h6>
                                                            <pre className="diff-content">
                                                                {renderJsonWithHighlights(
                                                                    v?.linq_config || {},
                                                                    highlightDifferences(
                                                                        task?.linq_config || {},
                                                                        v?.linq_config || {}
                                                                    )
                                                                )}
                                                            </pre>
                                                        </div>
                                                    </>
                                                );
                                            })()}
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

            {/* Rollback Confirmation Modal */}
            <ConfirmationModal
                show={showRollbackModal}
                onHide={() => {
                    setShowRollbackModal(false);
                    setSelectedVersion(null);
                }}
                onConfirm={handleRollback}
                title="Rollback Version"
                message={`Are you sure you want to rollback from version ${task?.version} to version ${selectedVersion?.version}?`}
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

            {/* Save Confirmation Modal */}
            <ConfirmationModal
                show={showConfirmModal}
                onHide={() => setShowConfirmModal(false)}
                onConfirm={handleSave}
                title="Save Changes"
                message="Are you sure you want to save these changes? This will create a new version of the task."
            />

            {/* Metadata Edit Modal */}
            <Modal
                show={showMetadataModal}
                onHide={() => setShowMetadataModal(false)}
                centered
            >
                <Modal.Header closeButton>
                    <Modal.Title>Edit Task Details</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label>Name</Form.Label>
                            <Form.Control
                                type="text"
                                name="name"
                                value={task?.name || ''}
                                onChange={handleInputChange}
                                placeholder="Enter task name"
                            />
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Description</Form.Label>
                            <Form.Control
                                as="textarea"
                                name="description"
                                value={task?.description || ''}
                                onChange={handleInputChange}
                                placeholder="Enter task description"
                                rows={3}
                            />
                        </Form.Group>

                        <Row className="mb-3">
                            <Col md={4}>
                                <Form.Group>
                                    <Form.Label>Priority</Form.Label>
                                    <Form.Control
                                        type="number"
                                        name="priority"
                                        value={task?.priority || 1}
                                        onChange={handleInputChange}
                                        min="1"
                                        max="10"
                                    />
                                </Form.Group>
                            </Col>
                            <Col md={4}>
                                <Form.Group>
                                    <Form.Label>Max Retries</Form.Label>
                                    <Form.Control
                                        type="number"
                                        name="maxRetries"
                                        value={task?.maxRetries || 0}
                                        onChange={handleInputChange}
                                        min="0"
                                    />
                                </Form.Group>
                            </Col>
                            <Col md={4}>
                                <Form.Group>
                                    <Form.Label>Timeout (minutes)</Form.Label>
                                    <Form.Control
                                        type="number"
                                        name="timeoutMinutes"
                                        value={task?.timeoutMinutes || 120}
                                        onChange={handleInputChange}
                                        min="1"
                                    />
                                </Form.Group>
                            </Col>
                        </Row>

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
                                {canEditTask ? 'Save task details' : 'Only team admins can save task details'}
                            </Tooltip>
                        }
                    >
                        <div>
                            <Button 
                                variant="primary" 
                                onClick={handleMetadataSave}
                                disabled={!canEditTask}
                            >
                                Save Changes
                            </Button>
                        </div>
                    </OverlayTrigger>
                </Modal.Footer>
            </Modal>

            {/* Execute Confirmation Modal */}
            <ConfirmationModal
                show={showExecuteConfirm}
                onHide={() => setShowExecuteConfirm(false)}
                onConfirm={handleExecute}
                title="Execute Task"
                message={`Are you sure you want to execute "${task?.name}"?`}
                confirmLabel={executing ? (
                    <>
                        <Spinner
                            as="span"
                            animation="border"
                            size="sm"
                            role="status"
                            aria-hidden="true"
                            className="me-2"
                        />
                        Executing...
                    </>
                ) : "Execute"}
                variant="primary"
                disabled={executing}
            />

            {/* Scheduling Configuration Modal */}
            <Modal show={showSchedulingModal} onHide={() => setShowSchedulingModal(false)} size="lg">
                <Modal.Header closeButton>
                    <Modal.Title>
                        <i className="fas fa-clock me-2"></i>
                        Configure Task Scheduling
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <Form>
                        {/* Section 1: Cron Expression Input */}
                        <Card className="mb-3 border-0">
                            <Card.Header className="bg-light px-0">
                                <h6 className="mb-0">
                                    <i className="fas fa-calendar-alt me-2"></i>
                                    Cron Expression (UTC)
                                </h6>
                            </Card.Header>
                            <Card.Body className="px-0">
                                <div className="cron-input-container">
                                    <div className="cron-field-group">
                                        <div className="cron-field">
                                            <label className="cron-field-label">Seconds</label>
                                            <Form.Control
                                                type="text"
                                                value={cronFields.seconds}
                                                onChange={(e) => setCronFields(prev => ({ ...prev, seconds: e.target.value }))}
                                                placeholder="0"
                                                maxLength={6}
                                                className="cron-input"
                                            />
                                        </div>
                                        <div className="cron-field">
                                            <label className="cron-field-label">Minutes</label>
                                            <Form.Control
                                                type="text"
                                                value={cronFields.minutes}
                                                onChange={(e) => setCronFields(prev => ({ ...prev, minutes: e.target.value }))}
                                                placeholder="0"
                                                maxLength={6}
                                                className="cron-input"
                                            />
                                        </div>
                                        <div className="cron-field">
                                            <label className="cron-field-label">Hours</label>
                                            <Form.Control
                                                type="text"
                                                value={cronFields.hours}
                                                onChange={(e) => setCronFields(prev => ({ ...prev, hours: e.target.value }))}
                                                placeholder="9"
                                                maxLength={6}
                                                className="cron-input"
                                            />
                                        </div>
                                        <div className="cron-field">
                                            <label className="cron-field-label">Day</label>
                                            <Form.Control
                                                type="text"
                                                value={cronFields.dayOfMonth}
                                                onChange={(e) => setCronFields(prev => ({ ...prev, dayOfMonth: e.target.value }))}
                                                placeholder="*"
                                                maxLength={6}
                                                className="cron-input"
                                            />
                                        </div>
                                        <div className="cron-field">
                                            <label className="cron-field-label">Month</label>
                                            <Form.Control
                                                type="text"
                                                value={cronFields.month}
                                                onChange={(e) => setCronFields(prev => ({ ...prev, month: e.target.value }))}
                                                placeholder="*"
                                                maxLength={6}
                                                className="cron-input"
                                            />
                                        </div>
                                        <div className="cron-field">
                                            <label className="cron-field-label">Day of Week</label>
                                            <Form.Control
                                                type="text"
                                                value={cronFields.dayOfWeek}
                                                onChange={(e) => setCronFields(prev => ({ ...prev, dayOfWeek: e.target.value }))}
                                                placeholder="*"
                                                maxLength={6}
                                                className="cron-input"
                                            />
                                        </div>
                                    </div>
                                    {cronValidationError && (
                                        <div className="text-danger small mt-2">
                                            <i className="fas fa-exclamation-triangle me-1"></i>
                                            {cronValidationError}
                                        </div>
                                    )}
                                </div>
                                <Form.Text className="text-muted mt-2">
                                    Examples: <code>0 0 9 * * ?</code> (Daily at 9 AM UTC), <code>0 */15 * * * ?</code> (Every 15 minutes), <code>0 0 17 ? * MON-FRI</code> (Weekdays at 5 PM UTC)
                                </Form.Text>
                                
                                {/* Timezone Conversion Display - Inside the card */}
                                {cronFields.hours && cronFields.minutes && (
                                    <div className="mt-3 p-2 bg-light border rounded">
                                        <small className="text-muted">
                                            <i className="fas fa-globe me-1"></i>
                                            <strong>UTC:</strong> {cronFields.hours.padStart(2, '0')}:{cronFields.minutes.padStart(2, '0')}
                                            <span className="mx-2">→</span>
                                            <i className="fas fa-map-marker-alt me-1"></i>
                                            <strong>Local ({Intl.DateTimeFormat().resolvedOptions().timeZone}):</strong> {(() => {
                                                const utcHour = parseInt(cronFields.hours) || 0;
                                                const utcMinute = parseInt(cronFields.minutes) || 0;
                                                const localTime = convertUTCToLocal(utcHour, utcMinute);
                                                return `${localTime.hour.toString().padStart(2, '0')}:${localTime.minute.toString().padStart(2, '0')}`;
                                            })()}
                                        </small>
                                    </div>
                                )}
                                
                                {/* Timezone Information */}
                                <div className="alert alert-info mt-3 mb-0">
                                    <i className="fas fa-info-circle me-2"></i>
                                    <strong>Timezone Note:</strong> Enter times in UTC (what gets stored). 
                                    We'll show you what that translates to in your local timezone ({Intl.DateTimeFormat().resolvedOptions().timeZone}) below.
                                </div>
                            </Card.Body>
                        </Card>

                        {/* Cron Expression Examples Accordion */}
                        <Accordion className="mb-4">
                            <Accordion.Item eventKey="0">
                                <Accordion.Header>
                                    <i className="fas fa-lightbulb me-2 text-warning"></i>
                                    Cron Expression Examples
                                </Accordion.Header>
                                <Accordion.Body>
                                    <div className="table-responsive">
                                    <table className="table table-sm table-bordered">
                                        <thead className="table-light">
                                            <tr>
                                                <th style={{ width: '30%' }}>Expression</th>
                                                <th style={{ width: '70%' }}>Description</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td><code>0 0 9 * * ?</code></td>
                                                <td>Every day at 9:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 */15 * * * ?</code></td>
                                                <td>Every 15 minutes</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 17 ? * MON-FRI</code></td>
                                                <td>Every weekday at 5:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 12 ? * SAT,SUN</code></td>
                                                <td>Every weekend at 12:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 0 1 * ?</code></td>
                                                <td>First day of every month at midnight</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 9 L * ?</code></td>
                                                <td>Last day of every month at 9:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 9 ? * MON</code></td>
                                                <td>Every Monday at 9:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 9-17 * * ?</code></td>
                                                <td>Every hour from 9:00 AM to 5:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 */30 6-18 * * ?</code></td>
                                                <td>Every 30 minutes from 6:00 AM to 6:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 0 1 1 ?</code></td>
                                                <td>New Year's Day at midnight</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 8 ? * MON,WED,FRI</code></td>
                                                <td>Every Monday, Wednesday, Friday at 8:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 6 ? * TUE-THU</code></td>
                                                <td>Every Tuesday through Thursday at 6:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 18 ? * WED-SAT</code></td>
                                                <td>Every Wednesday through Saturday at 6:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 0 15 * ?</code></td>
                                                <td>15th day of every month at midnight</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 9 1,15,30 * ?</code></td>
                                                <td>1st, 15th, and 30th of every month at 9:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 7 * 3-5 ?</code></td>
                                                <td>Every day from March to May at 7:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 8 * 6-8 ?</code></td>
                                                <td>Every day from June to August at 8:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 9 * 9-11 ?</code></td>
                                                <td>Every day from September to November at 9:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 10 * 12,1,2 ?</code></td>
                                                <td>Every day December, January, February at 10:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 0 1 1,4,7,10 ?</code></td>
                                                <td>First day of every quarter at midnight</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 9 ? * 2#1</code></td>
                                                <td>First Tuesday of every month at 9:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 14 ? * 6#3</code></td>
                                                <td>Third Saturday of every month at 2:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 9 15W * ?</code></td>
                                                <td>Nearest weekday to 15th of every month at 9:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 9 LW * ?</code></td>
                                                <td>Last weekday of every month at 9:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 */5 9-17 * * ?</code></td>
                                                <td>Every 5 minutes from 9:00 AM to 5:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 */10 * ? * SAT,SUN</code></td>
                                                <td>Every 10 minutes on weekends</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 */2 6-22 * ?</code></td>
                                                <td>Every 2 hours from 6:00 AM to 10:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 */3 7-19 * ?</code></td>
                                                <td>Every 3 hours from 7:00 AM to 7:00 PM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 22-6 * * ?</code></td>
                                                <td>Every hour from 10:00 PM to 6:00 AM</td>
                                            </tr>
                                            <tr>
                                                <td><code>0 0 0 L 12 ?</code></td>
                                                <td>Last day of December at midnight</td>
                                            </tr>
                                        </tbody>
                                    </table>
                                    <small className="text-muted">
                                        <i className="fas fa-info-circle me-1"></i>
                                        <strong>Note:</strong> Use <code>?</code> for Day of Week when specifying Day of Month, and use <code>?</code> for Day of Month when specifying Day of Week.
                                    </small>
                                </div>
                                </Accordion.Body>
                            </Accordion.Item>
                        </Accordion>

                        {/* Section 2: Schedule Description */}
                        <Card className="mb-3 border-0">
                            <Card.Header className="bg-light px-0">
                                <div className="d-flex justify-content-between align-items-center">
                                    <h6 className="mb-0">
                                        <i className="fas fa-file-alt me-2"></i>
                                        Schedule Description
                                    </h6>
                                    <Button
                                        type="button"
                                        variant="outline-primary"
                                        size="sm"
                                        onClick={validateAndGenerateCronDescription}
                                        disabled={!isCronExpressionValid() || isValidatingCron}
                                    >
                                        {isValidatingCron ? (
                                            <>
                                                <Spinner animation="border" size="sm" className="me-2" />
                                                Validating...
                                            </>
                                        ) : (
                                            <>
                                                <i className="fas fa-check-circle me-2"></i>
                                                Get Description
                                            </>
                                        )}
                                    </Button>
                                </div>
                            </Card.Header>
                            <Card.Body className="px-0">
                                <Form.Group className="mb-3">
                                    <Form.Label>UTC Time</Form.Label>
                                    <Form.Control
                                        type="text"
                                        name="cronDescription"
                                        value={task?.cronDescription || ''}
                                        readOnly
                                        disabled
                                        placeholder="Click 'Get Description' to validate and generate description"
                                        style={{ backgroundColor: '#e9ecef', cursor: 'not-allowed' }}
                                    />
                                </Form.Group>

                                {/* Local Time Description */}
                                {task?.cronDescription && cronFields.hours && cronFields.minutes && (
                                    <Form.Group className="mb-0">
                                        <Form.Label>Local Time ({Intl.DateTimeFormat().resolvedOptions().timeZone})</Form.Label>
                                        <Form.Control
                                            type="text"
                                            value={(() => {
                                        const utcHour = parseInt(cronFields.hours) || 0;
                                        const utcMinute = parseInt(cronFields.minutes) || 0;
                                        const localTime = convertUTCToLocal(utcHour, utcMinute);
                                        
                                        // Replace UTC time references with local time in the description
                                        let localDescription = task.cronDescription;
                                        
                                        // Convert UTC time to 12-hour format for description matching
                                        const utcHour12 = utcHour === 0 ? 12 : utcHour > 12 ? utcHour - 12 : utcHour;
                                        const utcAmPm = utcHour >= 12 ? 'PM' : 'AM';
                                        
                                        // Convert local time to 12-hour format for replacement
                                        const localHour12 = localTime.hour === 0 ? 12 : localTime.hour > 12 ? localTime.hour - 12 : localTime.hour;
                                        const localAmPm = localTime.hour >= 12 ? 'PM' : 'AM';
                                        
                                        // Try to find and replace the time pattern in the description
                                        // Start with the most specific pattern first to avoid partial matches
                                        
                                        // Pattern 1: 12-hour format with minutes and AM/PM (e.g., "11:11 PM")
                                        const utcPattern1 = `${utcHour12}:${utcMinute.toString().padStart(2, '0')} ${utcAmPm}`;
                                        const localPattern1 = `${localHour12}:${localTime.minute.toString().padStart(2, '0')} ${localAmPm}`;
                                        
                                        if (localDescription.includes(utcPattern1)) {
                                            console.log(`Converting: "${utcPattern1}" → "${localPattern1}"`);
                                            localDescription = localDescription.replace(new RegExp(utcPattern1.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), localPattern1);
                                        } 
                                        // Pattern 2: 24-hour format with minutes (e.g., "23:11")
                                        else if (localDescription.includes(`${utcHour}:${utcMinute.toString().padStart(2, '0')}`)) {
                                            const utcPattern2 = `${utcHour}:${utcMinute.toString().padStart(2, '0')}`;
                                            const localPattern2 = `${localTime.hour}:${localTime.minute.toString().padStart(2, '0')}`;
                                            console.log(`Converting: "${utcPattern2}" → "${localPattern2}"`);
                                            localDescription = localDescription.replace(new RegExp(utcPattern2.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), localPattern2);
                                        }
                                        // Pattern 3: 24-hour format padded (e.g., "23:11")
                                        else if (localDescription.includes(`${utcHour.toString().padStart(2, '0')}:${utcMinute.toString().padStart(2, '0')}`)) {
                                            const utcPattern3 = `${utcHour.toString().padStart(2, '0')}:${utcMinute.toString().padStart(2, '0')}`;
                                            const localPattern3 = `${localTime.hour.toString().padStart(2, '0')}:${localTime.minute.toString().padStart(2, '0')}`;
                                            console.log(`Converting: "${utcPattern3}" → "${localPattern3}"`);
                                            localDescription = localDescription.replace(new RegExp(utcPattern3.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), localPattern3);
                                        }
                                        // Pattern 4: 12-hour format without minutes (e.g., "11 PM") - only if no minutes pattern matched
                                        else if (localDescription.includes(`${utcHour12} ${utcAmPm}`)) {
                                            const utcPattern4 = `${utcHour12} ${utcAmPm}`;
                                            const localPattern4 = `${localHour12} ${localAmPm}`;
                                            console.log(`Converting: "${utcPattern4}" → "${localPattern4}"`);
                                            localDescription = localDescription.replace(new RegExp(utcPattern4.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), localPattern4);
                                        }
                                        
                                        console.log(`UTC Description: "${task.cronDescription}"`);
                                        console.log(`Local Description: "${localDescription}"`);
                                        console.log(`UTC Time: ${utcHour}:${utcMinute.toString().padStart(2, '0')}`);
                                        console.log(`Local Time: ${localTime.hour}:${localTime.minute.toString().padStart(2, '0')}`);
                                        console.log(`UTC Hour 12: ${utcHour12}, UTC AM/PM: ${utcAmPm}`);
                                        console.log(`Local Hour 12: ${localHour12}, Local AM/PM: ${localAmPm}`);
                                        console.log(`Local minute: ${localTime.minute}, padded: ${localTime.minute.toString().padStart(2, '0')}`);
                                        
                                        return localDescription;
                                    })()}
                                            readOnly
                                            disabled
                                            style={{ backgroundColor: '#f8f9fa', cursor: 'not-allowed' }}
                                        />
                                    </Form.Group>
                                )}
                                
                                {/* Error Messages */}
                                {(cronValidationError || cronDescriptionError) && (
                                    <div className="mt-3">
                                        {cronValidationError && (
                                            <div className="text-danger small mb-2">
                                                <i className="fas fa-exclamation-triangle me-1"></i>
                                                {cronValidationError}
                                            </div>
                                        )}
                                        {cronDescriptionError && (
                                            <div className="text-danger small">
                                                <i className="fas fa-exclamation-triangle me-1"></i>
                                                {cronDescriptionError}
                                            </div>
                                        )}
                                    </div>
                                )}
                                
                                <Form.Text className="text-muted mt-2">
                                    Click "Get Description" to validate your cron expression and generate a human-readable description.
                                </Form.Text>
                            </Card.Body>
                        </Card>

                        <Form.Group className="mb-3">
                            <Form.Check
                                type="switch"
                                id="scheduleOnStartup-switch"
                                name="scheduleOnStartup"
                                label="Schedule on Startup"
                                checked={task?.scheduleOnStartup || false}
                                onChange={handleInputChange}
                            />
                            <Form.Text className="text-muted">
                                If enabled, this task will be scheduled automatically when the agent starts.
                            </Form.Text>
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowSchedulingModal(false)}>
                        Cancel
                    </Button>
                    <Button 
                        variant="primary" 
                        onClick={async () => {
                            console.log('🖱️ Save Scheduling button clicked!');
                            try {
                                setSaving(true);
                                
                                // Validate cron expression before saving
                                const cronExpression = buildCronExpression(cronFields);
                                if (cronExpression.trim() && !isCronExpressionValid()) {
                                    showErrorToast('Please fix the cron expression errors before saving');
                                    return;
                                }
                                
                                // Send only the scheduling-related fields using the new endpoint
                                const schedulingUpdate = {
                                    cronExpression: cronExpression,
                                    cronDescription: task?.cronDescription || '',
                                    scheduleOnStartup: task?.scheduleOnStartup || false,
                                    executionTrigger: cronExpression.trim() ? 'CRON' : 'MANUAL'
                                };
                                console.log('🔍 Current task.scheduleOnStartup:', task?.scheduleOnStartup);
                                console.log('🚀 Calling updateSchedulingConfiguration with:', { taskId, schedulingUpdate });
                                const response = await agentTaskService.updateSchedulingConfiguration(taskId, schedulingUpdate);
                                console.log('📥 Response from updateSchedulingConfiguration:', response);
                                if (response.success) {
                                    showSuccessToast('Scheduling configuration updated successfully');
                                    setShowSchedulingModal(false);
                                    loadTask();
                                    loadVersions();
                                } else {
                                    showErrorToast(response.error || 'Failed to update scheduling configuration');
                                }
                            } catch (err) {
                                showErrorToast('Failed to update scheduling configuration');
                                console.error('Error updating scheduling:', err);
                            } finally {
                                setSaving(false);
                            }
                        }}
                        disabled={saving || !isCronExpressionValid() || !isCronValidated || !!cronValidationError || !!cronDescriptionError}
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
                        ) : 'Save Scheduling'}
                    </Button>
                </Modal.Footer>
            </Modal>

            {/* Unschedule Confirmation Modal */}
            <ConfirmationModal
                show={showUnscheduleModal}
                onHide={() => setShowUnscheduleModal(false)}
                onConfirm={async () => {
                    setUnscheduling(true);
                    try {
                        const response = await agentSchedulingService.unscheduleTask(taskId);
                        if (response.success) {
                            showSuccessToast('Task unscheduled successfully');
                            setShowUnscheduleModal(false);
                            loadTask(); // Reload task to reflect changes
                        } else {
                            showErrorToast(response.error || 'Failed to unschedule task');
                        }
                    } catch (error) {
                        showErrorToast('Failed to unschedule task');
                        console.error('Error unscheduling task:', error);
                    } finally {
                        setUnscheduling(false);
                    }
                }}
                title="Unschedule Task"
                message={
                    <div>
                        <p>Are you sure you want to unschedule this task?</p>
                        <p className="text-muted mb-0">
                            <i className="fas fa-exclamation-triangle me-2"></i>
                            The task will no longer run automatically according to its schedule.
                        </p>
                    </div>
                }
                confirmLabel={unscheduling ? "Unscheduling..." : "Unschedule"}
                cancelLabel="Cancel"
                variant="danger"
                disabled={unscheduling}
            />

            {/* Execution Details Modal */}
            <ExecutionDetailsModal
                show={showExecutionModal}
                onHide={() => {
                    setShowExecutionModal(false);
                    setSelectedExecution(null);
                }}
                execution={selectedExecution}
                formatDate={formatDate}
            />
        </div>
    );
}

export default ViewAgentTask;

