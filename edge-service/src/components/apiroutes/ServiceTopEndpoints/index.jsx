import React, { useState, useEffect } from 'react';
import { FaChartBar, FaClock, FaList, FaInfoCircle } from 'react-icons/fa';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import { apiRouteService } from '../../../services/apiRouteService';
import './styles.css';

const ServiceTopEndpoints = ({ serviceName }) => {
  const [endpoints, setEndpoints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [limit, setLimit] = useState(5);

  useEffect(() => {
    if (serviceName) {
      fetchTopEndpoints();
    }
  }, [serviceName, limit]);

  const fetchTopEndpoints = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiRouteService.getTopEndpointsByService(serviceName, null, null, limit);
      setEndpoints(data);
    } catch (err) {
      setError('Failed to fetch top endpoints: ' + err.message);
      console.error('Error fetching top endpoints:', err);
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
    if (ms === null || ms === undefined || isNaN(ms)) {
      return '0ms';
    }
    if (ms >= 1000) {
      return (ms / 1000).toFixed(1) + 's';
    }
    return ms.toFixed(0) + 'ms';
  };

  const getEndpointDisplayName = (endpoint) => {
    if (!endpoint) return 'Unknown';
    
    // Remove common prefixes and make it more readable
    let displayName = endpoint
      .replace(/^\/r\/[^/]+\//, '/') // Remove /r/service-name/ prefix
      .replace(/^\/[^/]+\/api\//, '/api/') // Simplify /service/api/ to /api/
      .replace(/\/api\//, '/'); // Remove /api/ for cleaner display
    
    // If it's just a slash, show it as root
    if (displayName === '/') {
      return 'Root Endpoint';
    }
    
    return displayName;
  };

  if (loading) {
    return (
      <div className="service-top-endpoints-container">
        <div className="service-top-endpoints-header">
          <h3>Top Endpoints</h3>
        </div>
        <div className="endpoints-loading">
          <div className="loading-spinner"></div>
          <span>Loading top endpoints...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="service-top-endpoints-container">
        <div className="service-top-endpoints-header">
          <h3>Top Endpoints</h3>
        </div>
        <div className="endpoints-error">
          <FaList />
          <span>{error}</span>
        </div>
      </div>
    );
  }

  return (
    <div className="service-top-endpoints-container">
      <div className="service-top-endpoints-header">
        <h3>
          <FaChartBar />
          Top Endpoints: {serviceName}
        </h3>
        <div className="limit-controls">
          <label htmlFor="limit-select">Show:</label>
          <select 
            id="limit-select"
            value={limit} 
            onChange={(e) => setLimit(parseInt(e.target.value))}
            className="limit-select"
          >
            <option value={5}>Top 5</option>
            <option value={10}>Top 10</option>
            <option value={15}>Top 15</option>
            <option value={20}>Top 20</option>
          </select>
        </div>
      </div>
      
      {endpoints.length === 0 ? (
        <div className="no-endpoints">
          <FaList />
          <span>No endpoint data available for this service</span>
        </div>
      ) : (
        <div className="endpoints-list">
          {endpoints.map((endpoint, index) => (
            <div key={`${endpoint.endpoint}-${index}`} className="endpoint-item">
              <div className="endpoint-rank">
                <span className="rank-number">#{index + 1}</span>
              </div>
              
              <div className="endpoint-info">
                <div className="endpoint-name">
                  <span className="endpoint-path">{getEndpointDisplayName(endpoint.endpoint)}</span>
                  <OverlayTrigger
                    placement="top"
                    overlay={
                      <Tooltip id={`tooltip-${index}`}>
                        <div className="endpoint-tooltip">
                          <strong>Full URL:</strong><br />
                          {endpoint.endpoint}
                        </div>
                      </Tooltip>
                    }
                  >
                    <span className="endpoint-info-icon">
                      <FaInfoCircle />
                    </span>
                  </OverlayTrigger>
                </div>
                <div className="endpoint-services">
                  {endpoint.services && endpoint.services.length > 0 ? (
                    <span className="services-badge">
                      {endpoint.services.join(', ')}
                    </span>
                  ) : (
                    <span className="services-badge">Unknown Service</span>
                  )}
                </div>
              </div>
              
              <div className="endpoint-metrics">
                <div className="metric-group">
                  <div className="metric-item">
                    <div className="metric-content">
                      <FaChartBar className="metric-icon" />
                      <div className="metric-value">{formatNumber(endpoint.count)}</div>
                      <div className="metric-label">Requests</div>
                    </div>
                  </div>
                </div>
                
                <div className="metric-group">
                  <div className="metric-item">
                    <div className="metric-content">
                      <FaClock className="metric-icon" />
                      <div className="metric-value">{formatDuration(endpoint.avgDuration)}</div>
                      <div className="metric-label">Avg Time</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default ServiceTopEndpoints;
