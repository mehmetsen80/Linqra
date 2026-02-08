import React, { useState, useEffect } from 'react';
import { toast } from 'react-toastify';
import IconSidebar from './components/IconSidebar';
import ChatPane from './components/ChatPane';
import DocumentViewer from './components/DocumentViewer';
import ReviewSuggestionsPanel from './components/ReviewSuggestionsPanel';
import DocReviewHistoryModal from './components/DocReviewHistoryModal';
import docReviewService from '../../../services/docReviewService';
import './styles.css';

const DocReviewAssistant = () => {
    const [assistant, setAssistant] = useState(null);
    const [reviewSession, setReviewSession] = useState(null);
    // Defines the currently loaded document object for the viewer
    const [currentDocument, setCurrentDocument] = useState(null);
    const [loading, setLoading] = useState(false);
    const [reviewPoints, setReviewPoints] = useState([]);
    const [analyzing, setAnalyzing] = useState(false);

    const [activePointId, setActivePointId] = useState(null);

    useEffect(() => {
        fetchDocReviewAssistant();
    }, []);

    // Load review points when session changes
    useEffect(() => {
        if (reviewSession?.reviewPoints) {
            setReviewPoints(reviewSession.reviewPoints);
        }
    }, [reviewSession]);

    const fetchDocReviewAssistant = async () => {
        try {
            // Find the assistant specifically configured for doc reviews
            const response = await docReviewService.getDocReviewAssistant();
            // Expecting a list, take the first one or active one
            const assistants = response.data;
            if (assistants && assistants.length > 0) {
                setAssistant(assistants[0]);
            } else {
                console.log('No Doc Review Assistant found. Please check configuration.');
            }
        } catch (error) {
            console.error('Error fetching assistant:', error);
        }
    };

    const handleDocumentSelected = async (doc) => {
        if (!assistant) {
            toast.error('Doc Review Assistant is not available.');
            return;
        }

        setLoading(true);
        setReviewPoints([]);
        try {
            // Create a new review session
            const payload = {
                documentId: doc.documentId,
                documentName: doc.fileName,
                assistantId: assistant.id,
                status: 'IN_PROGRESS'
            };

            const response = await docReviewService.createReview(payload);
            if (response.success) {
                setReviewSession(response.data);
                setCurrentDocument(doc);
                toast.success('Doc review session started');

                // Trigger initial AI analysis
                triggerAIAnalysis(response.data.id, doc.documentId);
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

    const triggerAIAnalysis = async (reviewId, documentId) => {
        setAnalyzing(true);
        try {
            const response = await docReviewService.analyzeDocument(reviewId, documentId, assistant.id);
            if (response.success && response.data?.reviewPoints) {
                setReviewPoints(response.data.reviewPoints);
                setReviewSession(prev => ({ ...prev, reviewPoints: response.data.reviewPoints }));
                toast.success('AI analysis complete!');
            }
        } catch (error) {
            console.error('Error during AI analysis:', error);
            // Don't show error toast - analysis might not be implemented yet
            console.log('AI analysis endpoint not available yet');
        } finally {
            setAnalyzing(false);
        }
    };

    const handleReviewPointAction = async (pointId, accepted) => {
        // Update local state immediately
        setReviewPoints(prev =>
            prev.map(point =>
                point.id === pointId
                    ? { ...point, userAccepted: accepted }
                    : point
            )
        );

        // Persist to backend
        try {
            const updatedPoints = reviewPoints.map(point =>
                point.id === pointId ? { ...point, userAccepted: accepted } : point
            );
            await docReviewService.updateReview(reviewSession.id, {
                reviewPoints: updatedPoints
            });
        } catch (error) {
            console.error('Error updating review point:', error);
            toast.error('Failed to save decision');
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
                {/* Chat Pane - 25% width */}
                <div style={{ flex: '0 0 25%', minWidth: '280px' }}>
                    <ChatPane
                        assistant={assistant}
                        reviewSession={reviewSession}
                        onSessionUpdate={(updates) => setReviewSession(prev => ({ ...prev, ...updates }))}
                        onLoadSession={(session) => {
                            setReviewSession(session);
                            setCurrentDocument({
                                id: session.documentId,
                                documentId: session.documentId, // This is the UUID needed for API calls
                                fileName: session.documentName
                            });
                            // Always update review points, defaulting to empty if null
                            // This prevents stale suggestions from persisting when switching to a fresh session
                            setReviewPoints(session.reviewPoints || []);

                            toast.info('Loaded previous review session');
                        }}
                    />
                </div>

                {/* Document Viewer - Main area */}
                <div className="flex-grow-1">
                    <DocumentViewer
                        onDocumentSelected={handleDocumentSelected}
                        document={currentDocument}
                        loading={loading}
                        reviewPoints={reviewPoints}
                        onReviewPointAction={handleReviewPointAction}
                        activePointId={activePointId}
                        onPointSelect={setActivePointId}
                        onContentUpdated={() => {
                            // Clear review points as they are now stale
                            setReviewPoints([]);

                            // Optional: Could automatically trigger new analysis or prompt user
                            toast.info('Document updated. Previous suggestions cleared.');

                            // Update session status if needed, or create new session
                            // For now, just clearing points avoids the confusion of stale suggestions
                            if (reviewSession) {
                                setReviewSession(prev => ({ ...prev, reviewPoints: [] }));
                            }
                        }}
                    />
                </div>

                {/* Review Suggestions Panel - Right sidebar */}
                {currentDocument && (
                    <div style={{ flex: '0 0 300px', maxWidth: '350px' }}>
                        <ReviewSuggestionsPanel
                            reviewPoints={reviewPoints}
                            onAction={handleReviewPointAction}
                            loading={analyzing}
                            activePointId={activePointId}
                            onPointSelect={setActivePointId}
                            documentName={currentDocument?.fileName}
                        />
                    </div>
                )}
            </div>
        </div>
    );
};

export default DocReviewAssistant;
