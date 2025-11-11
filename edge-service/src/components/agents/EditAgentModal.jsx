import React from 'react';
import { Modal, Form } from 'react-bootstrap';
import Select from 'react-select';
import Button from '../common/Button';

export const intentOptions = [
  { value: 'MONGODB_READ', label: 'MongoDB Read' },
  { value: 'MONGODB_WRITE', label: 'MongoDB Write' },
  { value: 'MILVUS_READ', label: 'Milvus Read' },
  { value: 'MILVUS_WRITE', label: 'Milvus Write' },
  { value: 'LLM_ANALYSIS', label: 'LLM Analysis' },
  { value: 'LLM_GENERATION', label: 'LLM Generation' },
  { value: 'API_INTEGRATION', label: 'API Integration' },
  { value: 'WORKFLOW_ORCHESTRATION', label: 'Workflow Orchestration' },
  { value: 'DATA_TRANSFORMATION', label: 'Data Transformation' },
  { value: 'NOTIFICATION_SENDING', label: 'Notification Sending' },
  { value: 'FILE_PROCESSING', label: 'File Processing' },
  { value: 'MONITORING', label: 'Monitoring' },
  { value: 'REPORTING', label: 'Reporting' },
  { value: 'SCHEDULING', label: 'Scheduling' },
  { value: 'EVENT_HANDLING', label: 'Event Handling' }
];

export const capabilityOptions = [
  { value: 'MONGODB_ACCESS', label: 'MongoDB Access' },
  { value: 'MILVUS_ACCESS', label: 'Milvus Access' },
  { value: 'LLM_INTEGRATION', label: 'LLM Integration' },
  { value: 'HTTP_CLIENT', label: 'HTTP Client' },
  { value: 'FILE_SYSTEM_ACCESS', label: 'File System Access' },
  { value: 'EMAIL_SENDING', label: 'Email Sending' },
  { value: 'SMS_SENDING', label: 'SMS Sending' },
  { value: 'SLACK_INTEGRATION', label: 'Slack Integration' },
  { value: 'WEBHOOK_HANDLING', label: 'Webhook Handling' },
  { value: 'CRON_SCHEDULING', label: 'Cron Scheduling' },
  { value: 'EVENT_STREAMING', label: 'Event Streaming' },
  { value: 'DATA_ENCRYPTION', label: 'Data Encryption' },
  { value: 'IMAGE_PROCESSING', label: 'Image Processing' },
  { value: 'PDF_PROCESSING', label: 'PDF Processing' },
  { value: 'JSON_PROCESSING', label: 'JSON Processing' },
  { value: 'XML_PROCESSING', label: 'XML Processing' },
  { value: 'CSV_PROCESSING', label: 'CSV Processing' },
  { value: 'TEMPLATE_RENDERING', label: 'Template Rendering' },
  { value: 'METRICS_COLLECTION', label: 'Metrics Collection' },
  { value: 'LOG_ANALYSIS', label: 'Log Analysis' },
  { value: 'BACKUP_OPERATIONS', label: 'Backup Operations' },
  { value: 'CACHE_MANAGEMENT', label: 'Cache Management' }
];

export const customSelectStyles = {
  menuPortal: base => ({ ...base, zIndex: 9999 }),
  control: (base, state) => ({
    ...base,
    borderColor: state.isFocused ? '#0d6efd' : base.borderColor,
    boxShadow: state.isFocused ? '0 0 0 0.25rem rgba(13,110,253,.25)' : base.boxShadow
  })
};

const EditAgentModal = ({
  show,
  onHide,
  onSave,
  onChange,
  onMultiSelectChange,
  editedAgent,
  saving
}) => (
  <Modal
    show={show}
    onHide={onHide}
    centered
    size="lg"
  >
    <Modal.Header closeButton>
      <Modal.Title>Edit Agent</Modal.Title>
    </Modal.Header>
    <Modal.Body>
      {editedAgent && (
        <Form>
          <Form.Group className="mb-3">
            <Form.Label>Name <span className="text-danger">*</span></Form.Label>
            <Form.Control
              type="text"
              name="name"
              value={editedAgent.name || ''}
              onChange={onChange}
              placeholder="Enter agent name"
            />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Description <span className="text-danger">*</span></Form.Label>
            <Form.Control
              as="textarea"
              name="description"
              value={editedAgent.description || ''}
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
              value={intentOptions.filter(option => editedAgent.supportedIntents?.includes(option.value))}
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
              value={capabilityOptions.filter(option => editedAgent.capabilities?.includes(option.value))}
              onChange={(selected) => onMultiSelectChange('capabilities', selected)}
              styles={customSelectStyles}
              classNamePrefix="select"
              placeholder="Select capabilities..."
              closeMenuOnSelect={false}
              menuPortalTarget={document.body}
            />
          </Form.Group>
        </Form>
      )}
    </Modal.Body>
    <Modal.Footer>
      <Button variant="secondary" onClick={onHide}>
        Cancel
      </Button>
      <Button
        variant="primary"
        onClick={onSave}
        disabled={saving || !editedAgent?.name || !editedAgent?.description}
      >
        {saving ? 'Saving...' : 'Save Changes'}
      </Button>
    </Modal.Footer>
  </Modal>
);

export default EditAgentModal;

