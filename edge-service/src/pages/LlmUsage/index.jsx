import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Table, Form, Button, Alert, Spinner, Breadcrumb } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { format, subDays } from 'date-fns';
import { Line, Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend
} from 'chart.js';
import llmCostService from '../../services/llmCostService';
import { useTeam } from '../../contexts/TeamContext';
import './styles.css';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend
);

const LlmUsage = () => {
  const { currentTeam } = useTeam();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [usageData, setUsageData] = useState(null);
  const [exportingPDF, setExportingPDF] = useState(false);
  
  // Date range state - default to last 30 days
  const [fromDate, setFromDate] = useState(format(subDays(new Date(), 30), 'yyyy-MM-dd'));
  const [toDate, setToDate] = useState(format(new Date(), 'yyyy-MM-dd'));

  const fetchUsageData = async () => {
    if (!currentTeam?.id) return;

    setLoading(true);
    setError(null);

    try {
      const data = await llmCostService.getTeamLlmUsage(currentTeam.id, fromDate, toDate);
      setUsageData(data);
    } catch (err) {
      console.error('Error fetching LLM usage data:', err);
      setError('Failed to load LLM usage data. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsageData();
  }, [currentTeam?.id]);

  const handleApplyDateRange = () => {
    fetchUsageData();
  };

  const formatNumber = (num) => {
    if (num === undefined || num === null) return '0';
    return num.toLocaleString('en-US');
  };

  const formatCurrency = (amount) => {
    if (amount === undefined || amount === null) return '$0.00';
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 4
    }).format(amount);
  };

  const formatDateDisplay = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr + 'T00:00:00');
    const year = date.getFullYear();
    const month = date.toLocaleString('en-US', { month: 'short' }).toUpperCase();
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const exportToPDF = () => {
    try {
      setExportingPDF(true);
      
      // Small delay to show the loading state
      setTimeout(() => {
        // Use browser's native print dialog to save as PDF
        // This is the cleanest approach without external dependencies
        window.print();
        setExportingPDF(false);
      }, 100);
    } catch (error) {
      console.error('Error opening print dialog:', error);
      alert('Failed to open print dialog. Please try again.');
      setExportingPDF(false);
    }
  };

  if (!currentTeam) {
    return (
      <Container className="llm-usage-container mt-4">
        <Alert variant="warning">Please select a team to view LLM usage.</Alert>
      </Container>
    );
  }

  return (
    <Container fluid className="llm-usage-container">
      {/* Breadcrumb Navigation */}
      <Card className="breadcrumb-card mb-3">
        <Card.Body className="p-2">
          <Breadcrumb className="bg-light mb-0">
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/' }}>
              <i className="fas fa-home me-1"></i>
              Home
            </Breadcrumb.Item>
            <Breadcrumb.Item 
              linkAs={Link} 
              linkProps={{ to: '/organizations' }}
            >
              <i className="fas fa-building me-1"></i>
              {currentTeam?.organization?.name || 'Organization'}
            </Breadcrumb.Item>
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/teams' }}>
              <i className="fas fa-users me-1"></i>
              {currentTeam?.name || 'Team'}
            </Breadcrumb.Item>
            <Breadcrumb.Item active>
              <i className="fas fa-chart-line me-1"></i>
              LLM Usage & Cost
            </Breadcrumb.Item>
          </Breadcrumb>
        </Card.Body>
      </Card>

      {/* Page Header */}
      <div className="page-header mb-4">
        <p className="page-description text-muted">
          Detailed breakdown of AI model usage and associated costs for {currentTeam?.name || 'your team'}
        </p>
      </div>

      {/* Date Range Filter */}
      <Card className="mb-4 filter-card">
        <Card.Body>
          <Row className="align-items-end">
            <Col md={4}>
              <Form.Group>
                <Form.Label className="filter-label">
                  <i className="fas fa-calendar-alt me-2"></i>
                  From Date
                </Form.Label>
                <Form.Control
                  type="date"
                  value={fromDate}
                  onChange={(e) => setFromDate(e.target.value)}
                  max={toDate}
                />
              </Form.Group>
            </Col>
            <Col md={4}>
              <Form.Group>
                <Form.Label className="filter-label">
                  <i className="fas fa-calendar-alt me-2"></i>
                  To Date
                </Form.Label>
                <Form.Control
                  type="date"
                  value={toDate}
                  onChange={(e) => setToDate(e.target.value)}
                  min={fromDate}
                  max={format(new Date(), 'yyyy-MM-dd')}
                />
              </Form.Group>
            </Col>
            <Col md={2}>
              <Button 
                variant="primary" 
                onClick={handleApplyDateRange}
                disabled={loading}
                className="w-100"
              >
                <i className="fas fa-search me-2"></i>
                Apply Date Range
              </Button>
            </Col>
            <Col md={2}>
              <Button 
                variant="success" 
                onClick={exportToPDF}
                disabled={loading || !usageData || exportingPDF}
                className="w-100 export-pdf-btn"
                title="Export to PDF"
              >
                {exportingPDF ? (
                  <>
                    <Spinner animation="border" size="sm" className="me-2" />
                    Exporting...
                  </>
                ) : (
                  <>
                    <i className="fas fa-file-pdf me-2"></i>
                    Export to PDF
                  </>
                )}
              </Button>
            </Col>
          </Row>
        </Card.Body>
      </Card>

      {loading ? (
        <div className="text-center py-5">
          <Spinner animation="border" variant="primary" />
          <p className="mt-3">Loading usage data...</p>
        </div>
      ) : error ? (
        <Alert variant="danger">
          <i className="fas fa-exclamation-triangle me-2"></i>
          {error}
        </Alert>
      ) : usageData ? (
        <>
          {/* Summary Cards */}
          <Row className="mb-4">
            <Col md={3}>
              <Card className="summary-card total-cost-card">
                <Card.Body>
                  <div className="summary-icon">
                    <i className="fas fa-dollar-sign"></i>
                  </div>
                  <div className="summary-content">
                    <p className="summary-label">Total Cost</p>
                    <h3 className="summary-value">{formatCurrency(usageData.totalUsage?.totalCostUsd || 0)}</h3>
                  </div>
                </Card.Body>
              </Card>
            </Col>
            <Col md={3}>
              <Card className="summary-card total-requests-card">
                <Card.Body>
                  <div className="summary-icon">
                    <i className="fas fa-tasks"></i>
                  </div>
                  <div className="summary-content">
                    <p className="summary-label">Total Requests</p>
                    <h3 className="summary-value">{formatNumber(usageData.totalUsage?.totalRequests || 0)}</h3>
                  </div>
                </Card.Body>
              </Card>
            </Col>
            <Col md={3}>
              <Card className="summary-card total-tokens-card">
                <Card.Body>
                  <div className="summary-icon">
                    <i className="fas fa-coins"></i>
                  </div>
                  <div className="summary-content">
                    <p className="summary-label">Total Tokens</p>
                    <h3 className="summary-value">{formatNumber(usageData.totalUsage?.totalTokens || 0)}</h3>
                  </div>
                </Card.Body>
              </Card>
            </Col>
            <Col md={3}>
              <Card className="summary-card avg-cost-card">
                <Card.Body>
                  <div className="summary-icon">
                    <i className="fas fa-calculator"></i>
                  </div>
                  <div className="summary-content">
                    <p className="summary-label">Cost Per Request</p>
                    <h3 className="summary-value">
                      {formatCurrency(
                        usageData.totalUsage?.totalRequests > 0 
                          ? usageData.totalUsage.totalCostUsd / usageData.totalUsage.totalRequests 
                          : 0
                      )}
                    </h3>
                  </div>
                </Card.Body>
              </Card>
            </Col>
          </Row>

          {/* Cost Over Time Chart */}
          {usageData.dailyBreakdown && usageData.dailyBreakdown.length > 0 && (
            <Card className="cost-over-time-card mb-4">
              <Card.Header className="d-flex justify-content-between align-items-center">
                <h5 className="mb-0">
                  <i className="fas fa-chart-line me-2"></i>
                  Cost Over Time
                </h5>
                <span className="date-range-badge">
                  <i className="fas fa-calendar-alt me-1"></i>
                  {formatDateDisplay(fromDate)} to {formatDateDisplay(toDate)}
                </span>
              </Card.Header>
              <Card.Body>
                <div className="chart-container" style={{ height: '300px' }}>
                  <Line
                    data={{
                      labels: usageData.dailyBreakdown.map(d => {
                        const date = new Date(d.date + 'T00:00:00');
                        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
                      }),
                      datasets: [{
                        label: 'Daily Cost (USD)',
                        data: usageData.dailyBreakdown.map(d => d.costUsd),
                        borderColor: 'rgba(75, 192, 192, 1)',
                        backgroundColor: 'rgba(75, 192, 192, 0.2)',
                        tension: 0.3,
                        fill: true,
                        pointRadius: 4,
                        pointHoverRadius: 6,
                        pointBackgroundColor: 'rgba(75, 192, 192, 1)',
                        pointBorderColor: '#fff',
                        pointBorderWidth: 2
                      }]
                    }}
                    options={{
                      responsive: true,
                      maintainAspectRatio: false,
                      interaction: {
                        mode: 'index',
                        intersect: false,
                      },
                      plugins: {
                        legend: {
                          display: true,
                          position: 'top',
                        },
                        tooltip: {
                          callbacks: {
                            label: function(context) {
                              let label = context.dataset.label || '';
                              if (label) {
                                label += ': ';
                              }
                              label += '$' + context.parsed.y.toFixed(6);
                              return label;
                            },
                            footer: function(tooltipItems) {
                              const index = tooltipItems[0].dataIndex;
                              const dailyData = usageData.dailyBreakdown[index];
                              return [
                                `Requests: ${dailyData.requests.toLocaleString()}`,
                                `Tokens: ${dailyData.totalTokens.toLocaleString()}`
                              ];
                            }
                          }
                        }
                      },
                      scales: {
                        x: {
                          grid: {
                            display: false
                          }
                        },
                        y: {
                          beginAtZero: true,
                          ticks: {
                            callback: function(value) {
                              return '$' + value.toFixed(4);
                            }
                          },
                          grid: {
                            color: 'rgba(0, 0, 0, 0.05)'
                          }
                        }
                      }
                    }}
                  />
                </div>
              </Card.Body>
            </Card>
          )}

          {/* Tokens by Model and Requests by Provider Charts */}
          <Row className="mb-4">
            <Col md={6}>
              <Card className="tokens-by-model-card">
                <Card.Header className="d-flex justify-content-between align-items-center">
                  <h5 className="mb-0">
                    <i className="fas fa-layer-group me-2"></i>
                    Tokens by Model
                  </h5>
                  <span className="date-range-badge">
                    <i className="fas fa-calendar-alt me-1"></i>
                    {formatDateDisplay(fromDate)} to {formatDateDisplay(toDate)}
                  </span>
                </Card.Header>
                <Card.Body>
                  {usageData.modelBreakdown && Object.keys(usageData.modelBreakdown).length > 0 ? (
                    <div className="chart-container" style={{ height: '300px' }}>
                      <Bar
                        data={{
                          labels: Object.values(usageData.modelBreakdown).map(m => m.modelName),
                          datasets: [
                            {
                              label: 'Input Tokens',
                              data: Object.values(usageData.modelBreakdown).map(m => m.promptTokens),
                              backgroundColor: 'rgba(54, 162, 235, 0.8)',
                              borderColor: 'rgba(54, 162, 235, 1)',
                              borderWidth: 1
                            },
                            {
                              label: 'Output Tokens',
                              data: Object.values(usageData.modelBreakdown).map(m => m.completionTokens),
                              backgroundColor: 'rgba(255, 99, 132, 0.8)',
                              borderColor: 'rgba(255, 99, 132, 1)',
                              borderWidth: 1
                            }
                          ]
                        }}
                        options={{
                          responsive: true,
                          maintainAspectRatio: false,
                          plugins: {
                            legend: {
                              display: true,
                              position: 'top',
                            },
                            tooltip: {
                              callbacks: {
                                label: function(context) {
                                  let label = context.dataset.label || '';
                                  if (label) {
                                    label += ': ';
                                  }
                                  const value = context.parsed.y !== undefined ? context.parsed.y : context.parsed;
                                  label += value.toLocaleString();
                                  return label;
                                }
                              }
                            }
                          },
                          scales: {
                            x: {
                              stacked: true,
                              grid: {
                                display: false
                              }
                            },
                            y: {
                              stacked: true,
                              beginAtZero: true,
                              ticks: {
                                callback: function(value) {
                                  return value.toLocaleString();
                                }
                              },
                              grid: {
                                color: 'rgba(0, 0, 0, 0.05)'
                              }
                            }
                          }
                        }}
                      />
                    </div>
                  ) : (
                    <p className="text-muted text-center">No token data available</p>
                  )}
                </Card.Body>
              </Card>
            </Col>

            <Col md={6}>
              <Card className="requests-by-provider-card">
                <Card.Header className="d-flex justify-content-between align-items-center">
                  <h5 className="mb-0">
                    <i className="fas fa-server me-2"></i>
                    Requests by Provider
                  </h5>
                  <span className="date-range-badge">
                    <i className="fas fa-calendar-alt me-1"></i>
                    {formatDateDisplay(fromDate)} to {formatDateDisplay(toDate)}
                  </span>
                </Card.Header>
                <Card.Body>
                  {usageData.providerBreakdown && Object.keys(usageData.providerBreakdown).length > 0 ? (
                    <div className="chart-container" style={{ height: '300px' }}>
                      <Bar
                        data={{
                          labels: Object.values(usageData.providerBreakdown).map(p => 
                            p.provider.charAt(0).toUpperCase() + p.provider.slice(1)
                          ),
                          datasets: [{
                            label: 'Requests',
                            data: Object.values(usageData.providerBreakdown).map(p => p.requests),
                            backgroundColor: [
                              'rgba(16, 185, 129, 0.8)',  // Green for first provider
                              'rgba(59, 130, 246, 0.8)',  // Blue for second
                              'rgba(228, 143, 14, 0.8)'   // Orange for third
                            ],
                            borderColor: [
                              'rgba(16, 185, 129, 1)',
                              'rgba(59, 130, 246, 1)',
                              'rgba(228, 143, 14, 1)'
                            ],
                            borderWidth: 2
                          }]
                        }}
                        options={{
                          responsive: true,
                          maintainAspectRatio: false,
                          plugins: {
                            legend: {
                              display: false
                            },
                            tooltip: {
                              callbacks: {
                                label: function(context) {
                                  let label = context.dataset.label || '';
                                  if (label) {
                                    label += ': ';
                                  }
                                  const value = context.parsed.y !== undefined ? context.parsed.y : context.parsed;
                                  label += value.toLocaleString();
                                  return label;
                                },
                                footer: function(tooltipItems) {
                                  const index = tooltipItems[0].dataIndex;
                                  const provider = Object.values(usageData.providerBreakdown)[index];
                                  return [
                                    `Tokens: ${provider.totalTokens.toLocaleString()}`,
                                    `Cost: $${provider.costUsd.toFixed(6)}`
                                  ];
                                }
                              }
                            }
                          },
                          scales: {
                            x: {
                              grid: {
                                display: false
                              }
                            },
                            y: {
                              beginAtZero: true,
                              ticks: {
                                precision: 0,
                                callback: function(value) {
                                  return value.toLocaleString();
                                }
                              },
                              grid: {
                                color: 'rgba(0, 0, 0, 0.05)'
                              }
                            }
                          }
                        }}
                      />
                    </div>
                  ) : (
                    <p className="text-muted text-center">No provider data available</p>
                  )}
                </Card.Body>
              </Card>
            </Col>
          </Row>

          {/* Model Breakdown Table */}
          <Card className="model-breakdown-card">
            <Card.Header className="d-flex justify-content-between align-items-center">
              <h5 className="mb-0">
                <i className="fas fa-table me-2"></i>
                Model Usage Breakdown
              </h5>
              <span className="date-range-badge">
                <i className="fas fa-calendar-alt me-1"></i>
                {formatDateDisplay(fromDate)} to {formatDateDisplay(toDate)}
              </span>
            </Card.Header>
            <Card.Body>
              {usageData.modelBreakdown && Object.keys(usageData.modelBreakdown).length > 0 ? (
                <div className="table-responsive">
                  <Table hover className="model-breakdown-table">
                    <thead>
                      <tr>
                        <th>Model</th>
                        <th className="text-center">Provider</th>
                        <th className="text-end">Requests</th>
                        <th className="text-end">Input Tokens</th>
                        <th className="text-end">Output Tokens</th>
                        <th className="text-end">Total Tokens</th>
                        <th className="text-end">Total Cost</th>
                        <th className="text-end">Avg Cost/Request</th>
                        <th className="text-end">% of Total Cost</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Object.values(usageData.modelBreakdown).map((model, index) => (
                        <tr key={index}>
                          <td>
                            <div className="model-name-cell">
                              <div className="model-name">
                                <i className="fas fa-robot me-2"></i>
                                {model.modelName}
                              </div>
                              {model.versions && model.versions.length > 0 && (
                                <div className="model-versions">
                                  <small className="text-muted">
                                    Versions: {model.versions.join(', ')}
                                  </small>
                                </div>
                              )}
                            </div>
                          </td>
                          <td className="text-center">
                            <span className={`provider-badge ${model.provider.toLowerCase()}`}>
                              {model.provider}
                            </span>
                          </td>
                          <td className="text-end">{formatNumber(model.requests)}</td>
                          <td className="text-end">{formatNumber(model.promptTokens)}</td>
                          <td className="text-end">{formatNumber(model.completionTokens)}</td>
                          <td className="text-end">
                            <strong>{formatNumber(model.totalTokens)}</strong>
                          </td>
                          <td className="text-end">
                            <strong className="cost-value">{formatCurrency(model.costUsd)}</strong>
                          </td>
                          <td className="text-end">
                            {formatCurrency(model.requests > 0 ? model.costUsd / model.requests : 0)}
                          </td>
                          <td className="text-end">
                            <span className="percentage-badge">
                              {((model.costUsd / usageData.totalUsage.totalCostUsd) * 100).toFixed(1)}%
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr className="total-row">
                        <td colSpan="2"><strong>TOTAL</strong></td>
                        <td className="text-end"><strong>{formatNumber(usageData.totalUsage?.totalRequests || 0)}</strong></td>
                        <td className="text-end"><strong>{formatNumber(usageData.totalUsage?.totalPromptTokens || 0)}</strong></td>
                        <td className="text-end"><strong>{formatNumber(usageData.totalUsage?.totalCompletionTokens || 0)}</strong></td>
                        <td className="text-end"><strong>{formatNumber(usageData.totalUsage?.totalTokens || 0)}</strong></td>
                        <td className="text-end"><strong>{formatCurrency(usageData.totalUsage?.totalCostUsd || 0)}</strong></td>
                        <td className="text-end">
                          <strong>
                            {formatCurrency(
                              usageData.totalUsage?.totalRequests > 0 
                                ? usageData.totalUsage.totalCostUsd / usageData.totalUsage.totalRequests 
                                : 0
                            )}
                          </strong>
                        </td>
                        <td className="text-end"><strong>100%</strong></td>
                      </tr>
                    </tfoot>
                  </Table>
                </div>
              ) : (
                <Alert variant="info" className="mb-0">
                  <i className="fas fa-info-circle me-2"></i>
                  No LLM usage data available for the selected date range.
                </Alert>
              )}
            </Card.Body>
          </Card>
        </>
      ) : (
        <Alert variant="info">
          <i className="fas fa-info-circle me-2"></i>
          No usage data available. Select a date range and click "Apply Date Range".
        </Alert>
      )}
    </Container>
  );
};

export default LlmUsage;

