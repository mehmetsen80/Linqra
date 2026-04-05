import React, { useState, useEffect } from 'react';
import { Modal, Form, Row, Col, Button as BSButton } from 'react-bootstrap';
import Button from '../../common/Button';
import { FiLock, FiUnlock } from 'react-icons/fi';
import toolService from '../../../services/toolService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import RichTextEditor from '../../common/RichTextEditor';
import ConfirmationModal from '../../common/ConfirmationModal';

const ToolEditorModal = ({ show, onHide, tool, editMode, onSuccess }) => {
    const [saving, setSaving] = useState(false);
    const [isSlugLocked, setIsSlugLocked] = useState(true);
    const [showUnlockConfirm, setShowUnlockConfirm] = useState(false);
    const [slugError, setSlugError] = useState(null);
    const [manuallyEditedSlug, setManuallyEditedSlug] = useState(false);

    const SL_REGEX = /^[a-z]+(\.[a-z]+)*$/;

    const slugify = (text) => {
        return text
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '.') // Replace non-alpha with dots
            .replace(/^\.|\.$/g, '')     // Trim dots from ends
            .replace(/\.{2,}/g, '.');    // Collapse multiple dots
    };

    const [form, setForm] = useState({
        toolId: '',
        name: '',
        description: '',
        category: 'Legal',
        type: 'INTERNAL_SERVICE',
        linq_config: {
            link: { target: '', action: '' },
            query: { intent: '' }
        },
        pricing: {
            type: 'PER_EXECUTION',
            cost: 0.10
        },
        inputSchema: '{\n  "type": "object",\n  "properties": {}\n}',
        outputSchema: '{\n  "type": "object",\n  "properties": {}\n}',
        examples: [],
        visibility: 'PUBLIC'
    });

    useEffect(() => {
        if (tool) {
            setForm({
                ...tool,
                inputSchema: (typeof tool.inputSchema === 'object') ? JSON.stringify(tool.inputSchema, null, 2) : (tool.inputSchema || '{\n  "type": "object",\n  "properties": {}\n}'),
                outputSchema: (typeof tool.outputSchema === 'object') ? JSON.stringify(tool.outputSchema, null, 2) : (tool.outputSchema || '{\n  "type": "object",\n  "properties": {}\n}'),
                examples: tool.examples || [],
                visibility: tool.visibility || 'PUBLIC'
            });
            setIsSlugLocked(true);
            setSlugError(null);
            setManuallyEditedSlug(false);
        } else {
            setForm({
                toolId: '',
                name: '',
                description: '',
                category: 'Legal',
                type: 'INTERNAL_SERVICE',
                linq_config: {
                    link: { target: '', action: '' },
                    query: { intent: '' }
                },
                pricing: {
                    type: 'PER_EXECUTION',
                    cost: 0.10
                },
                inputSchema: '{\n  "type": "object",\n  "properties": {}\n}',
                outputSchema: '{\n  "type": "object",\n  "properties": {}\n}',
                examples: [],
                visibility: 'PUBLIC'
            });
        }
    }, [tool, show]);

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        if (name.startsWith('pricing.')) {
            const field = name.split('.')[1];
            setForm(prev => ({
                ...prev,
                pricing: { ...prev.pricing, [field]: value }
            }));
        } else if (name === 'toolId') {
            const error = validateSlug(value);
            setSlugError(error);
            setManuallyEditedSlug(true);
            setForm(prev => ({ ...prev, [name]: value }));
        } else if (name === 'name') {
            let updates = { [name]: value };
            if (!editMode && !manuallyEditedSlug) {
                const suggestedSlug = slugify(value);
                updates.toolId = suggestedSlug;
                setSlugError(validateSlug(suggestedSlug));
            }
            setForm(prev => ({ ...prev, ...updates }));
        } else {
            setForm(prev => ({ ...prev, [name]: value }));
        }
    };

    const handleUnlockSlug = (e) => {
        if (e) e.preventDefault();
        setShowUnlockConfirm(true);
    };

    const confirmUnlockSlug = () => {
        setIsSlugLocked(false);
        setShowUnlockConfirm(false);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!form.name?.trim()) {
            showErrorToast('Please enter a tool name');
            return;
        }

        const plainDescription = form.description?.replace(/<[^>]*>/g, '').trim();
        if (!plainDescription) {
            showErrorToast('Please enter a tool description');
            return;
        }

        setSaving(true);
        const toolToSave = {
            ...form,
            pricing: {
                ...form.pricing,
                cost: form.pricing.type === 'FREE' ? 0 : parseFloat(form.pricing.cost)
            }
        };

        const response = editMode
            ? await toolService.updateTool(form.toolId, toolToSave)
            : await toolService.registerTool(toolToSave);

        if (response.success) {
            showSuccessToast(editMode ? 'Tool updated successfully' : 'Tool registered successfully');
            onHide();
            if (onSuccess) onSuccess(response.data);
        } else {
            showErrorToast(response.error || 'Failed to save tool');
        }
        setSaving(false);
    };

    return (
        <>
            <Modal show={show} onHide={onHide} size="lg" centered backdrop="static">
                <Form onSubmit={handleSubmit}>
                    <Modal.Header closeButton>
                        <Modal.Title>{editMode ? 'Edit Tool Configuration' : 'Register New Tool'}</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>

                        <Row>
                            <Col md={6}>
                                <Form.Group className="mb-3">
                                    <Form.Label className="d-flex justify-content-between align-items-center">
                                        Tool ID (Slug)
                                        {editMode && (
                                            <BSButton
                                                type="button"
                                                variant="link"
                                                size="sm"
                                                className={`p-0 text-decoration-none d-flex align-items-center ${isSlugLocked ? 'text-muted' : 'text-warning'}`}
                                                onClick={handleUnlockSlug}
                                                disabled={!isSlugLocked}
                                                style={{ fontSize: '0.75rem' }}
                                            >
                                                {isSlugLocked ? <><FiLock className="me-1" /> Identity Locked</> : <><FiUnlock className="me-1" /> Identity Unlocked</>}
                                            </BSButton>
                                        )}
                                    </Form.Label>
                                    <Form.Control
                                        name="toolId"
                                        placeholder="e.g. uscis.form.monitor"
                                        value={form.toolId || ''}
                                        required
                                        disabled={editMode && isSlugLocked}
                                        onChange={handleInputChange}
                                        isInvalid={!!slugError}
                                        className={`slug-input ${isSlugLocked && editMode ? 'locked' : 'unlocked'}`}
                                    />
                                    <Form.Control.Feedback type="invalid">
                                        {slugError}
                                    </Form.Control.Feedback>
                                    {!isSlugLocked && (
                                        <Form.Text className="text-warning small fw-bold">
                                            Caution: Changing the slug will break current integrations.
                                        </Form.Text>
                                    )}
                                </Form.Group>
                            </Col>
                            <Col md={6}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Display Name</Form.Label>
                                    <Form.Control
                                        name="name"
                                        placeholder="e.g. USCIS Sentinel"
                                        value={form.name || ''}
                                        required
                                        onChange={handleInputChange}
                                    />
                                </Form.Group>
                            </Col>
                        </Row>

                        <Form.Group className="mb-3">
                            <Form.Label>Description</Form.Label>
                            <RichTextEditor
                                content={form.description || ''}
                                onChange={(html) => handleInputChange({ target: { name: 'description', value: html } })}
                                placeholder="Enter tool description"
                            />
                        </Form.Group>

                        <Row>
                            <Col md={6}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Category</Form.Label>
                                    <Form.Select name="category" value={form.category || 'Legal'} onChange={handleInputChange}>
                                        <option>Legal</option>
                                        <option>Security</option>
                                        <option>Utility</option>
                                        <option>AI</option>
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                            <Col md={6}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Execution Type</Form.Label>
                                    <Form.Select name="type" value={form.type || 'INTERNAL_SERVICE'} onChange={handleInputChange}>
                                        <option value="INTERNAL_SERVICE">Internal Service</option>
                                        <option value="HTTP">HTTP Endpoint</option>
                                        <option value="MCP">MCP Connector</option>
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                        </Row>

                        <Row>
                            <Col md={12}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Visibility</Form.Label>
                                    <Form.Select name="visibility" value={form.visibility || 'PUBLIC'} onChange={handleInputChange}>
                                        <option value="PUBLIC">Public (Available for everyone)</option>
                                        <option value="PRIVATE">Private (Restricted access via API Key)</option>
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                        </Row>

                        <Row>
                            <Col md={6}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Pricing Type</Form.Label>
                                    <Form.Select name="pricing.type" value={form.pricing?.type || 'PER_EXECUTION'} onChange={handleInputChange}>
                                        <option value="PER_EXECUTION">Per Execution</option>
                                        <option value="FREE">Free</option>
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                            <Col md={6}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Price per Execution ($)</Form.Label>
                                    <Form.Control
                                        type="number"
                                        step="0.01"
                                        name="pricing.cost"
                                        value={form.pricing?.type === 'FREE' ? '0.00' : form.pricing?.cost}
                                        onChange={handleInputChange}
                                        required
                                        readOnly={form.pricing?.type === 'FREE'}
                                        className={form.pricing?.type === 'FREE' ? 'bg-light' : ''}
                                    />
                                </Form.Group>
                            </Col>
                        </Row>
                    </Modal.Body>
                    <Modal.Footer className="bg-light">
                        <Button variant="secondary" type="button" onClick={onHide}>Cancel</Button>
                        <Button
                            variant="primary"
                            type="submit"
                            loading={saving}
                            disabled={!!slugError}
                        >
                            {editMode ? 'Update Tool Details' : 'Register Tool'}
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>

            <ConfirmationModal
                show={showUnlockConfirm}
                onHide={() => setShowUnlockConfirm(false)}
                onConfirm={confirmUnlockSlug}
                title="Identity Change Warning"
                message="Warning: Changing the Tool ID will break all existing integrations using the previous slug. Are you absolutely sure?"
                confirmLabel="Yes, unlock identity"
                cancelLabel="Cancel"
                variant="warning"
            />
        </>
    );
};

export default ToolEditorModal;
