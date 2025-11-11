import React from 'react';
import { Modal, Form, Row, Col } from 'react-bootstrap';
import Button from '../common/Button';

const CreateAgentTaskModal = ({
  show,
  onHide,
  onChange,
  onCreate,
  newTask,
  creating
}) => (
  <Modal show={show} onHide={onHide} centered>
    <Modal.Header closeButton>
      <Modal.Title>Create New Agent Task</Modal.Title>
    </Modal.Header>
    <Modal.Body>
      <Form>
        <Form.Group className="mb-3">
          <Form.Label>
            Name <span className="text-danger">*</span>
          </Form.Label>
          <Form.Control
            type="text"
            name="name"
            value={newTask.name}
            onChange={onChange}
            placeholder="Enter task name"
          />
        </Form.Group>

        <Form.Group className="mb-3">
          <Form.Label>
            Description <span className="text-danger">*</span>
          </Form.Label>
          <Form.Control
            as="textarea"
            name="description"
            value={newTask.description}
            onChange={onChange}
            placeholder="Enter task description"
            rows={3}
          />
        </Form.Group>

        <Form.Group className="mb-3">
          <Form.Label>Task Type</Form.Label>
          <Form.Select
            name="taskType"
            value={newTask.taskType}
            onChange={onChange}
          >
            <option value="WORKFLOW_EMBEDDED">Workflow Embedded</option>
          </Form.Select>
          <Form.Text className="text-muted">
            Steps defined inline. More task types coming soon.
          </Form.Text>
        </Form.Group>

        <Row className="mb-3">
          <Col md={4}>
            <Form.Group>
              <Form.Label>Priority</Form.Label>
              <Form.Control
                type="number"
                name="priority"
                value={newTask.priority}
                onChange={onChange}
                min="1"
                max="10"
              />
            </Form.Group>
          </Col>
          <Col md={4}>
            <Form.Group>
              <Form.Label>Max Retries</Form.Label>
              <Form.Control
                type="number"
                name="maxRetries"
                value={newTask.maxRetries}
                onChange={onChange}
                min="0"
              />
            </Form.Group>
          </Col>
          <Col md={4}>
            <Form.Group>
              <Form.Label>Timeout (minutes)</Form.Label>
              <Form.Control
                type="number"
                name="timeoutMinutes"
                value={newTask.timeoutMinutes}
                onChange={onChange}
                min="1"
              />
            </Form.Group>
          </Col>
        </Row>
      </Form>
    </Modal.Body>
    <Modal.Footer>
      <Button variant="secondary" onClick={onHide}>
        Cancel
      </Button>
      <Button
        variant="primary"
        onClick={onCreate}
        disabled={creating || !newTask.name || !newTask.description}
      >
        {creating ? 'Creating...' : 'Create New Agent Task'}
      </Button>
    </Modal.Footer>
  </Modal>
);

export default CreateAgentTaskModal;

