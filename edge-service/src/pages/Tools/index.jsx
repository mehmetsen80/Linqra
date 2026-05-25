import React, { useState } from 'react';
import { Container, Breadcrumb, Card, Tabs, Tab } from 'react-bootstrap';
import { HiPlus } from 'react-icons/hi';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import Button from '../../components/common/Button';
import ToolEditorModal from '../../components/tools/ToolEditorModal';
import ToolCatalog from './ToolCatalog';
import McpConsole from './McpConsole';
import ToolExecutionsHistory from './ToolExecutionsHistory';
import toolService from '../../services/toolService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import './styles.css';

const Tools = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { user } = useAuth();
    const { currentTeam } = useTeam();
    const [showModal, setShowModal] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);
    const [activeTab, setActiveTab] = useState('catalog');
    const [mcpInitialPayload, setMcpInitialPayload] = useState(null);

    // If navigated here from ViewTool with MCP payload, activate MCP tab
    React.useEffect(() => {
        if (location.state?.tab === 'mcp') {
            setActiveTab('mcp');
            if (location.state?.mcpPayload) {
                setMcpInitialPayload(location.state.mcpPayload);
            }
            // Clear nav state so re-visiting tab doesn't re-inject
            window.history.replaceState({}, document.title);
        }
    }, [location.state]);

    return (
        <Container fluid className="tools-page py-4">
            <Card className="mb-4 mx-1 p-0 border-0 shadow-sm overflow-hidden">
                <Card.Header className="d-flex justify-content-between align-items-center bg-white py-3 px-4">
                    {user ? (
                        <Breadcrumb className="mb-0">
                            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/' }}>
                                Home
                            </Breadcrumb.Item>
                            <Breadcrumb.Item
                                linkAs={Link}
                                linkProps={{ to: '/organizations' }}
                            >
                                {currentTeam?.organization?.name || 'Organization'}
                            </Breadcrumb.Item>
                            <Breadcrumb.Item
                                onClick={() => currentTeam?.id && navigate(`/teams/${currentTeam.id}`)}
                                style={{ cursor: currentTeam?.id ? 'pointer' : 'default' }}
                            >
                                {currentTeam?.name || 'Team'}
                            </Breadcrumb.Item>
                            <Breadcrumb.Item active>Tools Catalog</Breadcrumb.Item>
                        </Breadcrumb>
                    ) : (
                        <div className="d-flex align-items-center justify-content-between w-100">
                            <div className="d-flex align-items-center">
                                <h4 className="mb-0 fw-bold text-dark">Linqra Tool Marketplace</h4>
                                <span className="ms-3 text-muted small border-start ps-3 d-none d-md-inline">Explore and integrate the next generation of AI Tools</span>
                            </div>
                            <Button
                                variant="outline-primary"
                                size="sm"
                                onClick={() => window.open('https://linqra.com/contact', '_blank')}
                                className="fw-bold px-3 shadow-none"
                            >
                                Request API Access
                            </Button>
                        </div>
                    )}

                    {user && (
                        <Button
                            variant="primary"
                            onClick={() => setShowModal(true)}
                            className="d-flex align-items-center"
                        >
                            <HiPlus className="me-2" /> Register Tool
                        </Button>
                    )}
                </Card.Header>
            </Card>

            {user ? (
                <Tabs
                    activeKey={activeTab}
                    onSelect={(k) => setActiveTab(k)}
                    className="mb-4 border-0 mcp-page-tabs mx-1"
                >
                    <Tab eventKey="catalog" title="📦 Curated Tools Catalog">
                        <div className="py-2">
                            <ToolCatalog
                                key={`${refreshKey}-${currentTeam?.id}`}
                                teamId={currentTeam?.id}
                            />
                        </div>
                    </Tab>
                    <Tab eventKey="mcp" title="⚡ MCP Developer Console">
                        <div className="py-2">
                            <McpConsole teamId={currentTeam?.id} initialPayload={mcpInitialPayload} />
                        </div>
                    </Tab>
                    <Tab eventKey="history" title="📜 Tool Executions History">
                        <div className="py-2">
                            <ToolExecutionsHistory />
                        </div>
                    </Tab>
                </Tabs>
            ) : (
                <ToolCatalog
                    key={`${refreshKey}-${currentTeam?.id}`}
                    teamId={currentTeam?.id}
                />
            )}

            <ToolEditorModal
                show={showModal}
                onHide={() => setShowModal(false)}
                editMode={false}
                onSuccess={(tool) => {
                    setRefreshKey(prev => prev + 1);
                    if (tool?.toolId) navigate(`/tools/${tool.toolId}`);
                }}
            />
        </Container>
    );
};

export default Tools;
