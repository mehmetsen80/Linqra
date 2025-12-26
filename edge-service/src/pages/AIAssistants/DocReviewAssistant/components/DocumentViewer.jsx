
import React, { useState } from 'react';
import { Spinner } from 'react-bootstrap';
import Button from '../../../../components/common/Button';
import { HiDatabase, HiDocumentText } from 'react-icons/hi';
import KnowledgeHubPicker from './KnowledgeHubPicker';

const DocumentViewer = ({ onDocumentSelected, document, loading }) => {
    const [showPicker, setShowPicker] = useState(false);

    const handleSelect = (doc) => {
        if (onDocumentSelected) {
            onDocumentSelected(doc);
        }
    };

    if (loading) {
        return (
            <div className="h-100 d-flex align-items-center justify-content-center bg-light">
                <Spinner animation="border" variant="primary" />
                <span className="ms-3">Loading document...</span>
            </div>
        );
    }

    if (document) {
        // Placeholder for actual PDF/Text viewer
        return (
            <div className="h-100 d-flex flex-column bg-light">
                <div className="p-3 border-bottom bg-white d-flex justify-content-between align-items-center">
                    <div className="d-flex align-items-center">
                        <HiDocumentText className="text-primary me-2" size={24} />
                        <h5 className="m-0">{document.fileName}</h5>
                    </div>
                    <Button variant="outline-secondary" size="sm" onClick={() => setShowPicker(true)}>
                        Change Document
                    </Button>
                </div>
                <div className="flex-grow-1 p-4 overflow-auto">
                    {/* TO DO: Implement actual PDF viewer or text content viewer */}
                    <div className="bg-white p-5 shadow-sm" style={{ minHeight: '100%' }}>
                        <p className="text-muted text-center mt-5">Document content ready for review.</p>
                        {/* If we have processed text, we could show it here */}
                    </div>
                </div>
                <KnowledgeHubPicker
                    show={showPicker}
                    onHide={() => setShowPicker(false)}
                    onSelect={handleSelect}
                />
            </div>
        );
    }

    return (
        <div className="h-100 bg-secondary bg-opacity-10 d-flex align-items-center justify-content-center">
            <div className="text-center">
                <div className="mb-3">
                    <HiDatabase size={48} style={{ color: 'var(--primary-color)' }} />
                </div>
                <h5 className="text-muted">No document selected</h5>
                <p className="text-muted small mb-4">Select a contract from Knowledge Hub to begin review</p>
                <Button variant="primary" onClick={() => setShowPicker(true)}>
                    Select from Knowledge Hub
                </Button>
            </div>
            <KnowledgeHubPicker
                show={showPicker}
                onHide={() => setShowPicker(false)}
                onSelect={handleSelect}
            />
        </div>
    );
};

export default DocumentViewer;
