import React from 'react';
import { Alert } from 'react-bootstrap';
import UserSummary from '../../components/dashboard/UserSummary';
import StatsSection from '../../components/dashboard/StatsSection';
import LatencyChart from '../../components/dashboard/LatencyChart';
import ModulesSection from '../../components/dashboard/ModulesSection';
import ServiceUsagePie from '../../components/dashboard/ServiceUsagePie';
import TeamRoutes from '../../components/dashboard/TeamRoutes';
import AgentTasks from '../../components/dashboard/AgentTasks';
import AgentStats from '../../components/dashboard/AgentStats';
import LlmCosts from '../../components/dashboard/LlmCosts';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin } from '../../utils/roleUtils';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import './styles.css';

function Dashboard() {
  const { currentTeam, loading: teamLoading } = useTeam();
  const { user, loading: authLoading } = useAuth();

  // Wait for both auth and team data to load before making decisions
  if (authLoading || teamLoading) {
    return <LoadingSpinner />;
  }

  // Only show "No Team Access" when we're certain loading is complete and there's no team
  if (!currentTeam && user && !isSuperAdmin(user)) {
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
      <StatsSection />
      <TeamRoutes />
      <LlmCosts />
      <AgentTasks />
      <AgentStats />
      {/* Workflows functionality has been removed - Agents now handle workflow execution directly
          This eliminates redundancy and simplifies the user experience by consolidating
          workflow management within the Agent interface where users can create and execute
          workflows as part of agent tasks */}
      {/* <Workflows /> */}
      {/* <WorkflowsStats /> */}
      <div className="dashboard-charts">
        <LatencyChart />
        <ServiceUsagePie />
      </div>
      <ModulesSection />
    </div>
  );
}

export default Dashboard;