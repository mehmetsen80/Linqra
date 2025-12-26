import React, { useState, useEffect } from 'react';
import { toast } from 'react-toastify';
import IconSidebar from './components/IconSidebar';
import ChatPane from './components/ChatPane';
import DocumentViewer from './components/DocumentViewer';
import DocReviewHistoryModal from './components/DocReviewHistoryModal';
import docReviewService from '../../../services/docReviewService';

const DocReviewAssistant = () => {
    const [assistant, setAssistant] = useState(null);
    const [reviewSession, setReviewSession] = useState(null);
    // Defines the currently loaded document object for the viewer
    const [currentDocument, setCurrentDocument] = useState(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        fetchDocReviewAssistant();
    }, []);

    const fetchDocReviewAssistant = async () => {
        try {
            // Find the assistant specifically configured for doc reviews
            const response = await docReviewService.getDocReviewAssistant();
            // Expecting a list, take the first one or active one
            const assistants = response.data;
            if (assistants && assistants.length > 0) {
                setAssistant(assistants[0]);
            } else {
                //toast.warning('No Doc Review Assistant found. Please check configuration.');
                console.log('No Doc Review Assistant found. Please check configuration.');
            }
        } catch (error) {
            console.error('Error fetching assistant:', error);
            // toast.error('Failed to initialize Contract Review Agent'); 
            // Suppress error for now as it might not be set up yet
        }
    };

    const handleDocumentSelected = async (doc) => {
        if (!assistant) {
            toast.error('Doc Review Assistant is not available.');
            return;
        }

        setLoading(true);
        try {
            // Create a new review session
            const payload = {
                documentId: doc.id,
                documentName: doc.fileName,
                assistantId: assistant.id,
                status: 'IN_PROGRESS'
            };

            const response = await docReviewService.createReview(payload);
            if (response.success) {
                setReviewSession(response.data);
                setCurrentDocument(doc);
                toast.success('Doc review session started');
            } else {
                toast.error(response.error || 'Failed to start review session');
            }
        } catch (error) {
            console.error('Error creating review session:', error);
            toast.error('Failed to start review session');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="d-flex w-100 vh-100 overflow-hidden bg-white" style={{ position: 'fixed', top: 0, left: 0, zIndex: 9999 }}>
            {/* Sidebar - Fixed Width */}
            <div style={{ flex: '0 0 60px' }}>
                <IconSidebar />
            </div>

            {/* Main Content Area */}
            <div className="d-flex flex-grow-1" style={{ marginLeft: '0px' }}>
                {/* Chat Pane - 30% width */}
                <div style={{ flex: '0 0 30%', minWidth: '300px' }}>
                    <ChatPane
                        assistant={assistant}
                        reviewSession={reviewSession}
                        onSessionUpdate={(updates) => setReviewSession(prev => ({ ...prev, ...updates }))}
                        onLoadSession={(session) => {
                            setReviewSession(session);
                            setCurrentDocument({
                                id: session.documentId,
                                fileName: session.documentName
                            });
                            toast.info('Loaded previous review session');
                        }}
                    />
                </div>

                {/* Document Viewer - Remaining width */}
                <div className="flex-grow-1">
                    <DocumentViewer
                        onDocumentSelected={handleDocumentSelected}
                        document={currentDocument}
                        loading={loading}
                    />
                </div>
            </div>
        </div>
    );
};

export default DocReviewAssistant;
