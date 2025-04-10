import React, { useState, useEffect, useRef } from 'react';
import { Badge, Tab, Nav, Accordion } from 'react-bootstrap';
import { FaCopy } from 'react-icons/fa';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { dracula } from "react-syntax-highlighter/dist/cjs/styles/prism";
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import './styles.css';
import CreateEndpointModal from '../CreateEndpointModal';
import Button from '../../common/Button';
import { apiEndpointService } from '../../../services/apiEndpointService';
import ApiEndpointVersionControl from '../ApiEndpointVersionControl';
import ViewSwaggerJsonModal from '../ViewSwaggerJsonModal';
import { linqService } from '../../../services/linqService';

const RouteEndpoints = ({ route }) => {
  const [showModal, setShowModal] = useState(false);
  const [endpoints, setEndpoints] = useState({});
  const [loading, setLoading] = useState(true);
  const [currentEndpoint, setCurrentEndpoint] = useState(null);
  const [originalEndpoint, setOriginalEndpoint] = useState(null);
  const [hasChanges, setHasChanges] = useState(false);
  const [showVersionControl, setShowVersionControl] = useState(false);
  const [showJsonModal, setShowJsonModal] = useState(false);
  const versionControlRef = useRef(null);
  const [isLoadingLinq, setIsLoadingLinq] = useState(false);
  const [linqFormats, setLinqFormats] = useState({});
  const [activeTab, setActiveTab] = useState('traditional');

  const fetchEndpoints = async () => {
    try {
      const response = await apiEndpointService.getEndpointsByRoute(route.routeIdentifier);
      if (response && response.length > 0) {
        setCurrentEndpoint(response[0]);
        setOriginalEndpoint(response[0]);
        await updateSwaggerDisplay(response[0].swaggerJson);
      }
    } catch (error) {
      console.error('Error fetching endpoints:', error);
      showErrorToast('Failed to fetch endpoints');
    } finally {
      setLoading(false);
    }
  };

  const updateSwaggerDisplay = async (swaggerJson) => {
    try {
      const extracted = await apiEndpointService.extractSwaggerInfo(swaggerJson);
      setEndpoints(extracted.endpointsByTag);
    } catch (error) {
      console.error('Error extracting Swagger info:', error);
    }
  };

  const handleVersionChange = async (newVersion) => {
    try {
      setLoading(true);
      setCurrentEndpoint(newVersion);
      await updateSwaggerDisplay(newVersion.swaggerJson);
      setHasChanges(true);
    } catch (error) {
      console.error('Error handling version change:', error);
      showErrorToast('Failed to load version data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEndpoints();
  }, [route.routeIdentifier]);

  const getMethodBadgeColor = (method) => {
    const colors = {
      GET: 'primary',
      POST: 'success',
      PUT: 'warning',
      DELETE: 'danger',
      PATCH: 'info',
      HEAD: 'secondary',
      OPTIONS: 'dark'
    };
    return colors[method] || 'secondary';
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    // You might want to add a toast notification here
  };

  const handleCancelChanges = async () => {
    try {
      setLoading(true);
      setCurrentEndpoint(originalEndpoint);
      const extracted = await apiEndpointService.extractSwaggerInfo(originalEndpoint.swaggerJson);
      setEndpoints(extracted.endpointsByTag);
      setHasChanges(false);
      // Pass null to clear the changes in VersionControl
      if (versionControlRef.current) {
        versionControlRef.current.clearChanges();
      }
    } catch (error) {
      console.error('Error canceling changes:', error);
      showErrorToast('Failed to cancel changes');
    } finally {
      setLoading(false);
    }
  };

  const handleSaveVersion = async () => {
    try {
      await apiEndpointService.updateEndpoint(currentEndpoint.id, currentEndpoint);
      setOriginalEndpoint(currentEndpoint);
      setHasChanges(false);
      showSuccessToast(`Version ${currentEndpoint.version} has been saved`);
      
      setTimeout(() => {
        window.location.reload();
      }, 1500);
    } catch (error) {
      console.error('Failed to save version:', error);
      showErrorToast(`Failed to save version: ${error.message}`);
    }
  };

  const formatEndpointName = (name) => {
    // Check if the name follows our date pattern
    const match = name.match(/Endpoint-(\d{2})-(\d{2})-(\d{4})-(\d{2})-(\d{2})-(\d{2})/);
    
    if (match) {
      const [_, month, day, year, hours, minutes, seconds] = match;
      const date = new Date(year, month - 1, day, hours, minutes, seconds);
      
      // Format the date in a more readable way
      return `Created on ${month}/${day}/${year} at ${hours}:${minutes}`;
    }
    
    return name; // Return original name if it doesn't match the pattern
  };

  const getLinqFormatsForAllEndpoints = async () => {
    if (isLoadingLinq) return;
    
    try {
      setIsLoadingLinq(true);
      console.log('Fetching Linq formats for route:', route.routeIdentifier);
      
      const linqData = await linqService.convertToLinq({
        routeIdentifier: route.routeIdentifier
      });
      
      console.log('Received Linq data:', linqData);
      
      if (!linqData || !linqData.endpointsByTag) {
        console.error('Invalid Linq data format received:', linqData);
        showErrorToast('Received invalid Linq format data');
        return;
      }
      
      setLinqFormats(linqData.endpointsByTag);
    } catch (error) {
      console.error('Error loading Linq formats:', error);
      showErrorToast('Failed to load Linq formats');
    } finally {
      setIsLoadingLinq(false);
    }
  };

  useEffect(() => {
    if (route.routeIdentifier) {
      fetchEndpoints();
    }
  }, [route.routeIdentifier]);

  return (
    <div className="view-route-page">
      <div className="page-header">
        <div className="header-section">
          <div className="header-content">
            <h1>Route Endpoints</h1>
            <div className="header-actions">
              {currentEndpoint ? (
                <>
                  <button 
                    className="json-view-button" 
                    onClick={() => setShowJsonModal(true)}
                  >
                    <i className="fas fa-code"></i>&nbsp;View JSON
                  </button>
                  {hasChanges && (
                    <>
                      <Button 
                        variant="secondary"
                        onClick={handleCancelChanges}
                      >
                        Cancel Changes
                      </Button>
                      <Button 
                        variant="success"
                        onClick={handleSaveVersion}
                      >
                        Save Version {currentEndpoint?.version}
                      </Button>
                    </>
                  )}
                </>
              ) : (
                <Button 
                  variant="primary"
                  onClick={() => setShowModal(true)}
                >
                  Add Endpoint
                </Button>
              )}
            </div>
          </div>
          {currentEndpoint && (
            <div className="version-section">
              <div className="endpoint-name">
                {formatEndpointName(currentEndpoint.name)}
              </div>
              <button 
                className="version-toggle-link"
                onClick={() => setShowVersionControl(!showVersionControl)}
              >
                {showVersionControl ? 'Hide Version History' : 'Show Version History'} (v{currentEndpoint.version || 1})
              </button>
              
              {showVersionControl && (
                <ApiEndpointVersionControl
                  ref={versionControlRef}
                  routeIdentifier={route.routeIdentifier}
                  currentVersion={currentEndpoint.version || 1}
                  onVersionChange={handleVersionChange}
                />
              )}
            </div>
          )}
        </div>
      </div>

      <div className="route-details-container">
        <div className="route-identifier-section">
          <code className="identifier-value">{route.routeIdentifier}</code>
        </div>

        {loading ? (
          <div className="loading">Loading endpoints...</div>
        ) : (
          <Tab.Container 
            defaultActiveKey="traditional"
            onSelect={(key) => {
              setActiveTab(key);
              if (key === 'linq' && (!linqFormats || Object.keys(linqFormats).length === 0)) {
                getLinqFormatsForAllEndpoints();
              }
            }}
          >
            <Nav variant="tabs" className="px-3 mt-2">
              <Nav.Item>
                <Nav.Link 
                  eventKey="traditional" 
                  className={`text-${activeTab === 'traditional' ? 'primary' : 'dark'}`}
                  style={{
                    borderBottom: activeTab === 'traditional' ? '2px solid var(--primary-color)' : 'none'
                  }}
                >
                  Traditional API
                </Nav.Link>
              </Nav.Item>
              <Nav.Item>
                <Nav.Link 
                  eventKey="linq" 
                  className={`text-${activeTab === 'linq' ? 'primary' : 'dark'}`}
                  style={{
                    borderBottom: activeTab === 'linq' ? '2px solid var(--primary-color)' : 'none'
                  }}
                >
                  Linq Protocol
                </Nav.Link>
              </Nav.Item>
            </Nav>

            <Tab.Content>
              <Tab.Pane eventKey="traditional">
                {/* Traditional API View */}
                {Object.entries(endpoints).map(([tag, tagEndpoints], tagIndex) => (
                  <div key={tagIndex} className="mb-4">
                    <h3 className="tag-header">{tag}</h3>
                    <Accordion>
                      {tagEndpoints.map((endpoint, index) => (
                        <Accordion.Item key={index} eventKey={`${tagIndex}-${index}`}>
                          <Accordion.Header>
                            <div className="endpoint-header-content">
                              <div className="endpoint-title">
                                <Badge bg={getMethodBadgeColor(endpoint.method)} className="endpoint-method-badge">
                                  {endpoint.method}
                                </Badge>
                                <span className="endpoint-summary">{endpoint.summary}</span>
                                <code className="endpoint-path-inline">{endpoint.path}</code>
                              </div>
                            </div>
                          </Accordion.Header>
                          <Accordion.Body>
                            <div className="endpoint-content">
                              <div className="endpoint-url">
                                <code>
                                  <span className="method-type">{endpoint.method}</span> {endpoint.path}
                                </code>
                                <button 
                                  className="copy-button"
                                  onClick={() => copyToClipboard(endpoint.path)}
                                >
                                  <FaCopy /> <span className="button-text">Copy URL</span>
                                </button>
                              </div>

                              {endpoint.parameters && endpoint.parameters.length > 0 && (
                                <div className="parameters-section">
                                  <h6>Parameters</h6>
                                  <div className="parameters-table">
                                    <table className="table table-bordered">
                                      <thead>
                                        <tr>
                                          <th>Name</th>
                                          <th>Location</th>
                                          <th>Type</th>
                                          <th>Required</th>
                                          <th>Description</th>
                                        </tr>
                                      </thead>
                                      <tbody>
                                        {endpoint.parameters.map((param, idx) => (
                                          <tr key={idx}>
                                            <td>{param.name}</td>
                                            <td>{param.in}</td>
                                            <td>{param.schema.type}</td>
                                            <td>{param.required ? 'Yes' : 'No'}</td>
                                            <td>{param.description}</td>
                                          </tr>
                                        ))}
                                      </tbody>
                                    </table>
                                  </div>
                                </div>
                              )}

                              {endpoint.requestBody && (
                                <div className="request-section">
                                  <h6>Request Body</h6>
                                  <div className="schema-section">
                                    <h6>Schema</h6>
                                    <SyntaxHighlighter language="json" style={dracula}>
                                      {JSON.stringify({
                                        type: "object",
                                        properties: endpoint.requestBody.content['application/json']?.schema?.properties || {}
                                      }, null, 2)}
                                    </SyntaxHighlighter>
                                  </div>
                                  {endpoint.requestBody.content['application/json']?.example && (
                                    <div className="example-section">
                                      <h6>Example</h6>
                                      <SyntaxHighlighter language="json" style={dracula}>
                                        {JSON.stringify(endpoint.requestBody.content['application/json'].example, null, 2)}
                                      </SyntaxHighlighter>
                                    </div>
                                  )}
                                </div>
                              )}

                              <div className="response-section">
                                <h6>Responses</h6>
                                {Object.entries(endpoint.responses).map(([code, response]) => (
                                  <div key={code} className="response-code-section">
                                    <h6 className="response-code">
                                      <Badge bg={code.startsWith('2') ? 'success' : 'danger'}>
                                        {code}
                                      </Badge>
                                      <span className="response-description">{response.description}</span>
                                    </h6>
                                    {response.content && Object.keys(response.content).length > 0 && (
                                      <>
                                        <div className="schema-section">
                                          <h6>Schema</h6>
                                          <SyntaxHighlighter language="json" style={dracula}>
                                            {JSON.stringify(Object.values(response.content)[0].schema, null, 2)}
                                          </SyntaxHighlighter>
                                        </div>
                                        {Object.values(response.content)[0].example && (
                                          <div className="example-section">
                                            <h6>Example</h6>
                                            <SyntaxHighlighter language="json" style={dracula}>
                                              {JSON.stringify(Object.values(response.content)[0].example, null, 2)}
                                            </SyntaxHighlighter>
                                          </div>
                                        )}
                                      </>
                                    )}
                                  </div>
                                ))}
                              </div>
                            </div>
                          </Accordion.Body>
                        </Accordion.Item>
                      ))}
                    </Accordion>
                  </div>
                ))}
              </Tab.Pane>

              <Tab.Pane eventKey="linq">
                {/* Linq Protocol View */}
                {!isLoadingLinq && linqFormats ? (
                  Object.entries(endpoints).map(([tag, tagEndpoints], tagIndex) => (
                    <div key={tagIndex} className="mb-4">
                      <h3 className="tag-header">{tag}</h3>
                      <Accordion>
                        {tagEndpoints.map((endpoint, index) => {
                          // Find the matching Linq format for this endpoint
                          const linqFormat = linqFormats[tag]?.find(e => e.summary === endpoint.summary);
                          
                          return (
                            <Accordion.Item key={index} eventKey={`${tagIndex}-${index}`}>
                              <Accordion.Header>
                                <div className="endpoint-header-content">
                                  <div className="endpoint-title">
                                    <Badge bg={getMethodBadgeColor(endpoint.method)} className="endpoint-method-badge">
                                      {endpoint.method}
                                    </Badge>
                                    <span className="endpoint-summary">{endpoint.summary}</span>
                                    <code className="endpoint-path-inline">{endpoint.path}</code>
                                  </div>
                                </div>
                              </Accordion.Header>
                              <Accordion.Body>
                                <div className="endpoint-content">
                                  <div className="endpoint-url">
                                    <code>POST /linq</code>
                                    <button 
                                      className="copy-button"
                                      onClick={() => copyToClipboard('/linq')}
                                    >
                                      <FaCopy /> <span className="button-text">Copy URL</span>
                                    </button>
                                  </div>

                                  <div className="headers-section">
                                    <h6>Headers</h6>
                                    <SyntaxHighlighter language="json" style={dracula}>
                                      {JSON.stringify({
                                        "X-API-Key": "YOUR_API_KEY",
                                        "X-API-Key-Name": "My Api Key",
                                        "Content-Type": "application/json"
                                      }, null, 2)}
                                    </SyntaxHighlighter>
                                  </div>

                                  {linqFormat ? (
                                    <>
                                      <div className="request-section">
                                        <h6>Linq Protocol Request</h6>
                                        <SyntaxHighlighter language="json" style={dracula}>
                                          {JSON.stringify(linqFormat.request, null, 2)}
                                        </SyntaxHighlighter>
                                      </div>

                                      <div className="response-section">
                                        <h6>Example Response</h6>
                                        <SyntaxHighlighter language="json" style={dracula}>
                                          {JSON.stringify(linqFormat.response, null, 2)}
                                        </SyntaxHighlighter>
                                      </div>
                                    </>
                                  ) : (
                                    <div className="loading">No Linq format available for this endpoint</div>
                                  )}
                                </div>
                              </Accordion.Body>
                            </Accordion.Item>
                          );
                        })}
                      </Accordion>
                    </div>
                  ))
                ) : (
                  <div className="loading">Loading Linq formats...</div>
                )}
              </Tab.Pane>
            </Tab.Content>
          </Tab.Container>
        )}
      </div>

      <CreateEndpointModal
        show={showModal}
        onHide={() => setShowModal(false)}
        routeIdentifier={route.routeIdentifier}
      />

      <ViewSwaggerJsonModal
        show={showJsonModal}
        onHide={() => setShowJsonModal(false)}
        json={currentEndpoint?.swaggerJson}
        endpoint={currentEndpoint}
        onSave={async (updatedJson) => {
          try {
            const extracted = await apiEndpointService.extractSwaggerInfo(updatedJson);
            setEndpoints(extracted.endpointsByTag);
            setHasChanges(true);
          } catch (error) {
            console.error('Error updating Swagger JSON:', error);
            showErrorToast(`Failed to update Swagger JSON: ${error.message}`);
          }
        }}
        onVersionCreated={fetchEndpoints}
      />
    </div>
  );
};

export default RouteEndpoints; 