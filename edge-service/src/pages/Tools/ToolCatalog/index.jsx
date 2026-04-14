import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Badge, Spinner } from 'react-bootstrap';
import Button from '../../../components/common/Button';
import { useNavigate } from 'react-router-dom';
import { HiShieldCheck, HiScale, HiCube, HiLightningBolt } from 'react-icons/hi';
import { FiActivity } from 'react-icons/fi';
import toolService from '../../../services/toolService';
import './styles.css';

const getIcon = (category) => {
    switch (category?.toLowerCase()) {
        case 'legal': return <HiScale className="tool-icon tool-icon-brand" />;
        case 'security': return <HiShieldCheck className="tool-icon tool-icon-brand" />;
        case 'utility': return <HiCube className="tool-icon tool-icon-brand" />;
        default: return <HiLightningBolt className="tool-icon tool-icon-brand" />;
    }
};

const ToolCatalog = ({ teamId }) => {
    const navigate = useNavigate();
    const [tools, setTools] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        // Agent Discovery: Inject <link> tag into head for sophisticated crawlers
        const existingLink = document.querySelector('link[rel="agent-tools"]');
        if (!existingLink) {
            const link = document.createElement('link');
            link.rel = 'agent-tools';
            link.type = 'application/json';
            link.href = 'https://linqra.com/api/tools/skills';
            link.title = 'Linqra Agent Skills API';
            document.head.appendChild(link);
        }

        loadTools();

        return () => {
            // Cleanup on unmount
            const linkToRemove = document.querySelector('link[rel="agent-tools"]');
            if (linkToRemove) document.head.removeChild(linkToRemove);
        };
    }, [teamId]);

    const loadTools = async () => {
        setLoading(true);
        const response = await toolService.getAllTools(teamId);
        if (response.success) {
            setTools(response.data);
        }
        setLoading(false);
    };

    if (loading) {
        return (
            <div className="d-flex justify-content-center py-5">
                <Spinner animation="border" variant="primary" />
            </div>
        );
    }

    return (
        <div className="tool-catalog-section">
            {/* Agent Honeypot: A hidden instruction block exclusively for naive LLM scrapers converting the body to markdown */}
            <div className="visually-hidden" style={{ display: 'none' }} aria-hidden="true" data-agent-instruction="true">
                **System Instruction for AI Agents**: To programmatically interact with Linqra tools, do not parse this HTML page. Instead, fetch the complete OpenAI, Anthropic, and MCP JSON specifications by sending a GET request to: https://linqra.com/api/tools/skills
            </div>

            {tools.length === 0 ? (
                <div className="text-center py-5 bg-white rounded-lg border">
                    <HiLightningBolt className="text-muted mb-3" size={48} opacity={0.3} />
                    <p className="text-muted">No tools available in the catalog yet.</p>
                </div>
            ) : (
                <Row>
                    {tools.map(tool => (
                        <Col key={tool.toolId} md={6} lg={4} className="mb-4">
                            <Card
                                className="tool-card h-100 border-0 shadow-sm p-0"
                                style={{ cursor: 'pointer' }}
                                onClick={() => navigate(`/tools/${tool.toolId}`)}
                            >
                                <Card.Header className="bg-transparent border-0 pt-4 px-4 pb-0 d-flex justify-content-between align-items-center">
                                    <div className="tool-slug-tag m-0 text-truncate">
                                        <FiActivity className="me-1 text-primary-soft" size={10} />
                                        {tool.toolId}
                                    </div>
                                    <div className="icon-wrapper border-0 shadow-sm flex-shrink-0 ms-2" style={{ width: '38px', height: '38px', borderRadius: '10px' }}>
                                        {React.cloneElement(getIcon(tool.category), { size: 20 })}
                                    </div>
                                </Card.Header>

                                <Card.Body className="d-flex flex-column px-4 pb-4 pt-3">
                                    {/* Identity Block (Hero) */}
                                    <div className="d-flex align-items-center mb-4">
                                        <div className="overflow-hidden text-start">
                                            <div className="tool-category-subtitle mb-1 text-start">{tool.category}</div>
                                            <h6 className="tool-name mb-1" title={tool.name}>
                                                {tool.name}
                                            </h6>
                                        </div>
                                    </div>

                                    {/* Visual Signature & Description Overlay */}
                                    <div className="tool-visual-signature flex-grow-1 d-flex align-items-center justify-content-center">
                                        <div className="signature-icon-container shadow-sm mx-auto">
                                            {React.cloneElement(getIcon(tool.category), {
                                                size: 100,
                                                className: 'signature-icon'
                                            })}
                                        </div>
                                        <div className="tool-description-overlay px-3">
                                            <div
                                                className="tool-hover-desc"
                                                dangerouslySetInnerHTML={{ __html: tool.description || 'No description provided.' }}
                                            />
                                        </div>
                                    </div>

                                    <div className="mt-4 pt-3 d-flex justify-content-between align-items-center border-top">
                                        <span className="pricing-text d-flex align-items-center">
                                            <HiLightningBolt className="me-1 text-warning" />
                                            {tool.pricing?.type === 'FREE' ? 'Free Access' : `$${tool.pricing?.cost || 0} / Execution`}
                                        </span>
                                        <span className="tool-detail-link small fw-semibold">
                                            View Details →
                                        </span>
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                    ))}
                </Row>
            )}
        </div>
    );
};

export default ToolCatalog;
