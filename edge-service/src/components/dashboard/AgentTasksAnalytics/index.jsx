import React, { useEffect, useState } from 'react';
import { Card } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import agentMonitoringService from '../../../services/agentMonitoringService';
import { formatDate } from '../../../utils/dateUtils';
import './styles.css';

const AgentTasksAnalytics = ({ agentId, teamId }) => {
    const navigate = useNavigate();
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
            <div className="ata-container ata-loading">
                <div className="ata-spinner"></div>
                <p>Loading task analytics...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="ata-container ata-error">
                <p className="ata-error-message">Error: {error}</p>
            </div>
        );
    }

    if (!taskStats || taskStats.length === 0) {
        return (
            <div className="ata-container ata-empty">
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

    // Helper function to get color based on success rate
    const getBarColor = (successRate) => {
        if (successRate >= 80) return '#28a745'; // Green
        if (successRate >= 50) return '#ffc107'; // Yellow
        return '#dc3545'; // Red
    };

    // Prepare data for chart
    const chartData = taskStats.map(task => ({
        taskId: task.taskId,
        name: task.taskName,
        successRate: task.successRate,
        totalExecutions: task.totalExecutions,
        successfulExecutions: task.successfulExecutions,
        failedExecutions: task.failedExecutions,
        avgDuration: formatDuration(task.averageExecutionTimeMs),
        lastExecuted: formatDate(task.lastExecutedAt),
        enabled: task.enabled,
        taskType: task.taskType
    }));

    return (
        <Card className="mb-4">
            <Card.Header className="d-flex justify-content-between align-items-center">
                <h5 className="mb-0">Task Performance</h5>
            </Card.Header>
            <Card.Body>
                <ResponsiveContainer width="100%" height={400}>
                    <BarChart
                        data={chartData}
                        margin={{ top: 20, right: 30, left: 20, bottom: 80 }}
                    >
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis
                            dataKey="name"
                            angle={-45}
                            textAnchor="end"
                            height={100}
                            interval={0}
                            style={{ fontSize: '12px' }}
                        />
                        <YAxis
                            label={{ value: 'Success Rate (%)', angle: -90, position: 'insideLeft' }}
                            domain={[0, 100]}
                        />
                        <Tooltip
                            content={({ active, payload }) => {
                                if (active && payload && payload.length) {
                                    const data = payload[0].payload;
                                    return (
                                        <div className="ata-custom-tooltip">
                                            <p className="ata-tooltip-title"><strong>{data.name}</strong></p>
                                            <p className="ata-tooltip-item ata-tooltip-hint">
                                                <em>Click bar to view task details</em>
                                            </p>
                                            <p className="ata-tooltip-item">
                                                <span className="ata-tooltip-label">Status:</span>
                                                <span className={`ata-tooltip-badge ${data.enabled ? 'ata-enabled' : 'ata-disabled'}`}>
                                                    {data.enabled ? 'Enabled' : 'Disabled'}
                                                </span>
                                            </p>
                                            <p className="ata-tooltip-item">
                                                <span className="ata-tooltip-label">Type:</span> {data.taskType || 'N/A'}
                                            </p>
                                            <p className="ata-tooltip-item">
                                                <span className="ata-tooltip-label">Success Rate:</span>
                                                <strong style={{ color: getBarColor(data.successRate) }}>
                                                    {data.successRate.toFixed(1)}%
                                                </strong>
                                            </p>
                                            <p className="ata-tooltip-item">
                                                <span className="ata-tooltip-label">Executions:</span> {data.totalExecutions}
                                                {data.totalExecutions > 0 && (
                                                    <span className="ata-text-muted"> ({data.successfulExecutions} success, {data.failedExecutions} failed)</span>
                                                )}
                                            </p>
                                            <p className="ata-tooltip-item">
                                                <span className="ata-tooltip-label">Avg Duration:</span> {data.avgDuration}
                                            </p>
                                            <p className="ata-tooltip-item">
                                                <span className="ata-tooltip-label">Last Executed:</span> {data.lastExecuted}
                                            </p>
                                        </div>
                                    );
                                }
                                return null;
                            }}
                        />
                        <Bar
                            dataKey="successRate"
                            radius={[8, 8, 0, 0]}
                            onClick={(data) => {
                                if (data && data.taskId) {
                                    navigate(`/agents/${agentId}/tasks/${data.taskId}`);
                                }
                            }}
                            style={{ cursor: 'pointer' }}
                        >
                            {chartData.map((entry, index) => (
                                <Cell key={`cell-${index}`} fill={getBarColor(entry.successRate)} />
                            ))}
                        </Bar>
                    </BarChart>
                </ResponsiveContainer>
            </Card.Body>
        </Card>
    );
};

export default AgentTasksAnalytics;
