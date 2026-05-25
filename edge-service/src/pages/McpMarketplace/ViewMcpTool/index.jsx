import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Spinner, Row, Col, OverlayTrigger, Tooltip } from 'react-bootstrap';
import {
    FiArrowLeft, FiZap, FiActivity,
    FiCpu, FiShield, FiCode, FiWifi
} from 'react-icons/fi';
import { HiLightningBolt } from 'react-icons/hi';
import toolService from '../../../services/toolService';
import McpConsole from '../../Tools/McpConsole';
import McpToolSchema from '../../../components/common/McpToolSchema';
import './styles.css';

const categoryConfig = {
    analytics: { label: 'Analytics', icon: <FiActivity />, color: '#ed7534' },
    security:  { label: 'Security',  icon: <FiShield />,   color: '#ed7534' },
    utility:   { label: 'Utility',   icon: <FiCpu />,      color: '#ed7534' },
    legal:     { label: 'Legal',     icon: <FiCode />,     color: '#ed7534' },
    default:   { label: 'General',   icon: <FiZap />,      color: '#ed7534' },
};
const getCategoryConfig = (cat) => categoryConfig[cat?.toLowerCase()] || categoryConfig.default;

const ViewMcpTool = () => {
    const { toolId } = useParams();
    const navigate = useNavigate();

    const [tool, setTool] = useState(null);
    const [toolSkill, setToolSkill] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => { load(); }, [toolId]);

    const load = async (silent = false) => {
        if (!silent) setLoading(true);
        const [toolRes, skillRes] = await Promise.all([
            toolService.getTool(toolId, true),
            toolService.getToolSkill(toolId),
        ]);
        if (toolRes.success) setTool(toolRes.data);
        else if (!silent) setError(toolRes.error);
        if (skillRes.success) setToolSkill(skillRes.data);
        if (!silent) setLoading(false);
    };

    if (loading) return (
        <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
            <Spinner animation="border" style={{ color: '#ed7534' }} />
        </Container>
    );

    if (error || !tool) return (
        <Container className="py-5 text-center">
            <HiLightningBolt size={48} className="text-muted mb-3" style={{ opacity: 0.3 }} />
            <h4 className="text-danger mt-3">Tool not found</h4>
            <p className="text-muted">{error}</p>
            <button className="btn btn-outline-secondary mt-2" onClick={() => navigate('/mcp-marketplace')}>
                <FiArrowLeft className="me-2" /> Back to Marketplace
            </button>
        </Container>
    );

    const config = getCategoryConfig(tool.category);

    return (
        <div className="view-mcp-tool-page">
            {/* ── Breadcrumb bar – sits above the white header, full-width ── */}
            <div className="vmcp-breadcrumb-bar">
                <Container fluid className="px-4">
                    <button className="vmcp-back-link" onClick={() => navigate('/mcp-marketplace')}>
                        <FiArrowLeft size={13} /> Back to MCP Marketplace
                    </button>
                </Container>
            </div>

            {/* ── White page header ── */}
            <div className="vmcp-page-header">
                <Container fluid className="px-4">
                    <Row className="align-items-stretch">
                        <Col lg={8} md={7} className="mb-4 mb-md-0 d-flex">
                            <div className="vmcp-details-card p-4 w-100 text-start">
                                {/* Row 1: Icon + Title */}
                                <div className="d-flex align-items-center gap-4 mb-3">
                                    <div className="vmcp-hero-icon" style={{ background: '#fff7ed', border: '1px solid #fed7aa' }}>
                                        {React.cloneElement(config.icon, { size: 28, color: '#ed7534' })}
                                    </div>
                                    <h1 className="vmcp-tool-name mb-0">{tool.name}</h1>
                                </div>

                                {/* Row 2: Status badges */}
                                <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
                                    <span className="vmcp-live-badge"><FiWifi size={10} className="me-1" /> LIVE</span>
                                    {tool.visibility === 'PUBLIC' && <span className="vmcp-public-badge">PUBLIC</span>}
                                </div>

                                {/* Row 3: Meta chips */}
                                <div className="d-flex flex-wrap align-items-center gap-3 mb-4">
                                    <span className="vmcp-tool-id-badge">{tool.toolId}</span>
                                    <span className="vmcp-meta-divider" />
                                    <span className="vmcp-category-tag">
                                        {React.cloneElement(config.icon, { size: 12 })}
                                        {config.label}
                                    </span>
                                    {tool.pricing?.type && (
                                        <>
                                            <span className="vmcp-meta-divider" />
                                            <span className="vmcp-category-tag">
                                                <HiLightningBolt size={12} style={{ color: '#f59e0b' }} />
                                                {tool.pricing.type === 'FREE' ? 'Free Access' : `$${tool.pricing.cost} / Execution`}
                                            </span>
                                        </>
                                    )}
                                </div>

                                {/* Row 4: Description */}
                                {tool.description && (
                                    <div
                                        className="vmcp-description"
                                        dangerouslySetInnerHTML={{ __html: tool.description }}
                                    />
                                )}
                            </div>
                        </Col>

                        <Col lg={4} md={5} className="d-flex">
                            <div className="vmcp-stats-card p-4 w-100 text-start">
                                <div className="d-flex align-items-center justify-content-between mb-3 pb-2 border-bottom">
                                    <span className="fw-bold text-dark small text-uppercase font-monospace tracking-wide" style={{ fontSize: '0.74rem', letterSpacing: '0.05em' }}>
                                        ⚡ Operational Analytics
                                    </span>
                                    <span className="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 py-1 px-2" style={{ fontSize: '0.65rem', fontWeight: '700' }}>
                                        ● ONLINE
                                    </span>
                                </div>
                                <Row className="g-3">
                                    <Col xs={6}>
                                        <OverlayTrigger
                                            placement="top"
                                            overlay={
                                                <Tooltip id="total-runs-tooltip">
                                                    Total successful and failed execution attempts compiled from database logs.
                                                </Tooltip>
                                            }
                                        >
                                            <div className="vmcp-stat-box text-start p-3 rounded" style={{ cursor: 'pointer' }}>
                                                <div className="text-muted font-monospace mb-1" style={{ fontSize: '0.65rem', textTransform: 'uppercase', fontWeight: '600' }}>
                                                    Total Runs
                                                </div>
                                                <div className="fw-bold text-dark font-monospace" style={{ fontSize: '1.2rem', letterSpacing: '-0.02em' }}>
                                                    {tool.stats?.totalExecutions || '0'}
                                                </div>
                                            </div>
                                        </OverlayTrigger>
                                    </Col>
                                    <Col xs={6}>
                                        <OverlayTrigger
                                            placement="top"
                                            overlay={
                                                <Tooltip id="success-rate-tooltip">
                                                    Percentage of execution attempts that resolved successfully.
                                                </Tooltip>
                                            }
                                        >
                                            <div className="vmcp-stat-box text-start p-3 rounded" style={{ cursor: 'pointer' }}>
                                                <div className="text-muted font-monospace mb-1" style={{ fontSize: '0.65rem', textTransform: 'uppercase', fontWeight: '600' }}>
                                                    Success Rate
                                                </div>
                                                <div className="fw-bold text-success font-monospace" style={{ fontSize: '1.2rem', letterSpacing: '-0.02em' }}>
                                                    {tool.stats?.successRate || '—'}
                                                </div>
                                            </div>
                                        </OverlayTrigger>
                                    </Col>
                                    <Col xs={6}>
                                        <OverlayTrigger
                                            placement="top"
                                            overlay={
                                                <Tooltip id="avg-latency-tooltip">
                                                    Average round-trip response duration in milliseconds.
                                                </Tooltip>
                                            }
                                        >
                                            <div className="vmcp-stat-box text-start p-3 rounded" style={{ cursor: 'pointer' }}>
                                                <div className="text-muted font-monospace mb-1" style={{ fontSize: '0.65rem', textTransform: 'uppercase', fontWeight: '600' }}>
                                                    Avg Latency
                                                </div>
                                                <div className="fw-bold text-dark font-monospace" style={{ fontSize: '1.2rem', letterSpacing: '-0.02em' }}>
                                                    {tool.stats?.avgLatencyMs || '—'}
                                                </div>
                                            </div>
                                        </OverlayTrigger>
                                    </Col>
                                    <Col xs={6}>
                                        <OverlayTrigger
                                            placement="top"
                                            overlay={
                                                <Tooltip id="active-connections-tooltip">
                                                    0 Active means no client or developer agent has executed this MCP tool in the last 5 minutes.
                                                </Tooltip>
                                            }
                                        >
                                            <div className="vmcp-stat-box text-start p-3 rounded" style={{ cursor: 'pointer' }}>
                                                <div className="text-muted font-monospace mb-1" style={{ fontSize: '0.65rem', textTransform: 'uppercase', fontWeight: '600' }}>
                                                    Concurrent
                                                </div>
                                                <div className="fw-bold text-primary font-monospace" style={{ fontSize: '1.2rem', letterSpacing: '-0.02em' }}>
                                                    {tool.stats?.activeConnections || '0 Active'}
                                                </div>
                                            </div>
                                        </OverlayTrigger>
                                    </Col>
                                </Row>
                            </div>
                        </Col>
                    </Row>
                </Container>
            </div>

            {/* ── Body ── */}
            <Container fluid className="px-4 py-4">
                {tool.inputSchema && (
                    <div className="mb-4">
                        <McpToolSchema 
                            schema={tool.inputSchema}
                            title={`${tool.name} Input Schema Parameters`}
                        />
                    </div>
                )}

                <McpConsole 
                    teamId={null}
                    predefinedToolId={tool.toolId}
                    onExecute={() => load(true)}
                />
            </Container>
        </div>
    );
};

export default ViewMcpTool;