import React, { useState, useEffect } from 'react';
import { Table, Badge, Spinner, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../../utils/roleUtils';
import { LoadingSpinner } from '../../../components/common/LoadingSpinner';
import { HiEye } from 'react-icons/hi';
import Button from '../../../components/common/Button';
import agentService from '../../../services/agentService';
import agentTaskService from '../../../services/agentTaskService';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import './styles.css';

function AgentTasks() {
    const { currentTeam } = useTeam();
    const { user } = useAuth();
    const navigate = useNavigate();
    const canViewTasks = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    const [tasks, setTasks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (currentTeam) {
            loadRecentTasks();
        }
    }, [currentTeam]);

    const loadRecentTasks = async () => {
        try {
            setLoading(true);
            // Get all agents for the team
            const agentsResponse = await agentService.getAgentsByTeam(currentTeam.id);
            if (agentsResponse.success && agentsResponse.data) {
                // Get tasks for each agent and flatten the results
                const allTasksPromises = agentsResponse.data.map(agent => 
                    agentService.getTasksByAgent(agent.id)
                        .then(response => {
                            if (response.success && response.data) {
                                // Add agent info to each task
                                return response.data.map(task => ({
                                    ...task,
                                    agentName: agent.name,
                                    agentId: agent.id
                                }));
                            }
                            return [];
                        })
                        .catch(err => {
                            console.error(`Error loading tasks for agent ${agent.id}:`, err);
                            return [];
                        })
                );
                
                const allTasksArrays = await Promise.all(allTasksPromises);
                const allTasks = allTasksArrays.flat();
                
                // Sort by updatedAt (most recent first) and take top 5
                const sortedTasks = allTasks.sort((a, b) => {
                    const dateA = a.updatedAt ? new Date(formatDateToISO(a.updatedAt)) : new Date(0);
                    const dateB = b.updatedAt ? new Date(formatDateToISO(b.updatedAt)) : new Date(0);
                    return dateB - dateA;
                });
                
                setTasks(sortedTasks);
            } else {
                setError(agentsResponse.error);
            }
        } catch (err) {
            setError('Failed to load agent tasks');
            console.error('Error loading agent tasks:', err);
        } finally {
            setLoading(false);
        }
    };

    const formatDateToISO = (dateArray) => {
        if (!dateArray || dateArray.length < 6) return null;
        const [year, month, day, hour, minute, second] = dateArray;
        return new Date(year, month - 1, day, hour || 0, minute || 0, second || 0).toISOString();
    };

    const formatDate = (dateArray) => {
        if (!dateArray || dateArray.length < 6) return 'N/A';
        const [year, month, day, hour, minute, second] = dateArray;
        return format(new Date(year, month - 1, day, hour || 0, minute || 0, second || 0), 'MMM d, yyyy HH:mm');
    };

    if (loading) {
        return (
            <div className="dashboard-agent-tasks-loading">
                <Spinner animation="border" role="status">
                    <span className="visually-hidden">Loading...</span>
                </Spinner>
            </div>
        );
    }

    if (error) {
        return (
            <div className="dashboard-agent-tasks-error">
                <p className="text-danger">{error}</p>
            </div>
        );
    }

    return (
        <div className="dashboard-agent-tasks">
            <h4 className="dashboard-section-title">Recent Agent Tasks</h4>
            <Table responsive hover className="dashboard-agent-tasks-table">
                <thead>
                    <tr>
                        <th className="col-name">Task Name</th>
                        <th className="col-agent">Agent</th>
                        <th className="col-type">Task Type</th>
                        <th className="col-trigger">Execution Trigger</th>
                        <th className="col-updated">Last Updated</th>
                        <th className="col-status">Status</th>
                        <th className="col-version">Version</th>
                        <th className="col-actions">Actions</th>
                    </tr>
                </thead>
                <tbody>
                    {tasks.slice(0, 5).map((task) => (
                        <tr key={task.id}>
                            <td className="col-name">{task.name}</td>
                            <td className="col-agent">
                                <span className="text-muted small">{task.agentName}</span>
                            </td>
                            <td className="col-type">
                                <Badge bg={
                                    task.taskType === 'WORKFLOW_TRIGGER' ? 'primary' :
                                    task.taskType === 'WORKFLOW_EMBEDDED' ? 'info' :
                                    task.taskType === 'WORKFLOW_EMBEDDED_ADHOC' ? 'warning' :
                                    'secondary'
                                }>
                                    {task.taskType === 'WORKFLOW_TRIGGER' ? 'Trigger' :
                                     task.taskType === 'WORKFLOW_EMBEDDED' ? 'Embedded' :
                                     task.taskType === 'WORKFLOW_EMBEDDED_ADHOC' ? 'Ad-hoc' :
                                     task.taskType}
                                </Badge>
                            </td>
                            <td className="col-trigger">
                                <Badge bg={
                                    task.executionTrigger === 'MANUAL' ? 'secondary' :
                                    task.executionTrigger === 'CRON' ? 'success' :
                                    task.executionTrigger === 'EVENT_DRIVEN' ? 'warning' :
                                    'info'
                                }>
                                    {task.executionTrigger}
                                </Badge>
                            </td>
                            <td className="col-updated">{formatDate(task.updatedAt)}</td>
                            <td className="col-status">
                                <Badge bg={task.enabled ? 'success' : 'secondary'}>
                                    {task.enabled ? 'Active' : 'Inactive'}
                                </Badge>
                            </td>
                            <td className="col-version">
                                <Badge bg="secondary">
                                    v{task.version || 1}
                                </Badge>
                            </td>
                            <td className="col-actions">
                                <div className="dashboard-agent-task-actions">
                                    <OverlayTrigger
                                        placement="top"
                                        overlay={
                                            <Tooltip id={`view-tooltip-${task.id}`}>
                                                View task details
                                            </Tooltip>
                                        }
                                    >
                                        <div>
                                            <Button 
                                                variant="outline-secondary" 
                                                size="sm"
                                                onClick={() => navigate(`/agents/${task.agentId}/tasks/${task.id}`)}
                                            >
                                                <HiEye className="me-1" /> View
                                            </Button>
                                        </div>
                                    </OverlayTrigger>
                                </div>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </Table>
        </div>
    );
}

export default AgentTasks;

