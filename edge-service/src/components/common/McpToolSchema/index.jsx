import React, { useState, useMemo } from 'react';
import { Card, Table, Badge, Form, InputGroup } from 'react-bootstrap';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { dracula } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { FiEye, FiCode, FiSearch, FiCheck, FiX, FiInfo } from 'react-icons/fi';
import './styles.css';

const McpToolSchema = ({ schema, title = 'Parameter Schema' }) => {
    const [viewMode, setViewMode] = useState('visual'); // 'visual' or 'json'
    const [searchQuery, setSearchQuery] = useState('');

    const parsedSchema = useMemo(() => {
        if (!schema) return null;
        if (typeof schema === 'string') {
            try {
                return JSON.parse(schema);
            } catch (e) {
                return null;
            }
        }
        return schema;
    }, [schema]);

    const parameters = useMemo(() => {
        if (!parsedSchema || !parsedSchema.properties) return [];
        const requiredList = parsedSchema.required || [];
        
        return Object.keys(parsedSchema.properties).map(key => {
            const prop = parsedSchema.properties[key];
            return {
                name: key,
                type: prop.type || 'any',
                description: prop.description || 'No description provided.',
                isRequired: requiredList.includes(key),
                defaultValue: prop.default !== undefined ? String(prop.default) : null,
                enumList: prop.enum || null
            };
        });
    }, [parsedSchema]);

    const filteredParameters = useMemo(() => {
        if (!searchQuery.trim()) return parameters;
        const query = searchQuery.toLowerCase();
        return parameters.filter(param => 
            param.name.toLowerCase().includes(query) || 
            param.type.toLowerCase().includes(query) ||
            param.description.toLowerCase().includes(query)
        );
    }, [parameters, searchQuery]);

    const getTypeBadgeColor = (type) => {
        switch (type?.toLowerCase()) {
            case 'string': return 'type-string';
            case 'number':
            case 'integer': return 'type-number';
            case 'boolean': return 'type-boolean';
            case 'array': return 'type-array';
            case 'object': return 'type-object';
            default: return 'type-default';
        }
    };

    if (!parsedSchema) {
        return (
            <Card className="mcp-schema-card shadow-sm border-0">
                <Card.Body className="p-4 text-center text-muted">
                    <FiInfo className="mb-2" size={24} />
                    <p className="small mb-0">No valid schema definition available for this tool.</p>
                </Card.Body>
            </Card>
        );
    }

    return (
        <Card className="mcp-schema-card shadow-sm border-0">
            <Card.Header className="mcp-schema-header d-flex justify-content-between align-items-center bg-transparent border-bottom">
                <h6 className="mb-0 fw-bold d-flex align-items-center gap-2 text-dark">
                    <FiCode className="text-secondary" size={16} />
                    {title}
                </h6>
                <div className="mcp-schema-toggle-group">
                    <button 
                        className={`mcp-toggle-btn ${viewMode === 'visual' ? 'active' : ''}`}
                        onClick={() => setViewMode('visual')}
                    >
                        <FiEye size={12} className="me-1" />
                        Visual Explorer
                    </button>
                    <button 
                        className={`mcp-toggle-btn ${viewMode === 'json' ? 'active' : ''}`}
                        onClick={() => setViewMode('json')}
                    >
                        <FiCode size={12} className="me-1" />
                        Raw JSON
                    </button>
                </div>
            </Card.Header>

            <Card.Body className="p-0">
                {viewMode === 'visual' ? (
                    <div className="mcp-visual-explorer-container">
                        {parameters.length > 5 && (
                            <div className="px-3 pt-3 pb-2 border-bottom">
                                <InputGroup className="mcp-schema-search-group">
                                    <InputGroup.Text className="bg-transparent border-end-0">
                                        <FiSearch size={14} className="text-muted" />
                                    </InputGroup.Text>
                                    <Form.Control
                                        placeholder="Search schema parameters..."
                                        value={searchQuery}
                                        onChange={e => setSearchQuery(e.target.value)}
                                        className="border-start-0 ps-0 text-sm"
                                        size="sm"
                                    />
                                </InputGroup>
                            </div>
                        )}

                        {filteredParameters.length > 0 ? (
                            <div className="table-responsive">
                                <Table hover className="mcp-schema-table mb-0 align-middle">
                                    <thead>
                                        <tr>
                                            <th>Parameter</th>
                                            <th>Type</th>
                                            <th>Required</th>
                                            <th>Description</th>
                                            <th>Details</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filteredParameters.map((param) => (
                                            <tr key={param.name}>
                                                <td className="font-monospace fw-bold text-dark param-name">
                                                    {param.name}
                                                </td>
                                                <td>
                                                    <span className={`mcp-type-badge ${getTypeBadgeColor(param.type)}`}>
                                                        {param.type}
                                                    </span>
                                                </td>
                                                <td>
                                                    {param.isRequired ? (
                                                        <span className="mcp-required-badge">
                                                            <FiCheck className="me-1" size={10} />
                                                            Required
                                                        </span>
                                                    ) : (
                                                        <span className="mcp-optional-badge">
                                                            Optional
                                                        </span>
                                                    )}
                                                </td>
                                                <td className="text-muted param-description text-wrap" style={{ maxWidth: '300px' }}>
                                                    {param.description}
                                                </td>
                                                <td>
                                                    <div className="d-flex flex-column gap-2" style={{ minWidth: '150px' }}>
                                                        {param.defaultValue && (
                                                            <div className="param-detail-row align-items-center">
                                                                <span className="detail-label me-1">Default:</span>
                                                                <span className="badge font-monospace bg-light text-dark border px-2 py-1" style={{ fontSize: '0.72rem', borderRadius: '4px' }}>
                                                                    {param.defaultValue}
                                                                </span>
                                                            </div>
                                                        )}
                                                        {param.enumList && (
                                                            <div className="d-flex flex-column gap-1">
                                                                <span className="detail-label" style={{ fontSize: '0.7rem' }}>Allowed Values:</span>
                                                                <div className="d-flex flex-wrap gap-1 mt-1">
                                                                    {param.enumList.slice(0, 2).map(val => (
                                                                        <span key={val} className="badge bg-light text-secondary border font-monospace px-2 py-1" style={{ fontSize: '0.68rem', borderRadius: '4px', background: '#f8fafc' }}>
                                                                            {val}
                                                                        </span>
                                                                    ))}
                                                                    {param.enumList.length > 2 && (
                                                                        <span 
                                                                            className="badge text-primary border font-monospace px-2 py-1 mcp-more-badge" 
                                                                            onClick={() => setViewMode('json')}
                                                                            title="Click to view all values in Raw JSON"
                                                                        >
                                                                            ... {param.enumList.length - 2} more
                                                                        </span>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        )}
                                                        {!param.defaultValue && !param.enumList && (
                                                            <span className="text-muted small italic">—</span>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </Table>
                            </div>
                        ) : (
                            <div className="p-4 text-center text-muted">
                                <p className="small mb-0">No parameters matching "{searchQuery}" found.</p>
                            </div>
                        )}
                    </div>
                ) : (
                    <div className="mcp-raw-json-container">
                        <SyntaxHighlighter 
                            language="json" 
                            style={dracula}
                            customStyle={{ margin: 0, padding: '1.25rem', fontSize: '0.82rem', background: '#0f172a', maxHeight: '450px' }}
                        >
                            {typeof schema === 'string' ? schema : JSON.stringify(schema, null, 2)}
                        </SyntaxHighlighter>
                    </div>
                )}
            </Card.Body>
        </Card>
    );
};

export default McpToolSchema;
