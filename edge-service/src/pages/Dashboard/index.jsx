import React from 'react';
import { Alert } from 'react-bootstrap';
import UserSummary from '../../components/dashboard/UserSummary';
import StatsSection from '../../components/dashboard/StatsSection';
import LatencyChart from '../../components/dashboard/LatencyChart';
import ModulesSection from '../../components/dashboard/ModulesSection';
import ServiceUsagePie from '../../components/dashboard/ServiceUsagePie';
import TeamRoutes from '../../components/dashboard/TeamRoutes';
import Workflows from '../../components/dashboard/Workflows';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin } from '../../utils/roleUtils';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import './styles.css';

function Dashboard() {
  const { currentTeam, loading } = useTeam();
  const { user } = useAuth();

  if (loading) {
    return <LoadingSpinner />;
  }

  if (!currentTeam && !isSuperAdmin(user)) {
    return (
      <div className="dashboard-container">
        <Alert variant="info" className="no-team-alert">
          <Alert.Heading>No Team Access</Alert.Heading>
          <p>
            You currently don't have access to any team. Please contact your administrator 
            to get assigned to a team to view the dashboard and access other features.
          </p>
        </Alert>
      </div>
    );
  }

  return (
    <div className="dashboard-container">
      <UserSummary />
      <TeamRoutes />
      <StatsSection />
      <Workflows />
      <div className="dashboard-charts">
        <LatencyChart />
        <ServiceUsagePie />
      </div>
      <ModulesSection />
    </div>
  );
}

export default Dashboard;