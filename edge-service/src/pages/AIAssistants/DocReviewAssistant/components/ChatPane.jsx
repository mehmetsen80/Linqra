import React, { useState, useEffect, useRef } from 'react';
import { Card, Form, Spinner, ListGroup, Badge } from 'react-bootstrap';
import Button from '../../../../components/common/Button';
import { HiPaperAirplane, HiInformationCircle, HiClock, HiCode, HiDownload, HiDocumentText } from 'react-icons/hi';
import docReviewService from '../../../../services/docReviewService';
import { toast } from 'react-toastify';
import DocReviewHistoryModal from './DocReviewHistoryModal';
import { useTeam } from '../../../../contexts/TeamContext';

const ChatPane = ({ assistant, reviewSession, onSessionUpdate, onLoadSession }) => {
    const { currentTeam } = useTeam();
    const [messages, setMessages] = useState([]);
    const [inputText, setInputText] = useState('');
    const [sending, setSending] = useState(false);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [showHistory, setShowHistory] = useState(false);
    const [recentReviews, setRecentReviews] = useState([]);
    const [loadingRecent, setLoadingRecent] = useState(false);
    const messagesEndRef = useRef(null);

    // Fetch recent reviews on mount
    useEffect(() => {
        if (currentTeam && !reviewSession) {
            fetchRecentReviews();
        }
    }, [currentTeam, reviewSession]);

    const fetchRecentReviews = async () => {
        if (!currentTeam?.id) return;
        setLoadingRecent(true);
        try {
            const response = await docReviewService.getReviewsByTeam(currentTeam.id);
            if (response.success) {
                // Sort by createdAt desc, take top 5
                const sorted = response.data
                    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
                    .slice(0, 5);
                setRecentReviews(sorted);
            }
        } catch (error) {
            console.error('Error fetching recent reviews:', error);
        } finally {
            setLoadingRecent(false);
        }
    };

    // Fetch history when conversationId is available
    useEffect(() => {
        if (reviewSession?.conversationId) {
            fetchHistory(reviewSession.conversationId);
        } else {
            setMessages([]);
        }
    }, [reviewSession?.conversationId]);

    const fetchHistory = async (conversationId) => {
        setLoadingHistory(true);
        try {
            const response = await docReviewService.getConversationMessages(conversationId);
            // Sort by timestamp if needed, but backend usually handles it. 
            // Asserting backend returns Flux/Array of messages
            setMessages(response.data);
        } catch (error) {
            console.error('Error fetching history:', error);
            toast.error('Failed to load chat history');
        } finally {
            setLoadingHistory(false);
        }
    };

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const handleSend = async (e) => {
        e.preventDefault();
        if (!inputText.trim()) return;

        if (!reviewSession) {
            toast.warning('Please select a document first.');
            return;
        }

        const userMsg = { role: 'user', content: inputText, timestamp: new Date().toISOString() };
        setMessages(prev => [...prev, userMsg]);
        setInputText('');
        setSending(true);

        try {
            let response;

            if (!reviewSession.conversationId) {
                // Start NEW conversation
                if (!assistant) {
                    throw new Error('Assistant not initialized');
                }
                response = await docReviewService.startReviewConversation(assistant.id, userMsg.content, {
                    contractReviewId: reviewSession.id,
                    documentId: reviewSession.documentId
                });

                const { conversationId, message: aiContent, metadata } = response.data;

                // Update review session with new conversationId
                await docReviewService.updateReview(reviewSession.id, {
                    conversationId: conversationId
                });

                const aiMsg = { role: 'assistant', content: aiContent, timestamp: new Date().toISOString(), metadata };
                setMessages(prev => [...prev, aiMsg]);

                if (onSessionUpdate) {
                    onSessionUpdate({ conversationId });
                }
            } else {
                // Continue EXISTING conversation
                response = await docReviewService.sendReviewMessage(reviewSession.conversationId, userMsg.content);

                const { message: aiContent, metadata } = response.data;
                const aiMsg = { role: 'assistant', content: aiContent, timestamp: new Date().toISOString(), metadata };
                setMessages(prev => [...prev, aiMsg]);
            }

        } catch (error) {
            console.error('Error sending message:', error);
            toast.error('Failed to send message');
            setMessages(prev => [...prev, { role: 'system', content: 'Error: Could not send message. Please try again.' }]);
        } finally {
            setSending(false);
        }
    };

    return (
        <div className="d-flex flex-column h-100 bg-light border-end">
            <div className="p-3 border-bottom bg-white d-flex align-items-center justify-content-between">
                <div>
                    <h5 className="mb-0">AI Assistant</h5>
                    <small className="text-muted">Doc Analysis Expert</small>
                </div>
                <div className="d-flex align-items-center gap-2">
                    {sending && <Spinner size="sm" animation="border" variant="primary" />}
                    <Button
                        variant="link"
                        className="p-1 text-secondary"
                        onClick={() => setShowHistory(true)}
                        title="Review History"
                    >
                        <HiClock size={20} />
                    </Button>
                </div>
            </div>

            <div className="flex-grow-1 p-3 overflow-auto" style={{ minHeight: 0 }}>
                {!reviewSession ? (
                    <div className="mt-3">
                        <div className="text-center text-muted mb-4">
                            <HiInformationCircle size={24} className="mb-2" />
                            <p>Select a document to start a new review.</p>
                        </div>

                        {/* Recent Reviews Section */}
                        {loadingRecent ? (
                            <div className="text-center py-3">
                                <Spinner size="sm" animation="border" variant="primary" />
                            </div>
                        ) : recentReviews.length > 0 && (
                            <div className="mt-3">
                                <h6 className="text-muted mb-3 d-flex align-items-center">
                                    <HiClock className="me-2" />
                                    Recent Reviews
                                </h6>
                                <ListGroup variant="flush">
                                    {recentReviews.map(review => (
                                        <ListGroup.Item
                                            key={review.id}
                                            action
                                            onClick={() => onLoadSession(review)}
                                            className="d-flex align-items-center justify-content-between py-2 px-2 small"
                                        >
                                            <div className="d-flex align-items-center text-truncate">
                                                <HiDocumentText className="text-secondary me-2 flex-shrink-0" />
                                                <span className="text-truncate">{review.documentName}</span>
                                            </div>
                                            <Badge
                                                bg={review.status === 'COMPLETED' ? 'success' : 'primary'}
                                                className="ms-2 flex-shrink-0"
                                            >
                                                {review.status}
                                            </Badge>
                                        </ListGroup.Item>
                                    ))}
                                </ListGroup>
                            </div>
                        )}
                    </div>
                ) : messages.length === 0 && !loadingHistory ? (
                    <div className="text-muted text-center mt-5">
                        <p>Start the conversation by asking a question about the contract.</p>
                    </div>
                ) : (
                    <div className="d-flex flex-column gap-3">
                        {messages.map((msg, idx) => (
                            <div
                                key={idx}
                                className={`p-3 rounded shadow-sm ${msg.role === 'user' ? 'bg-primary text-white align-self-end' : 'bg-white align-self-start'}`}
                                style={{ maxWidth: '85%' }}
                            >
                                {msg.content}
                                {/* Document Source Attachments */}
                                {msg.metadata?.documents && msg.metadata.documents.length > 0 && (
                                    <div className={`mt-3 pt-2 border-top ${msg.role === 'user' ? 'border-primary-light text-white-50' : 'border-light'}`}>
                                        <h6 className="small fw-bold mb-2">Sources:</h6>
                                        <div className="d-flex flex-column gap-2">
                                            {msg.metadata.documents.map((doc, i) => (
                                                <div key={i} className={`d-flex align-items-center justify-content-between p-2 rounded small ${msg.role === 'user' ? 'bg-primary-dark' : 'bg-light'}`}>
                                                    <div className="d-flex align-items-center text-truncate">
                                                        <HiCode className="me-2 opacity-75" />
                                                        <span className="text-truncate" title={doc.name}>{doc.name}</span>
                                                    </div>
                                                    <Button
                                                        variant="link"
                                                        size="sm"
                                                        className={`p-0 ms-2 ${msg.role === 'user' ? 'text-white' : ''}`}
                                                        title="Download"
                                                        onClick={() => {
                                                            if (doc.url) window.open(doc.url, '_blank');
                                                            // For now assuming doc.url or future download handler
                                                        }}
                                                    >
                                                        <HiDownload />
                                                    </Button>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        ))}
                        {loadingHistory && <div className="text-center"><Spinner size="sm" animation="border" /></div>}
                        <div ref={messagesEndRef} />
                    </div>
                )}
            </div>

            <div className="p-3 bg-white border-top">
                <Form className="d-flex gap-2" onSubmit={handleSend}>
                    <Form.Control
                        type="text"
                        placeholder={reviewSession ? "Ask about this contract..." : "Select a document first"}
                        className="rounded-pill"
                        value={inputText}
                        onChange={(e) => setInputText(e.target.value)}
                        disabled={!reviewSession || sending}
                    />
                    <Button
                        type="submit"
                        variant="primary"
                        className="rounded-circle d-flex align-items-center justify-content-center"
                        style={{ width: '38px', height: '38px' }}
                        disabled={!reviewSession || sending || !inputText.trim()}
                    >
                        <HiPaperAirplane />
                    </Button>
                </Form>
            </div>

            <DocReviewHistoryModal
                show={showHistory}
                onHide={() => setShowHistory(false)}
                onSelect={(session) => {
                    if (onLoadSession) onLoadSession(session);
                }}
            />
        </div>
    );
};

export default ChatPane;
