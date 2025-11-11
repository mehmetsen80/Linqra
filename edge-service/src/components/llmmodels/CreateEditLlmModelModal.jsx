import React from 'react';
import { Modal, Form, Row, Col } from 'react-bootstrap';
import Button from '../common/Button';

const CreateEditLlmModelModal = ({
  show,
  onHide,
  onClose,
  onSubmit,
  onChange,
  formData,
  editingModel
}) => {
  const handleHide = onHide || onClose;
  const handleClose = onClose || onHide;

  return (
    <Modal show={show} onHide={handleHide} size="lg">
      <Modal.Header closeButton>
        <Modal.Title>
          {editingModel ? 'Edit LLM Model' : 'Add New LLM Model'}
        </Modal.Title>
      </Modal.Header>
      <Form onSubmit={onSubmit}>
        <Modal.Body>
          <Row>
            <Col md={6}>
              <Form.Group className="mb-3">
                <Form.Label>Model Name *</Form.Label>
                <Form.Control
                  type="text"
                  name="modelName"
                  value={formData.modelName}
                  onChange={onChange}
                  placeholder="e.g., gpt-4o, gemini-2.0-flash"
                  required
                  disabled={!!editingModel}
                />
                <Form.Text className="text-muted">
                  Unique identifier for the model (cannot be changed after creation)
                </Form.Text>
              </Form.Group>
            </Col>
            <Col md={6}>
              <Form.Group className="mb-3">
                <Form.Label>Display Name</Form.Label>
                <Form.Control
                  type="text"
                  name="displayName"
                  value={formData.displayName}
                  onChange={onChange}
                  placeholder="e.g., GPT-4 Optimized"
                />
              </Form.Group>
            </Col>
          </Row>

          <Row>
            <Col md={6}>
              <Form.Group className="mb-3">
                <Form.Label>Provider *</Form.Label>
                <Form.Select
                  name="provider"
                  value={formData.provider}
                  onChange={onChange}
                  required
                >
                  <option value="openai">OpenAI</option>
                  <option value="gemini">Google Gemini</option>
                  <option value="anthropic">Anthropic</option>
                  <option value="cohere">Cohere</option>
                  <option value="other">Other</option>
                </Form.Select>
              </Form.Group>
            </Col>
            <Col md={6}>
              <Form.Group className="mb-3">
                <Form.Label>Category *</Form.Label>
                <Form.Select
                  name="category"
                  value={formData.category}
                  onChange={onChange}
                  required
                >
                  <option value="chat">Chat/Completion</option>
                  <option value="embedding">Embedding</option>
                  <option value="vision">Vision</option>
                  <option value="audio">Audio</option>
                  <option value="other">Other</option>
                </Form.Select>
              </Form.Group>
            </Col>
          </Row>

          <Form.Group className="mb-3">
            <Form.Label>API Endpoint</Form.Label>
            <Form.Control
              type="text"
              name="endpoint"
              value={formData.endpoint}
              onChange={onChange}
              placeholder="e.g., https://api.openai.com/v1/chat/completions"
            />
            <Form.Text className="text-muted">
              API endpoint URL for this model. Use {'{model}'} as placeholder if needed.
            </Form.Text>
          </Form.Group>

          {formData.category === 'embedding' && (
            <Form.Group className="mb-3">
              <Form.Label>Dimensions</Form.Label>
              <Form.Control
                type="number"
                min="1"
                name="dimensions"
                value={formData.dimensions || ''}
                onChange={onChange}
                placeholder="e.g., 768, 1024, 1536"
              />
              <Form.Text className="text-muted">
                Vector dimensions for embedding models (e.g., 768, 1024, 1536, 3072)
              </Form.Text>
            </Form.Group>
          )}

          <Row>
            <Col md={6}>
              <Form.Group className="mb-3">
                <Form.Label>Input Price per 1M Tokens (USD) *</Form.Label>
                <Form.Control
                  type="number"
                  step="0.00001"
                  min="0"
                  name="inputPricePer1M"
                  value={formData.inputPricePer1M}
                  onChange={onChange}
                  required
                />
                <Form.Text className="text-muted">
                  Price per 1 million input/prompt tokens
                </Form.Text>
              </Form.Group>
            </Col>
            <Col md={6}>
              <Form.Group className="mb-3">
                <Form.Label>Output Price per 1M Tokens (USD) *</Form.Label>
                <Form.Control
                  type="number"
                  step="0.00001"
                  min="0"
                  name="outputPricePer1M"
                  value={formData.outputPricePer1M}
                  onChange={onChange}
                  required
                />
                <Form.Text className="text-muted">
                  Price per 1 million output/completion tokens
                </Form.Text>
              </Form.Group>
            </Col>
          </Row>

          <Form.Group className="mb-3">
            <Form.Label>Context Window Tokens</Form.Label>
            <Form.Control
              type="number"
              min="1"
              name="contextWindowTokens"
              value={formData.contextWindowTokens ?? ''}
              onChange={onChange}
              placeholder="e.g., 8192, 128000"
            />
            <Form.Text className="text-muted">
              Maximum tokens the model can accept in a single request (leave blank if unknown)
            </Form.Text>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Description</Form.Label>
            <Form.Control
              as="textarea"
              rows={2}
              name="description"
              value={formData.description}
              onChange={onChange}
              placeholder="Optional description of the model"
            />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Check
              type="checkbox"
              name="active"
              label="Active (include in cost calculations)"
              checked={formData.active}
              onChange={onChange}
            />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer className="d-flex justify-content-between">
          <div>
            <Button variant="secondary" onClick={handleClose}>
              Cancel
            </Button>
          </div>
          <div>
            <Button variant="primary" type="submit">
              {editingModel ? 'Update Model' : 'Create Model'}
            </Button>
          </div>
        </Modal.Footer>
      </Form>
    </Modal>
  );
};

export default CreateEditLlmModelModal;

