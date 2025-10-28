import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { FaRoute, FaNetworkWired } from 'react-icons/fa';
import { LoadingSpinner } from '../../../components/common/LoadingSpinner';
import { apiRouteService } from '../../../services/apiRouteService';
import RouteDetails from '../../../components/apiroutes/RouteDetails';
import RouteEndpoints from '../../../components/apiroutes/RouteEndpoints';
import ServiceInteractionStats from '../../../components/apiroutes/ServiceInteractionStats';
import ServiceTopEndpoints from '../../../components/apiroutes/ServiceTopEndpoints';
import './styles.css';

const ViewRoute = () => {
  const [showEndpoints, setShowEndpoints] = useState(true);
  const [route, setRoute] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { routeId } = useParams();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchRouteDetails = async () => {
      try {
        const data = await apiRouteService.getRouteByIdentifier(routeId);
        setRoute(data);
      } catch (err) {
        setError('Failed to fetch route details: ' + err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchRouteDetails();
  }, [routeId]);

  if (loading) return <LoadingSpinner />;
  if (error) return <div className="error-message">{error}</div>;
  if (!route) return <div className="error-message">Route not found</div>;

  return (
    <div className="view-route-layout">
      <div className="route-header">
        <div className="route-title-section">
          <h2>{route.routeIdentifier}</h2>
          <div className="route-subtitle">Configure app settings, API endpoints, resiliency, and monitor performance with version control</div>
        </div>
      </div>
      
      <ServiceInteractionStats serviceName={route.routeIdentifier} />
      
      <ServiceTopEndpoints serviceName={route.routeIdentifier} />
      
      <div className="tab-navigation">
      <button 
          className={`tab-button ${showEndpoints ? 'active' : ''}`}
          onClick={() => setShowEndpoints(true)}
          type="button"
        >
          <FaNetworkWired />
          <span>API Endpoints</span>
        </button>
        <button 
          className={`tab-button ${!showEndpoints ? 'active' : ''}`}
          onClick={() => setShowEndpoints(false)}
          type="button"
        >
          <FaRoute />
          <span>Route Details</span>
        </button>
      </div>
      
      <div className="content-area">
        {showEndpoints ? (
          <RouteEndpoints route={route} />
        ) : (
          <RouteDetails 
            route={route}
            setRoute={setRoute}
            activeItem="basic"
          />
        )}
      </div>
    </div>
  );
};

export default ViewRoute;