import React, { useState, useEffect } from 'react';
import { Modal, Form } from 'react-bootstrap';
import Button from '../../common/Button';
import RichTextEditor from '../../common/RichTextEditor';
import './styles.css';

const InstructionsModal = ({
    show,
    onHide,
    onSave,
    initialContent,
    toolName,
    saving
}) => {
    const [content, setContent] = useState(initialContent || '');

    useEffect(() => {
        if (show) {
            setContent(initialContent || '');
        }
    }, [show, initialContent]);

    const handleSave = () => {
        onSave(content);
    };

    return (
        <Modal show={show} onHide={onHide} size="lg" centered className="instructions-modal">
            <Modal.Header closeButton className="instructions-modal-header">
                <Modal.Title className="fw-bold">API Integration Instructions</Modal.Title>
            </Modal.Header>
            <Modal.Body className="p-4">
                <div className="d-flex align-items-center mb-3">
                    <span className="text-muted small me-2">Configure documentation for</span>
                    <span className="instructions-tool-badge">{toolName}</span>
                </div>
                <div className="instructions-editor-wrapper flex-grow-1 d-flex flex-column">
                    <RichTextEditor
                        content={content}
                        onChange={setContent}
                        placeholder="List supported form IDs, provide integration tips, or explain business logic..."
                        minHeight="400px"
                    />
                </div>
            </Modal.Body>
            <Modal.Footer className="bg-light border-top p-3">
                <Button variant="secondary" onClick={onHide} disabled={saving}>
                    Cancel
                </Button>
                <Button variant="primary" onClick={handleSave} loading={saving}>
                    Update Instructions
                </Button>
            </Modal.Footer>
        </Modal>
    );
};

export default InstructionsModal;
