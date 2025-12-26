import React, { useState, useEffect, useRef } from 'react';
import { Card, Form, Spinner } from 'react-bootstrap';
import Button from '../../../../components/common/Button';
import { HiPaperAirplane, HiInformationCircle, HiClock } from 'react-icons/hi';
import docReviewService from '../../../../services/docReviewService';
import { toast } from 'react-toastify';
import DocReviewHistoryModal from './DocReviewHistoryModal';

const ChatPane = ({ assistant, reviewSession, onSessionUpdate, onLoadSession }) => {
    const [messages, setMessages] = useState([]);
    const [inputText, setInputText] = useState('');
    const [sending, setSending] = useState(false);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [showHistory, setShowHistory] = useState(false);
    const messagesEndRef = useRef(null);

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

                const { conversationId, message: aiContent } = response.data;

                // Update review session with new conversationId
                await docReviewService.updateReview(reviewSession.id, {
                    conversationId: conversationId
                });

                const aiMsg = { role: 'assistant', content: aiContent, timestamp: new Date().toISOString() };
                setMessages(prev => [...prev, aiMsg]);

                if (onSessionUpdate) {
                    onSessionUpdate({ conversationId });
                }
            } else {
                // Continue EXISTING conversation
                response = await docReviewService.sendReviewMessage(reviewSession.conversationId, userMsg.content);

                const { message: aiContent } = response.data;
                const aiMsg = { role: 'assistant', content: aiContent, timestamp: new Date().toISOString() };
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
                    <div className="text-center text-muted mt-5">
                        <HiInformationCircle size={24} className="mb-2" />
                        <p>Select a contract to start the review.</p>
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
