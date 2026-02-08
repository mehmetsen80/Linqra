import React, { useState, useEffect } from 'react';
import { Spinner, Alert, ButtonGroup, Nav, Tab, Modal, ListGroup, Badge } from 'react-bootstrap';
import Button from '../../../../components/common/Button';
import { HiDatabase, HiDocumentText, HiDownload, HiPencil, HiEye, HiInformationCircle, HiClock, HiRefresh } from 'react-icons/hi';
import KnowledgeHubPicker from './KnowledgeHubPicker';
import { knowledgeHubDocumentService } from '../../../../services/knowledgeHubDocumentService';
import { Document, Page, pdfjs } from 'react-pdf';
import RichTextEditor from './RichTextEditor';
import mammoth from 'mammoth';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

// Set up PDF.js worker
pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

const DocumentViewer = ({ onDocumentSelected, document, loading, reviewPoints = [], onReviewPointAction, activePointId, onPointSelect, onContentUpdated }) => {
    const [showPicker, setShowPicker] = useState(false);
    const [contentLoading, setContentLoading] = useState(false);
    const [numPages, setNumPages] = useState(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [activeTab, setActiveTab] = useState('editing');
    const [originalUrl, setOriginalUrl] = useState(null);
    const [editorContent, setEditorContent] = useState('');

    // History State
    const [showHistoryModal, setShowHistoryModal] = useState(false);
    const [versions, setVersions] = useState([]);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [restoringVersion, setRestoringVersion] = useState(null);

    // Fetch document content when document changes
    useEffect(() => {
        if (document?.id) {
            fetchDocumentContent(document);
        } else {
            setContentLoading(false);
        }
    }, [document?.id]);

    const fetchDocumentContent = async (doc) => {
        setContentLoading(true);
        setOriginalUrl(null);
        setEditorContent(''); // Reset content

        try {
            // Fetch original file URL for "Original" tab & "Editing" tab conversion
            const downloadResponse = await knowledgeHubDocumentService.generateDownloadUrl(doc.documentId);

            if (downloadResponse.success) {
                const url = downloadResponse.data.downloadUrl;
                setOriginalUrl(url);

                // If it's a DOCX file, convert to HTML for the editor
                if (doc.fileName && doc.fileName.toLowerCase().endsWith('.docx')) {
                    try {
                        const response = await fetch(url);
                        const arrayBuffer = await response.arrayBuffer();
                        const result = await mammoth.convertToHtml({ arrayBuffer: arrayBuffer });
                        setEditorContent(result.value);
                    } catch (conversionError) {
                        console.error("Error converting DOCX to HTML:", conversionError);
                        setEditorContent(`<p>Error loading document content: ${conversionError.message}</p>`);
                    }
                } else if (doc.fileName && doc.fileName.toLowerCase().endsWith('.pdf')) {
                    // For PDF files, try to fetch extracted text from backend
                    try {
                        const textResponse = await knowledgeHubDocumentService.getDocumentText(doc.documentId);
                        if (textResponse.success && textResponse.data && textResponse.data.text) {
                            // Format text as HTML paragraphs for Tiptap
                            const textContent = textResponse.data.text;
                            const htmlContent = textContent.split('\n\n').map(p => `<p>${p}</p>`).join('');
                            setEditorContent(htmlContent);
                        } else {
                            setEditorContent(`<p>No text content available for <strong>${doc.fileName}</strong>.</p>`);
                        }
                    } catch (textError) {
                        console.error("Error fetching PDF text:", textError);
                        setEditorContent(`<p>Error loading text content: ${textError.message}</p>`);
                    }
                } else {
                    // For other files, show a placeholder
                    setEditorContent(`<p>Preview not available for editing. Viewing <strong>${doc.fileName}</strong>.</p>`);
                }
            }
        } catch (error) {
            console.error('Error loading document URL:', error);
            setEditorContent(`<p>Error loading document: ${error.message}</p>`);
        } finally {
            setContentLoading(false);
        }
    };

    const handleSelect = (doc) => {
        if (onDocumentSelected) {
            onDocumentSelected(doc);
        }
    };

    const handleDownload = async () => {
        if (document?.id) {
            await knowledgeHubDocumentService.downloadDocument(document.id, document.fileName);
        }
    };

    const fetchHistory = async () => {
        if (!document?.documentId) return;
        setLoadingHistory(true);
        try {
            const response = await knowledgeHubDocumentService.getDocumentVersions(document.documentId);
            if (response.success) {
                setVersions(response.data);
            }
        } catch (error) {
            console.error("Failed to fetch history:", error);
        } finally {
            setLoadingHistory(false);
        }
    };

    const handleRestore = async (versionNumber) => {
        if (!document?.documentId) return;
        if (!window.confirm(`Are you sure you want to restore version ${versionNumber}? This will create a new version with the content from v${versionNumber}.`)) {
            return;
        }

        setRestoringVersion(versionNumber);
        try {
            const response = await knowledgeHubDocumentService.restoreVersion(document.documentId, versionNumber);
            if (response.success) {
                // Refresh document content
                await fetchDocumentContent(document);
                setShowHistoryModal(false);
                alert(`Successfully restored version ${versionNumber}`);

                // Notify parent that content has changed so it can reset AI reviews
                if (onContentUpdated) {
                    onContentUpdated();
                }
            } else {
                alert(`Failed to restore: ${response.error}`);
            }
        } catch (error) {
            console.error("Restore failed:", error);
            alert("An error occurred while restoring the version.");
        } finally {
            setRestoringVersion(null);
        }
    };

    const openHistory = () => {
        setShowHistoryModal(true);
        fetchHistory();
    };

    const onDocumentLoadSuccess = ({ numPages }) => {
        setNumPages(numPages);
        setCurrentPage(1);
    };

    // Loading state
    if (loading || contentLoading) {
        return (
            <div className="h-100 d-flex align-items-center justify-content-center bg-light">
                <Spinner animation="border" variant="primary" />
                <span className="ms-3">Loading document...</span>
            </div>
        );
    }

    // Document loaded - render viewer
    if (document) {
        return (
            <div className="h-100 d-flex flex-column bg-light">
                {/* Header */}
                <div className="p-3 border-bottom bg-white d-flex justify-content-between align-items-center">
                    <div className="d-flex align-items-center">
                        <HiDocumentText className="text-primary me-2" size={24} />
                        <h5 className="m-0">{document.fileName}</h5>
                    </div>
                    <div className="d-flex gap-2">
                        <Button variant="outline-secondary" size="sm" onClick={openHistory}>
                            <HiClock className="me-1" /> History
                        </Button>
                        <Button variant="outline-secondary" size="sm" onClick={handleDownload}>
                            <HiDownload className="me-1" /> Download
                        </Button>
                        <Button variant="outline-secondary" size="sm" onClick={() => setShowPicker(true)}>
                            Change Document
                        </Button>
                    </div>
                </div>

                {/* Tabs - Editing and Original */}
                <div className="doc-viewer-tabs flex-grow-1 d-flex flex-column overflow-hidden">
                    <Tab.Container activeKey={activeTab} onSelect={setActiveTab}>
                        <Nav variant="tabs" className="px-3 pt-2 bg-white">
                            <Nav.Item>
                                <Nav.Link eventKey="editing" className="d-flex align-items-center gap-1">
                                    <HiPencil /> Live Editor
                                </Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="original" className="d-flex align-items-center gap-1">
                                    <HiEye /> Source File (Read Only)
                                </Nav.Link>
                            </Nav.Item>
                        </Nav>

                        <Tab.Content className="flex-grow-1 overflow-auto">
                            {/* Editing Tab */}
                            <Tab.Pane eventKey="editing" className="h-100 p-3">
                                <div className="bg-white shadow-sm rounded doc-viewer-editor h-100">
                                    <RichTextEditor
                                        readOnly={false}
                                        content={editorContent}
                                        reviewPoints={reviewPoints}
                                        activePointId={activePointId}
                                        onPointSelect={onPointSelect}
                                    />
                                </div>
                            </Tab.Pane>

                            <Tab.Pane eventKey="original" className="h-100 p-3">
                                {originalUrl ? (
                                    <div className="bg-white shadow-sm rounded p-3">
                                        <Alert variant="info" className="mb-3 py-2 small d-flex align-items-center">
                                            <HiInformationCircle className="me-2" size={18} />
                                            <span>
                                                This is the original source file. It is read-only and <strong>does not update</strong> with your edits in the Live Editor.
                                            </span>
                                        </Alert>
                                        {document?.fileName?.toLowerCase().endsWith('.pdf') ? (
                                            <>
                                                <Document
                                                    file={originalUrl}
                                                    onLoadSuccess={onDocumentLoadSuccess}
                                                    loading={<Spinner animation="border" />}
                                                    error={<Alert variant="danger">Failed to load PDF</Alert>}
                                                >
                                                    <Page
                                                        pageNumber={currentPage}
                                                        renderTextLayer={true}
                                                        renderAnnotationLayer={true}
                                                        width={Math.min(800, window.innerWidth - 100)}
                                                    />
                                                </Document>
                                                {numPages > 1 && (
                                                    <div className="d-flex justify-content-center align-items-center gap-3 mt-3 pt-3 border-top">
                                                        <Button
                                                            variant="outline-secondary"
                                                            size="sm"
                                                            disabled={currentPage <= 1}
                                                            onClick={() => setCurrentPage(p => p - 1)}
                                                        >
                                                            Previous
                                                        </Button>
                                                        <span className="text-muted">
                                                            Page {currentPage} of {numPages}
                                                        </span>
                                                        <Button
                                                            variant="outline-secondary"
                                                            size="sm"
                                                            disabled={currentPage >= numPages}
                                                            onClick={() => setCurrentPage(p => p + 1)}
                                                        >
                                                            Next
                                                        </Button>
                                                    </div>
                                                )}
                                            </>
                                        ) : (
                                            <div className="text-center py-5">
                                                <HiDocumentText size={64} className="text-muted mb-3" />
                                                <p className="text-muted mb-3">
                                                    Preview not available for this file type.
                                                </p>
                                                <Button variant="primary" onClick={handleDownload}>
                                                    <HiDownload className="me-1" /> Download Original
                                                </Button>
                                            </div>
                                        )}
                                    </div>
                                ) : (
                                    <div className="bg-white shadow-sm rounded p-5 text-center">
                                        <Spinner animation="border" variant="primary" />
                                        <p className="text-muted mt-3">Loading original document...</p>
                                    </div>
                                )}
                            </Tab.Pane>
                        </Tab.Content>
                    </Tab.Container>
                </div>

                <KnowledgeHubPicker
                    show={showPicker}
                    onHide={() => setShowPicker(false)}
                    onSelect={handleSelect}
                />

                {/* History Modal */}
                <Modal show={showHistoryModal} onHide={() => setShowHistoryModal(false)} size="lg">
                    <Modal.Header closeButton>
                        <Modal.Title className="d-flex align-items-center gap-2">
                            <HiClock /> Version History
                        </Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {loadingHistory ? (
                            <div className="text-center py-4">
                                <Spinner animation="border" size="sm" />
                                <span className="ms-2">Loading history...</span>
                            </div>
                        ) : versions.length === 0 ? (
                            <div className="text-center py-4 text-muted">
                                <p>No version history available.</p>
                                <small>Versions are created when you edit the document.</small>
                            </div>
                        ) : (
                            <ListGroup variant="flush">
                                {/* Current Version Header */}
                                <ListGroup.Item className="bg-light fw-bold d-flex justify-content-between align-items-center">
                                    <span>
                                        Current Version <Badge bg="primary">v{document.currentVersion || (versions.length > 0 ? versions[0].versionNumber + 1 : 1)}</Badge>
                                    </span>
                                    <span className="text-muted small">Now</span>
                                </ListGroup.Item>

                                {versions.map((version) => (
                                    <ListGroup.Item key={version.id} className="d-flex justify-content-between align-items-center">
                                        <div>
                                            <div className="d-flex align-items-center gap-2 mb-1">
                                                <Badge bg="secondary">v{version.versionNumber}</Badge>
                                                <span className="fw-medium">{version.summary || "Edit"}</span>
                                            </div>
                                            <small className="text-muted">
                                                {new Date(version.createdAt).toLocaleString()}
                                            </small>
                                        </div>
                                        <Button
                                            variant="outline-primary"
                                            size="sm"
                                            onClick={() => handleRestore(version.versionNumber)}
                                            disabled={restoringVersion === version.versionNumber}
                                        >
                                            {restoringVersion === version.versionNumber ? (
                                                <>
                                                    <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-1" />
                                                    Restoring...
                                                </>
                                            ) : (
                                                <>
                                                    <HiRefresh className="me-1" /> Restore
                                                </>
                                            )}
                                        </Button>
                                    </ListGroup.Item>
                                ))}
                            </ListGroup>
                        )}
                    </Modal.Body>
                </Modal>
            </div>
        );
    }
    // No document selected - show picker prompt
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
