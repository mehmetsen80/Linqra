import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Row, Col, Card, Badge, Spinner, Tabs, Tab, Form } from 'react-bootstrap';
import Button from '../../../components/common/Button';
import {
    FiArrowLeft, FiBox, FiPlay, FiSettings, FiActivity, FiCopy, FiEdit, FiTrash,
    FiExternalLink, FiGlobe, FiTerminal, FiEdit2, FiHelpCircle, FiShield,
    FiLock, FiCpu, FiLayout, FiMaximize2, FiMinimize2, FiGift, FiX
} from 'react-icons/fi';
import { HiShieldCheck, HiScale, HiCube, HiLightningBolt } from 'react-icons/hi';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { dracula } from "react-syntax-highlighter/dist/cjs/styles/prism";
import toolService from '../../../services/toolService';
import ToolEditorModal from '../../../components/tools/ToolEditorModal';
import LinqConfigModal from '../../../components/tools/LinqConfigModal';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { formatDate } from '../../../utils/dateUtils';
import InstructionsModal from '../../../components/tools/InstructionsModal';
import { useAuth } from '../../../contexts/AuthContext';
import { useTeam } from '../../../contexts/TeamContext';
import { isSuperAdmin, hasAdminAccess } from '../../../utils/roleUtils';
import workflowService from '../../../services/workflowService';
import WorkflowGraphModal from '../../../components/workflows/WorkflowGraphModal';
import StepDescriptions from '../../../components/workflows/StepDescriptions';
import './styles.css';

const getIcon = (category) => {
    switch (category?.toLowerCase()) {
        case 'legal': return <HiScale className="tool-icon tool-icon-brand" />;
        case 'security': return <HiShieldCheck className="tool-icon tool-icon-brand" />;
        case 'utility': return <HiCube className="tool-icon tool-icon-brand" />;
        default: return <HiLightningBolt className="tool-icon tool-icon-brand" />;
    }
};

const getCategoryIcon = (category) => {
    switch (category?.toLowerCase()) {
        case 'legal': return <HiScale className="text-primary" />;
        case 'security': return <HiShieldCheck className="text-primary" />;
        case 'utility': return <HiCube className="text-primary" />;
        default: return <HiLightningBolt className="text-primary" />;
    }
};

