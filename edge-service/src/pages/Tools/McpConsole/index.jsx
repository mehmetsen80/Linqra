import React, { useState, useEffect, useRef } from 'react';
import { Container, Row, Col, Card, Form, Spinner, Tabs, Tab } from 'react-bootstrap';
import Button from '../../../components/common/Button';
import {
    FiPlay, FiActivity, FiCopy, FiTerminal, FiGlobe, FiCpu,
    FiSettings, FiShare2, FiZap, FiWifi, FiWifiOff, FiGrid, FiCode, FiCommand
} from 'react-icons/fi';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { dracula } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import toolService from '../../../services/toolService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import './styles.css';

const McpConsole = ({ teamId, initialPayload, predefinedToolId, onExecute }) => {
    const [tools, setTools] = useState([]);
    const [selectedToolId, setSelectedToolId] = useState(predefinedToolId || '');
    const [loadingTools, setLoadingTools] = useState(true);

    // SSE Connection State
    const [connectionStatus, setConnectionStatus] = useState('disconnected'); // disconnected, connecting, connected
    const [sseSessionId, setSseSessionId] = useState('');
    const [logs, setLogs] = useState([]);
    const sseRef = useRef(null);
    const logsEndRef = useRef(null);

    // Playground JSON-RPC State
    const [jsonRpcPayload, setJsonRpcPayload] = useState('{\n  "jsonrpc": "2.0",\n  "method": "tools/list",\n  "id": "1"\n}');
    const [responsePayload, setResponsePayload] = useState('');
    const [sendingRequest, setSendingRequest] = useState(false);
    const [responseViewMode, setResponseViewMode] = useState('parsed'); // 'parsed' or 'raw'

    // Snippet Guides State
    const [guideTab, setGuideTab] = useState('cursor');

    // Auto-scroll terminal logs to bottom
    useEffect(() => {
        if (logsEndRef.current) {
            logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [logs]);

    // Load tools on mount
    useEffect(() => {
        loadTools();
        return () => {
            disconnectSse();
        };
    }, [teamId]);

    // Pre-populate playground with payload passed from ViewTool "MCP Console" button
    useEffect(() => {
        if (initialPayload) {
            setJsonRpcPayload(initialPayload);
            // Try to pre-select the tool in the dropdown
            try {
                const parsed = JSON.parse(initialPayload);
                const toolName = parsed?.params?.name;
                if (toolName) {
                    // MCP name uses underscores; match against toolId (dots)
                    const matchingToolId = toolName.replace(/_/g, '.');
                    setSelectedToolId(matchingToolId);
                }
            } catch (e) {}
        }
    }, [initialPayload]);

    // Auto-select predefined tool when tools load
    useEffect(() => {
        if (predefinedToolId && tools.length > 0) {
            handleToolSelect(predefinedToolId);
        }
    }, [predefinedToolId, tools]);

    const loadTools = async () => {
        setLoadingTools(true);
        try {
            const response = await toolService.getAllTools(teamId);
            if (response.success) {
                setTools(response.data);
                if (response.data.length > 0) {
                    // Preselect first tool if any
                    const firstTool = response.data[0];
                    setSelectedToolId(firstTool.toolId);
                }
            }
        } catch (err) {
            console.error('Failed to load tools for MCP Console', err);
        } finally {
            setLoadingTools(false);
        }
    };

    const addLog = (type, text) => {
        const timestamp = new Date().toLocaleTimeString();
        setLogs(prev => [...prev, { timestamp, type, text }]);
    };

    const connectSse = () => {
        if (connectionStatus === 'connected' || connectionStatus === 'connecting') return;

        setConnectionStatus('connecting');
        addLog('sys', 'Establishing secure EventSource handshake...');

        // Use relative path for current origin proxying to avoid CORS security blocks
        const sseUrl = '/api/mcp/sse';
        const displaySseUrl = `${window.location.protocol}//${window.location.host.replace(':3000', ':7777')}/api/mcp/sse`;
        addLog('sys', `Connecting to SSE: ${displaySseUrl}`);

        try {
            const eventSource = new EventSource(sseUrl);
            sseRef.current = eventSource;

            eventSource.onopen = () => {
                setConnectionStatus('connected');
                addLog('sys', 'SSE Channel established successfully!');
                addLog('sys', 'Receiving active ping heartbeats...');
            };

            eventSource.addEventListener('endpoint', (event) => {
                const endpoint = event.data;
                try {
                    const url = new URL(endpoint, window.location.origin);
                    const sessionId = url.searchParams.get('sessionId');
                    if (sessionId) {
                        setSseSessionId(sessionId);
                        addLog('sys', `Session registered! ID: ${sessionId}`);
                        addLog('sys', `Post messages to: ${endpoint}`);
                        
                        setJsonRpcPayload(prev => {
                            try {
                                const parsed = JSON.parse(prev);
                                if (parsed.method === 'tools/list') {
                                    return JSON.stringify(parsed, null, 2);
                                }
                            } catch(e){}
                            return prev;
                        });
                    }
                } catch (e) {
                    console.error('Failed to parse endpoint URL:', e);
                }
            });

            eventSource.addEventListener('ping', (event) => {
                addLog('in', 'ping [heartbeat]');
            });

            eventSource.onmessage = (event) => {
                // Parse standard MCP SSE endpoint registration or ping heartbeats fallback
                try {
                    const data = JSON.parse(event.data);
                    if (data.ping) {
                        addLog('in', `ping [heartbeat]: seq=${data.ping}`);
                    } else if (data.endpoint) {
                        const url = new URL(data.endpoint, window.location.origin);
                        const sessionId = url.searchParams.get('sessionId');
                        if (sessionId) {
                            setSseSessionId(sessionId);
                            addLog('sys', `Session registered! ID: ${sessionId}`);
                            addLog('sys', `Post messages to: ${data.endpoint}`);
                        }
                    } else {
                        addLog('in', event.data);
                    }
                } catch (e) {
                    addLog('in', event.data);
                }
            };

            eventSource.onerror = (err) => {
                console.error('SSE Error:', err);
                addLog('err', 'Handshake aborted or disconnected by remote gateway');
                disconnectSse();
            };

        } catch (e) {
            addLog('err', `Connection failed: ${e.message}`);
            setConnectionStatus('disconnected');
        }
    };

    const disconnectSse = () => {
        if (sseRef.current) {
            sseRef.current.close();
            sseRef.current = null;
        }
        setConnectionStatus('disconnected');
        setSseSessionId('');
        addLog('sys', 'Connection terminated by client.');
    };

    // Toggle connection helper
    const handleToggleConnection = () => {
        if (connectionStatus === 'connected' || connectionStatus === 'connecting') {
            disconnectSse();
        } else {
            connectSse();
        }
    };

    // Handle tool change from dropdown: auto-generate standard call template
    const handleToolSelect = (toolId) => {
        setSelectedToolId(toolId);
        if (!toolId) return;

        const targetTool = tools.find(t => t.toolId === toolId);
        if (!targetTool) return;

        // Guess params based on config
        let sampleParams = {};
        if (targetTool.inputSchema) {
            try {
                const schema = JSON.parse(targetTool.inputSchema);
                if (schema.properties) {
                    Object.keys(schema.properties).forEach(key => {
                        sampleParams[key] = schema.properties[key].type === 'string' ? 'example_value' : null;
                    });
                }
            } catch (e) {}
        }
        if (Object.keys(sampleParams).length === 0) {
            sampleParams = { "param1": "value" };
        }

        const callPayload = {
            jsonrpc: "2.0",
            method: "tools/call",
            params: {
                name: targetTool.toolId?.replace(/\./g, '_') || targetTool.toolId,
                arguments: sampleParams
            },
            id: String(Math.floor(Math.random() * 100) + 1)
        };

        setJsonRpcPayload(JSON.stringify(callPayload, null, 2));
    };

    // Send JSON-RPC payload to backend gateway over message channel
    const handleSendPayload = async () => {
        if (sendingRequest) return;

        setSendingRequest(true);
        setResponsePayload('');

        let requestBody;
        try {
            requestBody = JSON.parse(jsonRpcPayload);
        } catch (err) {
            showErrorToast('Invalid JSON structure in request payload');
            setSendingRequest(false);
            return;
        }

        addLog('out', `POST /api/mcp/message: ${JSON.stringify(requestBody)}`);

        // Send to standard MCP message API
        // If sseSessionId is active, pass it as query param, otherwise stateless
        let url = '/api/mcp/message';
        if (sseSessionId) {
            url += `?sessionId=${sseSessionId}`;
        }

        try {
            // Standard fetch call to Linqra Gateway
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestBody)
            });

            const result = await response.json();
            setResponsePayload(JSON.stringify(result, null, 2));
            
            // Auto toggle view mode based on presence of nested JSON content
            const innerJson = extractInnerJson(result);
            if (innerJson) {
                setResponseViewMode('parsed');
            } else {
                setResponseViewMode('raw');
            }
            
            if (result.error) {
                addLog('err', `Response Error: ${result.error.message} (code: ${result.error.code})`);
            } else {
                addLog('sys', `Received response for JSON-RPC Request (ID: ${requestBody.id || 'N/A'})`);
                if (onExecute) {
                    onExecute();
                }
            }
        } catch (error) {
            const errStr = `HTTP execution failed: ${error.message}`;
            setResponsePayload(JSON.stringify({ error: errStr }, null, 2));
            addLog('err', errStr);
        } finally {
            setSendingRequest(false);
        }
    };

    const copyToClipboard = (text, message = 'Copied to clipboard') => {
        navigator.clipboard.writeText(text);
        showSuccessToast(message);
    };

    // Helper to extract inner JSON from Markdown code blocks in tool execution results
    const extractInnerJson = (rawResponse) => {
        if (!rawResponse) return null;
        try {
            const parsed = typeof rawResponse === 'string' ? JSON.parse(rawResponse) : rawResponse;
            const contentText = parsed?.result?.content?.[0]?.text;
            if (contentText && typeof contentText === 'string') {
                // Look for ```json ... ``` code block
                const jsonBlockRegex = /```json\s+([\s\S]*?)\s+```/;
                const match = contentText.match(jsonBlockRegex);
                if (match && match[1]) {
                    // Try parsing the extracted block to verify it is valid JSON
                    return JSON.parse(match[1].trim());
                }
            }
        } catch (e) {
            // Not a valid JSON or doesn't match format
        }
        return null;
    };

    // Integration configs code snippets helper using dynamic origin coordinates
    const getDynamicSseUrl = () => {
        const envUrl = import.meta.env.VITE_API_GATEWAY_URL;
        if (envUrl && envUrl.startsWith('http')) {
            return `${envUrl.replace(/\/$/, '')}/api/mcp/sse`;
        }
        return `${window.location.protocol}//${window.location.host.replace(':3000', ':7777')}/api/mcp/sse`;
    };

    const getClaudeConfig = () => {
        return `{
  "mcpServers": {
    "linqra-sentinel": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-sse",
        "${getDynamicSseUrl()}"
      ]
    }
  }
}`;
    };

    const getCursorInstructions = () => {
        return `1. Open Cursor Settings -> Features -> MCP
2. Click "+ Add New MCP Server"
3. Configure the following values:
   - Name: Linqra Gateway
   - Type: SSE
   - URL: ${getDynamicSseUrl()}
4. Click Save. Cursor will connect and fetch all registered tools instantly!`;
    };

    const getVSCodeInstructions = () => {
        return `{
  "mcpServers": {
    "linqra-gateway": {
      "type": "sse",
      "url": "${getDynamicSseUrl()}"
    }
  }
}`;
    };

    const getSdkBootstrap = () => {
        return `// Connect custom node clients to Linqra SSE
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { SSEClientTransport } from "@modelcontextprotocol/sdk/client/sse.js";

const transport = new SSEClientTransport(
  new URL("${getDynamicSseUrl()}")
);

const client = new Client(
  { name: "custom-agent", version: "1.0.0" },
  { capabilities: {} }
);

await client.connect(transport);
console.log("Connected to Linqra Gateway MCP!");

// List all active tools dynamically
const tools = await client.listTools();
console.log(tools);`;
    };

    const renderSafePayload = (payload, isParsed = false) => {
        if (!payload) return null;
        const isLarge = payload.length > 100000;
        const sizeInKb = (payload.length / 1024).toFixed(1);
        const sizeDisplay = payload.length > 1024 * 1024 
          ? `${(payload.length / (1024 * 1024)).toFixed(2)} MB` 
          : `${sizeInKb} KB`;

        let contentNode;
        if (isLarge) {
            const previewText = payload.substring(0, 5000) + '\n\n... [TRUNCATED - Payload is too large to render dynamically (' + sizeDisplay + ')]';
            contentNode = (
                <pre className="text-light m-0 font-monospace" style={{ fontSize: '0.8rem', maxHeight: '300px', overflow: 'auto', whiteSpace: 'pre-wrap' }}>
                    {previewText}
                </pre>
            );
        } else {
            contentNode = (
                <SyntaxHighlighter
                    language="json"
                    style={dracula}
                    customStyle={{ margin: 0, padding: 0, fontSize: '0.8rem', background: 'transparent' }}
                >
                    {payload}
                </SyntaxHighlighter>
            );
        }

        return (
            <div className={`payload-container rounded border ${isLarge ? 'border-warning' : 'border-secondary'}`} style={{ background: '#0e1424' }}>
                <div className="d-flex justify-content-between align-items-center p-2 px-3 border-bottom border-secondary bg-dark bg-opacity-50">
                    <span className={`fw-bold small d-flex align-items-center gap-1 ${isLarge ? 'text-warning' : 'text-muted'}`}>
                        {isLarge ? '⚠️ Large Payload Bypassed' : '✨ JSON Payload'} ({sizeDisplay})
                    </span>
                    <div className="d-flex gap-2">
                        <button 
                            className="btn btn-outline-secondary btn-sm py-1 font-monospace"
                            style={{ fontSize: '0.72rem' }}
                            onClick={(e) => {
                                copyToClipboard(payload, 'JSON copied');
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
                                const blob = new Blob([payload], { type: 'application/json' });
                                const url = URL.createObjectURL(blob);
                                const a = document.createElement('a');
                                a.href = url;
                                a.download = isParsed ? 'mcp_parsed_response.json' : 'mcp_raw_envelope.json';
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
        <div className="mcp-console-section px-1">
            {/* Component Title & Workspace Context Indicator */}
            <div className="mcp-console-header-block mb-4 text-start">
                <div className="d-flex align-items-center gap-2 mb-2">
                    <span className="badge bg-primary text-uppercase font-monospace px-2 py-1" style={{ fontSize: '0.7rem', letterSpacing: '0.05em' }}>
                        MCP Execution Environment
                    </span>
                    <span className="badge bg-success text-uppercase font-monospace px-2 py-1" style={{ fontSize: '0.7rem', letterSpacing: '0.05em' }}>
                        Live Playground
                    </span>
                </div>
                <h4 className="fw-bold mcp-console-title mb-1 d-flex align-items-center gap-2">
                    <FiTerminal size={20} className="text-primary" /> MCP Live Console & Execution Playground
                </h4>
                <p className="mcp-console-desc small mb-0">
                    Interact directly with registered Model Context Protocol servers. Connect the SSE event stream to initiate a persistent state channel and execute live JSON-RPC messages.
                </p>
            </div>

            <Row className="mb-4">
                <Col lg={4}>
                    <Card className="mcp-glass-card border-0 mb-4 h-100">
                        <Card.Header className="mcp-card-header bg-transparent d-flex justify-content-between align-items-center">
                            <h5 className="mb-0 fw-bold d-flex align-items-center gap-2 text-white">
                                <FiWifi size={16} className="text-primary" /> Connection Center
                            </h5>
                            <span className={`connection-indicator ${connectionStatus}`}>
                                <span className="pulse-dot"></span>
                                {connectionStatus.toUpperCase()}
                            </span>
                        </Card.Header>
                        <Card.Body className="d-flex flex-column justify-content-between gap-4 p-4 text-start">
                            <div>
                                <p className="mcp-desc-text small mb-4">
                                    The Model Context Protocol (MCP) server binds your tools dynamically. Handshake to open a persistent state channel.
                                </p>

                                <div className="d-flex flex-column gap-3 mb-4">
                                    <div className="endpoint-coord">
                                        <div className="coord-label">SSE Gateway Address</div>
                                        <div className="coord-value">
                                            <span>{getDynamicSseUrl()}</span>
                                            <button 
                                                className="coord-copy-btn"
                                                onClick={() => copyToClipboard(getDynamicSseUrl(), 'SSE Endpoint copied')}
                                            >
                                                <FiCopy size={13} />
                                            </button>
                                        </div>
                                    </div>
                                    <div className="endpoint-coord">
                                        <div className="coord-label">Session Coordinates (SessionId)</div>
                                        <div className="coord-value">
                                            <span className={sseSessionId ? 'text-success font-monospace' : 'italic'}>
                                                {sseSessionId ? sseSessionId : 'Awaiting Connection...'}
                                            </span>
                                            {sseSessionId && (
                                                <button 
                                                    className="coord-copy-btn"
                                                    onClick={() => copyToClipboard(sseSessionId, 'Session ID copied')}
                                                >
                                                    <FiCopy size={13} />
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <Button
                                variant={connectionStatus === 'connected' ? 'danger' : 'primary'}
                                onClick={handleToggleConnection}
                                className="w-100 py-2 fw-bold d-flex align-items-center justify-content-center gap-2"
                            >
                                {connectionStatus === 'connected' ? (
                                    <>
                                        <FiWifiOff size={16} /> Disconnect Channel
                                    </>
                                ) : connectionStatus === 'connecting' ? (
                                    <>
                                        <Spinner animation="border" size="sm" /> Handshaking...
                                    </>
                                ) : (
                                    <>
                                        <FiWifi size={16} /> Connect SSE Stream
                                    </>
                                )}
                            </Button>
                        </Card.Body>
                    </Card>
                </Col>

                <Col lg={8}>
                    <Card className="mcp-glass-card border-0 mb-4 h-100">
                        <Card.Header className="mcp-card-header bg-transparent">
                            <h5 className="mb-0 fw-bold d-flex align-items-center gap-2 text-white">
                                <FiTerminal size={16} className="text-success" /> SSE Live Event Stream Logs
                            </h5>
                        </Card.Header>
                        <Card.Body className="p-4 d-flex flex-column h-100 overflow-hidden">
                            <div className="terminal-stream-panel flex-grow-1">
                                <div className="terminal-header">
                                    <div className="terminal-window-dots">
                                        <span className="window-dot dot-red"></span>
                                        <span className="window-dot dot-yellow"></span>
                                        <span className="window-dot dot-green"></span>
                                    </div>
                                    <span className="text-muted small font-monospace" style={{ fontSize: '0.7rem' }}>
                                        mcp_sse_stream.log
                                    </span>
                                </div>
                                <div className="terminal-logs text-start">
                                    {logs.length === 0 ? (
                                        <div className="text-muted italic py-5 text-center small">
                                            Channel offline. Establish a secure SSE stream to watch connection events.
                                        </div>
                                    ) : (
                                        logs.map((log, idx) => (
                                            <div key={idx} className="log-entry">
                                                <span className="log-timestamp">[{log.timestamp}]</span>
                                                <span className={`log-type ${log.type}`}>{log.type}</span>
                                                <span className="log-text">{log.text}</span>
                                            </div>
                                        ))
                                    )}
                                    <div ref={logsEndRef} />
                                </div>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Card className="mcp-glass-card border-0 mb-4">
                <Card.Header className="mcp-card-header bg-transparent d-flex justify-content-between align-items-center">
                    <h5 className="mb-0 fw-bold d-flex align-items-center gap-2 text-white">
                        <FiZap size={16} className="text-warning" /> JSON-RPC 2.0 Command Playground
                    </h5>
                    <div className="d-flex align-items-center gap-3">
                        {predefinedToolId ? (
                            <div className="d-flex align-items-center gap-2">
                                <span className="text-muted small">Predefined Tool:</span>
                                <span className="badge bg-secondary font-monospace px-3 py-2 text-white" style={{ fontSize: '0.78rem', border: '1px solid rgba(255,255,255,0.15)', background: '#1e293b' }}>
                                    {predefinedToolId}
                                </span>
                            </div>
                        ) : (
                            <Form.Select 
                                className="bg-dark text-light border-secondary size-sm py-1 font-monospace" 
                                style={{ fontSize: '0.8rem', width: '220px' }}
                                value={selectedToolId}
                                onChange={(e) => handleToolSelect(e.target.value)}
                                disabled={loadingTools || tools.length === 0}
                            >
                                {loadingTools ? (
                                    <option>Loading tools...</option>
                                ) : tools.length === 0 ? (
                                    <option>No tools registered</option>
                                ) : (
                                    <>
                                        <option value="">-- Choose Tool Template --</option>
                                        {tools.map(tool => (
                                            <option key={tool.toolId} value={tool.toolId}>
                                                {tool.toolId}
                                            </option>
                                        ))}
                                    </>
                                )}
                            </Form.Select>
                        )}
                        <Button 
                            variant="primary" 
                            size="sm"
                            disabled={sendingRequest || connectionStatus !== 'connected'}
                            title={connectionStatus !== 'connected' ? 'Please connect SSE Stream first to establish an active session' : 'Send JSON-RPC Message'}
                            onClick={handleSendPayload}
                            className="py-1 px-3 d-flex align-items-center gap-2"
                        >
                            {sendingRequest ? <Spinner animation="border" size="sm" /> : <FiPlay size={12} />} Send Message
                        </Button>
                    </div>
                </Card.Header>
                <Card.Body className="p-4">
                    <div className="playground-split">
                        {/* Request Composer */}
                        <div className="playground-editor-wrapper text-start">
                            <div className="editor-toolbar">
                                <span className="text-muted small font-monospace d-flex align-items-center gap-1">
                                    <FiCode className="text-info" /> REQUEST PAYLOAD (JSON-RPC)
                                </span>
                                <button 
                                    className="coord-copy-btn"
                                    onClick={() => copyToClipboard(jsonRpcPayload, 'Request JSON copied')}
                                    title="Copy Request"
                                >
                                    <FiCopy size={13} />
                                </button>
                            </div>
                            <div className="mcp-code-editor-wrapper">
                                <SyntaxHighlighter
                                    language="json"
                                    style={dracula}
                                    customStyle={{ margin: 0, padding: '1rem', fontSize: '0.8rem', background: 'transparent', minHeight: '100%' }}
                                >
                                    {jsonRpcPayload || ' '}
                                </SyntaxHighlighter>
                                <textarea
                                    className="mcp-code-editor-overlay"
                                    value={jsonRpcPayload}
                                    onChange={(e) => setJsonRpcPayload(e.target.value)}
                                    placeholder="Write standard JSON-RPC 2.0 payload..."
                                    spellCheck={false}
                                />
                            </div>
                        </div>

                        {/* Response Terminal */}
                        <div className="playground-editor-wrapper text-start">
                            <div className="editor-toolbar d-flex justify-content-between align-items-center">
                                <div className="d-flex align-items-center gap-2">
                                    <span className="text-muted small font-monospace d-flex align-items-center gap-1">
                                        <FiTerminal className="text-success" /> RESPONSE PACKET
                                    </span>
                                    {responsePayload && extractInnerJson(responsePayload) && (
                                        <div className="mcp-response-view-selector ms-2">
                                            <button
                                                className={`response-selector-btn ${responseViewMode === 'parsed' ? 'active' : ''}`}
                                                onClick={() => setResponseViewMode('parsed')}
                                                type="button"
                                            >
                                                📦 Parsed JSON
                                            </button>
                                            <button
                                                className={`response-selector-btn ${responseViewMode === 'raw' ? 'active' : ''}`}
                                                onClick={() => setResponseViewMode('raw')}
                                                type="button"
                                            >
                                                🌐 Raw Envelope
                                            </button>
                                        </div>
                                    )}
                                </div>
                                {responsePayload && (
                                    <button 
                                        className="coord-copy-btn"
                                        onClick={() => {
                                            const inner = extractInnerJson(responsePayload);
                                            const textToCopy = (responseViewMode === 'parsed' && inner) 
                                                ? JSON.stringify(inner, null, 2) 
                                                : responsePayload;
                                            copyToClipboard(textToCopy, 'Response copied');
                                        }}
                                        title="Copy Response"
                                    >
                                        <FiCopy size={13} />
                                    </button>
                                )}
                            </div>
                            <div className="flex-grow-1 overflow-auto p-3" style={{ background: '#0b0f19' }}>
                                {responsePayload ? (
                                    responseViewMode === 'parsed' && extractInnerJson(responsePayload) ? (
                                        renderSafePayload(JSON.stringify(extractInnerJson(responsePayload), null, 2), true)
                                    ) : (
                                        renderSafePayload(responsePayload, false)
                                    )
                                ) : (
                                    <div className="text-muted italic py-5 text-center small">
                                        No request triggered yet. Hit "Send Message" to dispatch the command playground.
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                </Card.Body>
            </Card>

            <Card className="mcp-glass-card border-0 mb-4 text-start">
                <Card.Header className="mcp-card-header bg-transparent">
                    <h5 className="mb-0 fw-bold d-flex align-items-center gap-2 text-white">
                        <FiSettings size={16} className="text-info" /> Agent Client Integration Guide
                    </h5>
                </Card.Header>
                <Card.Body className="p-4">
                    <div className="d-flex justify-content-between align-items-center mb-4 border-bottom border-secondary pb-3">
                        <div className="mcp-pill-nav">
                            <button
                                className={`mcp-pill-btn ${guideTab === 'cursor' ? 'active' : ''}`}
                                onClick={() => setGuideTab('cursor')}
                            >
                                <FiCpu className="me-1" size={13} /> Cursor IDE
                            </button>
                            <button
                                className={`mcp-pill-btn ${guideTab === 'vscode' ? 'active' : ''}`}
                                onClick={() => setGuideTab('vscode')}
                            >
                                <FiCommand className="me-1" size={13} /> VS Code / Antigravity
                            </button>
                            <button
                                className={`mcp-pill-btn ${guideTab === 'claude' ? 'active' : ''}`}
                                onClick={() => setGuideTab('claude')}
                            >
                                <FiShare2 className="me-1" size={13} /> Claude Desktop
                            </button>
                            <button
                                className={`mcp-pill-btn ${guideTab === 'sdk' ? 'active' : ''}`}
                                onClick={() => setGuideTab('sdk')}
                            >
                                <FiCode className="me-1" size={13} /> Custom SDK Client
                            </button>
                        </div>
                        <button 
                            className="btn btn-outline-secondary btn-sm font-monospace"
                            style={{ fontSize: '0.78rem' }}
                            onClick={() => {
                                const textToCopy = 
                                    guideTab === 'cursor' ? getCursorInstructions() :
                                    guideTab === 'vscode' ? getVSCodeInstructions() :
                                    guideTab === 'claude' ? getClaudeConfig() : 
                                    getSdkBootstrap();
                                copyToClipboard(textToCopy, 'Instructions copied');
                            }}
                        >
                            <FiCopy className="me-1" size={12} /> Copy Guide Configuration
                        </button>
                    </div>

                    {guideTab === 'cursor' ? (
                        <div className="px-1 text-light">
                            <h6 className="fw-bold mb-3 text-info">Connecting Cursor IDE dynamically to Linqra Gateway:</h6>
                            <pre className="text-light bg-dark p-3 rounded-lg font-monospace small" style={{ whiteSpace: 'pre-wrap', border: '1px solid rgba(255,255,255,0.06)' }}>
                                {getCursorInstructions()}
                            </pre>
                        </div>
                    ) : guideTab === 'vscode' ? (
                        <div className="px-1 text-light">
                            <h6 className="fw-bold mb-2 text-info">Connecting VS Code / Antigravity Developer Agents:</h6>
                            <p className="mcp-desc-text small mb-3">
                                If you are using MCP extensions in VS Code (like <strong>Cline</strong> or <strong>Roo Code</strong>) to power your developer agents:
                            </p>
                            <ol className="mcp-desc-text small mb-4 ps-3" style={{ lineHeight: '1.6' }}>
                                <li>Open your VS Code MCP Extension panel (e.g., Cline / Roo Code tab).</li>
                                <li>Click the <strong>Settings (Gear)</strong> icon at the top right of the extension panel.</li>
                                <li>Click the <strong>"Edit MCP Settings"</strong> button. This will automatically open your settings JSON file directly inside VS Code!</li>
                                <li>Append the following server configuration to your <code>"mcpServers"</code> object and save!</li>
                            </ol>
                            <SyntaxHighlighter
                                language="json"
                                style={dracula}
                                customStyle={{ margin: 0, padding: '1.25rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.06)', fontSize: '0.8rem' }}
                            >
                                {getVSCodeInstructions()}
                            </SyntaxHighlighter>
                        </div>
                    ) : guideTab === 'claude' ? (
                        <div className="px-1">
                            <h6 className="fw-bold mb-3 text-info">Add this to your Claude Desktop config JSON (located under <code className="text-warning">~/Library/Application Support/Claude/claude_desktop_config.json</code>):</h6>
                            <SyntaxHighlighter
                                language="json"
                                style={dracula}
                                customStyle={{ margin: 0, padding: '1.25rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.06)', fontSize: '0.8rem' }}
                            >
                                {getClaudeConfig()}
                            </SyntaxHighlighter>
                        </div>
                    ) : (
                        <div className="px-1">
                            <h6 className="fw-bold mb-3 text-info">Boilerplate code to establish custom Javascript/Typescript client connection using MCP official SDK:</h6>
                            <SyntaxHighlighter
                                language="javascript"
                                style={dracula}
                                customStyle={{ margin: 0, padding: '1.25rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.06)', fontSize: '0.8rem' }}
                            >
                                {getSdkBootstrap()}
                            </SyntaxHighlighter>
                        </div>
                    )}
                </Card.Body>
            </Card>
        </div>
    );
};

export default McpConsole;
