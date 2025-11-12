import React from 'react';
import PropTypes from 'prop-types';
import { Modal, Form } from 'react-bootstrap';
import Button from '../common/Button';

const ExecuteAgentWithQuestionModal = ({
  show,
  onHide,
  question,
  onQuestionChange,
  onExecute,
  executing
}) => (
  <Modal
    show={show}
    onHide={onHide}
    centered
  >
    <Modal.Header closeButton>
      <Modal.Title>Execute with Question</Modal.Title>
    </Modal.Header>
    <Modal.Body>
      <Form>
        <Form.Group className="mb-3">
          <Form.Label>Question</Form.Label>
          <Form.Control
            as="textarea"
            rows={4}
            value={question}
            onChange={onQuestionChange}
            placeholder="Enter the question to send to the agent"
            autoFocus
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
        onClick={onExecute}
        disabled={executing || !(question ?? '').trim()}
      >
        {executing ? 'Executing...' : 'Execute'}
      </Button>
    </Modal.Footer>
  </Modal>
);

ExecuteAgentWithQuestionModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onHide: PropTypes.func.isRequired,
  question: PropTypes.string.isRequired,
  onQuestionChange: PropTypes.func.isRequired,
  onExecute: PropTypes.func.isRequired,
  executing: PropTypes.bool
};

ExecuteAgentWithQuestionModal.defaultProps = {
  executing: false
};

export default ExecuteAgentWithQuestionModal;
