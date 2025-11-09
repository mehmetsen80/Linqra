import { Link } from 'react-router-dom';
import { useTeam } from '../../../contexts/TeamContext';
import './styles.css';

function ModulesSection() {
  const { currentTeam } = useTeam();
  const isAdmin = currentTeam?.roles?.includes('ADMIN');

  return (
    <div className="modules-section">
      <div className="section-header">
        <h2>Modules</h2>
        <p className="text-muted">Access and manage different aspects of your AI Agents</p>
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-card">
          <Link to="/api-routes" className="card-icon-link">
            <div className="card-icon">
              <i className="fas fa-route"></i>
            </div>
          </Link>
          <h3>Apps</h3>
          <p>Configure and manage your API routes and policies</p>
          <Link to="/api-routes" className="card-link">
            Manage Apps <i className="fas fa-arrow-right"></i>
          </Link>
        </div>

        <div className="dashboard-card">
          <Link to="/metrics" className="card-icon-link">
            <div className="card-icon">
              <i className="fas fa-chart-line"></i>
            </div>
          </Link>
          <h3>Metrics Overview</h3>
          <p>View detailed performance metrics and analytics</p>
          <Link to="/metrics" className="card-link">
            View Metrics <i className="fas fa-arrow-right"></i>
          </Link>
        </div>

        <div className="dashboard-card">
          <Link to="/service-status" className="card-icon-link">
            <div className="card-icon">
              <i className="fas fa-server"></i>
            </div>
          </Link>
          <h3>Services Status</h3>
          <p>Monitor health and performance of your microservices</p>
          <Link to="/service-status" className="card-link">
            Check Status <i className="fas fa-arrow-right"></i>
          </Link>
        </div>

        <div className="dashboard-card">
          <Link to="/linq-protocol" className="card-icon-link">
            <div className="card-icon">
              <i className="fas fa-link"></i>
            </div>
          </Link>
          <h3>Linq Protocol</h3>
          <p>Learn about the Linq Protocol and how to use it</p>
          <Link to="/linq-protocol" className="card-link">
            Learn More <i className="fas fa-arrow-right"></i>
          </Link>
        </div>

        {isAdmin && (
          <>
            <div className="dashboard-card">
              <Link to="/teams" className="card-icon-link">
                <div className="card-icon">
                  <i className="fas fa-users"></i>
                </div>
              </Link>
              <h3>Teams</h3>
              <p>Manage your teams and collaborate with members</p>
              <Link to="/teams" className="card-link">
                Manage Teams <i className="fas fa-arrow-right"></i>
              </Link>
            </div>

            <div className="dashboard-card">
              <Link to="/organizations" className="card-icon-link">
                <div className="card-icon">
                  <i className="fas fa-building"></i>
                </div>
              </Link>
              <h3>Organizations</h3>
              <p>Manage your organizations and their teams</p>
              <Link to="/organizations" className="card-link">
                Manage Organizations <i className="fas fa-arrow-right"></i>
              </Link>
            </div>

            {/* Workflows card removed - workflow functionality is now integrated into Agents
                Users can create and manage workflows directly within the Agent interface,
                eliminating the need for a separate workflows management section */}
            {/* <div className="dashboard-card">
              <Link to="/workflows" className="card-icon-link">
                <div className="card-icon">
                  <i className="fas fa-project-diagram"></i>
                </div>
              </Link>
              <h3>Workflows</h3>
              <p>Create and manage your workflow automations</p>
              <Link to="/workflows" className="card-link">
                Manage Workflows <i className="fas fa-arrow-right"></i>
              </Link>
            </div> */}

            <div className="dashboard-card">
              <Link to="/agents" className="card-icon-link">
                <div className="card-icon">
                  <i className="fas fa-robot"></i>
                </div>
              </Link>
              <h3>Agents</h3>
              <p>Create and manage your AI agents</p>
              <Link to="/agents" className="card-link">
                Manage Agents <i className="fas fa-arrow-right"></i>
              </Link>
            </div>
          </>
        )}

        <div className="dashboard-card disabled">
          <Link to="/alerts" className="card-icon-link">
            <div className="card-icon">
              <i className="fas fa-bell"></i>
            </div>
          </Link>
          <h3>Alerts</h3>
          <p>View and manage system alerts and notifications</p>
          <span className="coming-soon-badge">Coming Soon</span>
          <Link to="/alerts" className="card-link disabled">
            View Alerts <i className="fas fa-arrow-right"></i>
          </Link>
        </div>

        <div className="dashboard-card">
          <Link to="/rag" className="card-icon-link">
            <div className="card-icon">
              <i className="fas fa-cog"></i>
            </div>
          </Link>
          <h3>RAG</h3>
          <p>Configure Retrieval Augmented Generation preferences</p>
          <Link to="/rag" className="card-link">
            RAG Management <i className="fas fa-arrow-right"></i>
          </Link>
        </div>

        <div className="dashboard-card ">
          <Link to="/llm-models" className="card-icon-link">
            <div className="card-icon">
              <i className="fas fa-microchip"></i>
            </div>
          </Link>
          <h3>LLM Models</h3>
          <p>Manage and explore available AI LLM models for your applications</p>
          <Link to="/llm-models" className="card-link">
            Explore Models <i className="fas fa-arrow-right"></i>
          </Link>
        </div>

        <div className="dashboard-card disabled">
          <Link to="/tools" className="card-icon-link">
            <div className="card-icon">
              <i className="fas fa-toolbox"></i>
            </div>
          </Link>
          <h3>Tools</h3>
          <p>Connect and manage integrations like Google Drive, Excel, and PDF converters for your AI apps</p>
          <span className="coming-soon-badge">Coming Soon</span>
          <button className="card-link" disabled>
            Explore Tools <i className="fas fa-arrow-right"></i>
          </button>
        </div>
      </div>
    </div>
  );
}

export default ModulesSection;
