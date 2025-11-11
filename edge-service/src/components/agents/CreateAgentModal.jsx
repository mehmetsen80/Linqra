import React from 'react';
import { Modal, Form } from 'react-bootstrap';
import Select from 'react-select';
import Button from '../common/Button';
import { intentOptions, capabilityOptions, customSelectStyles } from './EditAgentModal';

const CreateAgentModal = ({
  show,
  onHide,
  newAgent,
  onChange,
  onMultiSelectChange,
  onCreate,
  creating
}) => (
  <Modal
    show={show}
    onHide={onHide}
    centered
    size="lg"
  >
    <Modal.Header closeButton>
      <Modal.Title>Create New Agent</Modal.Title>
    </Modal.Header>
    <Modal.Body>
      <Form>
        <Form.Group className="mb-3">
          <Form.Label>Name <span className="text-danger">*</span></Form.Label>
          <Form.Control
            type="text"
            name="name"
            value={newAgent.name}
            onChange={onChange}
            placeholder="Enter agent name"
          />
        </Form.Group>

        <Form.Group className="mb-3">
          <Form.Label>Description <span className="text-danger">*</span></Form.Label>
          <Form.Control
            as="textarea"
            name="description"
            value={newAgent.description}
            onChange={onChange}
            placeholder="Enter agent description"
            rows={3}
          />
        </Form.Group>

        <Form.Group className="mb-3">
          <Form.Label>Supported Intents</Form.Label>
          <Select
            isMulti
            name="supportedIntents"
            options={intentOptions}
            value={intentOptions.filter(option => newAgent.supportedIntents?.includes(option.value))}
            onChange={(selected) => onMultiSelectChange('supportedIntents', selected)}
            styles={customSelectStyles}
            classNamePrefix="select"
            placeholder="Select supported intents..."
            closeMenuOnSelect={false}
            menuPortalTarget={document.body}
          />
        </Form.Group>

        <Form.Group className="mb-3">
          <Form.Label>Capabilities</Form.Label>
          <Select
            isMulti
            name="capabilities"
            options={capabilityOptions}
            value={capabilityOptions.filter(option => newAgent.capabilities?.includes(option.value))}
            onChange={(selected) => onMultiSelectChange('capabilities', selected)}
            styles={customSelectStyles}
            classNamePrefix="select"
            placeholder="Select capabilities..."
            closeMenuOnSelect={false}
            menuPortalTarget={document.body}
          />
        </Form.Group>
      </Form>
    </Modal.Body>
    <Modal.Footer>
      <Button variant="secondary" onClick={onHide}>
        Cancel
      </Button>
      <Button
        variant="primary"
        onClick={onCreate}
        disabled={creating || !newAgent.name || !newAgent.description}
      >
        {creating ? 'Creating...' : 'Create Agent'}
      </Button>
    </Modal.Footer>
  </Modal>
);

export default CreateAgentModal;

