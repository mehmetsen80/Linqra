import React, { useState, useEffect, forwardRef, useImperativeHandle } from 'react';
import { Dropdown, Modal } from 'react-bootstrap';
import { apiEndpointService } from '../../../services/apiEndpointService';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import Button from '../../common/Button';
import './styles.css';

const ApiEndpointVersionControl = forwardRef(({ routeIdentifier, currentVersion, onVersionChange }, ref) => {
  const [versions, setVersions] = useState([]);
  const [latestVersion, setLatestVersion] = useState(null);
  const [showCompareModal, setShowCompareModal] = useState(false);
  const [compareVersions, setCompareVersions] = useState({ version1: '', version2: '' });
  const [comparison, setComparison] = useState(null);
  const [endpointChanges, setEndpointChanges] = useState(null);

  useImperativeHandle(ref, () => ({
    clearChanges: () => {
      setEndpointChanges(null);
    }
  }));

  useEffect(() => {
    console.log('Fetching versions with routeIdentifier:', routeIdentifier);
    fetchVersions();
  }, [routeIdentifier]);

  const fetchVersions = async () => {
    try {
      console.log('Fetching versions for route:', routeIdentifier);
      const data = await apiEndpointService.getEndpointVersions(routeIdentifier);
      console.log('Raw version data:', data);
      
      if (data && data.length > 0) {
        const sortedVersions = data.sort((a, b) => b.version - a.version);
        console.log('Sorted versions:', sortedVersions);
        setVersions(sortedVersions);
        setLatestVersion(sortedVersions[0]?.version);
      } else {
        console.log('No versions found for route:', routeIdentifier);
      }
    } catch (error) {
      console.error('Failed to fetch versions:', error);
    }
  };

  const getFilteredVersions = () => {
    return versions;  // Return all versions, no filtering
  };

  const handleVersionSelect = async (version) => {
    try {
      console.log('Selecting version:', version);
      const selectedData = await apiEndpointService.getEndpointVersion(routeIdentifier, version);
      console.log('Selected version data:', selectedData);
      
      // Compare with latest version
      if (versions[0] && version !== versions[0].version) {
        const latestVersion = versions[0];
        
        // Extract Swagger info for both versions
        const selectedSwagger = await apiEndpointService.extractSwaggerInfo(selectedData.swaggerJson);
        const latestSwagger = await apiEndpointService.extractSwaggerInfo(latestVersion.swaggerJson);

        // Compare the endpoints and schemas
        const changes = {
          endpoints: compareEndpoints(selectedSwagger.endpointsByTag, latestSwagger.endpointsByTag),
          schemas: compareObjects(selectedSwagger.schemas, latestSwagger.schemas)
        };
        
        console.log('Version comparison:', changes);
        setEndpointChanges(changes);
      } else {
        // If selecting latest version, clear the changes
        setEndpointChanges(null);
      }

      onVersionChange(selectedData);
    } catch (error) {
      console.error('Failed to fetch version:', error);
    }
  };

  const compareEndpoints = (current, latest) => {
    const changes = {
      added: [],
      removed: [],
      modified: []
    };

    // Compare endpoints
    Object.keys(current).forEach(tag => {
      current[tag].forEach(endpoint => {
        const latestEndpoint = latest[tag]?.find(e => 
          e.path === endpoint.path && e.method === endpoint.method
        );

        if (!latestEndpoint) {
          changes.added.push({ tag, ...endpoint });
        } else if (JSON.stringify(endpoint) !== JSON.stringify(latestEndpoint)) {
          changes.modified.push({
            tag,
            path: endpoint.path,
            method: endpoint.method,
            changes: compareEndpointDetails(endpoint, latestEndpoint)
          });
        }
      });
    });

    // Check for removed endpoints
    Object.keys(latest).forEach(tag => {
      latest[tag].forEach(endpoint => {
        const currentEndpoint = current[tag]?.find(e => 
          e.path === endpoint.path && e.method === endpoint.method
        );
        if (!currentEndpoint) {
          changes.removed.push({ tag, ...endpoint });
        }
      });
    });

    return changes;
  };

  const compareEndpointDetails = (current, latest) => {
    return {
      parameters: compareArrays(current.parameters, latest.parameters),
      responses: compareObjects(current.responses, latest.responses),
      requestBody: compareObjects(current.requestBody, latest.requestBody)
    };
  };

  const handleCompare = async () => {
    try {
      const version1Data = await apiEndpointService.getEndpointVersion(routeIdentifier, compareVersions.version1);
      const version2Data = await apiEndpointService.getEndpointVersion(routeIdentifier, compareVersions.version2);

      // Extract and compare Swagger JSON
      const v1Swagger = JSON.parse(version1Data.swaggerJson);
      const v2Swagger = JSON.parse(version2Data.swaggerJson);

      const differences = {
        paths: compareObjects(v1Swagger.paths, v2Swagger.paths),
        schemas: compareObjects(v1Swagger.components?.schemas, v2Swagger.components?.schemas),
        version: compareObjects(v1Swagger.info?.version, v2Swagger.info?.version),
        servers: compareArrays(v1Swagger.servers, v2Swagger.servers)
      };

      setComparison(differences);
    } catch (error) {
      console.error('Failed to compare versions:', error);
    }
  };

  const compareObjects = (obj1, obj2) => {
    const changes = {
      added: {},
      removed: {},
      modified: {}
    };

    // Check for added and modified
    Object.keys(obj2 || {}).forEach(key => {
      if (!(key in (obj1 || {}))) {
        changes.added[key] = obj2[key];
      } else if (JSON.stringify(obj1[key]) !== JSON.stringify(obj2[key])) {
        changes.modified[key] = {
          from: obj1[key],
          to: obj2[key]
        };
      }
    });

    // Check for removed
    Object.keys(obj1 || {}).forEach(key => {
      if (!(key in (obj2 || {}))) {
        changes.removed[key] = obj1[key];
      }
    });

    return changes;
  };

  const compareArrays = (arr1, arr2) => {
    const changes = {
      added: [],
      removed: []
    };

    arr2?.forEach(item => {
      if (!arr1?.some(i => JSON.stringify(i) === JSON.stringify(item))) {
        changes.added.push(item);
      }
    });

    arr1?.forEach(item => {
      if (!arr2?.some(i => JSON.stringify(i) === JSON.stringify(item))) {
        changes.removed.push(item);
      }
    });

    return changes;
  };

  console.log('Current versions state:', versions);
  console.log('Current latestVersion:', latestVersion);
  console.log('Filtered versions:', getFilteredVersions());

  return (
    <div className="version-control">
      <h2 className="section-title">Endpoint Version Control</h2>
      <div className="version-control-container">
        <div className="version-actions">
          <Dropdown>
            <Dropdown.Toggle variant="secondary">
              {currentVersion ? `Version ${currentVersion}` : `Latest Version (${latestVersion})`}
            </Dropdown.Toggle>
            <Dropdown.Menu>
              <Dropdown.Item 
                key="latest"
                onClick={() => handleVersionSelect('latest')}
                className={!currentVersion ? 'current-version' : ''}
              >
                Latest Version ({latestVersion})
                {!currentVersion && <span className="current-label">(Current)</span>}
              </Dropdown.Item>
              <Dropdown.Divider />
              {versions.map((versionData) => (
                <Dropdown.Item 
                  key={versionData.version} 
                  onClick={() => handleVersionSelect(versionData.version)}
                  className={currentVersion === versionData.version ? 'current-version' : ''}
                >
                  Version {versionData.version} 
                  {currentVersion === versionData.version && <span className="current-label">(Current)</span>}
                </Dropdown.Item>
              ))}
            </Dropdown.Menu>
          </Dropdown>
          
      
        </div>

        {currentVersion !== latestVersion && (
          <div className="version-differences-container">
            <div className="version-differences">
              <h4>Changes from Latest Version:</h4>
              
              {(!endpointChanges || 
                (endpointChanges.endpoints.added.length === 0 && 
                 endpointChanges.endpoints.removed.length === 0 && 
                 endpointChanges.endpoints.modified.length === 0 && 
                 Object.keys(endpointChanges.schemas.added).length === 0 &&
                 Object.keys(endpointChanges.schemas.removed).length === 0 &&
                 Object.keys(endpointChanges.schemas.modified).length === 0)) ? (
                <div className="no-changes">
                  <p>No differences found - versions are identical</p>
                </div>
              ) : (
                <>
                  {/* Endpoint Changes */}
                  {Object.keys(endpointChanges.endpoints).map(changeType => 
                    endpointChanges.endpoints[changeType].length > 0 && (
                      <div key={changeType} className={`change-section ${changeType}`}>
                        <h5>{changeType.charAt(0).toUpperCase() + changeType.slice(1)} Endpoints:</h5>
                        {endpointChanges.endpoints[changeType].map((endpoint, i) => (
                          <div key={i} className="endpoint-change">
                            {endpoint.tag && <span className="tag">{endpoint.tag}</span>}
                            {endpoint.method && <span className="method">{endpoint.method}</span>}
                            {endpoint.path && <span className="path">{endpoint.path}</span>}
                          </div>
                        ))}
                      </div>
                    )
                  )}
                  
                  {/* Schema Changes */}
                  {Object.keys(endpointChanges.schemas).map(changeType => 
                    Object.keys(endpointChanges.schemas[changeType]).length > 0 && (
                      <div key={changeType} className={`change-section ${changeType}`}>
                        <h5>{changeType.charAt(0).toUpperCase() + changeType.slice(1)} Schemas:</h5>
                        {Object.entries(endpointChanges.schemas[changeType]).map(([schemaName, schema], i) => (
                          <div key={i} className="schema-change">
                            <span className="schema-name">{schemaName}</span>
                          </div>
                        ))}
                      </div>
                    )
                  )}
                </>
              )}
            </div>
          </div>
        )}
      </div>


      
    </div>
  );
});

export default ApiEndpointVersionControl; 