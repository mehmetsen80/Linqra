import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { executionMonitoringWebSocket } from '../../services/executionMonitoringService';
import { Alert, Box, Grid2, Card, CardContent, Typography, Chip, LinearProgress, Button, IconButton, Dialog, DialogTitle, DialogContent, DialogActions, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper } from '@mui/material';
import { PlayArrow, Stop, Refresh, Memory, Timer, CheckCircle, Error, Cancel, Replay, Close, Visibility } from '@mui/icons-material';
import agentTaskService from '../../services/agentTaskService';
import executionQueueService from '../../services/executionQueueService';
import workflowService from '../../services/workflowService';
import { useAuth } from '../../contexts/AuthContext';
import { useTeam } from '../../contexts/TeamContext';
import ExecutionDetailsModal from '../../components/workflows/ExecutionDetailsModal';
import './styles.css';

const ExecutionMonitoring = () => {
    const { user } = useAuth();
    const { currentTeam } = useTeam();
    const navigate = useNavigate();
    const [executions, setExecutions] = useState(new Map());
    const [queue, setQueue] = useState([]);
    const [connectionStatus, setConnectionStatus] = useState('disconnected');
    const [error, setError] = useState(null);
    const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
    const [executionToCancel, setExecutionToCancel] = useState(null);
    const [cancelling, setCancelling] = useState(false);
    const [recentExecutions, setRecentExecutions] = useState([]);
    const [loadingRecentExecutions, setLoadingRecentExecutions] = useState(true);
    const [executionTimers, setExecutionTimers] = useState(new Map());
    const [stepDetailsModalOpen, setStepDetailsModalOpen] = useState(false);
    const [selectedStepDetails, setSelectedStepDetails] = useState(null);
    const [currentTime, setCurrentTime] = useState(new Date());
    const [closedExecutions, setClosedExecutions] = useState(new Set());
    const [showExecutionModal, setShowExecutionModal] = useState(false);
    const [selectedExecution, setSelectedExecution] = useState(null);

    // Load recent executions
    const loadRecentExecutions = async () => {
        try {
            setLoadingRecentExecutions(true);
            console.log('Loading recent executions...');
            
            const response = await agentTaskService.getRecentExecutions(100);
            if (response.success) {
                console.log('Loaded recent executions:', response.data);
                setRecentExecutions(response.data || []);
            } else {
                console.error('Failed to load recent executions:', response.error);
                setError('Failed to load recent executions: ' + response.error);
                setRecentExecutions([]);
            }
        } catch (error) {
            console.error('Error loading recent executions:', error);
            setError('Failed to load recent executions');
            setRecentExecutions([]);
        } finally {
            setLoadingRecentExecutions(false);
        }
    };

    const loadQueue = async () => {
        try {
            const queueData = await executionQueueService.getQueue();
            setQueue(queueData);
        } catch (error) {
            console.error('Error loading execution queue:', error);
        }
    };

    // Update current time every second for real-time duration calculation
    useEffect(() => {
        const interval = setInterval(() => {
            setCurrentTime(new Date());
        }, 1000);

        return () => clearInterval(interval);
    }, []);

    // Timer countdown and cleanup for executions
    useEffect(() => {
        const timerInterval = setInterval(() => {
            // Update countdown timers
            setExecutionTimers(prevTimers => {
                const newTimers = new Map();
                for (const [executionId, timeLeft] of prevTimers.entries()) {
                    if (timeLeft > 1) {
                        newTimers.set(executionId, timeLeft - 1);
                    }
                }
                return newTimers;
            });
            
            // Clean up executions when countdown reaches 0 or they're completed and old
            setExecutions(prevExecutions => {
                const newExecutions = new Map(prevExecutions);
                const now = new Date().getTime();

                for (const [executionId, execution] of newExecutions.entries()) {
                    const isCompleted = execution.status === 'COMPLETED' || execution.status === 'FAILED' || execution.status === 'CANCELLED';
                    const timer = executionTimers.get(executionId);
                    const hasStoppedUpdating = now - new Date(execution.lastUpdated).getTime() > 15000; // 15 seconds without updates

                    // Auto-mark as completed if execution has stopped receiving updates and reached the last step
                    if (execution.status === 'RUNNING' && hasStoppedUpdating && execution.currentStep === execution.totalSteps) {
                        execution.status = 'COMPLETED';
                        console.log('Auto-marking execution as completed due to no updates:', executionId);
                    }

                    // Remove execution if timer expired or it's completed and old
                    const isOld = now - new Date(execution.lastUpdated).getTime() > 30000; // 30 seconds
                    if ((timer === 0) || (isCompleted && isOld)) {
                        console.log('Removing execution:', executionId, 'timer:', timer, 'completed:', isCompleted, 'old:', isOld);
                        newExecutions.delete(executionId);
                    }
                }
                return newExecutions;
            });
            
            // Clean up timers for removed executions and closed executions
            setExecutionTimers(prevTimers => {
                const newTimers = new Map();
                for (const [executionId, timeLeft] of prevTimers.entries()) {
                    // Keep timer if it has time left and execution is not closed
                    if (timeLeft > 0 && !closedExecutions.has(executionId)) {
                        newTimers.set(executionId, timeLeft);
                    }
                }
                return newTimers;
            });
        }, 1000); // Update every second

        return () => clearInterval(timerInterval);
    }, [executionTimers, closedExecutions]);

    // Refresh queue every 2 seconds
    useEffect(() => {
        const queueInterval = setInterval(() => {
            loadQueue();
        }, 2000);

        return () => clearInterval(queueInterval);
    }, []);

    useEffect(() => {
        // Load recent executions and queue on component mount
        loadRecentExecutions();
        loadQueue();
        // Track connection status
        const handleConnectionChange = (status) => {
            setConnectionStatus(status);
            if (status === 'disconnected') {
                setError('Lost connection to server. Attempting to reconnect...');
            } else if (status === 'connected') {
                setError(null);
            }
        };


        // Subscribe to execution updates
        const unsubscribeFromExecutionUpdates = executionMonitoringWebSocket.subscribe(data => {
            try {
                
                // Filter out health messages - only process execution messages
                if (Array.isArray(data) || !data?.executionId) {
                    console.log('🔍 Skipping non-execution message (health or invalid data)');
                    return;
                }
                
                setExecutions(prevExecutions => {
                    const newExecutions = new Map(prevExecutions);
                    const currentTime = new Date().toISOString();
                    
                    
                    // Update the execution with new data
                    newExecutions.set(data.executionId, {
                        ...data,
                        lastUpdated: currentTime
                    });
                    
                    // Start timer for new executions (30 seconds countdown)
                    if (!prevExecutions.has(data.executionId)) {
                        setExecutionTimers(prevTimers => {
                            const newTimers = new Map(prevTimers);
                            newTimers.set(data.executionId, 30); // 30 seconds countdown
                            return newTimers;
                        });
                    }
                    
                    
                    // Check if this is a completion update (last step reached)
                    if (data.currentStep === data.totalSteps && data.status === 'RUNNING') {
                        // Mark as completed if we've reached the last step
                        const completedExecution = newExecutions.get(data.executionId);
                        if (completedExecution) {
                            completedExecution.status = 'COMPLETED';
                            console.log('Marking execution as completed:', data.executionId);
                        }
                        
                        // Refresh recent executions when an execution completes
                        setTimeout(() => {
                            loadRecentExecutions();
                        }, 1000); // Wait 1 second for the execution to fully complete
                    }
                    
                    // Also refresh recent executions for FAILED or CANCELLED status
                    if (data.status === 'FAILED' || data.status === 'CANCELLED') {
                        setTimeout(() => {
                            loadRecentExecutions();
                        }, 1000);
                    }
                    
                    return newExecutions;
                });
                
                setError(null);
                
                // Refresh recent executions when we get new execution data
                loadRecentExecutions();
            } catch (err) {
                setError('Failed to process execution data');
                console.error('Error processing execution data:', err);
            }
        });

        // Subscribe to connection status updates
        executionMonitoringWebSocket.onConnectionChange(handleConnectionChange);

        // Cleanup subscription on unmount
        return () => {
            unsubscribeFromExecutionUpdates();
            executionMonitoringWebSocket.offConnectionChange(handleConnectionChange);
            executionMonitoringWebSocket.disconnect();
        };
    }, []);


    const getStatusColor = (status) => {
        switch (status) {
            case 'STARTED':
            case 'RUNNING':
                return 'primary';
            case 'COMPLETED':
                return 'success';
            case 'FAILED':
                return 'error';
            case 'CANCELLED':
                return 'warning';
            default:
                return 'default';
        }
    };

    const getStatusIcon = (status) => {
        switch (status) {
            case 'STARTED':
            case 'RUNNING':
                return <PlayArrow />;
            case 'COMPLETED':
                return <CheckCircle />;
            case 'FAILED':
                return <Error />;
            case 'CANCELLED':
                return <Cancel />;
            default:
                return <PlayArrow />;
        }
    };

    const formatMemory = (bytes) => {
        const mb = bytes / (1024 * 1024);
        return `${mb.toFixed(2)} MB`;
    };

    const formatDuration = (ms) => {
        if (!ms || ms === 0) return '0ms';
        if (ms < 1000) return `${ms}ms`;
        const seconds = Math.floor(ms / 1000);
        const minutes = Math.floor(seconds / 60);
        if (minutes > 0) {
            return `${minutes}m ${seconds % 60}s`;
        }
        return `${seconds}s`;
    };

    const formatProgress = (currentStep, totalSteps) => {
        if (totalSteps === 0) return 0;
        return (currentStep / totalSteps) * 100;
    };

    const handleCancelExecution = (execution) => {
        setExecutionToCancel(execution);
        setCancelDialogOpen(true);
    };

    const confirmCancelExecution = async () => {
        if (!executionToCancel) return;
        
        setCancelling(true);
        try {
            await agentTaskService.cancelExecution(executionToCancel.executionId);
            setCancelDialogOpen(false);
            setExecutionToCancel(null);
        } catch (error) {
            console.error('Failed to cancel execution:', error);
            setError('Failed to cancel execution: ' + error.message);
        } finally {
            setCancelling(false);
        }
    };

    const handleCancelDialogClose = () => {
        setCancelDialogOpen(false);
        setExecutionToCancel(null);
    };

    const handleRerunExecution = async (execution) => {
        try {
            // Extract task ID from execution data and rerun
            if (execution.taskId) {
                const response = await agentTaskService.executeTask(execution.taskId);
                if (response.success) {
                    console.log('Task rerun started successfully');
                    // The WebSocket will pick up the new execution automatically
                } else {
                    setError('Failed to rerun execution: ' + response.error);
                }
            } else {
                setError('Cannot rerun execution: Task ID not found');
            }
        } catch (error) {
            console.error('Error rerunning execution:', error);
            setError('Failed to rerun execution: ' + error.message);
        }
    };

    const handleViewTask = (execution) => {
        if (execution.taskId) {
            navigate(`/agents/${execution.agentId}/tasks/${execution.taskId}`);
        } else {
            setError('Cannot view task: Task ID not found');
        }
    };

    const handleStepClick = (stepNumber, execution) => {
        const stepDetails = {
            stepNumber,
            executionId: execution.executionId,
            taskName: execution.taskName,
            agentName: execution.agentName,
            currentStep: execution.currentStep,
            totalSteps: execution.totalSteps,
            status: execution.status,
            isCurrentStep: stepNumber === execution.currentStep,
            isCompleted: stepNumber < execution.currentStep,
            isFuture: stepNumber > execution.currentStep,
            currentStepName: execution.currentStepName,
            currentStepTarget: execution.currentStepTarget,
            currentStepAction: execution.currentStepAction,
            startedAt: execution.startedAt,
            durationMs: execution.durationMs || execution.executionDurationMs,
            memoryUsage: execution.memoryUsage
        };
        
        setSelectedStepDetails(stepDetails);
        setStepDetailsModalOpen(true);
    };

    const handleCloseExecution = (executionId) => {
        setClosedExecutions(prev => new Set([...prev, executionId]));
    };

    const handleRowClick = async (execution) => {
        try {
            // Fetch full execution details using workflowService
            const response = await workflowService.getExecutionByAgentExecutionId(execution.executionId);
            if (response.success) {
                setSelectedExecution(response.data);
                setShowExecutionModal(true);
            } else {
                setError('Failed to load execution details: ' + response.error);
            }
        } catch (error) {
            console.error('Error fetching execution details:', error);
            setError('Failed to load execution details');
        }
    };

    const formatDate = (date) => {
        if (!date) return 'Unknown';
        try {
            let parsedDate;
            // Handle array format from LocalDateTime serialization
            if (Array.isArray(date)) {
                // Convert array [year, month, day, hour, minute, second, nano] to Date
                const [year, month, day, hour, minute, second, nano] = date;
                parsedDate = new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
            } else {
                parsedDate = new Date(date);
            }
            return isNaN(parsedDate.getTime()) ? 'Invalid Date' : parsedDate.toLocaleString();
        } catch (e) {
            console.error('Date parsing error:', e, 'Value:', date);
            return 'Invalid Date';
        }
    };

    return (
        <div className="execution-monitoring">
            <Box sx={{ mb: 3 }}>
                <Typography variant="h5" component="h1" gutterBottom sx={{ textAlign: 'left' }}>
                    Execution Monitoring
                </Typography>
                
                <Alert 
                    severity={
                        connectionStatus === 'connected' ? 'success' :
                        connectionStatus === 'connecting' ? 'info' :
                        connectionStatus === 'disconnected' ? 'warning' : 'error'
                    }
                    sx={{ mb: 2 }}
                >
                    {connectionStatus === 'connected' && 'Connected to execution monitoring'}
                    {connectionStatus === 'connecting' && 'Connecting to execution monitoring...'}
                    {connectionStatus === 'disconnected' && 'Connection lost. Attempting to reconnect...'}
                    {connectionStatus === 'error' && 'Failed to connect to execution monitoring'}
                </Alert>
                
                {error && (
                    <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>
                )}
            </Box>

            <Grid2 container spacing={3} sx={{ width: '100%' }}>
                {Array.from(executions.values())
                    .filter(execution => {
                        // Don't show closed executions
                        if (closedExecutions.has(execution.executionId)) {
                            return false;
                        }
                        // Only show executions that are still running or completed recently (within last 30 seconds)
                        const isRunning = execution.status === 'STARTED' || execution.status === 'RUNNING';
                        const isRecentlyCompleted = (execution.status === 'COMPLETED' || execution.status === 'FAILED' || execution.status === 'CANCELLED') && 
                            new Date().getTime() - new Date(execution.lastUpdated).getTime() < 30000; // 30 seconds
                        return isRunning || isRecentlyCompleted;
                    })
                    .map((execution) => {
                        const timer = executionTimers.get(execution.executionId) || 0;
                        return (
                    <Grid2 xs={12} key={execution.executionId} sx={{ width: '100%' }}>
                        <Card className="execution-card" sx={{ width: '100%' }}>
                            <CardContent>
                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                        <Typography variant="h6" component="h2" noWrap>
                                            {execution.taskName}
                                        </Typography>
                                        {timer > 0 && (
                                            <Chip
                                                label={`Auto-close in ${timer}s`}
                                                size="small"
                                                color={timer <= 5 ? 'error' : timer <= 10 ? 'warning' : 'default'}
                                                variant="outlined"
                                                className={`countdown-timer ${timer <= 5 ? 'error' : timer <= 10 ? 'warning' : ''}`}
                                            />
                                        )}
                                    </Box>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                        <Chip
                                            icon={getStatusIcon(execution.status)}
                                            label={execution.status}
                                            color={getStatusColor(execution.status)}
                                            size="small"
                                        />
                                        {(execution.status === 'STARTED' || execution.status === 'RUNNING') && (
                                            <IconButton
                                                size="small"
                                                color="error"
                                                onClick={() => handleCancelExecution(execution)}
                                                title="Cancel Execution"
                                            >
                                                <Stop />
                                            </IconButton>
                                        )}
                                        <IconButton
                                            size="small"
                                            onClick={() => handleCloseExecution(execution.executionId)}
                                            title="Close Execution Box"
                                            color="default"
                                            className="close-button"
                                        >
                                            <Close />
                                        </IconButton>
                                    </Box>
                                </Box>

                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Agent: {execution.agentName || 'Unknown'}
                                </Typography>

                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Execution ID: {execution.executionId}
                                </Typography>

                                {/* Progress Bar */}
                                <Box sx={{ mb: 2 }}>
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                                        <Typography variant="body2">
                                            Step {execution.currentStep} of {execution.totalSteps}
                                        </Typography>
                                        <Typography variant="body2">
                                            {formatProgress(execution.currentStep, execution.totalSteps).toFixed(0)}%
                                        </Typography>
                                    </Box>
                                    <LinearProgress 
                                        variant="determinate" 
                                        value={formatProgress(execution.currentStep, execution.totalSteps)}
                                        sx={{ height: 8, borderRadius: 4 }}
                                    />
                                </Box>

                                {/* Current Step Info */}
                                {execution.currentStepName && (
                                    <Box sx={{ mb: 2 }}>
                                        <Typography variant="body2" color="text.secondary">
                                            Current Step: {execution.currentStepName}
                                        </Typography>
                                        {execution.currentStepTarget && (
                                            <Typography variant="body2" color="text.secondary">
                                                Target: {execution.currentStepTarget}
                                            </Typography>
                                        )}
                                    </Box>
                                )}

                                {/* All Steps Overview */}
                                <Box sx={{ mb: 2 }}>
                                    <Typography variant="body2" sx={{ mb: 1, fontWeight: 'bold' }}>
                                        Workflow Steps:
                                    </Typography>
                                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                                        {Array.from({ length: execution.totalSteps || 1 }, (_, index) => {
                                            const stepNumber = index + 1;
                                            const isCurrentStep = stepNumber === execution.currentStep;
                                            const isCompleted = stepNumber < execution.currentStep;
                                            
                                            return (
                                                <Chip
                                                    key={stepNumber}
                                                    label={`Step ${stepNumber}`}
                                                    size="small"
                                                    color={isCurrentStep ? 'primary' : isCompleted ? 'success' : 'default'}
                                                    variant={isCurrentStep ? 'filled' : 'outlined'}
                                                    onClick={() => handleStepClick(stepNumber, execution)}
                                                    sx={{ cursor: 'pointer', '&:hover': { opacity: 0.8 } }}
                                                />
                                            );
                                        })}
                                    </Box>
                                </Box>

                                {/* Memory Usage */}
                                {execution.memoryUsage && (
                                    <Box sx={{ mb: 2 }}>
                                        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                                            <Memory sx={{ mr: 1, fontSize: 16 }} />
                                            <Typography variant="body2">
                                                Memory Usage
                                            </Typography>
                                        </Box>
                                        <Typography variant="body2" color="text.secondary">
                                            Heap: {formatMemory(execution.memoryUsage.heapUsed)} / {formatMemory(execution.memoryUsage.heapMax)} ({execution.memoryUsage.heapUsagePercent.toFixed(1)}%)
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            Non-Heap: {formatMemory(execution.memoryUsage.nonHeapUsed)} ({execution.memoryUsage.nonHeapUsagePercent.toFixed(1)}%)
                                        </Typography>
                                    </Box>
                                )}

                                {/* Timing Info */}
                                <Box sx={{ mb: 2 }}>
                                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                                        <Timer sx={{ mr: 1, fontSize: 16 }} />
                                        <Typography variant="body2">
                                            Execution Time
                                        </Typography>
                                    </Box>
                                    {execution.stepDurationMs && (
                                        <Typography variant="body2" color="text.secondary">
                                            Step Duration: {formatDuration(execution.stepDurationMs)}
                                        </Typography>
                                    )}
                                </Box>

                                {/* Error Message */}
                                {execution.errorMessage && (
                                    <Alert severity="error" sx={{ mt: 2 }}>
                                        {execution.errorMessage}
                                    </Alert>
                                )}

                                {/* Last Updated */}
                                <Typography variant="caption" color="text.secondary">
                                    Last updated: {new Date(execution.lastUpdated).toLocaleString()}
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid2>
                        );
                    })}
            </Grid2>

            {Array.from(executions.values()).filter(execution => !closedExecutions.has(execution.executionId)).length === 0 && connectionStatus === 'connected' && (
                <Box sx={{ textAlign: 'center', mt: 4 }}>
                    <Typography variant="h6" color="text.secondary">
                        No executions running
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        Execute an agent task to see real-time monitoring here
                    </Typography>
                </Box>
            )}

            {/* Execution Queue Section */}
            {queue.length > 0 && (
                <Box sx={{ mt: 4 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                        <Typography variant="h5" component="h2">
                            Execution Queue ({queue.length})
                        </Typography>
                        <Button
                            variant="outlined"
                            startIcon={<Refresh />}
                            onClick={loadQueue}
                            size="small"
                        >
                            Refresh
                        </Button>
                    </Box>
                    <TableContainer component={Paper}>
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Position</TableCell>
                                    <TableCell>Agent</TableCell>
                                    <TableCell>Task</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Queued At</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {queue.map((queueItem, index) => (
                                    <TableRow key={queueItem.executionId}>
                                        <TableCell>
                                            <Chip 
                                                label={`#${queueItem.queuePosition || index + 1}`}
                                                color={queueItem.status === 'STARTING' ? 'primary' : 'default'}
                                                variant={queueItem.status === 'STARTING' ? 'filled' : 'outlined'}
                                            />
                                        </TableCell>
                                        <TableCell>{queueItem.agentName}</TableCell>
                                        <TableCell>{queueItem.taskName}</TableCell>
                                        <TableCell>
                                            <Chip 
                                                label={queueItem.status}
                                                color={
                                                    queueItem.status === 'STARTING' ? 'primary' :
                                                    queueItem.status === 'QUEUED' ? 'default' : 'secondary'
                                                }
                                                variant={queueItem.status === 'STARTING' ? 'filled' : 'outlined'}
                                                icon={queueItem.status === 'STARTING' ? <PlayArrow /> : null}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            {queueItem.queuedAt ? 
                                                (() => {
                                                    try {
                                                        let date;
                                                        // Handle array format from LocalDateTime serialization
                                                        if (Array.isArray(queueItem.queuedAt)) {
                                                            // Convert array [year, month, day, hour, minute, second, nano] to Date
                                                            const [year, month, day, hour, minute, second, nano] = queueItem.queuedAt;
                                                            date = new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
                                                        } else {
                                                            date = new Date(queueItem.queuedAt);
                                                        }
                                                        return isNaN(date.getTime()) ? 'Invalid Date' : date.toLocaleString();
                                                    } catch (e) {
                                                        console.error('Queue date parsing error:', e, 'Value:', queueItem.queuedAt);
                                                        return 'Invalid Date';
                                                    }
                                                })() 
                                                : 'Unknown'
                                            }
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </Box>
            )}

            {/* Recent Executions Section */}
            <Box sx={{ mt: 4 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                    <Typography variant="h5" component="h2">
                        Recent Executions
                    </Typography>
                    <Button
                        variant="outlined"
                        startIcon={<Refresh />}
                        onClick={loadRecentExecutions}
                        disabled={loadingRecentExecutions}
                        size="small"
                    >
                        {loadingRecentExecutions ? 'Loading...' : 'Refresh'}
                    </Button>
                </Box>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    View and rerun your recent task executions
                </Typography>
                
                {loadingRecentExecutions ? (
                    <Box sx={{ textAlign: 'center', py: 4 }}>
                        <Typography>Loading recent executions...</Typography>
                    </Box>
                ) : recentExecutions.length === 0 ? (
                    <Box sx={{ textAlign: 'center', py: 4 }}>
                        <Typography variant="body2" color="text.secondary">
                            No recent executions found. Execute a task to see it here.
                        </Typography>
                    </Box>
                ) : (
                    <TableContainer component={Paper} sx={{ mb: 2 }}>
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Task Name</TableCell>
                                    <TableCell>Agent</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Duration</TableCell>
                                    <TableCell>Started</TableCell>
                                    <TableCell>Actions</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {recentExecutions.map((execution) => (
                                    <TableRow 
                                        key={execution.executionId}
                                        onClick={() => handleRowClick(execution)}
                                        sx={{ 
                                            cursor: 'pointer',
                                            '&:hover': {
                                                backgroundColor: 'rgba(0, 0, 0, 0.04)'
                                            }
                                        }}
                                    >
                                        <TableCell>{execution.taskName}</TableCell>
                                        <TableCell>{execution.agentName}</TableCell>
                                        <TableCell>
                                            <Chip
                                                icon={getStatusIcon(execution.status)}
                                                label={execution.status}
                                                color={getStatusColor(execution.status)}
                                                size="small"
                                            />
                                        </TableCell>
                                        <TableCell>{formatDuration(execution.executionDurationMs)}</TableCell>
                                        <TableCell>
                                            {formatDate(execution.startedAt)}
                                        </TableCell>
                                        <TableCell>
                                            <Box sx={{ display: 'flex', gap: 0.5 }}>
                                                <IconButton
                                                    size="small"
                                                    onClick={(e) => {
                                                        e.stopPropagation(); // Prevent row click
                                                        handleRerunExecution(execution);
                                                    }}
                                                    title="Rerun Execution"
                                                    color="primary"
                                                >
                                                    <Replay />
                                                </IconButton>
                                                <IconButton
                                                    size="small"
                                                    onClick={(e) => {
                                                        e.stopPropagation(); // Prevent row click
                                                        handleViewTask(execution);
                                                    }}
                                                    title="View Task"
                                                    color="primary"
                                                >
                                                    <Visibility />
                                                </IconButton>
                                            </Box>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                )}
            </Box>

            {/* Cancel Execution Dialog */}
            <Dialog open={cancelDialogOpen} onClose={handleCancelDialogClose}>
                <DialogTitle>Cancel Execution</DialogTitle>
                <DialogContent>
                    <Typography>
                        Are you sure you want to cancel the execution of "{executionToCancel?.taskName}"?
                        This action cannot be undone.
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleCancelDialogClose} disabled={cancelling}>
                        Keep Running
                    </Button>
                    <Button 
                        onClick={confirmCancelExecution} 
                        color="error" 
                        disabled={cancelling}
                    >
                        {cancelling ? 'Cancelling...' : 'Cancel Execution'}
                    </Button>
                </DialogActions>
                    </Dialog>

                    {/* Step Details Modal */}
                    <Dialog 
                        open={stepDetailsModalOpen} 
                        onClose={() => setStepDetailsModalOpen(false)}
                        maxWidth="md"
                        fullWidth
                    >
                        <DialogTitle>
                            Step {selectedStepDetails?.stepNumber} Details
                        </DialogTitle>
                        <DialogContent>
                            {selectedStepDetails && (
                                <Box>
                                    <Typography variant="h6" gutterBottom>
                                        Execution Information
                                    </Typography>
                                    <Box sx={{ mb: 3 }}>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Task:</strong> {selectedStepDetails.taskName}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Agent:</strong> {selectedStepDetails.agentName || 'Unknown'}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Execution ID:</strong> {selectedStepDetails.executionId}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Status:</strong> {selectedStepDetails.status}
                                        </Typography>
                                    </Box>

                                    <Typography variant="h6" gutterBottom>
                                        Step Information
                                    </Typography>
                                    <Box sx={{ mb: 3 }}>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Step Number:</strong> {selectedStepDetails.stepNumber} of {selectedStepDetails.totalSteps}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Current Step:</strong> {selectedStepDetails.currentStep}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Step Status:</strong> {
                                                selectedStepDetails.isCurrentStep ? 'Currently Running' :
                                                selectedStepDetails.isCompleted ? 'Completed' :
                                                selectedStepDetails.isFuture ? 'Pending' : 'Unknown'
                                            }
                                        </Typography>
                                        {selectedStepDetails.currentStepName && (
                                            <Typography variant="body2" color="text.secondary">
                                                <strong>Step Name:</strong> {selectedStepDetails.currentStepName}
                                            </Typography>
                                        )}
                                        {selectedStepDetails.currentStepTarget && (
                                            <Typography variant="body2" color="text.secondary">
                                                <strong>Target:</strong> {selectedStepDetails.currentStepTarget}
                                            </Typography>
                                        )}
                                        {selectedStepDetails.currentStepAction && (
                                            <Typography variant="body2" color="text.secondary">
                                                <strong>Action:</strong> {selectedStepDetails.currentStepAction}
                                            </Typography>
                                        )}
                                    </Box>

                                    <Typography variant="h6" gutterBottom>
                                        Progress Information
                                    </Typography>
                                    <Box sx={{ mb: 3 }}>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Overall Progress:</strong> {selectedStepDetails.currentStep} / {selectedStepDetails.totalSteps} steps
                                        </Typography>
                                        <LinearProgress 
                                            variant="determinate" 
                                            value={(selectedStepDetails.currentStep / selectedStepDetails.totalSteps) * 100}
                                            sx={{ height: 8, borderRadius: 4, mt: 1 }}
                                        />
                                    </Box>

                                    <Typography variant="h6" gutterBottom>
                                        Timing Information
                                    </Typography>
                                    <Box sx={{ mb: 3 }}>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Started At:</strong> {selectedStepDetails.startedAt ? 
                                                (() => {
                                                    try {
                                                        let date;
                                                        // Handle array format from LocalDateTime serialization
                                                        if (Array.isArray(selectedStepDetails.startedAt)) {
                                                            // Convert array [year, month, day, hour, minute, second, nano] to Date
                                                            const [year, month, day, hour, minute, second, nano] = selectedStepDetails.startedAt;
                                                            date = new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
                                                        } else {
                                                            date = new Date(selectedStepDetails.startedAt);
                                                        }
                                                        return isNaN(date.getTime()) ? 'Invalid Date' : date.toLocaleString();
                                                    } catch (e) {
                                                        console.error('Date parsing error in modal:', e, 'Value:', selectedStepDetails.startedAt);
                                                        return 'Invalid Date';
                                                    }
                                                })() 
                                                : 'Unknown'
                                            }
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            <strong>Duration:</strong> {formatDuration(
                                                selectedStepDetails.executionDurationMs || 0
                                            )}
                                        </Typography>
                                    </Box>

                                    {selectedStepDetails.memoryUsage && (
                                        <>
                                            <Typography variant="h6" gutterBottom>
                                                Memory Usage
                                            </Typography>
                                            <Box>
                                                <Typography variant="body2" color="text.secondary">
                                                    <strong>Heap:</strong> {formatMemory(selectedStepDetails.memoryUsage.heapUsed)} / {formatMemory(selectedStepDetails.memoryUsage.heapMax)} ({selectedStepDetails.memoryUsage.heapUsagePercent.toFixed(1)}%)
                                                </Typography>
                                                <Typography variant="body2" color="text.secondary">
                                                    <strong>Non-Heap:</strong> {formatMemory(selectedStepDetails.memoryUsage.nonHeapUsed)} ({selectedStepDetails.memoryUsage.nonHeapUsagePercent.toFixed(1)}%)
                                                </Typography>
                                            </Box>
                                        </>
                                    )}
                                </Box>
                            )}
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setStepDetailsModalOpen(false)}>
                                Close
                            </Button>
                        </DialogActions>
                    </Dialog>

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
        };

        export default ExecutionMonitoring;

