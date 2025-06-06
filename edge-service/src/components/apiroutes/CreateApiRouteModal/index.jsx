import React, { useState, useEffect, useRef } from 'react';
import { Modal, Form, Spinner, OverlayTrigger, Tooltip } from 'react-bootstrap';
import Select from 'react-select';
import { HiQuestionMarkCircle } from 'react-icons/hi';
import Button from '../../common/Button';
import './styles.css';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { isSuperAdmin } from '../../../utils/roleUtils';

// Move constant outside of component
const AVAILABLE_METHODS = [
  { value: 'GET', label: 'GET', color: '#61affe' },
  { value: 'POST', label: 'POST', color: '#49cc90' },
  { value: 'PUT', label: 'PUT', color: '#fca130' },
  { value: 'DELETE', label: 'DELETE', color: '#f93e3e' },
  { value: 'PATCH', label: 'PATCH', color: '#50e3c2' },
  { value: 'HEAD', label: 'HEAD', color: '#9012fe' },
  { value: 'OPTIONS', label: 'OPTIONS', color: '#0d5aa7' }
];

const CreateApiRouteModal = ({ show, onHide, onSubmit }) => {
  const { currentTeam, teams, userTeams } = useTeam();
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [validated, setValidated] = useState(false);
  const [showMethodDropdown, setShowMethodDropdown] = useState(false);
  const methodDropdownRef = useRef(null);

  // Initialize formData with the first method (GET)
  const [formData, setFormData] = useState({
    routeIdentifier: '',
    path: '',
    uri: '',
    scope: '',
    methods: [AVAILABLE_METHODS[0]], // Default to GET
    teamId: currentTeam?.id
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    if (name === 'routeIdentifier') {
      setFormData(prev => ({
        ...prev,
        routeIdentifier: value,
        uri: `lb://${value}`,
        path: `/r/${value}/**`,
        scope: `${value}.read`
      }));
      return;
    }
    
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleMethodChange = (selectedOptions) => {
    setFormData(prev => ({
      ...prev,
      methods: selectedOptions
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    
    if (!form.checkValidity()) {
      e.stopPropagation();
      setValidated(true);
      return;
    }

    setLoading(true);
    try {
      // Transform the formData to extract just the method values
      const submissionData = {
        ...formData,
        methods: formData.methods.map(method => method.value) // Extract just the method names
      };
      
      await onSubmit(submissionData);
      onHide();
    } catch (error) {
      console.error('Error creating route:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (methodDropdownRef.current && 
          !methodDropdownRef.current.contains(event.target) && 
          showMethodDropdown) {
        setShowMethodDropdown(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [showMethodDropdown]);

  const getTeamsByOrganization = () => {
    console.log('user:', user);
    console.log('isSuperAdmin:', isSuperAdmin(user));
    console.log('teams from context:', teams);
    console.log('userTeams from context:', userTeams);
    
    const teamsToShow = isSuperAdmin(user) ? teams : userTeams;
    console.log('teamsToShow', teamsToShow);
    
    if (!teamsToShow) return [];
    
    const groupedTeams = teamsToShow.reduce((acc, team) => {
      const orgName = team.organization?.name || 'Unassigned';
      if (!acc[orgName]) {
        acc[orgName] = [];
      }
      acc[orgName].push(team);
      return acc;
    }, {});

    return Object.entries(groupedTeams).map(([orgName, orgTeams]) => ({
      organization: orgName,
      teams: orgTeams
    }));
  };

  const isFormValid = () => {
    return (
      formData.teamId &&
      formData.routeIdentifier?.trim() &&
      formData.path?.trim() &&
      formData.uri?.trim() &&
      formData.scope?.trim()
    );
  };

  const customStyles = {
    option: (provided, state) => ({
      ...provided,
      backgroundColor: state.isSelected ? state.data.color : state.isFocused ? `${state.data.color}22` : 'white',
      color: state.isSelected ? 'white' : state.data.color,
      ':active': {
        backgroundColor: state.data.color,
        color: 'white'
      }
    }),
    multiValue: (provided, state) => ({
      ...provided,
      backgroundColor: `${state.data.color}22`,
    }),
    multiValueLabel: (provided, state) => ({
      ...provided,
      color: state.data.color,
      fontWeight: 'bold'
    }),
    multiValueRemove: (provided, state) => ({
      ...provided,
      color: state.data.color,
      ':hover': {
        backgroundColor: state.data.color,
        color: 'white'
      }
    })
  };

  return (
    <Modal show={show} onHide={onHide} centered>
      <Modal.Header closeButton>
        <Modal.Title>Create New API Route</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form noValidate validated={validated} onSubmit={handleSubmit}>
          <Form.Group className="mb-3">
            <Form.Label>Select Team</Form.Label>
            <Form.Select
              name="teamId"
              value={formData.teamId || ''}
              onChange={handleChange}
              required
            >
              <option value="">Select a team...</option>
              {getTeamsByOrganization().map(({ organization, teams }) => (
                <optgroup key={organization} label={organization}>
                  {teams.map(team => (
                    <option key={team.id} value={team.id}>
                      {team.name}
                    </option>
                  ))}
                </optgroup>
              ))}
            </Form.Select>
            <Form.Control.Feedback type="invalid">
              Please select a team
            </Form.Control.Feedback>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label className="d-flex align-items-center gap-2">
              HTTP Methods
              <OverlayTrigger
                placement="top"
                overlay={
                  <Tooltip>
                    Select one or multiple HTTP methods that this route will support.
                  </Tooltip>
                }
              >
                <span className="scope-help-icon">
                  <HiQuestionMarkCircle />
                </span>
              </OverlayTrigger>
            </Form.Label>
            <Select
              isMulti
              name="methods"
              options={AVAILABLE_METHODS}
              value={formData.methods}
              onChange={handleMethodChange}
              styles={customStyles}
              className="basic-multi-select"
              classNamePrefix="select"
              placeholder="Select HTTP methods..."
              closeMenuOnSelect={false}
            />
            {formData.methods.length === 0 && (
              <div className="text-danger small mt-1">
                Please select at least one HTTP method
              </div>
            )}
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label className="d-flex align-items-center gap-2">
              Route Identifier
              <OverlayTrigger
                placement="top"
                overlay={
                  <Tooltip>
                    This identifier must be unique and cannot be modified after creation. 
                    It will be used as a permanent reference for this route.
                    <br /><br />
                    Examples:
                    <br />• inventory-service
                    <br />• product-catalog
                    <br />• order-management
                    <br />• analytics-app
                    <br />• reporting-dashboard
                  </Tooltip>
                }
              >
                <span className="scope-help-icon">
                  <HiQuestionMarkCircle />
                </span>
              </OverlayTrigger>
            </Form.Label>
            <Form.Control
              type="text"
              name="routeIdentifier"
              value={formData.routeIdentifier}
              onChange={handleChange}
              placeholder="inventory-service"
              required
            />
            <Form.Control.Feedback type="invalid">
              Please provide a route identifier
            </Form.Control.Feedback>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label className="d-flex align-items-center gap-2">
              Path
              <OverlayTrigger
                placement="top"
                overlay={
                  <Tooltip>
                    Choose the path carefully as it cannot be modified later. 
                    This is the URL path that users and services will use to access your API.
                    <br /><br />
                    Examples for "inventory-service":
                    <br />• /inventory/** - matches all inventory paths
                    <br />• /inventory/api/** - matches all paths under /inventory/api
                  </Tooltip>
                }
              >
                <span className="scope-help-icon">
                  <HiQuestionMarkCircle />
                </span>
              </OverlayTrigger>
            </Form.Label>
            <Form.Control
              type="text"
              name="path"
              value={formData.path}
              onChange={handleChange}
              placeholder="/r/inventory-service/**"
              required
            />
            <Form.Control.Feedback type="invalid">
              Please provide a valid path
            </Form.Control.Feedback>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>URI</Form.Label>
            <Form.Control
              type="text"
              name="uri"
              value={formData.uri}
              onChange={handleChange}
              placeholder="lb://service-name"
              required
              readOnly
            />
            <Form.Control.Feedback type="invalid">
              Please provide a valid URI
            </Form.Control.Feedback>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label className="d-flex align-items-center gap-2">
              Scope
              <OverlayTrigger
                placement="top"
                trigger={['hover', 'focus']}
                rootClose
                delay={{ show: 0, hide: 3000 }}
                overlay={props => (
                  <Tooltip 
                    {...props} 
                    className="persistent-tooltip"
                  >
                    This must be configured as a Client Scope in your identity provider (e.g., Keycloak)
                    and assigned to your OpenID Client. Client Scopes define the level of access
                    that will be included in the access token for this route.
                    <br /><br />
                    See how to configure Client Scopes in Keycloak:
                    <br />
                    <a 
                      href="https://github.com/mehmetsen80/LiteMeshApp/blob/master/KEYCLOAK.md#adding-client-scopes" 
                      target="_blank" 
                      rel="noopener noreferrer"
                    >
                      How to Configure and Assign Client Scopes
                    </a>
                  </Tooltip>
                )}
              >
                <span className="scope-help-icon">
                  <HiQuestionMarkCircle />
                </span>
              </OverlayTrigger>
            </Form.Label>
            <Form.Control
              type="text"
              name="scope"
              value={formData.scope}
              onChange={handleChange}
              placeholder={`${formData.routeIdentifier || 'service'}.read`}
              required
            />
            <Form.Control.Feedback type="invalid">
              Please provide a valid scope
            </Form.Control.Feedback>
          </Form.Group>
        </Form>
      </Modal.Body>
      <Modal.Footer className="d-flex justify-content-between">
        <div>
          <Button variant="secondary" onClick={onHide}>
            Cancel
          </Button>
        </div>
        <div>
          <Button 
            variant="primary" 
            type="submit" 
            disabled={loading || !isFormValid()} 
            onClick={handleSubmit}
          >
            {loading ? (
              <>
                <Spinner
                  as="span"
                  animation="border"
                  size="sm"
                  role="status"
                  aria-hidden="true"
                  className="me-2"
                />
                Creating...
              </>
            ) : (
              'Create API Route'
            )}
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
};

export default CreateApiRouteModal; 