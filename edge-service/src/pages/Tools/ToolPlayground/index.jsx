import React, { useState, useEffect } from 'react';
import { Form, Card, Row, Col, Spinner, Badge } from 'react-bootstrap';
import Button from '../../../components/common/Button';
import { HiPlay, HiCheckCircle, HiExclamationCircle } from 'react-icons/hi';
import toolService from '../../../services/toolService';
import './styles.css';

const ToolPlayground = ({ initialTool, initialParams }) => {
    const [tools, setTools] = useState([]);
    const [selectedToolId, setSelectedToolId] = useState(initialTool?.toolId || '');
    const [selectedTool, setSelectedTool] = useState(initialTool || null);
    const [viewMode, setViewMode] = useState('form'); // 'form' or 'json'
    const [formValues, setFormValues] = useState({});
    const [params, setParams] = useState('{}');
    const [result, setResult] = useState(null);
    const [executing, setExecuting] = useState(false);
    const [loadingTools, setLoadingTools] = useState(true);
    const [error, setError] = useState(null);
    const [forceRefresh, setForceRefresh] = useState(false);

    useEffect(() => {
        loadTools();
    }, []);

    const loadTools = async () => {
        setLoadingTools(true);
        const response = await toolService.getAllTools();
        if (response.success) {
            setTools(response.data);
            if (response.data.length > 0 && !selectedToolId) {
                const firstTool = response.data[0];
                setSelectedToolId(firstTool.toolId);
                setSelectedTool(firstTool);
            } else if (selectedToolId) {
                const current = response.data.find(t => t.toolId === selectedToolId);
                if (current) setSelectedTool(current);
            }
        }
        setLoadingTools(false);
    };

    useEffect(() => {
        if (selectedTool) {
            const schema = parseSchema(selectedTool.inputSchema);
            const initialValues = {};
            if (schema?.properties) {
                Object.keys(schema.properties).forEach(key => {
                    const prop = schema.properties[key];
                    initialValues[key] = prop.default !== undefined ? prop.default : (prop.enum ? prop.enum[0] : '');
                });
            }

            if (initialParams) {
                try {
                    const p = typeof initialParams === 'string' ? JSON.parse(initialParams) : initialParams;
                    setParams(JSON.stringify(p, null, 2));
                    setFormValues(p);
                    setViewMode('json');
                } catch (e) {
                    console.error("Failed to parse initialParams:", e);
                    setFormValues(initialValues);
                    setParams(JSON.stringify(initialValues, null, 2));
                }
            } else {
                setFormValues(initialValues);
                setParams(JSON.stringify(initialValues, null, 2));
                setViewMode(schema?.properties ? 'form' : 'json');
            }
        }
    }, [selectedToolId, tools, initialParams]);

    const parseSchema = (schema) => {
        if (!schema) return null;
        if (typeof schema === 'object') return schema;
        try {
            return JSON.parse(schema);
        } catch (e) {
            console.error("Failed to parse schema:", e);
            return null;
        }
    };

    const handleFormChange = (key, value) => {
        const newValues = { ...formValues, [key]: value };
        setFormValues(newValues);
        setParams(JSON.stringify(newValues, null, 2));
    };

    const handleToolSelect = (toolId) => {
        setSelectedToolId(toolId);
        const tool = tools.find(t => t.toolId === toolId);
        setSelectedTool(tool);
    };

    const handleExecute = async () => {
        if (!selectedToolId) return;
        
        setExecuting(true);
        setError(null);
        setResult(null);

        try {
            const parsedParams = JSON.parse(params);
            const response = await toolService.executeTool(selectedToolId, parsedParams, forceRefresh);
            if (response.success) {
                setResult(response.data);
            } else {
                setError(response.error);
            }
        } catch (e) {
            setError('Invalid JSON parameters: ' + e.message);
        }
        setExecuting(false);
    };

    const renderSafeResult = (res) => {
        if (!res) return null;
        const jsonStr = JSON.stringify(res, null, 2);
        const isLarge = jsonStr.length > 100000;
        const sizeInKb = (jsonStr.length / 1024).toFixed(1);
        const sizeDisplay = jsonStr.length > 1024 * 1024 
          ? `${(jsonStr.length / (1024 * 1024)).toFixed(2)} MB` 
          : `${sizeInKb} KB`;

        let contentNode;
        if (isLarge) {
            const previewText = jsonStr.substring(0, 5000) + '\n\n... [TRUNCATED - Output is too large to render dynamically (' + sizeDisplay + ')]';
            contentNode = (
                <pre className="text-success m-0 font-monospace" style={{ fontSize: '0.85rem', maxHeight: '350px', overflow: 'auto', whiteSpace: 'pre-wrap', color: '#10b981' }}>
                    {previewText}
                </pre>
            );
        } else {
            contentNode = (
                <pre className="m-0 text-success" style={{ fontSize: '0.85rem', maxHeight: '400px', overflow: 'auto', whiteSpace: 'pre' }}>
                    {jsonStr}
                </pre>
            );
        }

        return (
            <div className={`payload-container rounded border ${isLarge ? 'border-warning' : 'border-secondary'}`} style={{ background: '#0b0f19' }}>
                <div className="d-flex justify-content-between align-items-center p-2 px-3 border-bottom border-secondary bg-dark bg-opacity-50">
                    <span className={`fw-bold small d-flex align-items-center gap-1 ${isLarge ? 'text-warning' : 'text-muted'}`}>
                        {isLarge ? '⚠️ Large Output Bypassed' : '✨ Output Payload'} ({sizeDisplay})
                    </span>
                    <div className="d-flex gap-2">
                        <button 
                            className="btn btn-outline-secondary btn-sm py-1 font-monospace"
                            style={{ fontSize: '0.72rem' }}
                            onClick={(e) => {
                                navigator.clipboard.writeText(jsonStr);
                                const origText = e.target.innerHTML;
                                e.target.innerHTML = "Copied! ✓";
                                setTimeout(() => { e.target.innerHTML = origText; }, 1500);
                            }}
                        >
                            📋 Copy
                        </button>
                        <button 
                            className={`btn btn-sm py-1 font-monospace text-decoration-none ${isLarge ? 'btn-warning' : 'btn-outline-info'}`}
                            style={{ fontSize: '0.72rem' }}
                            onClick={() => {
                                const blob = new Blob([jsonStr], { type: 'application/json' });
                                const url = URL.createObjectURL(blob);
                                const a = document.createElement('a');
                                a.href = url;
                                a.download = 'tool_execution_result.json';
                                a.click();
                                URL.revokeObjectURL(url);
                            }}
                        >
                            💾 Download JSON
                        </button>
                    </div>
                </div>
                <div className="p-3 text-start">
                    {contentNode}
                </div>
            </div>
        );
    };


    return (
        <div className="tool-playground-section pt-3">
            <Row>
                <Col lg={5}>
                    <Card className="border-0 shadow-sm mb-4">
                        <Card.Header className="bg-white border-bottom py-3 d-flex justify-content-between align-items-center">
                            <h6 className="mb-0 fw-bold">Execution Console</h6>
                            {selectedTool?.inputSchema && (
                                <div className="btn-group btn-group-sm">
                                    <button 
                                        className={`btn ${viewMode === 'form' ? 'btn-primary' : 'btn-outline-primary'}`}
                                        onClick={() => setViewMode('form')}
                                    >
                                        Form
                                    </button>
                                    <button 
                                        className={`btn ${viewMode === 'json' ? 'btn-primary' : 'btn-outline-primary'}`}
                                        onClick={() => setViewMode('json')}
                                    >
                                        JSON
                                    </button>
                                </div>
                            )}
                        </Card.Header>
                        <Card.Body>
                            <Form.Group className="mb-3">
                                <Form.Label className="small fw-semibold">Select Tool</Form.Label>
                                <Form.Select 
                                    value={selectedToolId} 
                                    onChange={(e) => handleToolSelect(e.target.value)}
                                    disabled={loadingTools}
                                >
                                    {loadingTools ? <option>Loading tools...</option> : null}
                                    {tools.length === 0 && !loadingTools ? <option>No tools registered</option> : null}
                                    {tools.map(tool => (
                                        <option key={tool.toolId} value={tool.toolId}>{tool.name}</option>
                                    ))}
                                </Form.Select>
                            </Form.Group>

                            {viewMode === 'form' && selectedTool?.inputSchema ? (
                                <div className="dynamic-form-fields border rounded p-3 bg-light mb-3">
                                    {Object.entries(parseSchema(selectedTool.inputSchema)?.properties || {}).map(([key, prop]) => (
                                        <Form.Group key={key} className="mb-3">
                                            <Form.Label className="small fw-bold text-muted text-uppercase" style={{ fontSize: '0.65rem' }}>
                                                {prop.title || key}
                                                {prop.required ? <span className="text-danger">*</span> : null}
                                            </Form.Label>
                                            
                                            {prop.enum ? (
                                                <Form.Select 
                                                    size="sm"
                                                    value={formValues[key] || ''}
                                                    onChange={(e) => handleFormChange(key, e.target.value)}
                                                >
                                                    {prop.enum.map(opt => (
                                                        <option key={opt} value={opt}>{opt}</option>
                                                    ))}
                                                </Form.Select>
                                            ) : prop.type === 'boolean' ? (
                                                <Form.Check 
                                                    type="switch"
                                                    id={`switch-${key}`}
                                                    checked={!!formValues[key]}
                                                    onChange={(e) => handleFormChange(key, e.target.checked)}
                                                />
                                            ) : (
                                                <Form.Control 
                                                    size="sm"
                                                    type={prop.type === 'number' || prop.type === 'integer' ? 'number' : 'text'}
                                                    placeholder={prop.description || `Enter ${key}`}
                                                    value={formValues[key] || ''}
                                                    onChange={(e) => handleFormChange(key, e.target.value)}
                                                />
                                            )}
                                            {prop.description && <Form.Text className="text-muted tiny">{prop.description}</Form.Text>}
                                        </Form.Group>
                                    ))}
                                </div>
                            ) : (
                                <Form.Group className="mb-3">
                                    <Form.Label className="small fw-semibold">Parameters (JSON)</Form.Label>
                                    <Form.Control 
                                        as="textarea" 
                                        rows={10} 
                                        className="font-monospace small bg-light"
                                        value={params}
                                        onChange={(e) => setParams(e.target.value)}
                                    />
                                </Form.Group>
                            )}

                            <div className="d-flex align-items-center gap-3 mt-4 pt-2 border-top">
                                <div className="border rounded-2 px-2 py-1 bg-white shadow-sm d-flex align-items-center" style={{ cursor: 'pointer' }}>
                                    <Form.Check 
                                        type="checkbox"
                                        id="playground-force-refresh"
                                        label={<span className="ms-1 fw-bold text-dark" style={{ fontSize: '0.85rem' }}>Force Refresh</span>}
                                        className="mb-0 d-flex align-items-center"
                                        checked={forceRefresh}
                                        onChange={(e) => setForceRefresh(e.target.checked)}
                                        title="Bypass gateway cache and fetch latest definition from DB"
                                    />
                                </div>
                                <Button 
                                    variant="primary" 
                                    className="py-3 fw-bold d-flex align-items-center justify-content-center flex-grow-1 shadow-sm me-3"
                                    onClick={handleExecute}
                                    loading={executing}
                                    disabled={!selectedToolId}
                                >
                                    <HiPlay className="me-2" size={20} /> Execute Tool
                                </Button>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>

                <Col lg={7}>
                    <Card className="border-0 shadow-sm tool-result-card h-100">
                        <Card.Header className="bg-white border-bottom py-3 d-flex justify-content-between align-items-center">
                            <h6 className="mb-0 fw-bold">Output</h6>
                            {result && <Badge bg="success"><HiCheckCircle className="me-1" /> Success</Badge>}
                            {error && <Badge bg="danger"><HiExclamationCircle className="me-1" /> Error</Badge>}
                        </Card.Header>
                        <Card.Body className="bg-dark p-0 overflow-hidden position-relative terminal-body">
                            <div className="terminal-header py-2 px-3 border-bottom border-secondary d-flex gap-1">
                                <div className="dot dot-red"></div>
                                <div className="dot dot-yellow"></div>
                                <div className="dot dot-green"></div>
                            </div>
                            <div className="p-3 terminal-content font-monospace text-light small overflow-auto" style={{ height: '400px' }}>
                                {error && (
                                    <div className="text-danger h6">
                                        <HiExclamationCircle className="me-2" />
                                        Error: {error}
                                    </div>
                                )}
                                {result ? (
                                    renderSafeResult(result)
                                ) : !error && !executing ? (
                                    <div className="text-muted italic">Waiting for tool execution...</div>
                                ) : executing ? (
                                    <div className="placeholder-glow">
                                        <div className="placeholder w-75 bg-secondary mb-2 opacity-25"></div>
                                        <div className="placeholder w-50 bg-secondary mb-2 opacity-25"></div>
                                        <div className="placeholder w-100 bg-secondary mb-2 opacity-25"></div>
                                    </div>
                                ) : null}
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default ToolPlayground;
