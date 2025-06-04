import React, { useEffect, useState } from 'react';
import { Line, Bar, Doughnut } from 'react-chartjs-2';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    BarElement,
    ArcElement,
    Title,
    Tooltip,
    Legend,
} from 'chart.js';
import workflowService from '../../../services/workflowService';
import './styles.css';

// Register ChartJS components
ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    BarElement,
    ArcElement,
    Title,
    Tooltip,
    Legend
);

const WorkflowsStats = () => {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        fetchStats();
    }, []);

    const fetchStats = async () => {
        try {
            const result = await workflowService.getTeamStats();
            if (result.success) {
                setStats(result.data);
            } else {
                setError(result.error);
            }
        } catch (err) {
            setError('Failed to fetch workflow statistics');
        } finally {
            setLoading(false);
        }
    };

    if (loading) return <div className="workflow-stats-loading">Loading statistics...</div>;
    if (error) return <div className="workflow-stats-error">Error: {error}</div>;
    if (!stats) return null;

    // Prepare data for execution trends chart
    const executionTrendsData = {
        labels: Object.keys(stats.hourlyExecutions || {}),
        datasets: [
            {
                label: 'Executions per Hour',
                data: Object.values(stats.hourlyExecutions || {}),
                borderColor: 'rgb(75, 192, 192)',
                tension: 0.1,
            },
        ],
    };

    // Prepare data for workflow distribution chart
    const workflowDistributionData = {
        labels: Object.entries(stats.workflowStats || {}).map(([id, data]) => 
            `${data.workflowName || 'Unnamed'} (${id})`
        ),
        datasets: [
            {
                data: Object.values(stats.workflowStats || {}).map(w => w.totalExecutions),
                backgroundColor: [
                    'rgb(255, 99, 132)',
                    'rgb(54, 162, 235)',
                    'rgb(255, 206, 86)',
                    'rgb(75, 192, 192)',
                    'rgb(153, 102, 255)',
                ],
            },
        ],
    };

    // Prepare data for success/failure ratio chart
    const successFailureData = {
        labels: ['Successful', 'Failed'],
        datasets: [
            {
                data: [stats.successfulExecutions, stats.failedExecutions],
                backgroundColor: ['rgb(75, 192, 192)', 'rgb(255, 99, 132)'],
            },
        ],
    };

    // Prepare data for model usage chart
    const modelUsageData = {
        labels: Object.keys(stats.modelStats || {}),
        datasets: [
            {
                label: 'Total Tokens Used',
                data: Object.values(stats.modelStats || {}).map(m => m.totalTokens),
                backgroundColor: 'rgba(54, 162, 235, 0.5)',
            },
        ],
    };

    return (
        <div className="workflow-stats-container">
            <h2 className="workflow-stats-title">Workflow Analytics</h2>
            
            <div className="workflow-stats-grid">
                {/* Summary Cards */}
                <div className="workflow-stats-card">
                    <h3>Total Executions</h3>
                    <p className="stats-number">{stats.totalExecutions}</p>
                </div>
                <div className="workflow-stats-card">
                    <h3>Success Rate</h3>
                    <p className="stats-number">
                        {((stats.successfulExecutions / stats.totalExecutions) * 100).toFixed(1)}%
                    </p>
                </div>
                <div className="workflow-stats-card">
                    <h3>Avg Execution Time</h3>
                    <p className="stats-number">{stats.averageExecutionTime.toFixed(2)}ms</p>
                </div>

                {/* Charts */}
                <div className="workflow-stats-chart">
                    <h3>Execution Trends</h3>
                    <Line 
                        data={executionTrendsData}
                        options={{
                            responsive: true,
                            plugins: {
                                legend: {
                                    position: 'top',
                                },
                            },
                        }}
                    />
                </div>

                <div className="workflow-stats-chart">
                    <h3>Workflow Distribution</h3>
                    <Doughnut 
                        data={workflowDistributionData}
                        options={{
                            responsive: true,
                            plugins: {
                                legend: {
                                    position: 'right',
                                },
                                tooltip: {
                                    callbacks: {
                                        label: function(context) {
                                            const label = context.label || '';
                                            const value = context.raw || 0;
                                            const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                            const percentage = ((value / total) * 100).toFixed(1);
                                            const [name, id] = label.split(' (');
                                            return [
                                                `Name: ${name}`,
                                                `ID: ${id.replace(')', '')}`,
                                                `Executions: ${value} (${percentage}%)`
                                            ];
                                        }
                                    }
                                }
                            },
                        }}
                    />
                </div>

                <div className="workflow-stats-chart">
                    <h3>Success/Failure Ratio</h3>
                    <Doughnut 
                        data={successFailureData}
                        options={{
                            responsive: true,
                            plugins: {
                                legend: {
                                    position: 'right',
                                },
                                tooltip: {
                                    callbacks: {
                                        label: function(context) {
                                            const label = context.label || '';
                                            const value = context.raw || 0;
                                            const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                            const percentage = ((value / total) * 100).toFixed(1);
                                            return `${label}: ${value} (${percentage}%)`;
                                        }
                                    }
                                }
                            },
                        }}
                    />
                </div>

                <div className="workflow-stats-chart">
                    <h3>Model Usage</h3>
                    <Bar 
                        data={modelUsageData}
                        options={{
                            responsive: true,
                            plugins: {
                                legend: {
                                    position: 'top',
                                },
                            },
                        }}
                    />
                </div>
            </div>
        </div>
    );
};

export default WorkflowsStats;
