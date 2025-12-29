import React, { useState, useEffect } from 'react';
import { Modal, ListGroup, Form, Spinner, Badge } from 'react-bootstrap';
import Button from '../../../../components/common/Button';
import { HiSearch, HiDocumentText, HiDatabase } from 'react-icons/hi';
import { knowledgeHubDocumentService } from '../../../../services/knowledgeHubDocumentService';
import { toast } from 'react-toastify';

const KnowledgeHubPicker = ({ show, onHide, onSelect }) => {
    const [documents, setDocuments] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedDoc, setSelectedDoc] = useState(null);

    useEffect(() => {
        if (show) {
            fetchDocuments();
            setSelectedDoc(null);
            setSearchQuery('');
        }
    }, [show]);

    const fetchDocuments = async () => {
        setLoading(true);
        try {
            // Fetch all documents. You can filter by status=PROCESSED if needed.
            const response = await knowledgeHubDocumentService.getAllDocuments();
            setDocuments(response.data);
        } catch (error) {
            console.error('Error fetching documents:', error);
            toast.error('Failed to load documents from Knowledge Hub');
        } finally {
            setLoading(false);
        }
    };

    const handleSelect = () => {
        if (selectedDoc) {
            onSelect(selectedDoc);
            onHide();
        }
    };

    const filteredDocuments = documents.filter(doc =>
        doc.fileName.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <Modal show={show} onHide={onHide} size="lg" centered>
            <Modal.Header closeButton>
                <Modal.Title className="d-flex align-items-center">
                    <HiDatabase className="me-2 text-primary" />
                    Select Contract
                </Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <div className="mb-3 position-relative">
                    <HiSearch className="position-absolute text-muted" style={{ top: '10px', left: '10px' }} />
                    <Form.Control
                        type="text"
                        placeholder="Search documents..."
                        className="ps-4"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                    />
                </div>

                {loading ? (
                    <div className="text-center py-5">
                        <Spinner animation="border" variant="primary" />
                        <p className="mt-2 text-muted">Loading documents...</p>
                    </div>
                ) : (
                    <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
                        {filteredDocuments.length === 0 ? (
                            <div className="text-center py-5 text-muted">
                                <p>No documents found.</p>
                            </div>
                        ) : (
                            <ListGroup>
                                {filteredDocuments.map(doc => (
                                    <ListGroup.Item
                                        key={doc.id}
                                        action
                                        active={selectedDoc?.id === doc.id}
                                        onClick={() => setSelectedDoc(doc)}
                                        className="d-flex justify-content-between align-items-center"
                                    >
                                        <div className="d-flex align-items-center">
                                            <HiDocumentText className="me-3" size={20} />
                                            <div>
                                                <div className="fw-bold">{doc.fileName}</div>
                                                <small className="text-muted">
                                                    {(doc.fileSize / 1024).toFixed(1)} KB • {new Date(doc.createdAt).toLocaleDateString()}
                                                </small>
                                            </div>
                                        </div>
                                        <div>
                                            {doc.status === 'PROCESSED' ? (
                                                <Badge bg="success">Ready</Badge>
                                            ) : (
                                                <Badge bg="secondary">{doc.status}</Badge>
                                            )}
                                        </div>
                                    </ListGroup.Item>
                                ))}
                            </ListGroup>
                        )}
                    </div>
                )}
            </Modal.Body>
            <Modal.Footer>
                <Button variant="secondary" onClick={onHide}>
                    Cancel
                </Button>
                <Button
                    variant="primary"
                    onClick={handleSelect}
                    disabled={!selectedDoc}
                >
                    Select Document
                </Button>
            </Modal.Footer>
        </Modal>
    );
};

export default KnowledgeHubPicker;
