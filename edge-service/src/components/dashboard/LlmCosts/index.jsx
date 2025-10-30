import React, { useState, useEffect, useCallback } from 'react';
import { Card, Spinner, Row, Col, Button } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { Doughnut, Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  ArcElement,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend
} from 'chart.js';
import llmCostService from '../../../services/llmCostService';
import { useTeam } from '../../../contexts/TeamContext';
import './styles.css';

ChartJS.register(
  ArcElement,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend
);

const LlmCosts = () => {
  const { currentTeam } = useTeam();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState(null);
  const [error, setError] = useState(null);

  const formatDateDisplay = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr + 'T00:00:00');
    const year = date.getFullYear();
    const month = date.toLocaleString('en-US', { month: 'short' }).toUpperCase();
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const getDateRange = () => {
    const today = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(today.getDate() - 30);
    
    const formatDate = (date) => {
      const year = date.getFullYear();
      const month = date.toLocaleString('en-US', { month: 'short' }).toUpperCase();
      const day = String(date.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    };
    
    return `${formatDate(thirtyDaysAgo)} to ${formatDate(today)}`;
  };

  const loadLlmUsage = useCallback(async () => {
    if (!currentTeam?.id) return;
    
    try {
      setLoading(true);
      setError(null);
      // Get last 30 days data
      const today = new Date();
      const thirtyDaysAgo = new Date();
      thirtyDaysAgo.setDate(today.getDate() - 30);
      
      const fromDate = thirtyDaysAgo.toISOString().split('T')[0];
      const toDate = today.toISOString().split('T')[0];
      
      const data = await llmCostService.getTeamLlmUsage(currentTeam.id, fromDate, toDate);
      setStats(data);
    } catch (err) {
      console.error('Error loading LLM usage:', err);
      setError('Failed to load LLM usage data');
    } finally {
      setLoading(false);
    }
  }, [currentTeam?.id]);

  useEffect(() => {
    loadLlmUsage();
  }, [loadLlmUsage]);

  if (loading) {
    return (
      <Card className="llm-costs-card">
        <Card.Header>
          <h5 className="mb-0">
            <i className="fas fa-brain me-2"></i>
            LLM Usage & Costs
          </h5>
        </Card.Header>
        <Card.Body className="text-center">
          <Spinner animation="border" variant="primary" />
          <p className="mt-3 text-muted">Loading LLM usage data...</p>
        </Card.Body>
      </Card>
    );
  }

  if (error || !stats) {
    return (
      <Card className="llm-costs-card">
        <Card.Header>
          <h5 className="mb-0">
            <i className="fas fa-brain me-2"></i>
            LLM Usage & Costs
          </h5>
        </Card.Header>
        <Card.Body className="text-center text-muted">
          <i className="fas fa-exclamation-circle fa-2x mb-3"></i>
          <p>{error || 'No LLM usage data available'}</p>
        </Card.Body>
      </Card>
    );
  }

  // Prepare data for charts
  const modelNames = Object.keys(stats.modelBreakdown || {});
  const modelCosts = modelNames.map(model => stats.modelBreakdown[model].costUsd);
  const modelRequests = modelNames.map(model => stats.modelBreakdown[model].requests);
  
  const providers = Object.keys(stats.providerBreakdown || {});
  const providerCosts = providers.map(provider => stats.providerBreakdown[provider].costUsd);

  const costByModelData = {
    labels: modelNames,
    datasets: [{
      label: 'Cost (USD)',
      data: modelCosts,
      backgroundColor: [
        'rgba(228, 143, 14, 0.8)',
        'rgba(99, 102, 241, 0.8)',
        'rgba(16, 185, 129, 0.8)',
        'rgba(245, 158, 11, 0.8)',
        'rgba(236, 72, 153, 0.8)',
        'rgba(6, 182, 212, 0.8)'
      ],
      borderColor: [
        'rgba(228, 143, 14, 1)',
        'rgba(99, 102, 241, 1)',
        'rgba(16, 185, 129, 1)',
        'rgba(245, 158, 11, 1)',
        'rgba(236, 72, 153, 1)',
        'rgba(6, 182, 212, 1)'
      ],
      borderWidth: 2
    }]
  };

  const requestsByModelData = {
    labels: modelNames,
    datasets: [{
      label: 'Requests',
      data: modelRequests,
      backgroundColor: 'rgba(228, 143, 14, 0.8)',
      borderColor: 'rgba(228, 143, 14, 1)',
      borderWidth: 2
    }]
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          padding: 15,
          font: {
            size: 11
          }
        }
      },
      tooltip: {
        callbacks: {
          label: function(context) {
            let label = context.dataset.label || '';
            if (label) {
              label += ': ';
            }
            // For bar charts, use context.parsed.y; for doughnut charts, use context.parsed
            const value = context.parsed.y !== undefined ? context.parsed.y : context.parsed;
            if (context.dataset.label === 'Cost (USD)') {
              label += '$' + (typeof value === 'number' ? value.toFixed(6) : value);
            } else {
              label += value;
            }
            return label;
          }
        }
      }
    }
  };

  const barChartOptions = {
    ...chartOptions,
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          precision: 0
        }
      }
    }
  };

  return (
    <Card className="llm-costs-card">
      <Card.Header className="d-flex justify-content-between align-items-center">
        <h5 className="mb-0">
          <i className="fas fa-brain me-2"></i>
          LLM Usage & Costs
          <span className="period-badge ms-2">Last 30 Days</span>
        </h5>
        <Button 
          as={Link} 
          to="/llm-usage" 
          variant="outline-primary" 
          size="sm"
          className="view-details-btn"
        >
          <i className="fas fa-chart-line me-1"></i>
          View Details
        </Button>
      </Card.Header>
      <Card.Body>
        {/* Summary Cards */}
        <Row className="summary-row mb-4">
          <Col md={3} sm={6} className="mb-3">
            <div className="stat-card total-cost">
              <div className="stat-icon">
                <i className="fas fa-dollar-sign"></i>
              </div>
              <div className="stat-details">
                <div className="stat-label">Total Cost</div>
                <div className="stat-value">${stats.totalUsage.totalCostUsd.toFixed(4)}</div>
              </div>
            </div>
          </Col>
          <Col md={3} sm={6} className="mb-3">
            <div className="stat-card total-requests">
              <div className="stat-icon">
                <i className="fas fa-paper-plane"></i>
              </div>
              <div className="stat-details">
                <div className="stat-label">Total Requests</div>
                <div className="stat-value">{stats.totalUsage.totalRequests.toLocaleString()}</div>
              </div>
            </div>
          </Col>
          <Col md={3} sm={6} className="mb-3">
            <div className="stat-card total-tokens">
              <div className="stat-icon">
                <i className="fas fa-coins"></i>
              </div>
              <div className="stat-details">
                <div className="stat-label">Total Tokens</div>
                <div className="stat-value">{stats.totalUsage.totalTokens.toLocaleString()}</div>
              </div>
            </div>
          </Col>
          <Col md={3} sm={6} className="mb-3">
            <div className="stat-card avg-cost">
              <div className="stat-icon">
                <i className="fas fa-chart-line"></i>
              </div>
              <div className="stat-details">
                <div className="stat-label">Avg Cost/Request</div>
                <div className="stat-value">
                  ${stats.totalUsage.totalRequests > 0 ? 
                    (stats.totalUsage.totalCostUsd / stats.totalUsage.totalRequests).toFixed(6) : 
                    '0.000000'}
                </div>
              </div>
            </div>
          </Col>
        </Row>

        {/* Charts */}
        <Row className="charts-row">
          <Col md={6} className="mb-4">
            <div className="chart-container">
              <h6 className="chart-title">Cost by Model</h6>
              {modelNames.length > 0 ? (
                <Doughnut data={costByModelData} options={chartOptions} />
              ) : (
                <p className="text-muted text-center">No model data available</p>
              )}
            </div>
          </Col>
          <Col md={6} className="mb-4">
            <div className="chart-container">
              <h6 className="chart-title">Requests by Model</h6>
              {modelNames.length > 0 ? (
                <Bar data={requestsByModelData} options={barChartOptions} />
              ) : (
                <p className="text-muted text-center">No request data available</p>
              )}
            </div>
          </Col>
        </Row>

        {/* Model Breakdown Table */}
        <div className="model-breakdown">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <h6 className="section-title mb-0">Model Breakdown</h6>
            <span className="date-range-text">
              <i className="fas fa-calendar-alt me-1"></i>
              {getDateRange()}
            </span>
          </div>
          <div className="table-responsive">
            <table className="table table-hover">
              <thead>
                <tr>
                  <th>Model</th>
                  <th>Provider</th>
                  <th className="text-end">Requests</th>
                  <th className="text-end">Tokens</th>
                  <th className="text-end">Cost (USD)</th>
                  <th className="text-end">Avg Latency</th>
                </tr>
              </thead>
              <tbody>
                {modelNames.map(modelName => {
                  const model = stats.modelBreakdown[modelName];
                  return (
                    <tr key={modelName}>
                      <td>{modelName}</td>
                      <td>
                        <span className={`provider-badge ${model.provider}`}>
                          {model.provider}
                        </span>
                      </td>
                      <td className="text-end">{model.requests.toLocaleString()}</td>
                      <td className="text-end">{model.totalTokens.toLocaleString()}</td>
                      <td className="text-end cost-cell">${model.costUsd.toFixed(4)}</td>
                      <td className="text-end">{model.averageLatencyMs.toFixed(0)}ms</td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr className="total-row">
                  <td colSpan="2"><strong>Total</strong></td>
                  <td className="text-end"><strong>{stats.totalUsage.totalRequests.toLocaleString()}</strong></td>
                  <td className="text-end"><strong>{stats.totalUsage.totalTokens.toLocaleString()}</strong></td>
                  <td className="text-end cost-cell"><strong>${stats.totalUsage.totalCostUsd.toFixed(4)}</strong></td>
                  <td></td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      </Card.Body>
    </Card>
  );
};

export default LlmCosts;

