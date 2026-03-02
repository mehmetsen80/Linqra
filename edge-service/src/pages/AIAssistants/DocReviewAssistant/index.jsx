import React, { useState, useEffect, useRef } from 'react';
import { toast } from 'react-toastify';
import IconSidebar from './components/IconSidebar';
import ChatPane from './components/ChatPane';
import DocumentViewer from './components/DocumentViewer';
import ReviewSuggestionsPanel from './components/ReviewSuggestionsPanel';
import DocReviewHistoryModal from './components/DocReviewHistoryModal';
import docReviewService from '../../../services/docReviewService';
import { knowledgeHubDocumentService } from '../../../services/knowledgeHubDocumentService';
import { chatWebSocketService } from '../../../services/chatWebSocketService';
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
    const [statusMessages, setStatusMessages] = useState([]);
    const editorContentRef = useRef('');
    const documentViewerRef = useRef(null);
    const [externalHtml, setExternalHtml] = useState(null);
    const [activeSelection, setActiveSelection] = useState(null);

    const handleEditorChange = (newContent) => {
        editorContentRef.current = newContent;
        // If user edits, clear the external override to prevent re-application
        if (externalHtml) {
            setExternalHtml(null);
        }
    };

    const handleSelectionChange = (selection) => {
        setActiveSelection(selection);
    };

    useEffect(() => {
        fetchDocReviewAssistant();
    }, []);

    // Subscribe to WebSocket chat updates
    useEffect(() => {
        const conversationId = reviewSession?.conversationId;
        if (!conversationId) return;

        console.log(`🔌 Subscribing to Doc Review WebSocket for conversation: ${conversationId}`);
        const unsubscribe = chatWebSocketService.subscribeToConversation(conversationId, (update) => {
            switch (update.type) {
                case 'LLM_RESPONSE_STREAMING_STARTED':
                case 'LLM_RESPONSE_STREAMING_COMPLETE':
                case 'LLM_RESPONSE_STREAMING_CANCELLED':
                    setStatusMessages([]);
                    break;

                case 'AGENT_TASKS_EXECUTING':
                    setStatusMessages(['Starting agent tasks...']);
                    break;

                case 'AGENT_TASKS_COMPLETED':
                    setStatusMessages(prev => {
                        const completionMessage = 'Agent tasks completed.';
                        const lastMessage = prev.length > 0 ? prev[prev.length - 1] : '';
                        if (completionMessage !== lastMessage) {
                            return [...prev, completionMessage];
                        }
                        return prev;
                    });

                    // Refresh review points from backend
                    if (reviewSession?.id) {
                        console.log('🔄 Agent tasks completed. Refreshing review points...');
                        docReviewService.getReview(reviewSession.id).then(response => {
                            if (response.success && response.data?.reviewPoints) {
                                setReviewPoints(response.data.reviewPoints);
                                setReviewSession(prev => ({ ...prev, reviewPoints: response.data.reviewPoints }));
                                toast.success('Agent analysis results loaded!');
                            }
                        }).catch(err => console.error('Error refreshing session after agent completion:', err));
                    }
                    break;

                case 'LLM_CALL_STARTED':
                    setStatusMessages(prev => {
                        const provider = update.provider || '';
                        const llmMessage = `Calling ${provider ? `${provider} ` : ''}${update.modelName || 'AI model'}...`;
                        const lastMessage = prev.length > 0 ? prev[prev.length - 1] : '';
                        if (llmMessage !== lastMessage) {
                            return [...prev, llmMessage];
                        }
                        return prev;
                    });
                    break;

                default:
                    break;
            }
        });

        // Connect WebSocket if not connected
        if (!chatWebSocketService.connected) {
            chatWebSocketService.connect();
        }

        return () => {
            unsubscribe();
        };
    }, [reviewSession?.conversationId]);

    // Load review points when session changes
    useEffect(() => {
        if (reviewSession?.reviewPoints) {
            setReviewPoints(reviewSession.reviewPoints);
        }
        // Clear status messages when switching sessions
        setStatusMessages([]);
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
            // Get current content from editor ref
            const currentContent = editorContentRef.current;

            const response = await docReviewService.analyzeDocument(
                reviewId,
                documentId,
                assistant.id,
                currentContent
            );

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

    const [pendingReplacement, setPendingReplacement] = useState(null);

    const handleReviewPointAction = async (pointId, accepted) => {
        const point = reviewPoints.find(p => p.id === pointId);
        if (!point) return;

        // CASE 1: Rejecting a point
        // We update local state and backend immediately as no document change is involved
        if (!accepted) {
            setReviewPoints(prev =>
                prev.map(p => p.id === pointId ? { ...p, userAccepted: false } : p)
            );

            try {
                const updatedPoints = reviewPoints.map(p =>
                    p.id === pointId ? { ...p, userAccepted: false } : p
                );
                await docReviewService.updateReview(reviewSession.id, {
                    reviewPoints: updatedPoints
                });
            } catch (error) {
                console.error('Error updating review point (reject):', error);
                toast.error('Failed to save decision');
            }
            return;
        }

        // CASE 2: Accepting a point with a suggested replacement
        // We trigger the editor replacement first. Persistence happens in handleReplacementApplied.
        if (point.suggestedReplacement) {
            setPendingReplacement({
                pointId: point.id, // Include pointId to identify which one succeeded
                originalText: point.originalText,
                newText: point.suggestedReplacement
            });
            toast.info('Analyzing document for replacement...');
        } else {
            // CASE 3: Accepting a point without a replacement (just acknowledgment)
            setReviewPoints(prev =>
                prev.map(p => p.id === pointId ? { ...p, userAccepted: true } : p)
            );
            try {
                const updatedPoints = reviewPoints.map(p =>
                    p.id === pointId ? { ...p, userAccepted: true } : p
                );
                await docReviewService.updateReview(reviewSession.id, {
                    reviewPoints: updatedPoints
                });
            } catch (error) {
                console.error('Error updating review point (accept, no replacement):', error);
            }
        }
    };

    /**
     * Called by the Editor when a replacement attempt completes.
     * This implements the 'Two-Phase Commit' for accepting suggestions.
     */
    const handleReplacementApplied = async (success, pointId) => {
        // Clear the pending request regardless of success
        setPendingReplacement(null);

        if (!success) {
            // Error handling is done by the RichTextEditor (showErrorToast)
            return;
        }

        // Phase 2: Success! Update local state and persist to backend
        setReviewPoints(prev =>
            prev.map(p => p.id === pointId ? {
                ...p,
                userAccepted: true,
                originalText: p.suggestedReplacement || p.originalText // Sync text for future highlights
            } : p)
        );

        try {
            const updatedPoints = reviewPoints.map(p =>
                p.id === pointId ? {
                    ...p,
                    userAccepted: true,
                    originalText: p.suggestedReplacement || p.originalText
                } : p
            );

            // 1. Update review point metadata
            await docReviewService.updateReview(reviewSession.id, {
                reviewPoints: updatedPoints
            });

            // 2. AUTO-SAVE: Sync document content to Knowledge Hub
            if (currentDocument?.documentId) {
                await knowledgeHubDocumentService.updateDocumentContent(
                    currentDocument.documentId,
                    editorContentRef.current
                );
                console.log("💾 Auto-saved document content to Knowledge Hub after Accept");
            }

            // Final success toast is handled by Editor for UI feedback
        } catch (error) {
            console.error('Error persisting accepted state after replacement:', error);
            toast.error('Replacement succeeded but failed to save session status');
        }
    };

    const stripHtml = (html) => {
        if (!html) return '';
        const doc = new DOMParser().parseFromString(html, 'text/html');
        return doc.body.textContent || "";
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
                        statusMessages={statusMessages}
                        onSessionUpdate={(updates) => setReviewSession(prev => ({ ...prev, ...updates }))}
                        onLoadSession={async (session) => {
                            setReviewSession(session);

                            // Fetch latest metadata to ensure we have the correct contentType (especially if converted to HTML)
                            let docData = {
                                id: session.documentId,
                                documentId: session.documentId, // This is the UUID needed for API calls
                                fileName: session.documentName
                            };

                            try {
                                const response = await knowledgeHubDocumentService.getDocumentStatus(session.documentId);
                                if (response.success) {
                                    docData = response.data;
                                    console.log("📄 Loaded document metadata for session:", docData.contentType);
                                }
                            } catch (error) {
                                console.warn('Failed to fetch latest document metadata, falling back to session data:', error);
                            }

                            setCurrentDocument(docData);
                            // Always update review points, defaulting to empty if null
                            // This prevents stale suggestions from persisting when switching to a fresh session
                            setReviewPoints(session.reviewPoints || []);

                            toast.info('Loaded previous review session');
                        }}
                        getEditorContent={() => {
                            const selection = documentViewerRef.current?.getSelectionContext();
                            return {
                                content: editorContentRef.current,
                                selectedText: selection?.text || null
                            };
                        }}
                        activeSelection={activeSelection}
                        onApplyAiEdit={(newContent, type = 'full') => {
                            console.log(`[DocReviewAssistant] Applying AI edit. Type: ${type}, Content length: ${newContent?.length}`);

                            if (type === 'partial') {
                                const applied = documentViewerRef.current?.applyPartialUpdate(newContent);
                                console.log(`[DocReviewAssistant] Partial update result: ${applied}`);
                                if (applied) {
                                    toast.success('Document section updated by AI');
                                    return;
                                }
                                // If partial failed (e.g. no selection), fall back or notify
                                console.warn('AI requested partial update but no selection found/application failed.');
                                toast.info('No selection found. Applying change as full update.');
                            }
                            // Heuristic Safety Check: If type 'full' but suspiciously short
                            const currentLen = editorContentRef.current?.length || 0;
                            const newLen = newContent?.length || 0;
                            if (type === 'full' && currentLen > 1500 && newLen < currentLen * 0.4) {
                                console.warn("Detected likely fragment in full update tag. Attempting partial update.");
                                const applied = documentViewerRef.current?.applyPartialUpdate(newContent);
                                if (applied) {
                                    toast.info('AI suggested a fragment; applied to your selection/cursor to prevent data loss.');
                                    return;
                                } else {
                                    toast.error('AI suggested a replacement that seems incomplete. Update blocked.');
                                    return;
                                }
                            }

                            // Full document update
                            setExternalHtml(newContent);
                            editorContentRef.current = newContent;
                            toast.success('Document updated by AI');
                        }}
                    />
                </div>

                {/* Document Viewer - Main area */}
                <div className="flex-grow-1">
                    <DocumentViewer
                        ref={documentViewerRef}
                        onDocumentSelected={handleDocumentSelected}
                        document={currentDocument}
                        loading={loading}
                        reviewPoints={reviewPoints}
                        onReviewPointAction={handleReviewPointAction}
                        activePointId={activePointId}
                        onPointSelect={setActivePointId}
                        onSelectionChange={handleSelectionChange}
                        externalContent={externalHtml}
                        pendingReplacement={pendingReplacement}
                        onReplacementApplied={handleReplacementApplied}
                        onContentUpdated={(newContent, newVersion, isHighlight, isSuggestion) => {
                            // 1. Skip if this is just a highlight update or an automated suggestion application
                            if (isHighlight || isSuggestion) {
                                // Still need to update the ref if content changed
                                if (typeof newContent === 'string') {
                                    handleEditorChange(newContent);
                                }
                                return;
                            }

                            // 2. Check if text content actually changed to avoid clearing points on formatting
                            const oldText = stripHtml(editorContentRef.current);
                            const newText = newContent ? stripHtml(newContent) : oldText;
                            const textChanged = oldText !== newText;

                            // 3. Protection for initial load: if editorContentRef was empty, this is the first load from DB
                            const isInitialLoad = !oldText && !!newText;

                            // Update ref
                            if (typeof newContent === 'string') {
                                handleEditorChange(newContent);
                            }

                            // Clear review points ONLY if:
                            // - It's not the initial load (which is just document fetching)
                            // - AND (Text actually changed OR it's a new version/save/restore)
                            if (!isInitialLoad && (textChanged || newVersion)) {
                                console.log("🧹 Clearing review points due to content change or new version");
                                setReviewPoints([]);

                                // Update session with new version if provided
                                if (reviewSession) {
                                    const updates = { reviewPoints: [] };
                                    if (newVersion) {
                                        updates.documentVersion = newVersion;
                                    }
                                    setReviewSession(prev => ({ ...prev, ...updates }));

                                    // Persist version change to backend for the session if saved manually
                                    if (newVersion) {
                                        docReviewService.updateReview(reviewSession.id, {
                                            documentVersion: newVersion
                                        }).catch(err => console.error("Failed to sync version to session:", err));
                                    }
                                }
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
