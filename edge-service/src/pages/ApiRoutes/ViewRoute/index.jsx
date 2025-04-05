import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { FaRoute, FaNetworkWired } from 'react-icons/fa';
import { LoadingSpinner } from '../../../components/common/LoadingSpinner';
import { apiRouteService } from '../../../services/apiRouteService';
import RouteDetails from '../../../components/apiroutes/RouteDetails';
import RouteEndpoints from '../../../components/apiroutes/RouteEndpoints';
import './styles.css';

const ViewRoute = () => {
  const [activeItem, setActiveItem] = useState('config');
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

  const renderContent = () => {
    switch (activeItem) {
      case 'basic':
        return (
          <RouteDetails 
            route={route}
            setRoute={setRoute}
            activeItem={activeItem}
          />
        );
      case 'config':
        return <RouteEndpoints route={route} />;
      default:
        return <RouteDetails route={route} setRoute={setRoute} activeItem={activeItem} />;
    }
  };

  return (
    <div className="view-route-layout">
      <nav className="left-navbar">
        <div className="nav-header">
          <h2>API Route</h2>
          <div className="route-identifier">{route.routeIdentifier}</div>
        </div>
        <ul className="nav-menu">
          <li className="nav-item">
            <button 
              className={`nav-button ${activeItem === 'config' ? 'active' : ''}`}
              onClick={() => setActiveItem('config')}
            >
              <FaNetworkWired className="nav-icon" />
              <span>Route Endpoints</span>
            </button>
          </li>
          <li className="nav-item">
            <button 
              className={`nav-button ${activeItem === 'basic' ? 'active' : ''}`}
              onClick={() => setActiveItem('basic')}
            >
              <FaRoute className="nav-icon" />
              <span>Route Details</span>
            </button>
          </li>
        </ul>
      </nav>
      <div className="content-area">
        {renderContent()}
      </div>
    </div>
  );
};

export default ViewRoute;