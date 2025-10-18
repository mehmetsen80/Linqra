import React, { useState, useEffect } from 'react';
import { FaArrowDown, FaArrowUp, FaClock, FaCheckCircle, FaTimesCircle, FaChartLine } from 'react-icons/fa';
import { apiRouteService } from '../../../services/apiRouteService';
import './styles.css';

const ServiceInteractionStats = ({ serviceName }) => {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (serviceName) {
      fetchStats();
    }
  }, [serviceName]);

  const fetchStats = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiRouteService.getServiceInteractionsSummary(serviceName);
      setStats(data);
    } catch (err) {
      setError('Failed to fetch service interaction stats: ' + err.message);
      console.error('Error fetching stats:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatNumber = (num) => {
    if (num === null || num === undefined || isNaN(num)) {
      return '0';
    }
    if (num >= 1000000) {
      return (num / 1000000).toFixed(1) + 'M';
    } else if (num >= 1000) {
      return (num / 1000).toFixed(1) + 'K';
    }
    return num.toString();
  };

  const formatDuration = (ms) => {
    if (ms >= 1000) {
      return (ms / 1000).toFixed(1) + 's';
    }
    return ms.toFixed(0) + 'ms';
  };

  const formatPercentage = (num) => {
    return num.toFixed(1) + '%';
  };

  if (loading) {
    return (
      <div className="service-stats-container">
        <div className="service-stats-header">
          <h3>Service Performance</h3>
        </div>
        <div className="stats-loading">
          <div className="loading-spinner"></div>
          <span>Loading performance data...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="service-stats-container">
        <div className="service-stats-header">
          <h3>Service Performance</h3>
        </div>
        <div className="stats-error">
          <FaTimesCircle />
          <span>{error}</span>
        </div>
      </div>
    );
  }

  if (!stats) {
    return null;
  }

  const { incoming, outgoing, serviceName: service } = stats;

  // Handle different response formats and provide defaults
  const processStatsData = (data) => {
    if (!data) {
      return {
        totalCount: 0,
        avgDuration: 0,
        totalDuration: 0,
        minDuration: 0,
        maxDuration: 0,
        successCount: 0,
        failureCount: 0,
        successRate: 0
      };
    }

    // If it's an array, aggregate the data
    if (Array.isArray(data)) {
      if (data.length === 0) {
        return {
          totalCount: 0,
          avgDuration: 0,
          totalDuration: 0,
          minDuration: 0,
          maxDuration: 0,
          successCount: 0,
          failureCount: 0,
          successRate: 0
        };
      }
      
      // Aggregate array data
      const aggregated = data.reduce((acc, item) => {
        acc.totalCount += item.count || 0;
        acc.totalDuration += item.totalDuration || 0;
        acc.successCount += item.successCount || 0;
        acc.failureCount += item.failureCount || 0;
        acc.minDuration = Math.min(acc.minDuration, item.minDuration || 0);
        acc.maxDuration = Math.max(acc.maxDuration, item.maxDuration || 0);
        return acc;
      }, {
        totalCount: 0,
        totalDuration: 0,
        successCount: 0,
        failureCount: 0,
        minDuration: Infinity,
        maxDuration: 0
      });

      aggregated.avgDuration = aggregated.totalCount > 0 ? aggregated.totalDuration / aggregated.totalCount : 0;
      aggregated.successRate = aggregated.totalCount > 0 ? (aggregated.successCount * 100 / aggregated.totalCount) : 0;
      aggregated.minDuration = aggregated.minDuration === Infinity ? 0 : aggregated.minDuration;

      return aggregated;
    }

    // If it's already an object, return it with defaults
    return {
      totalCount: data.totalCount || 0,
      avgDuration: data.avgDuration || 0,
      totalDuration: data.totalDuration || 0,
      minDuration: data.minDuration || 0,
      maxDuration: data.maxDuration || 0,
      successCount: data.successCount || 0,
      failureCount: data.failureCount || 0,
      successRate: data.successRate || 0
    };
  };

  const incomingStats = processStatsData(incoming);
  const outgoingStats = processStatsData(outgoing);

  return (
    <div className="service-stats-container">
      <div className="service-stats-header">
        <h3>
          <FaChartLine />
          Service Performance: {service}
        </h3>
      </div>
      
      <div className="stats-grid">
        {/* Incoming Traffic */}
        <div className="service-stats-section">
          <div className="service-stats-section-header">
            <FaArrowDown className="incoming-icon" />
            <h4>Incoming Traffic</h4>
          </div>
          <div className="stats-metrics">
            <div className="metric-card">
              <div className="metric-icon">
                <FaChartLine />
              </div>
              <div className="metric-content">
                <div className="metric-value">{formatNumber(incomingStats.totalCount)}</div>
                <div className="metric-label">Total Requests</div>
              </div>
            </div>
            
            <div className="metric-card">
              <div className="metric-icon">
                <FaClock />
              </div>
              <div className="metric-content">
                <div className="metric-value">{formatDuration(incomingStats.avgDuration)}</div>
                <div className="metric-label">Avg Response Time</div>
              </div>
            </div>
            
            <div className="metric-card">
              <div className="metric-icon">
                <FaCheckCircle className="success-icon" />
              </div>
              <div className="metric-content">
                <div className="metric-value">{formatPercentage(incomingStats.successRate)}</div>
                <div className="metric-label">Success Rate</div>
              </div>
            </div>
            
            <div className="metric-card">
              <div className="metric-icon">
                <FaTimesCircle className="error-icon" />
              </div>
              <div className="metric-content">
                <div className="metric-value">{formatNumber(incomingStats.failureCount)}</div>
                <div className="metric-label">Failed Requests</div>
              </div>
            </div>
          </div>
        </div>

        {/* Outgoing Traffic */}
        <div className="service-stats-section">
          <div className="service-stats-section-header">
            <FaArrowUp className="outgoing-icon" />
            <h4>Outgoing Traffic</h4>
          </div>
          <div className="stats-metrics">
            <div className="metric-card">
              <div className="metric-icon">
                <FaChartLine />
              </div>
              <div className="metric-content">
                <div className="metric-value">{formatNumber(outgoingStats.totalCount)}</div>
                <div className="metric-label">Total Requests</div>
              </div>
            </div>
            
            <div className="metric-card">
              <div className="metric-icon">
                <FaClock />
              </div>
              <div className="metric-content">
                <div className="metric-value">{formatDuration(outgoingStats.avgDuration)}</div>
                <div className="metric-label">Avg Response Time</div>
              </div>
            </div>
            
            <div className="metric-card">
              <div className="metric-icon">
                <FaCheckCircle className="success-icon" />
              </div>
              <div className="metric-content">
                <div className="metric-value">{formatPercentage(outgoingStats.successRate)}</div>
                <div className="metric-label">Success Rate</div>
              </div>
            </div>
            
            <div className="metric-card">
              <div className="metric-icon">
                <FaTimesCircle className="error-icon" />
              </div>
              <div className="metric-content">
                <div className="metric-value">{formatNumber(outgoingStats.failureCount)}</div>
                <div className="metric-label">Failed Requests</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ServiceInteractionStats;
