import React, { useEffect, useState } from 'react';
import { Bar, Doughnut } from 'react-chartjs-2';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    BarElement,
    ArcElement,
    Title,
    Tooltip,
    Legend,
} from 'chart.js';
import { useTeam } from '../../../contexts/TeamContext';
import agentMonitoringService from '../../../services/agentMonitoringService';
import './styles.css';

// Register ChartJS components
ChartJS.register(
    CategoryScale,
    LinearScale,
    BarElement,
    ArcElement,
    Title,
    Tooltip,
    Legend
);

const AgentStats = () => {
    const { currentTeam } = useTeam();
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (currentTeam) {
            fetchStats();
        }
    }, [currentTeam]);

    const fetchStats = async () => {
        try {
            setLoading(true);
            const result = await agentMonitoringService.getTeamExecutionStats(currentTeam.id);
            if (result.success) {
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

    return (
        <div className="agent-stats-container">
            <h2 className="agent-stats-title">Agent Analytics</h2>
            
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
            </div>
        </div>
    );
};

export default AgentStats;