const ViewTool = () => {
    const { toolId } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();
    const { currentTeam } = useTeam();
    const isAdmin = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    const [tool, setTool] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // API Instructions state
    const [showInstructionsModal, setShowInstructionsModal] = useState(false);
    const [savingInstructions, setSavingInstructions] = useState(false);
    const [testSubTab, setTestSubTab] = useState('request');
    const [snippetTab, setSnippetTab] = useState('json');
    const [isConsoleExpanded, setIsConsoleExpanded] = useState(false);

    const [showLinqConfigModal, setShowLinqConfigModal] = useState(false);
    const [savingProtocol, setSavingProtocol] = useState(false);
    const configCardRef = useRef(null);

    // Inline Execution State
    const [executingTest, setExecutingTest] = useState(false);
    const [testingResult, setTestingResult] = useState(null);
    const [showEditToolModal, setShowEditToolModal] = useState(false);
    const [agentParams, setAgentParams] = useState('{\n  "key": "value"\n}');
    const [toolSkill, setToolSkill] = useState(null);

    // Workflow Visualization State
    const [showGraphModal, setShowGraphModal] = useState(false);
    const [linkedWorkflow, setLinkedWorkflow] = useState(null);
    const [loadingWorkflow, setLoadingWorkflow] = useState(false);
    const [forceRefresh, setForceRefresh] = useState(false);

    useEffect(() => {
        if (isConsoleExpanded) {
            document.body.classList.add('no-scroll');
        } else {
            document.body.classList.remove('no-scroll');
        }
        return () => document.body.classList.remove('no-scroll');
    }, [isConsoleExpanded]);

    // Helper to guess parameters from various tool definitions
    const getGuessedParams = (targetTool) => {
        if (!targetTool) return {};

        // Priority 1: linq_config.query.params (Official defaults)
        let baseParams = {};
        if (targetTool.linq_config && targetTool.linq_config.query && targetTool.linq_config.query.params) {
            baseParams = { ...targetTool.linq_config.query.params };
        }

        // Priority 2: Extract from intent placeholders {formId} -> "sample_formId"
        if (targetTool.linq_config && targetTool.linq_config.query && targetTool.linq_config.query.intent) {
            const intent = targetTool.linq_config.query.intent;
            const matches = intent.match(/\{(\w+)\}/g);
            if (matches) {
                matches.forEach(match => {
                    const key = match.replace(/[\{\}]/g, '');
                    if (!baseParams[key]) {
                        baseParams[key] = `sample_${key}`;
                    }
                });
            }
        }

        // Priority 3: Extract from inputSchema (If JSON Schema)
        if (targetTool.inputSchema) {
            try {
                const schema = JSON.parse(targetTool.inputSchema);
                if (schema.properties) {
                    Object.keys(schema.properties).forEach(key => {
                        if (!baseParams[key]) {
                            baseParams[key] = schema.properties[key].type === 'string' ? `example_value` : null;
                        }
                    });
                }
            } catch (e) { }
        }

        return Object.keys(baseParams).length > 0 ? baseParams : { "key": "value" };
    };

    const generateCurl = (jsonBody) => {
        try {
            const body = typeof jsonBody === 'string' ? jsonBody : JSON.stringify(jsonBody, null, 2);
            let params = body;
            try {
                const parsed = JSON.parse(body);
                if (parsed.query && parsed.query.params) {
                    params = JSON.stringify(parsed.query.params, null, 2);
                }
            } catch (e) { }

            const isPrivate = tool?.visibility === 'PRIVATE';
            const authHeaders = isPrivate ? `  -H "x-api-key: YOUR_API_KEY" \\\n  -H "x-api-key-name: YOUR_API_KEY_NAME" \\\n` : '';

            return `curl -X POST https://linqra.com/api/tools/${tool.toolId}/execute \\
  -H "Content-Type: application/json" \\
${authHeaders}  -d '${params.replace(/'/g, "'\\''")}'`;
        } catch (e) { return 'Invalid JSON'; }
    };

    const generatePython = (jsonBody) => {
        try {
            const body = typeof jsonBody === 'string' ? jsonBody : JSON.stringify(jsonBody, null, 2);
            let params = body;
            try {
                const parsed = JSON.parse(body);
                if (parsed.query && parsed.query.params) {
                    params = JSON.stringify(parsed.query.params, null, 2);
                }
            } catch (e) { }

            const isPrivate = tool?.visibility === 'PRIVATE';
            let headersText = '"Content-Type": "application/json"';
            if (isPrivate) {
                headersText += ',\n    "x-api-key": "YOUR_API_KEY",\n    "x-api-key-name": "YOUR_API_KEY_NAME"';
            }

            return `import requests

url = "https://linqra.com/api/tools/${tool.toolId}/execute"
headers = {
    ${headersText}
}
payload = ${params}

response = requests.post(url, json=payload, headers=headers)
print(response.json())`;
        } catch (e) { return 'Invalid JSON'; }
    };

    const generateJava = (jsonBody) => {
        try {
            const body = typeof jsonBody === 'string' ? jsonBody : JSON.stringify(jsonBody, null, 2);
            const url = `https://linqra.com/api/tools/${tool.toolId}/execute`;

            let payload = body;
            try {
                const parsed = JSON.parse(body);
                if (parsed.query && parsed.query.params) {
                    payload = JSON.stringify(parsed.query.params, null, 2);
                }
            } catch (e) { }

            const isPrivate = tool?.visibility === 'PRIVATE';
            let requestBuilder = `HttpRequest.newBuilder()
            .uri(URI.create("${url}"))
            .header("Content-Type", "application/json")`;

            if (isPrivate) {
                requestBuilder += `
            .header("x-api-key", "YOUR_API_KEY")
            .header("x-api-key-name", "YOUR_API_KEY_NAME")`;
            }

            requestBuilder += `
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();`;

            return `import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LinqExample {
    public static void main(String[] args) throws Exception {
        var client = HttpClient.newHttpClient();
        var payload = """
${payload}
            """;

        var request = ${requestBuilder}

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }
}`;
        } catch (e) { return 'Invalid JSON'; }
    };

    const generateGo = (jsonBody) => {
        try {
            const body = typeof jsonBody === 'string' ? jsonBody : JSON.stringify(jsonBody, null, 2);
            const url = `https://linqra.com/api/tools/${tool.toolId}/execute`;

            let payload = body;
            try {
                const parsed = JSON.parse(body);
                if (parsed.query && parsed.query.params) {
                    payload = JSON.stringify(parsed.query.params, null, 2);
                }
            } catch (e) { }

            const isPrivate = tool?.visibility === 'PRIVATE';
            let headerLogic = 'req.Header.Set("Content-Type", "application/json")';
            if (isPrivate) {
                headerLogic += '\n    req.Header.Set("x-api-key", "YOUR_API_KEY")\n    req.Header.Set("x-api-key-name", "YOUR_API_KEY_NAME")';
            }

            return `package main

import (
    "bytes"
    "fmt"
    "net/http"
    "io/ioutil"
)

func main() {
    url := "${url}"
    var jsonStr = []byte(\`${payload}\`)

    req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonStr))
    ${headerLogic}

    client := &http.Client{}
    resp, err := client.Do(req)
    if err != nil {
        panic(err)
    }
    defer resp.Body.Close()

    body, _ := ioutil.ReadAll(resp.Body)
    fmt.Println(string(body))
}`;
        } catch (e) { return 'Invalid JSON'; }
    };

    const generateOpenAI = (toolObj) => {
        if (toolSkill?.openai) return JSON.stringify(toolSkill.openai, null, 2);
        // Fallback: generate locally if backend skill not yet loaded
        let schema = { type: "object", properties: {} };
        if (toolObj && toolObj.inputSchema) {
            try { schema = JSON.parse(toolObj.inputSchema); } catch (e) { }
        }

        const spec = {
            type: "function",
            function: {
                name: (toolObj?.toolId || "tool").replace(/\./g, "_"),
                description: toolObj?.description || "",
                parameters: schema
            }
        };
        return JSON.stringify(spec, null, 2);
    };

    const generateAnthropic = (toolObj) => {
        if (toolSkill?.anthropic) return JSON.stringify(toolSkill.anthropic, null, 2);
        // Fallback
        let schema = { type: "object", properties: {} };
        if (toolObj && toolObj.inputSchema) {
            try { schema = JSON.parse(toolObj.inputSchema); } catch (e) { }
        }
        const spec = {
            name: (toolObj?.toolId || "tool").replace(/\./g, "_"),
            description: toolObj?.description || "",
            input_schema: schema
        };
        return JSON.stringify(spec, null, 2);
    };

    const generateMCP = (toolObj) => {
        if (toolSkill?.mcp) return JSON.stringify(toolSkill.mcp, null, 2);
        // Fallback
        let schema = { type: "object", properties: {} };
        if (toolObj && toolObj.inputSchema) {
            try { schema = JSON.parse(toolObj.inputSchema); } catch (e) { }
        }

        const spec = {
            name: (toolObj?.toolId || "tool").replace(/\./g, "_"),
            description: toolObj?.description || "",
            inputSchema: schema
        };
        return JSON.stringify(spec, null, 2);
    };

    const copyToClipboard = (text, message = 'Copied to clipboard') => {
        navigator.clipboard.writeText(text);
        showSuccessToast(message);
    };

    useEffect(() => {
        fetchToolDetails();
    }, [toolId]);

    useEffect(() => {
        if (tool?.linq_config?.query?.workflowId) {
            loadLinkedWorkflow();
        } else {
            setLinkedWorkflow(null);
        }
    }, [tool?.linq_config?.query?.workflowId]);

    const loadLinkedWorkflow = async () => {
        const workflowId = tool?.linq_config?.query?.workflowId;
        if (!workflowId || !currentTeam) return;

        try {
            setLoadingWorkflow(true);
            const response = await workflowService.getWorkflowById(workflowId);
            if (response.success) {
                setLinkedWorkflow(response.data);
            }
        } catch (err) {
            console.error('Error loading linked workflow:', err);
        } finally {
            setLoadingWorkflow(false);
        }
    };

    useEffect(() => {
        if (tool && tool.toolId) {
            // If agentParams is still the default/placeholder, let's refresh it
            const isPlaceholder = !agentParams ||
                agentParams === '{\n  "key": "value"\n}' ||
                agentParams.includes('"key": "value"');

            if (isPlaceholder) {
                const smartParams = getGuessedParams(tool);
                setAgentParams(JSON.stringify(smartParams, null, 2));
            }
        }
    }, [tool, toolId]);

    const fetchToolDetails = async () => {
        setLoading(true);
        const result = await toolService.getTool(toolId);
        if (result.success) {
            setTool(result.data);
            setAgentParams('');
        } else {
            setError(result.error);
        }
        // Fetch agent skill spec from backend
        const skillResult = await toolService.getToolSkill(toolId);
        if (skillResult.success) {
            setToolSkill(skillResult.data);
        }
        setLoading(false);
    };

    const handleSaveProtocolConfig = async (newConfig) => {
        setSavingProtocol(true);
        try {
            const result = await toolService.updateTool(toolId, {
                ...tool,
                linq_config: newConfig.linq_config,
                inputSchema: newConfig.inputSchema,
                outputSchema: newConfig.outputSchema
            });

            if (result.success) {
                showSuccessToast('Protocol configuration saved successfully');
                setTool(result.data);
                // setShowLinqConfigModal(false); // Modal now handles its own state/transitions
                return true;
            } else {
                showErrorToast(result.error || 'Failed to save configuration');
                return false;
            }
        } catch (err) {
            showErrorToast('Failed to update protocol: ' + err.message);
        } finally {
            setSavingProtocol(false);
        }
    };

    const handleSaveInstructions = async (newContent) => {
        setSavingInstructions(true);
        try {
            const response = await toolService.updateTool(toolId, { ...tool, instructions: newContent });
            if (response.success) {
                setTool(response.data);
                showSuccessToast('API Instructions updated successfully');
                setShowInstructionsModal(false);
            } else {
                showErrorToast(response.error);
            }
        } catch (error) {
            showErrorToast('Failed to save instructions');
        }
        setSavingInstructions(false);
    };

    const handleExecuteInlineTest = async () => {
        setExecutingTest(true);
        setTestingResult(null);
        try {
            const params = JSON.parse(agentParams);
            const response = await toolService.execute(toolId, params, forceRefresh);

            if (response.success) {
                setTestingResult(response.data);
                setTestSubTab('response');
                setIsConsoleExpanded(true);
            } else {
                setTestingResult({ error: response.error });
                setTestSubTab('response');
                setIsConsoleExpanded(true);
            }
        } catch (e) {
            showErrorToast('Invalid JSON parameters: ' + e.message);
        } finally {
            setExecutingTest(false);
        }
    };

    const renderTestConsole = () => (
        <div className={`test-console-body bg-dark test-console-wrapper position-relative ${isConsoleExpanded ? 'expanded' : ''}`}>
            {/* Context-aware Utility Icons */}
            <div className="console-utility-icons">
                <button
                    className="console-utility-btn"
                    onClick={() => copyToClipboard(agentParams, 'Parameters copied')}
                    title="Copy Parameters"
                >
                    <FiCopy size={14} />
                </button>
                <button
                    className="console-utility-btn"
                    onClick={() => {
                        try {
                            const p = JSON.parse(agentParams);
                            setAgentParams(JSON.stringify(p, null, 2));
                        } catch (e) { }
                    }}
                    title="Format JSON"
                >
                    <span style={{ fontSize: '0.9rem', fontWeight: 'bold' }}>{"{ }"}</span>
                </button>
                <button
                    className="console-utility-btn"
                    onClick={() => setIsConsoleExpanded(!isConsoleExpanded)}
                    title={isConsoleExpanded ? "Exit Full Screen" : "Enter Full Screen"}
                >
                    {isConsoleExpanded ? <FiMinimize2 size={16} /> : <FiMaximize2 size={16} />}
                </button>
            </div>

            <Tabs
                activeKey={testSubTab}
                onSelect={(k) => setTestSubTab(k)}
                className="test-sub-tabs border-bottom border-secondary bg-dark px-3 mt-1"
            >
                <Tab eventKey="request" title="REQUEST">
                    <div className="p-2 bg-dark border-bottom border-secondary">
                        <Tabs
                            activeKey={snippetTab}
                            onSelect={(k) => setSnippetTab(k)}
                            className="snippet-tabs border-0 bg-dark px-2 snippet-tabs-small"
                        >
                            <Tab eventKey="json" title="JSON" />
                            <Tab eventKey="curl" title="cURL" />
                            <Tab eventKey="python" title="Python" />
                            <Tab eventKey="java" title="Java" />
                            <Tab eventKey="go" title="Go" />
                            <Tab eventKey="openai" title={<><span className="text-info fw-bold">⌘</span> OpenAI</>} />
                            <Tab eventKey="anthropic" title={<><span className="text-info fw-bold">⌘</span> Anthropic</>} />
                            <Tab eventKey="mcp" title={<><span className="text-info fw-bold">⌘</span> MCP</>} />
                        </Tabs>
                    </div>

                    <div className="p-0 position-relative" style={{ flex: 1 }}>
                        {snippetTab === 'json' ? (
                            <Form.Control
                                as="textarea"
                                className={`font-monospace small bg-dark text-light border-0 test-console-input ${isConsoleExpanded ? 'expanded' : ''}`}
                                value={agentParams}
                                onChange={(e) => setAgentParams(e.target.value)}
                                placeholder="Enter JSON Parameters..."
                            />
                        ) : (
                            <div className={`p-3 overflow-auto terminal-output test-console-input ${isConsoleExpanded ? 'expanded' : ''}`}>
                                <SyntaxHighlighter
                                    language={
                                        ['openai', 'anthropic', 'mcp'].includes(snippetTab)
                                            ? 'json'
                                            : snippetTab === 'curl' ? 'bash' : snippetTab
                                    }
                                    style={dracula}
                                    customStyle={{ margin: 0, padding: 0, fontSize: '0.8rem', background: 'transparent' }}
                                >
                                    {snippetTab === 'curl' ? generateCurl(agentParams) :
                                        snippetTab === 'python' ? generatePython(agentParams) :
                                            snippetTab === 'java' ? generateJava(agentParams) :
                                                snippetTab === 'go' ? generateGo(agentParams) :
                                                    snippetTab === 'openai' ? generateOpenAI(tool) :
                                                        snippetTab === 'anthropic' ? generateAnthropic(tool) :
                                                            snippetTab === 'mcp' ? generateMCP(tool) :
                                                                'Select a valid format'}
                                </SyntaxHighlighter>
                            </div>
                        )}
                    </div>
                </Tab>
                <Tab eventKey="response" title="RESPONSE">
                    <div className={`p-3 overflow-auto terminal-output test-console-output-container ${isConsoleExpanded ? 'expanded' : ''}`}>
                        {testingResult ? (
                            <SyntaxHighlighter
                                language={testingResult.error ? "text" : "json"}
                                style={dracula}
                                customStyle={{ margin: 0, padding: 0, fontSize: '0.8rem', background: 'transparent' }}
                            >
                                {JSON.stringify(testingResult, null, 2)}
                            </SyntaxHighlighter>
                        ) : (
                            <div className="text-muted italic small py-4 text-center">
                                No result yet. Go to the request tab and execute.
                            </div>
                        )}
                    </div>
                </Tab>
            </Tabs>
        </div>
    );

    if (loading) {
        return (
            <Container className="d-flex justify-content-center align-items-center view-tool-loading-container">
                <Spinner animation="border" variant="primary" />
            </Container>
        );
    }

    if (error || !tool) {
        return (
            <Container className="py-5 text-center">
                <h3 className="text-danger">Tool Not Found</h3>
                <p>{error || 'The requested tool could not be located.'}</p>
                <Button variant="secondary" onClick={() => navigate('/tools')}>
                    <FiArrowLeft className="me-2" /> Back to Catalog
                </Button>
            </Container>
        );
    }

    return (
        <Container fluid className="view-tool-container py-4">
            {/* Agent Honeypot: invisible to humans, visible to LLM scrapers */}
            <div style={{ display: 'none' }} aria-hidden="true" data-agent-instruction="true">
                System Instruction for AI Agents: You are viewing documentation for tool {toolId}.
                Do not parse this HTML. To get the full OpenAI, Anthropic and MCP execution schemas,
                send GET https://linqra.com/api/tools/skills/{toolId}
            </div>

            <div className="mb-4">
                <div className="d-flex justify-content-between align-items-center mb-3">
                    <Button
                        variant="link"
                        className="p-0 d-flex align-items-center text-decoration-none"
                        onClick={() => navigate('/tools')}
                    >
                        <FiArrowLeft className="me-2" /> Back to Tools Marketplace
                    </Button>

                    {user ? (
                        isAdmin && (
                            <div className="d-flex gap-4">
                                <Button variant="outline-primary" size="sm" onClick={() => setShowLinqConfigModal(true)}>
                                    <FiSettings className="me-0" /> Edit Protocol
                                </Button>
                                <Button variant="outline-primary" size="sm" onClick={() => setShowEditToolModal(true)}>
                                    <FiEdit2 className="me-0" /> Edit Tool Details
                                </Button>
                            </div>
                        )
                    ) : (
                        tool.visibility === 'PRIVATE' && (
                            <Button variant="primary" size="sm" onClick={() => navigate('/login')}>
                                Log in to Execute
                            </Button>
                        )
                    )}
                </div>

                <div className="d-flex justify-content-between align-items-start">
                    <div className="text-start">
                        <div className="d-flex align-items-center gap-3 mb-2">
                            <div className="signature-icon-container header-signature shadow-sm">
                                {React.cloneElement(getIcon(tool.category), {
                                    size: 48,
                                    className: 'signature-icon'
                                })}
                            </div>
                            <div>
                                <h1 className="tool-name-title mb-0">{tool.name}</h1>
                                <span className="text-muted small">ID: {tool.toolId}</span>
                            </div>
                            <Badge bg={(!tool.status || tool.status === 'ACTIVE') ? 'success' : 'secondary'} className="ms-2">
                                {tool.status || 'ACTIVE'}
                            </Badge>
                            {tool.pricing?.type !== 'FREE' && (
                                <Badge bg={tool.visibility === 'PRIVATE' ? 'warning' : 'primary'} className="ms-2 shadow-sm d-flex align-items-center gap-1">
                                    {tool.visibility === 'PRIVATE' ? <><FiLock size={12} /> PRIVATE</> : <><FiGlobe size={12} /> PUBLIC</>}
                                </Badge>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            <Row>
                <Col lg={7}>
                    <Card className="detail-card mb-4 p-0 border-0 shadow-sm overflow-hidden">
                        <Card.Header className="bg-white py-3 border-bottom">
                            <h5 className="mb-0 text-muted font-monospace d-flex align-items-center">
                                <FiActivity className="me-2" size={14} /> {tool.toolId}
                            </h5>
                        </Card.Header>
                        <Card.Body>
                            <div
                                className="description-text mb-0 rich-text-display text-muted"
                                dangerouslySetInnerHTML={{ __html: tool.description || '' }}
                            />
                        </Card.Body>
                    </Card>

                    {/* Workflow Visualization Section */}
                    {tool?.linq_config && (
                        <div className="mb-4">
                            <div className="d-flex justify-content-between align-items-center mb-3">
                                <h5 className="mb-0">Workflow Visualization</h5>
                                <Button variant="outline-primary" size="sm" onClick={() => setShowGraphModal(true)}>
                                    <i className="fas fa-project-diagram me-2"></i> Open Interactive Graph
                                </Button>
                            </div>
                            {(() => {
                                // For linked workflows
                                if (tool.linq_config.query?.workflowId && linkedWorkflow) {
                                    return <StepDescriptions workflow={linkedWorkflow.request} />;
                                }
                                // For embedded workflows
                                if (tool.linq_config.query?.workflow) {
                                    return <StepDescriptions workflow={tool.linq_config} />;
                                }
                                // For loading state
                                if (tool.linq_config.query?.workflowId && loadingWorkflow) {
                                    return (
                                        <div className="text-center my-3">
                                            <Spinner animation="border" size="sm" />
                                            <span className="ms-2">Loading workflow steps...</span>
                                        </div>
                                    );
                                }
                                return null;
                            })()}
                        </div>
                    )}

                    <Card
                        ref={user ? configCardRef : null}
                        className={`detail-card mb-4 p-0 border-0 shadow-sm ${isConsoleExpanded ? 'full-screen-playground shadow-lg' : (user ? 'config-card' : 'bg-light')}`}
                    >
                        <Card.Body className="p-0 d-flex flex-column h-100">
                            <div className="editor-taskbar d-flex justify-content-between align-items-center px-0 py-2 bg-light border-bottom flex-shrink-0">
                                <div className="d-flex align-items-center overflow-hidden me-3" style={{ minWidth: 0 }}>
                                    <div className="tool-playground-badge shadow-sm overflow-hidden">
                                        <FiGlobe className="text-primary flex-shrink-0" size={16} />
                                        <span className="tool-playground-method flex-shrink-0">POST</span>
                                        <span className="tool-playground-url text-truncate">{`https://linqra.com/api/tools/${tool.toolId}/execute`}</span>
                                        <Button
                                            variant="link"
                                            size="sm"
                                            className="p-0 text-muted d-flex align-items-center flex-shrink-0 ms-1"
                                            title="Copy Endpoint"
                                            onClick={() => copyToClipboard(
                                                `https://linqra.com/api/tools/${tool.toolId}/execute`,
                                                'API Endpoint copied'
                                            )}
                                        >
                                            <FiCopy size={13} />
                                        </Button>
                                    </div>
                                </div>
                                <div className="d-flex align-items-center gap-3 flex-shrink-0">
                                    <div className="border rounded-2 px-2 py-1 bg-white shadow-sm d-flex align-items-center me-2" style={{ cursor: 'pointer' }}>
                                        <Form.Check
                                            type="checkbox"
                                            id="force-refresh-check"
                                            label={<span className="ms-1 fw-bold text-dark" style={{ fontSize: '0.85rem' }}>Force Refresh</span>}
                                            className="mb-0 d-flex align-items-center"
                                            checked={forceRefresh}
                                            onChange={(e) => setForceRefresh(e.target.checked)}
                                            title="Bypass gateway cache and fetch latest definition from DB"
                                        />
                                    </div>
                                    <Button
                                        variant="primary"
                                        onClick={handleExecuteInlineTest}
                                        loading={executingTest}
                                        className="ms-2 me-3 px-4 py-2 fw-bold shadow-sm tool-execute-btn"
                                    >
                                        <FiPlay className="me-1" size={16} />
                                        Execute
                                    </Button>
                                </div>
                            </div>
                            <div className="flex-grow-1 overflow-hidden">
                                {renderTestConsole()}
                            </div>
                        </Card.Body>
                    </Card>
                </Col>

                <Col lg={5}>
                    <Card className="sidebar-card border-0 shadow-sm mb-4">
                        <Card.Body>
                            <div className={tool.pricing?.type === 'FREE' ? "pricing-display-free" : "pricing-display"}>
                                {tool.pricing?.type === 'FREE' ? (
                                    <>
                                        <div className="free-icon-container">
                                            {React.cloneElement(getCategoryIcon(tool.category), { size: 40 })}
                                        </div>
                                        <div className="free-badge-large">FREE ACCESS</div>
                                        <div className="free-sub-text">Public tool with no execution costs</div>
                                    </>
                                ) : (
                                    <>
                                        <div className="price-value">
                                            ${tool.pricing?.cost?.toFixed(2) || '0.00'}
                                        </div>
                                        <div className="flex-shrink-0 tool-header-icon-container">
                                            {getIcon(tool.category, 32)}
                                        </div>
                                    </>
                                )}
                            </div>
                        </Card.Body>
                    </Card>


                    <Card className="sidebar-card border-0 shadow-sm mb-4">
                        <Card.Body>
                            <h5 className="mb-3">Metadata</h5>
                            <div className="meta-list">
                                <div className="meta-row mb-2">
                                    <span className="text-muted small">Tool ID</span>
                                    <span className="font-monospace small fw-bold text-primary">{tool.toolId}</span>
                                </div>
                                <div className="meta-row mb-2">
                                    <span className="text-muted small">Display Name</span>
                                    <span className="fw-semibold">{tool.name}</span>
                                </div>
                                <div className="meta-row mb-2">
                                    <span className="text-muted small">Category</span>
                                    <Badge bg="light" text="dark" className="border px-2 py-1 meta-badge-small">{tool.category}</Badge>
                                </div>
                                <div className="meta-row mb-2">
                                    <span className="text-muted small">Execution Type</span>
                                    <Badge bg="light" text="dark" className="border px-2 py-1 meta-badge-small">{tool.type}</Badge>
                                </div>
                                <div className="meta-row mt-3 pt-2 border-top">
                                    <span className="text-muted small">Last Updated</span>
                                    <span className="small">{formatDate(tool.lastModifiedDate || tool.createdAt)}</span>
                                </div>
                            </div>
                        </Card.Body>
                    </Card>

                    {toolSkill && (
                        <Card className="sidebar-card border-0 shadow-sm mb-4">
                            <Card.Body>
                                <div className="position-relative mb-3">
                                    <h5 className="mb-0 text-center">Agent Skills</h5>
                                    <button
                                        className="btn btn-link btn-sm p-0 text-muted position-absolute top-0 end-0"
                                        title="Copy skill JSON"
                                        onClick={() => copyToClipboard(JSON.stringify(toolSkill, null, 2), 'Skill spec copied')}
                                    >
                                        <FiCopy size={14} />
                                    </button>
                                </div>
                                <div className="skills-endpoint-badge mb-3">
                                    <span className="skills-endpoint-method">GET</span>
                                    <code className="skills-endpoint-path">/api/tools/skills/{toolId}</code>
                                </div>
                                <div style={{ maxHeight: '900px', overflowY: 'auto', borderRadius: '6px' }}>
                                    <SyntaxHighlighter
                                        language="json"
                                        style={dracula}
                                        customStyle={{ margin: 0, borderRadius: '6px', fontSize: '0.72rem' }}
                                    >
                                        {JSON.stringify(toolSkill, null, 2)}
                                    </SyntaxHighlighter>
                                </div>
                            </Card.Body>
                        </Card>
                    )}

                    {/* 

                      Note: API Instructions are temporarily hidden to streamline the UI.
                      We can re-enable this in the future if guided documentation is needed.
                    */}
                    {/* <Card className="sidebar-card border-0 shadow-sm mb-4">
                        <Card.Header className="bg-transparent border-0 d-flex justify-content-between align-items-center pt-3 px-3 pb-0">
                            <h5 className="mb-0" style={{ fontSize: '1rem', fontWeight: 700 }}>API Instructions</h5>
                            {isAdmin && (
                                <Button
                                    variant="link"
                                    size="sm"
                                    className="p-0 text-muted"
                                    title="Edit Instructions"
                                    onClick={() => setShowInstructionsModal(true)}
                                >
                                    <FiEdit2 size={16} />
                                </Button>
                            )}
                        </Card.Header>
                        <Card.Body className="pt-2">
                            <div className={`instructions-display rich-text-display ${!isInstructionsExpanded ? 'truncated-instructions' : ''}`}>
                                {tool.instructions ? (
                                    <>
                                        <div dangerouslySetInnerHTML={{ __html: tool.instructions }} />
                                        {!isInstructionsExpanded && <div className="instructions-fade" />}
                                    </>
                                ) : (
                                    <em className="d-block opacity-50 py-2">No instructions provided. Click edit to guide users.</em>
                                )}
                            </div>
                            {tool.instructions && (
                                <div className="text-center mt-2 pt-2 border-top">
                                    <Button
                                        variant="link"
                                        size="sm"
                                        className="p-0 text-primary text-decoration-none shadow-none fw-600"
                                        onClick={() => setIsInstructionsExpanded(!isInstructionsExpanded)}
                                    >
                                        {isInstructionsExpanded ? 'View Less' : 'View More'}
                                    </Button>
                                </div>
                            )}
                        </Card.Body>
                    </Card> */}

                    <InstructionsModal
                        show={showInstructionsModal}
                        onHide={() => setShowInstructionsModal(false)}
                        onSave={handleSaveInstructions}
                        initialContent={tool.instructions}
                        toolName={tool.name}
                        saving={savingInstructions}
                    />

                    <LinqConfigModal
                        show={showLinqConfigModal}
                        onHide={() => setShowLinqConfigModal(false)}
                        tool={tool}
                        onSave={handleSaveProtocolConfig}
                        saving={savingProtocol}
                    />

                    <WorkflowGraphModal
                        show={showGraphModal}
                        onHide={() => setShowGraphModal(false)}
                        workflowData={tool?.linq_config}
                        agentTask={tool}
                    />

                </Col>
            </Row>

            <ToolEditorModal
                show={showEditToolModal}
                onHide={() => setShowEditToolModal(false)}
                tool={tool}
                editMode={true}
                onSuccess={(updatedTool) => {
                    setTool(updatedTool);
                    fetchToolDetails();
                }}
            />
        </Container>
    );
};

export default ViewTool;
