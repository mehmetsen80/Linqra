import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Badge, Spinner, Form, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { 
    FiSearch, FiCpu, FiActivity, FiGlobe, FiWifi, 
    FiArrowRight, FiZap, FiShield, FiCode 
} from 'react-icons/fi';
import toolService from '../../services/toolService';
import './styles.css';

const categoryConfig = {
    analytics: { label: 'Analytics', icon: <FiActivity />, color: '#10b981', description: 'Monitor performance, usage analytics, and execution telemetry' },
    security:  { label: 'Security',  icon: <FiShield />,   color: '#f59e0b', description: 'Manage permissions, API keys, and secure gateway credentials' },
    utility:   { label: 'Utility',   icon: <FiCpu />,      color: '#6366f1', description: 'General system helper utilities, file converters, and scripting tools' },
    legal:     { label: 'Legal',     icon: <FiCode />,     color: '#ec4899', description: 'Analyze contracts, privacy audits, and regulatory compliance tools' },
    default:   { label: 'General',   icon: <FiZap />,      color: '#38bdf8', description: 'General purpose operations, prompt assistance, and core integrations' },
};

const getCategoryConfig = (cat) =>
    categoryConfig[cat?.toLowerCase()] || categoryConfig.default;

const getCleanDescription = (desc) => {
    if (!desc) return 'No description provided.';
    // Strip HTML tags
    let clean = desc.replace(/<[^>]*>/g, '');
    // Strip common markdown elements
    clean = clean.replace(/[\#\*\_`\[\]\(\)]/g, '');
    // Truncate cleanly
    if (clean.length > 130) {
        return clean.substring(0, 130).trim() + '...';
    }
    return clean;
};

const McpMarketplace = () => {
    const navigate = useNavigate();
    const [tools, setTools] = useState([]);
    const [totalRuns, setTotalRuns] = useState(0);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [activeCategory, setActiveCategory] = useState('all');

    useEffect(() => {
        loadTools();
    }, []);

    const loadTools = async () => {
        setLoading(true);
        const [toolsRes, execsRes] = await Promise.all([
            toolService.getAllTools(),
            toolService.getToolExecutions()
        ]);
        if (toolsRes.success) {
            setTools(toolsRes.data);
        }
        if (execsRes.success && Array.isArray(execsRes.data)) {
            setTotalRuns(execsRes.data.length);
        }
        setLoading(false);
    };

    const categories = ['all', ...new Set(tools.map(t => t.category?.toLowerCase()).filter(Boolean))];

    const filtered = tools.filter(tool => {
        const matchSearch = !searchQuery ||
            tool.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            tool.toolId?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            tool.description?.replace(/<[^>]*>/g, '').toLowerCase().includes(searchQuery.toLowerCase());
        const matchCat = activeCategory === 'all' || tool.category?.toLowerCase() === activeCategory;
        return matchSearch && matchCat;
    });

    return (
        <div className="mcp-marketplace-page">
            {/* Hero Banner */}
            <div className="mcp-hero-banner">
                <div className="mcp-hero-glow" />
                <div className="mcp-hero-glow-right" />
                <Container fluid className="py-5 px-4 position-relative">
                    <Row className="align-items-center">
                        <Col lg={7}>
                            <div className="d-flex align-items-center gap-3 mb-3">
                                <div className="mcp-hero-icon">
                                    <FiGlobe size={26} />
                                </div>
                                <div>
                                    <div className="d-flex align-items-center gap-2 flex-wrap">
                                        <h1 className="mcp-hero-title mb-0">MCP Server Marketplace</h1>
                                        <Badge bg="" className="mcp-hero-tools-count">
                                            {tools.length || '…'} Tools Available
                                        </Badge>
                                    </div>
                                    <p className="mcp-hero-subtitle mb-0">
                                        Browse, connect, and execute tools via the Model Context Protocol
                                    </p>
                                </div>
                            </div>
                            
                            <div className="mcp-hero-info-callout">
                                <div className="mcp-info-header">
                                    <FiShield size={16} className="mcp-info-shield-icon" />
                                    <span className="mcp-info-title">Secure MCP Gateway</span>
                                    <span className="mcp-info-status-dot" />
                                    <span className="mcp-info-status-text">Active & Enforced</span>
                                </div>
                                <div className="mcp-info-body">
                                    Every tool is MCP-compatible and securely exposed through Linqra. 
                                    Connect your AI IDE directly or execute via the browser console.
                                </div>
                            </div>
                        </Col>
                        
                        <Col lg={5} className="mt-4 mt-lg-0">
                            <div className="d-flex gap-3 justify-content-lg-end justify-content-start flex-wrap">
                                {[
                                    { label: 'Total Executions', value: totalRuns || '0' },
                                    { label: 'Available Tools', value: tools.length || '…' },
                                    { label: 'Transport', value: 'SSE' },
                                ].map(s => (
                                    <div key={s.label} className="mcp-stat-card">
                                        <div className="mcp-stat-value">{s.value}</div>
                                        <div className="mcp-stat-label">{s.label}</div>
                                    </div>
                                ))}
                            </div>
                        </Col>
                    </Row>
                </Container>
            </div>

            <Container fluid className="px-4 pb-5">
                {/* Filter Bar */}
                <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 my-4">
                    <div className="d-flex gap-2 flex-wrap">
                        {categories.map(cat => {
                            const config = getCategoryConfig(cat);
                            const tooltipText = cat === 'all' 
                                ? 'Browse all available MCP tools in the marketplace' 
                                : config.description;
                            return (
                                <OverlayTrigger
                                    key={cat}
                                    placement="top"
                                    overlay={
                                        <Tooltip id={`tooltip-${cat}`}>
                                            {tooltipText}
                                        </Tooltip>
                                    }
                                >
                                    <button
                                        className={`mcp-filter-pill d-inline-flex align-items-center gap-2 ${activeCategory === cat ? 'active' : ''}`}
                                        onClick={() => setActiveCategory(cat)}
                                    >
                                        {cat === 'all' ? (
                                            <>
                                                <span style={{ fontSize: '0.9rem' }}>🌐</span> All Tools
                                            </>
                                        ) : (
                                            <>
                                                {React.cloneElement(config.icon, { size: 13 })}
                                                {config.label}
                                            </>
                                        )}
                                    </button>
                                </OverlayTrigger>
                            );
                        })}
                    </div>
                    <div className="mcp-search-wrapper">
                        <FiSearch className="mcp-search-icon" />
                        <Form.Control
                            type="text"
                            placeholder="Search tools, IDs, descriptions..."
                            className="mcp-search-input"
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                        />
                    </div>
                </div>

                {/* Tool Grid */}
                {loading ? (
                    <div className="d-flex justify-content-center py-5">
                        <Spinner animation="border" variant="primary" />
                    </div>
                ) : filtered.length === 0 ? (
                    <div className="mcp-empty-state">
                        <FiZap size={48} opacity={0.3} />
                        <p className="mt-3 text-muted">No MCP tools found matching your criteria.</p>
                    </div>
                ) : (
                    <Row>
                        {filtered.map(tool => {
                            const config = getCategoryConfig(tool.category);
                            return (
                                <Col key={tool.toolId} md={6} xl={4} className="mb-4">
                                    <Card
                                        className="mcp-tool-card h-100"
                                        onClick={() => navigate(`/mcp-marketplace/${tool.toolId}`)}
                                    >
                                        {/* Color accent bar */}
                                        <div className="mcp-card-accent" style={{ background: config.color }} />

                                        <Card.Body className="p-4">
                                            {/* Header row */}
                                            <div className="d-flex justify-content-between align-items-center mb-3">
                                                <div className="mcp-tool-icon-wrap" style={{ color: config.color, borderColor: `${config.color}33`, background: `${config.color}0f` }}>
                                                    {React.cloneElement(config.icon, { size: 22 })}
                                                </div>
                                                <div className="d-flex gap-2 align-items-center">
                                                    <Badge bg="" className="mcp-status-badge d-inline-flex align-items-center gap-1">
                                                        <FiWifi size={10} /> LIVE
                                                    </Badge>
                                                    {tool.visibility === 'PUBLIC' && (
                                                        <Badge bg="" className="mcp-public-badge d-inline-flex align-items-center">PUBLIC</Badge>
                                                    )}
                                                </div>
                                            </div>

                                            {/* Tool identity */}
                                            <div className="mb-3">
                                                <div className="mcp-tool-id font-monospace">{tool.toolId}</div>
                                                <h5 className="mcp-tool-name mt-1">{tool.name}</h5>
                                            </div>

                                            {/* Clean Description */}
                                            <div className="mcp-tool-desc">
                                                {getCleanDescription(tool.description)}
                                            </div>
                                        </Card.Body>

                                        {/* Footer */}
                                        <Card.Footer className="mcp-card-footer bg-transparent border-0 px-4 pb-4">
                                            <div className="d-flex justify-content-between align-items-center">
                                                <div className="mcp-category-tag d-inline-flex align-items-center gap-1" style={{ color: config.color }}>
                                                    {React.cloneElement(config.icon, { size: 12 })}
                                                    {config.label}
                                                </div>
                                                <span className="mcp-view-link d-inline-flex align-items-center gap-1">
                                                    View & Execute <FiArrowRight size={13} />
                                                </span>
                                            </div>
                                        </Card.Footer>
                                    </Card>
                                </Col>
                            );
                        })}
                    </Row>
                )}
            </Container>
        </div>
    );
};

export default McpMarketplace;
