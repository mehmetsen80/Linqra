import React, { useState } from 'react';
import { Modal, Form, Spinner, Alert } from 'react-bootstrap';
import Button from '../common/Button';
import { JsonView, allExpanded, defaultStyles } from 'react-json-view-lite';
import 'react-json-view-lite/dist/index.css';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import workflowService from '../../services/workflowService';

const exampleTemplate = {
    "request": {
        "link": {
            "target": "workflow",
            "action": "execute"
        },
        "query": {
            "intent": "get_historical_saying",
            "workflow": [
                {
                    "step": 1,
                    "target": "quotes-service",
                    "action": "fetch",
                    "intent": "/api/people/random",
                    "params": {},
                    "payload": null,
                    "llmConfig": null,
                    "cacheConfig": {
                        "enabled": false,
                        "ttl": "86400",  // 24 hours in seconds
                        "key": "historical_people_cache"
                    }
                },
                {
                    "step": 2,
                    "target": "openai",
                    "action": "generate",
                    "intent": "generate",
                    "params": {
                        "prompt": "Output only a single inspirational saying by {{step1.result.fullName}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                    },
                    "payload": [
                        {
                            "role": "user",
                            "content": "Output only a single inspirational saying by {{step1.result.fullName}}. Do not include any other text, explanation, or formatting. Do not use quotation marks. Only the saying."
                        }
                    ],
                    "llmConfig": {
                        "model": "gpt-4o",
                        "settings": {
                            "temperature": 0.9,
                            "max_tokens": 200
                        }
                    }
                }
            ]
        }
    }
};

function CreateWorkflowModal({ show, onHide, onSuccess }) {
    const [formData, setFormData] = useState({
        name: '',
        description: '',
        public: false,
        request: JSON.stringify(exampleTemplate, null, 2)
    });
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    const isFormValid = () => {
        return (
            formData.name.trim().length >= 10 &&
            formData.description.trim().length >= 20 &&
            formData.request.trim().length > 0
        );
    };

    const handleInputChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value
        }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);
        setSaving(true);

        try {
            // Parse the request JSON to validate it
            const requestJson = JSON.parse(formData.request);
            
            const workflowData = {
                name: formData.name,
                description: formData.description,
                public: formData.public,
                request: requestJson.request  // Keep the original request structure
            };

            const response = await workflowService.createWorkflow(workflowData);
            
            if (response.success) {
                showSuccessToast('Workflow created successfully. You can edit it anytime from the workflows list.');
                onSuccess();
                onHide();
            } else {
                setError(response.error || 'Failed to create workflow');
                showErrorToast(response.error || 'Failed to create workflow');
            }
        } catch (err) {
            const errorMessage = err.message || 'Failed to create workflow';
            setError(errorMessage);
            showErrorToast(errorMessage);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal show={show} onHide={onHide} size="lg" centered>
            <Modal.Header closeButton>
                <Modal.Title>Create New Workflow</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <Form onSubmit={handleSubmit}>
                    <Form.Group className="mb-3">
                        <Form.Label>Name</Form.Label>
                        <Form.Control
                            type="text"
                            name="name"
                            value={formData.name}
                            onChange={handleInputChange}
                            placeholder="Enter workflow name (minimum 15 characters)"
                            required
                            minLength={10}
                        />
                        <Form.Text className="text-muted">
                            {formData.name.length < 10 
                                ? `${10 - formData.name.length} more characters required` 
                                : 'Name is valid'}
                        </Form.Text>
                    </Form.Group>

                    <Form.Group className="mb-3">
                        <Form.Label>Description</Form.Label>
                        <Form.Control
                            as="textarea"
                            name="description"
                            value={formData.description}
                            onChange={handleInputChange}
                            placeholder="Enter workflow description (minimum 20 characters)"
                            rows={3}
                            required
                            minLength={20}
                        />
                        <Form.Text className="text-muted">
                            {formData.description.length < 20 
                                ? `${20 - formData.description.length} more characters required` 
                                : 'Description is valid'}
                        </Form.Text>
                    </Form.Group>

                    <Form.Group className="mb-3">
                        <Form.Label>Visibility</Form.Label>
                        <div>
                            <Form.Check
                                type="switch"
                                id="visibility-switch"
                                label={formData.public ? "Public - Accessible by all teams" : "Private - Only accessible by your team"}
                                name="public"
                                checked={formData.public}
                                onChange={handleInputChange}
                            />
                            <Form.Text className="text-muted">
                                {formData.public 
                                    ? "Public workflows can be viewed and executed by all teams, but only your team's admins can edit them."
                                    : "Private workflows are only accessible to your team members."}
                            </Form.Text>
                        </div>
                    </Form.Group>

                    <Form.Group className="mb-3">
                        <Form.Label>Request JSON</Form.Label>
                        <Form.Control
                            as="textarea"
                            name="request"
                            value={formData.request}
                            onChange={handleInputChange}
                            rows={15}
                            className="font-monospace"
                            required
                        />
                        <Form.Text className="text-muted">
                            {formData.request.trim().length === 0 
                                ? 'Request JSON is required' 
                                : 'This is an example template. You need to modify the query intent and steps according to your needs.'}
                        </Form.Text>
                        <Alert variant="info" className="mt-2">
                            <small>
                                <strong>Note:</strong> Don't worry about getting everything perfect right now. You can always edit this workflow later from the workflows list, including its name, description, visibility, and request configuration.
                            </small>
                        </Alert>
                    </Form.Group>

                    {error && (
                        <Alert variant="danger" className="mt-3">
                            {error}
                        </Alert>
                    )}
                </Form>
            </Modal.Body>
            <Modal.Footer>
                <div className="d-flex justify-content-between w-100">
                    <Button variant="secondary" onClick={onHide}>
                        Cancel
                    </Button>
                    <div>
                        <Button 
                            variant="primary" 
                            onClick={handleSubmit}
                            disabled={saving || !isFormValid()}
                        >
                            {saving ? (
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
                            ) : 'Create Workflow'}
                        </Button>
                    </div>
                </div>
            </Modal.Footer>
        </Modal>
    );
}

export default CreateWorkflowModal; 