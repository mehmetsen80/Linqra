import React, { useEffect, useState } from 'react';
import { Bar, Doughnut, Line } from 'react-chartjs-2';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    BarElement,
    ArcElement,
    LineElement,
    PointElement,
    Title,
    Tooltip,
    Legend,
} from 'chart.js';
import { useTeam } from '../../../contexts/TeamContext';
import agentMonitoringService from '../../../services/agentMonitoringService';
import agentService from '../../../services/agentService';
import './styles.css';

// Register ChartJS components
ChartJS.register(
    CategoryScale,
    LinearScale,
    BarElement,
    ArcElement,
    LineElement,
    PointElement,
    Title,
    Tooltip,
    Legend
);

const AgentStats = ({ agentId = null }) => {
    const { currentTeam } = useTeam();
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [agentInfo, setAgentInfo] = useState(null);

    useEffect(() => {
        if (currentTeam) {
            fetchStats();
            // Also fetch agent info if we have an agentId
            if (agentId) {
                fetchAgentInfo();
            }
        }
    }, [currentTeam, agentId]);


    const fetchAgentInfo = async () => {
        if (!agentId || !currentTeam) return;
        
        try {
            console.log('Fetching agent info for agentId:', agentId, 'teamId:', currentTeam.id);
            const agentResult = await agentService.getAgent(currentTeam.id, agentId);
            if (agentResult.success) {
                console.log('Agent info loaded:', agentResult.data);
                setAgentInfo(agentResult.data);
            } else {
                console.error('Failed to fetch agent info:', agentResult.error);
            }
        } catch (err) {
            console.error('Error fetching agent info:', err);
        }
    };

    const fetchStats = async () => {
        try {
            setLoading(true);
            // Always use getTeamExecutionStats, but pass agentId if provided
            const result = await agentMonitoringService.getTeamExecutionStats(currentTeam.id, null, null, agentId);
            
            if (result.success) {
                console.log('Agent stats data:', result.data);
                console.log('Hourly executions data:', result.data.hourlyExecutions);
                setStats(result.data);
            } else {
                setError(result.error);
            }
        } catch (err) {
            setError('Failed to fetch agent statistics');
            console.error('Error fetching agent stats:', err);
        } finally {
            setLoading(false);
        }
    };

    if (loading) return <div className="agent-stats-loading">Loading agent statistics...</div>;
    if (error) return <div className="agent-stats-error">Error: {error}</div>;
    if (!stats) return null;

    // Prepare data for result breakdown chart (Doughnut)
    const resultBreakdownData = {
        labels: Object.keys(stats.resultBreakdown || {}).map(key => 
            key.charAt(0).toUpperCase() + key.slice(1)
        ),
        datasets: [
            {
                data: Object.values(stats.resultBreakdown || {}),
                backgroundColor: [
                    'rgb(75, 192, 192)',   // successful - green
                    'rgb(255, 206, 86)',   // unknown - yellow
                    'rgb(255, 99, 132)',   // failed - red
                    'rgb(201, 203, 207)',  // skipped - gray
                    'rgb(153, 102, 255)',  // partialSuccess - purple
                ],
            },
        ],
    };

    // Prepare data for status breakdown chart (Bar)
    const statusBreakdownData = {
        labels: Object.keys(stats.statusBreakdown || {}).map(key => 
            key.charAt(0).toUpperCase() + key.slice(1)
        ),
        datasets: [
            {
                label: 'Executions by Status',
                data: Object.values(stats.statusBreakdown || {}),
                backgroundColor: [
                    'rgba(75, 192, 192, 0.5)',   // completed - green
                    'rgba(54, 162, 235, 0.5)',   // running - blue
                    'rgba(255, 206, 86, 0.5)',   // timeout - yellow
                    'rgba(255, 99, 132, 0.5)',   // failed - red
                    'rgba(201, 203, 207, 0.5)',  // cancelled - gray
                ],
                borderColor: [
                    'rgb(75, 192, 192)',
                    'rgb(54, 162, 235)',
                    'rgb(255, 206, 86)',
                    'rgb(255, 99, 132)',
                    'rgb(201, 203, 207)',
                ],
                borderWidth: 1,
            },
        ],
    };

    // Prepare data for execution trends chart (Line)
    const executionTrendsData = {
        labels: Object.keys(stats.hourlyExecutions || {}).sort().map(hour => {
            const hourNum = parseInt(hour);
            return `${hourNum.toString().padStart(2, '0')}:00`;
        }),
        datasets: [
            {
                label: 'Agent Executions',
                data: Object.keys(stats.hourlyExecutions || {}).sort().map(hour => 
                    stats.hourlyExecutions[hour] || 0
                ),
                borderColor: 'rgb(75, 192, 192)',
                backgroundColor: 'rgba(75, 192, 192, 0.1)',
                tension: 0.4,
                fill: true,
            },
        ],
    };

    // Check if we have hourly execution data
    const hasHourlyData = stats.hourlyExecutions && Object.keys(stats.hourlyExecutions).length > 0;

    return (
        <div className="agent-stats-container">
            <h2 className="agent-stats-title">
                {agentId ? 
                    `Agent Analytics${agentInfo ? ` - ${agentInfo.name}` : ''}` : 
                    `Team Agent Analytics${currentTeam?.name ? ` - ${currentTeam.name}` : ''}`
                }
            </h2>
            
            <div className="agent-stats-grid">
                {/* Summary Cards */}
                <div className="agent-stats-card">
                    <h3>Total Executions</h3>
                    <p className="stats-number">{stats.totalExecutions}</p>
                </div>
                <div className="agent-stats-card">
                    <h3>Success Rate</h3>
                    <p className="stats-number">
                        {stats.successRate ? stats.successRate.toFixed(1) : '0.0'}%
                    </p>
                </div>
                <div className="agent-stats-card">
                    <h3>Successful</h3>
                    <p className="stats-number text-success">
                        {stats.resultBreakdown?.successful || 0}
                    </p>
                </div>
                <div className="agent-stats-card">
                    <h3>Failed</h3>
                    <p className="stats-number text-danger">
                        {stats.resultBreakdown?.failed || 0}
                    </p>
                </div>
                <div className="agent-stats-card wide">
                    <h3>Avg Execution Time</h3>
                    <p className="stats-number">
                        {stats.averageExecutionTimeMs 
                            ? stats.averageExecutionTimeMs.toFixed(2) 
                            : '0.00'}ms
                    </p>
                </div>

                {/* Charts */}
                <div className="agent-stats-chart">
                    <h3>Result Breakdown</h3>
                    <Doughnut 
                        data={resultBreakdownData}
                        options={{
                            responsive: true,
                            maintainAspectRatio: true,
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
                                            const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : '0.0';
                                            return `${label}: ${value} (${percentage}%)`;
                                        }
                                    }
                                }
                            }
                        }}
                    />
                </div>

                <div className="agent-stats-chart">
                    <h3>Status Breakdown</h3>
                    <Bar 
                        data={statusBreakdownData}
                        options={{
                            responsive: true,
                            maintainAspectRatio: true,
                            plugins: {
                                legend: {
                                    position: 'top',
                                },
                                tooltip: {
                                    callbacks: {
                                        label: function(context) {
                                            const value = context.raw || 0;
                                            const total = stats.totalExecutions || 1;
                                            const percentage = ((value / total) * 100).toFixed(1);
                                            return `Executions: ${value} (${percentage}%)`;
                                        }
                                    }
                                }
                            },
                            scales: {
                                y: {
                                    beginAtZero: true,
                                    ticks: {
                                        precision: 0
                                    }
                                }
                            }
                        }}
                    />
                </div>

                <div className="agent-stats-chart full-width">
                    <h3>Execution Trends</h3>
                    {hasHourlyData ? (
                        <Line 
                            data={executionTrendsData}
                            options={{
                                responsive: true,
                                maintainAspectRatio: true,
                                plugins: {
                                    legend: {
                                        position: 'top',
                                    },
                                    tooltip: {
                                        callbacks: {
                                            label: function(context) {
                                                return `Executions: ${context.raw}`;
                                            },
                                            title: function(context) {
                                                return `Hour: ${context[0].label}`;
                                            }
                                        }
                                    }
                                },
                                scales: {
                                    y: {
                                        beginAtZero: true,
                                        ticks: {
                                            precision: 0,
                                            stepSize: 1
                                        }
                                    }
                                }
                            }}
                        />
                    ) : (
                        <div className="text-center text-muted py-5">
                            <i className="fas fa-chart-line fa-3x mb-3 opacity-25"></i>
                            <p>No execution data available yet</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default AgentStats;

