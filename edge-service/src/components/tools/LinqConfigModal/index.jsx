import React, { useState, useEffect } from 'react';
import { Modal, Form, Tabs, Tab } from 'react-bootstrap';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { dracula } from "react-syntax-highlighter/dist/cjs/styles/prism";
import Button from '../../common/Button';
import { showErrorToast, showSuccessToast } from '../../../utils/toastConfig';
import toolService from '../../../services/toolService';
import './styles.css';

const LinqConfigModal = ({
    show,
    onHide,
    onSave,
    tool,
    saving
}) => {
    const [config, setConfig] = useState({
        linq_config: '',
        inputSchema: '',
        outputSchema: ''
    });

    const [validationErrors, setValidationErrors] = useState({
        linq_config: null
    });

    const [testResult, setTestResult] = useState(null);
    const [executing, setExecuting] = useState(false);
    const [activeTab, setActiveTab] = useState('protocol');

    useEffect(() => {
        if (show && tool) {
            console.log('LinqConfigModal logic running for show=true. Tool keys:', Object.keys(tool));
            const rawConfig = tool.linq_config || tool.linqConfig;
            console.log('Detected rawConfig:', rawConfig);
            
            setConfig({
                linq_config: rawConfig ? (typeof rawConfig === 'string' ? rawConfig : JSON.stringify(rawConfig, null, 2)) : '',
                inputSchema: tool.inputSchema ? (typeof tool.inputSchema === 'string' ? tool.inputSchema : JSON.stringify(tool.inputSchema, null, 2)) : '',
                outputSchema: tool.outputSchema ? (typeof tool.outputSchema === 'string' ? tool.outputSchema : JSON.stringify(tool.outputSchema, null, 2)) : ''
            });
            
            setValidationErrors({
                linq_config: null,
                inputSchema: null
            });
        }
    }, [show, tool]);

    // Independent effect for modal reset on open
    useEffect(() => {
        if (show) {
            setActiveTab('protocol');
            setTestResult(null);
        }
    }, [show]);

    const handleJsonChange = (field, value) => {
        setConfig(prev => ({ ...prev, [field]: value }));

        // Immediate validation
        try {
            if (value.trim()) {
                JSON.parse(value);
            }
            setValidationErrors(prev => ({ ...prev, [field]: null }));
        } catch (err) {
            setValidationErrors(prev => ({ ...prev, [field]: err.message }));
        }
    };

    const handleFormat = (field) => {
        try {
            const val = config[field];
            const parsed = JSON.parse(val);
            handleJsonChange(field, JSON.stringify(parsed, null, 2));
        } catch (err) {
            showErrorToast(`Cannot format ${field}: Invalid JSON`);
        }
    };

    const handleGenerateSchema = () => {
        try {
            const parsedConfig = JSON.parse(config.linq_config || '{}');
            const schema = {
                type: "object",
                title: parsedConfig.query?.summary || undefined,
                description: parsedConfig.query?.description || undefined,
                properties: {},
                required: []
            };

            // Priority 1: explicitly defined params
            if (parsedConfig.query && parsedConfig.query.params) {
                Object.keys(parsedConfig.query.params).forEach(key => {
                    schema.properties[key] = { type: "string", description: "Parameter description here" };
                    schema.required.push(key);
                });
            }

            // Priority 2: Path placeholders
            if (parsedConfig.query && parsedConfig.query.intent) {
                const intent = parsedConfig.query.intent;
                const matches = intent.match(/\{(\w+)\}/g);
                if (matches) {
                    matches.forEach(match => {
                        const key = match.replace(/[\{\}]/g, '');
                        if (!schema.properties[key]) {
                            schema.properties[key] = { type: "string", description: "Path parameter description here" };
                            schema.required.push(key);
                        }
                    });
                }
            }

            // Priority 3: Enums from protocol (query.enums)
            if (parsedConfig.query && parsedConfig.query.enums) {
                Object.entries(parsedConfig.query.enums).forEach(([key, values]) => {
                    // Detect type from enum values
                    let detectedType = "string";
                    if (Array.isArray(values) && values.length > 0) {
                        const allNumbers = values.every(v => typeof v === 'number');
                        const allBooleans = values.every(v => typeof v === 'boolean');

                        if (allNumbers) {
                            const allIntegers = values.every(v => Number.isInteger(v));
                            detectedType = allIntegers ? "integer" : "number";
                        } else if (allBooleans) {
                            detectedType = "boolean";
                        }
                    }

                    if (schema.properties[key]) {
                        schema.properties[key].type = detectedType;
                        schema.properties[key].enum = values;
                    } else {
                        // In case key exists in enums but not placeholders/params yet
                        schema.properties[key] = {
                            type: detectedType,
                            description: "Parameter with enum values",
                            enum: values
                        };
                        schema.required.push(key);
                    }
                });
            }

            // Priority 4: Metadata (summary/description) from protocol
            if (parsedConfig.query && parsedConfig.query.metadata) {
                Object.entries(parsedConfig.query.metadata).forEach(([key, meta]) => {
                    if (schema.properties[key]) {
                        if (typeof meta === 'string') {
                            schema.properties[key].description = meta;
                        } else {
                            if (meta.summary) schema.properties[key].title = meta.summary;
                            if (meta.description) schema.properties[key].description = meta.description;
                        }
                    }
                });
            }

            // Even if no parameters are found, we allow generating a valid empty schema
            if (Object.keys(schema.properties).length === 0) {
                showSuccessToast("No parameters found. Generated empty schema boilerplate.");
            } else {
                showSuccessToast("Schema generated! Please customize descriptions for the LLM.");
            }
            
            handleJsonChange('inputSchema', JSON.stringify(schema, null, 2));
        } catch (err) {
            showErrorToast("Cannot generate: Protocol must be valid JSON");
        }
    };

    const handleRunTest = async () => {
        setExecuting(true);
        setTestResult(null);
        try {
            let parsedParams = {};
            // Using empty object as default since UI parameters were removed

            let currentLinqConfig = {};
            try {
                currentLinqConfig = JSON.parse(config.linq_config || '{}');
            } catch (err) {
                showErrorToast('Invalid Protocol Configuration JSON');
                setExecuting(false);
                return;
            }

            const dryRunTool = {
                toolId: tool.toolId,
                linqConfig: currentLinqConfig,
                inputSchema: config.inputSchema,
                outputSchema: config.outputSchema
            };

            const result = await toolService.testToolConfig(tool.toolId, dryRunTool, parsedParams);

            if (result.success) {
                showSuccessToast('Test execution completed');
                setTestResult(result.data);
                setActiveTab('result'); // Switch to result tab automatically
            } else {
                showErrorToast(result.error || 'Test execution failed');
                setTestResult({ error: result.error });
                setActiveTab('result'); // Switch to results even on failure
            }
        } catch (err) {
            showErrorToast('Unexpected error during test');
            setTestResult({ error: err.message });
            setActiveTab('result');
        } finally {
            setExecuting(false);
        }
    };

    const handleSave = async () => {
        try {
            if (config.linq_config.trim()) {
                JSON.parse(config.linq_config);
            }
        } catch (err) {
            setValidationErrors({ linq_config: err.message });
            showErrorToast('Please fix JSON validation errors before saving');
            return;
        }

        const success = await onSave({
            linq_config: JSON.parse(config.linq_config || '{}'),
            inputSchema: config.inputSchema,
            outputSchema: config.outputSchema
        });

        if (success && activeTab === 'protocol') {
            setActiveTab('schema');
        }
    };

    return (
        <Modal show={show} onHide={onHide} size="xl" centered className="linq-config-modal">
            <Modal.Header closeButton>
                <Modal.Title>Protocol Configuration</Modal.Title>
            </Modal.Header>
            <Modal.Body className="p-0"> {/* Remove padding to let tabs fill */}
                <Tabs
                    id="linq-config-tabs"
                    activeKey={activeTab}
                    onSelect={(k) => setActiveTab(k)}
                    className="linq-config-tabs px-3 pt-2"
                >
                    <Tab eventKey="protocol" title="Protocol (linq_config)">
                        <div className="p-3 linq-tab-pane">
                            <Form.Group className="h-100 d-flex flex-column mb-0">
                                <div className="d-flex justify-content-between align-items-center mb-2">
                                    <Form.Label className="mb-0 text-muted small fw-bold text-uppercase opacity-75">
                                        Editor
                                    </Form.Label>
                                    <div className="d-flex align-items-center gap-3">
                                        {validationErrors.linq_config && (
                                            <span className="text-danger small fw-bold">Invalid JSON</span>
                                        )}
                                        <Button
                                            variant="link"
                                            size="sm"
                                            className="p-0 text-decoration-none format-json-btn"
                                            onClick={() => handleFormat('linq_config')}
                                        >
                                            Format JSON
                                        </Button>
                                    </div>
                                </div>
                                <Form.Control
                                    as="textarea"
                                    className={`flex-grow-1 font-monospace linq-config-textarea ${validationErrors.linq_config ? 'is-invalid' : ''}`}
                                    value={config.linq_config}
                                    onChange={(e) => handleJsonChange('linq_config', e.target.value)}
                                    placeholder='{\n  "link": { "target": "...", "action": "..." }\n}'
                                />
                                {validationErrors.linq_config && (
                                    <Form.Control.Feedback type="invalid" className="mt-2">
                                        {validationErrors.linq_config}
                                    </Form.Control.Feedback>
                                )}
                            </Form.Group>
                        </div>
                    </Tab>
                    <Tab eventKey="schema" title="Input Schema (LLM Spec)">
                        <div className="p-3 linq-tab-pane">
                            <Form.Group className="h-100 d-flex flex-column mb-0">
                                <div className="d-flex justify-content-between align-items-center mb-2">
                                    <div className="d-flex align-items-center gap-2">
                                        <Form.Label className="mb-0 text-muted small fw-bold text-uppercase opacity-75">
                                            JSON Schema
                                        </Form.Label>
                                    </div>
                                    <div className="d-flex align-items-center gap-3">
                                        {validationErrors.inputSchema && (
                                            <span className="text-danger small fw-bold">Invalid JSON</span>
                                        )}
                                        <Button
                                            variant="outline-primary"
                                            size="sm"
                                            className="fw-bold py-1 px-2"
                                            onClick={handleGenerateSchema}
                                        >
                                            <i className="bi bi-magic me-1"></i> Auto-Generate from Protocol
                                        </Button>
                                        <Button
                                            variant="link"
                                            size="sm"
                                            className="p-0 text-decoration-none format-json-btn"
                                            onClick={() => handleFormat('inputSchema')}
                                        >
                                            Format JSON
                                        </Button>
                                    </div>
                                </div>
                                <Form.Control
                                    as="textarea"
                                    className={`flex-grow-1 font-monospace linq-config-textarea ${validationErrors.inputSchema ? 'is-invalid' : ''}`}
                                    value={config.inputSchema}
                                    onChange={(e) => handleJsonChange('inputSchema', e.target.value)}
                                    placeholder='{\n  "type": "object",\n  "properties": { ... }\n}'
                                />
                                {validationErrors.inputSchema && (
                                    <Form.Control.Feedback type="invalid" className="mt-2">
                                        {validationErrors.inputSchema}
                                    </Form.Control.Feedback>
                                )}
                            </Form.Group>
                        </div>
                    </Tab>
                    <Tab eventKey="result" title="Execution Result">
                        <div className="p-3 linq-tab-pane">
                            {testResult ? (
                                <div className="h-100 d-flex flex-column animate-in">
                                    <Form.Label className="small fw-bold text-muted opacity-75">Output</Form.Label>
                                    <div className="test-result-display flex-grow-1">
                                        <div className="h-100 overflow-auto">
                                            <SyntaxHighlighter
                                                language="json"
                                                style={dracula}
                                                customStyle={{ margin: 0, padding: '1.25rem', fontSize: '0.85rem', background: 'transparent' }}
                                            >
                                                {JSON.stringify(testResult, null, 2)}
                                            </SyntaxHighlighter>
                                        </div>
                                    </div>
                                </div>
                            ) : (
                                <div className="h-100 d-flex align-items-center justify-content-center text-muted opacity-50">
                                    <div className="text-center">
                                        <i className="bi bi-play-circle fs-1 mb-2 d-block"></i>
                                        <p>Run <strong>Execute Test</strong> to see results here</p>
                                    </div>
                                </div>
                            )}
                        </div>
                    </Tab>
                </Tabs>
            </Modal.Body>
            <Modal.Footer className="d-flex justify-content-between">
                <div className="d-flex gap-2">
                    <Button variant="secondary" onClick={onHide} disabled={saving || executing}>
                        Cancel
                    </Button>
                    <Button
                        variant="outline-primary"
                        className="px-4"
                        onClick={handleRunTest}
                        loading={executing}
                        disabled={validationErrors.linq_config !== null || saving}
                    >
                        Execute Test
                    </Button>
                </div>
                <div>
                    <Button
                        variant="primary"
                        onClick={handleSave}
                        loading={saving}
                        disabled={executing}
                    >
                        Save Configuration
                    </Button>
                </div>
            </Modal.Footer>
        </Modal>
    );
};

export default LinqConfigModal;
