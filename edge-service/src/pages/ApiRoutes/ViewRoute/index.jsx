import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { FaRoute, FaNetworkWired } from 'react-icons/fa';
import { Form } from 'react-bootstrap';
import { LoadingSpinner } from '../../../components/common/LoadingSpinner';
import { apiRouteService } from '../../../services/apiRouteService';
import RouteDetails from '../../../components/apiroutes/RouteDetails';
import RouteEndpoints from '../../../components/apiroutes/RouteEndpoints';
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
          <h2>API Route</h2>
          <div className="route-identifier">{route.routeIdentifier}</div>
        </div>
        <div className="view-toggle">
          <div 
            className={`toggle-option ${!showEndpoints ? 'active' : ''}`}
            onClick={() => setShowEndpoints(false)}
            role="button"
          >
            <FaRoute /> Details
          </div>
          <Form.Check 
            type="switch"
            id="view-switch"
            checked={showEndpoints}
            onChange={(e) => setShowEndpoints(e.target.checked)}
            className="mx-2"
          />
          <div 
            className={`toggle-option ${showEndpoints ? 'active' : ''}`}
            onClick={() => setShowEndpoints(true)}
            role="button"
          >
            <FaNetworkWired /> Endpoints
          </div>
        </div>
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