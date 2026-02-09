import React, { useEffect, useState } from 'react';
import { Card } from 'react-bootstrap';
import agentMonitoringService from '../../../services/agentMonitoringService';
import { formatDate } from '../../../utils/dateUtils';
import './AgentTasksAnalytics.css';

const AgentTasksAnalytics = ({ agentId, teamId }) => {
    const [taskStats, setTaskStats] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchTaskStats = async () => {
            if (!agentId || !teamId) {
                setLoading(false);
                return;
            }

            setLoading(true);
            setError(null);

            const result = await agentMonitoringService.getAgentTasksStatistics(agentId, teamId);

            if (result.success) {
                setTaskStats(result.data.taskStatistics || []);
            } else {
                setError(result.error || 'Failed to fetch task statistics');
            }

            setLoading(false);
        };

        fetchTaskStats();
    }, [agentId, teamId]);

    if (loading) {
        return (
            <div className="task-analytics-container loading">
                <div className="spinner"></div>
                <p>Loading task analytics...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="task-analytics-container error">
                <p className="error-message">Error: {error}</p>
            </div>
        );
    }

    if (!taskStats || taskStats.length === 0) {
        return (
            <div className="task-analytics-container empty">
                <p>No task statistics available</p>
            </div>
        );
    }



    const formatDuration = (ms) => {
        if (!ms) return '0ms';
        if (ms < 1000) return `${Math.round(ms)}ms`;
        if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
        return `${(ms / 60000).toFixed(1)}m`;
    };

    return (
        <Card className="mb-4">
            <Card.Header className="d-flex justify-content-between align-items-center">
                <h5 className="mb-0">Task Performance</h5>
            </Card.Header>
            <Card.Body className="p-0 my-2">
                <div className="table-responsive">
                    <table className="table table-hover task-analytics-table">
                        <thead>
                            <tr>
                                <th>Task Name</th>
                                <th>Status</th>
                                <th>Type</th>
                                <th>Total Executions</th>
                                <th>Success Rate</th>
                                <th>Avg Duration</th>
                                <th>Last Executed</th>
                            </tr>
                        </thead>
                        <tbody>
                            {taskStats.map((task) => (
                                <tr key={task.taskId}>
                                    <td className="task-name">
                                        <span className="task-name-text">{task.taskName}</span>
                                    </td>
                                    <td>
                                        <span className={`status-badge ${task.enabled ? 'enabled' : 'disabled'}`}>
                                            {task.enabled ? 'Enabled' : 'Disabled'}
                                        </span>
                                    </td>
                                    <td>
                                        <span className="task-type">{task.taskType || 'N/A'}</span>
                                    </td>
                                    <td className="text-center">
                                        <span className="metric-value">{task.totalExecutions}</span>
                                    </td>
                                    <td className="text-center">
                                        <div className="success-rate-container">
                                            <span className={`success-rate ${task.successRate >= 80 ? 'good' :
                                                task.successRate >= 50 ? 'medium' : 'poor'
                                                }`}>
                                                {task.successRate.toFixed(1)}%
                                            </span>
                                            {task.totalExecutions > 0 && (
                                                <span className="execution-breakdown">
                                                    ({task.successfulExecutions}/{task.totalExecutions})
                                                </span>
                                            )}
                                        </div>
                                    </td>
                                    <td className="text-center">
                                        <span className="duration">{formatDuration(task.averageExecutionTimeMs)}</span>
                                    </td>
                                    <td className="last-executed">
                                        <span className="timestamp">{formatDate(task.lastExecutedAt)}</span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </Card.Body>
        </Card>
    );
};

export default AgentTasksAnalytics;
