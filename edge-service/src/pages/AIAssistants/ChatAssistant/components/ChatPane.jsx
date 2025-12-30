import React, { useState, useEffect, useRef } from 'react';
import {
    Badge,
    Form,
    Spinner,
    Alert,
} from 'react-bootstrap';
import { HiChatAlt, HiCode, HiClipboardCopy, HiCheck, HiDownload, HiPaperAirplane, HiClock, HiStop } from 'react-icons/hi';
import Button from '../../../../components/common/Button';
import ReactMarkdown from 'react-markdown';
import { formatDate } from '../../../../utils/dateUtils';

const ChatPane = ({
    assistant,
    user,
    messages,
    statusMessages,
    conversationTitle,
    selectedConversationId,
    showSidebar,
    onToggleSidebar,
    onToggleCodeTab,
    onSendMessage,
    onCancel,
    sending,
    isCancelling,
    errorMessage,
    setErrorMessage,
    onDownloadDocument
}) => {
    const [inputMessage, setInputMessage] = useState('');
    const [copiedCode, setCopiedCode] = useState(false);
    const [visibleMessageCount, setVisibleMessageCount] = useState(10);
    const [waitingTooLong, setWaitingTooLong] = useState(false);
    const messagesEndRef = useRef(null);
    const isLoadingOlderMessagesRef = useRef(false);
    const timeoutRef = useRef(null);

    // Timeout indicator: show "Taking longer than expected..." after 30 seconds
    useEffect(() => {
        if (sending) {
            setWaitingTooLong(false);
            timeoutRef.current = setTimeout(() => {
                setWaitingTooLong(true);
            }, 30000); // 30 seconds
        } else {
            setWaitingTooLong(false);
            if (timeoutRef.current) {
                clearTimeout(timeoutRef.current);
                timeoutRef.current = null;
            }
        }
        return () => {
            if (timeoutRef.current) {
                clearTimeout(timeoutRef.current);
            }
        };
    }, [sending]);

    useEffect(() => {
        if (messages.length > visibleMessageCount) {
            const previousLength = messages.length - 1;
            // If we were already showing all messages before, or if a new message was just added (streaming or not), show it
            if (previousLength <= visibleMessageCount || messages.some(m => m.streaming)) {
                setVisibleMessageCount(messages.length);
            }
        }

        // Auto-scroll to bottom when new messages arrive, streaming updates, or sending starts
        // Only if we're showing the latest messages or just started sending
        if (visibleMessageCount >= messages.length || messages.length <= 10 || sending) {
            scrollToBottom();
        }
    }, [messages, visibleMessageCount, statusMessages, sending]);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    const loadOlderMessages = () => {
        isLoadingOlderMessagesRef.current = true;
        const messagesContainer = document.querySelector('.chat-messages-container');
        if (!messagesContainer) {
            isLoadingOlderMessagesRef.current = false;
            return;
        }

        const previousScrollHeight = messagesContainer.scrollHeight;
        const previousScrollTop = messagesContainer.scrollTop;

        setVisibleMessageCount(prev => {
            const newCount = Math.min(prev + 10, messages.length);
            requestAnimationFrame(() => {
                setTimeout(() => {
                    if (messagesContainer) {
                        const newScrollHeight = messagesContainer.scrollHeight;
                        const heightDifference = newScrollHeight - previousScrollHeight;
                        messagesContainer.scrollTop = previousScrollTop + heightDifference;
                    }
                    isLoadingOlderMessagesRef.current = false;
                }, 50);
            });
            return newCount;
        });
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!inputMessage.trim() || sending) return;
        onSendMessage(inputMessage);
        setInputMessage('');
    };

    const handleCopyCode = (code) => {
        navigator.clipboard.writeText(code);
        setCopiedCode(true);
        setTimeout(() => setCopiedCode(false), 2000);
    };

    // Helper to get documents from any location (direct, additionalData, or taskResults)
    const getDocuments = (msg) => {
        const directDocs = msg.metadata?.documents || [];
        const additionalDocs = msg.metadata?.additionalData?.documents || [];

        // Extract documents from taskResults
        let taskDocs = [];
        if (msg.metadata?.taskResults) {
            Object.values(msg.metadata.taskResults).forEach(result => {
                if (result && typeof result === 'object') {
                    if (Array.isArray(result.documents)) {
                        taskDocs = [...taskDocs, ...result.documents];
                    }
                }
            });
        }

        // Combine all unique documents (by documentId or name)
        const allDocs = [...directDocs, ...additionalDocs, ...taskDocs];

        // Deduplicate
        const uniqueDocs = [];
        const seenIds = new Set();

        allDocs.forEach(doc => {
            // Normalize name: use name, fileName, or title
            const name = doc.name || doc.fileName || doc.title || 'Untitled Document';
            const normalizedDoc = { ...doc, name };

            const id = doc.documentId || doc.id || name;
            if (id && !seenIds.has(id)) {
                seenIds.add(id);
                uniqueDocs.push(normalizedDoc);
            }
        });

        return uniqueDocs;
    };

    return (
        <div className="d-flex flex-column flex-grow-1 bg-white" style={{ minWidth: 0 }}>
            {/* Header */}
            <div className="p-3 border-bottom d-flex justify-content-between align-items-center" style={{ height: '60px' }}>
                <div className="d-flex align-items-center gap-2">
                    <Button
                        variant="link"
                        size="sm"
                        className="p-0 text-muted me-2"
                        onClick={onToggleSidebar}
                        title={showSidebar ? "Collapse Sidebar" : "Expand Sidebar"}
                    >
                        <HiChatAlt size={20} />
                    </Button>
                    <h5 className="mb-0">
                        {selectedConversationId ?
                            (conversationTitle || 'Current Chat')
                            : 'New Chat'}
                    </h5>
                    {assistant?.accessControl?.type === 'PUBLIC' && (
                        <Badge bg="info" className="fw-normal">Public</Badge>
                    )}
                </div>
                <div>
                    {selectedConversationId && (
                        <Button
                            variant="outline-secondary"
                            size="sm"
                            className="me-2"
                            onClick={onToggleCodeTab}
                        >
                            <HiCode className="me-1" /> Widget
                        </Button>
                    )}
                </div>
            </div>

            {/* Chat Messages Area */}
            <div className="flex-grow-1 overflow-auto p-4 chat-messages-container bg-light">
                {messages.length === 0 ? (
                    <div className="h-100 d-flex flex-column align-items-center justify-content-center text-muted">
                        <div className="mb-3 bg-white p-4 rounded-circle shadow-sm">
                            <HiChatAlt size={48} className="text-primary opacity-50" />
                        </div>
                        <h5>Welcome to {assistant?.name}</h5>
                        <p className="mb-0">Start a new conversation to begin.</p>
                    </div>
                ) : (
                    <div className="d-flex flex-column gap-3 p-2">
                        {/* Load More Trigger */}
                        {messages.length > visibleMessageCount && !isLoadingOlderMessagesRef.current && (
                            <div className="mb-3 text-start">
                                <Button
                                    variant="link"
                                    size="sm"
                                    onClick={loadOlderMessages}
                                >
                                    Load older messages
                                </Button>
                            </div>
                        )}

                        {messages.slice(-visibleMessageCount).map((msg, index) => {
                            const documents = getDocuments(msg);
                            return (
                                <div
                                    key={msg.id || index}
                                    className={`d-flex flex-column ${msg.role === 'user' ? 'align-items-end' : 'align-items-start'}`}
                                >

                                    <div
                                        className={`p-3 rounded-3 shadow-sm ${msg.role === 'user' ? 'bg-primary text-white' : 'bg-white border'}`}
                                        style={{ maxWidth: '80%', minWidth: '200px' }}
                                    >
                                        <div className="message-content text-start text-break">
                                            {msg.role === 'user' ? (
                                                <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
                                            ) : (
                                                <ReactMarkdown
                                                    components={{
                                                        code({ node, inline, className, children, ...props }) {
                                                            const match = /language-(\w+)/.exec(className || '')
                                                            return !inline && match ? (
                                                                <div className="position-relative">
                                                                    <div className="d-flex justify-content-between align-items-center bg-light px-2 py-1 rounded-top border-bottom">
                                                                        <small className="text-muted font-monospace">{match[1]}</small>
                                                                        <button
                                                                            onClick={() => handleCopyCode(String(children).replace(/\n$/, ''))}
                                                                            className="btn btn-link btn-sm p-0 text-muted"
                                                                            title="Copy code"
                                                                        >
                                                                            {copiedCode ? <HiCheck /> : <HiClipboardCopy />}
                                                                        </button>
                                                                    </div>
                                                                    <code className={className} {...props} style={{ display: 'block', padding: '1rem', overflowX: 'auto', backgroundColor: '#f8f9fa' }}>
                                                                        {children}
                                                                    </code>
                                                                </div>
                                                            ) : (
                                                                <code className={className} {...props} style={inline ? { backgroundColor: 'rgba(0,0,0,0.05)', padding: '0.2em 0.4em', borderRadius: '3px' } : {}}>
                                                                    {children}
                                                                </code>
                                                            )
                                                        }
                                                    }}
                                                >
                                                    {msg.content}
                                                </ReactMarkdown>
                                            )}
                                        </div>
                                        {/* Document Source Attachments */}
                                        {documents.length > 0 && (
                                            <div className="mt-3 pt-2 border-top">
                                                <h6 className="small fw-bold mb-2 text-start">Sources:</h6>
                                                <div className="d-flex flex-column gap-2">
                                                    {documents.map((doc, i) => (
                                                        <div key={i} className="d-flex align-items-center justify-content-between bg-light p-2 rounded border small">
                                                            <div className="d-flex align-items-center text-truncate">
                                                                <HiCode className="me-2 text-primary" />
                                                                <span className="text-truncate" title={doc.name}>{doc.name}</span>
                                                            </div>
                                                            <Button
                                                                variant="link"
                                                                size="sm"
                                                                className="p-0 ms-2"
                                                                title="Download/View"
                                                                onClick={() => {
                                                                    if (doc.url) {
                                                                        window.open(doc.url, '_blank');
                                                                    } else if (onDownloadDocument && (doc.id || doc.documentId)) {
                                                                        onDownloadDocument(doc.id || doc.documentId, doc.name);
                                                                    }
                                                                }}
                                                            >
                                                                <HiDownload />
                                                            </Button>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                        <div className={`text-end mt-1 small ${msg.role === 'user' ? 'text-white-50' : 'text-muted'}`} style={{ fontSize: '0.7rem' }}>
                                            <span className="me-2 fw-bold">{msg.role === 'user' ? (user?.name || user?.username || 'You') : (assistant?.name || 'Assistant')}</span>
                                            {formatDate(msg.createdAt, 'HH:mm')}
                                            {msg.metadata?.executionTime && (
                                                <span className="ms-2">
                                                    <HiClock className="me-1" />
                                                    {msg.metadata.executionTime}ms
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            )
                        })}
                        {[...statusMessages].reverse().map((status, idx) => {
                            // Since we reversed, the "last" (latest) message is now at index 0
                            const isLatest = idx === 0;
                            const isCompleted = status.includes('completed') || status.includes('Success');

                            return (
                                <div key={idx} className={`text-start small my-1 ms-3 ${isLatest ? 'text-primary fw-medium' : 'text-muted'}`}>
                                    {isLatest && !isCompleted ? (
                                        <Spinner size="sm" animation="grow" variant="primary" className="me-2" />
                                    ) : (
                                        <HiCheck className="me-2 text-primary" />
                                    )}
                                    {status}
                                </div>
                            );
                        })}
                        {waitingTooLong && sending && (
                            <div className="text-start small my-1 ms-3 text-warning fw-medium">
                                <HiClock className="me-2" />
                                Taking longer than expected... The AI model is still processing your request.
                            </div>
                        )}
                        <div ref={messagesEndRef} />
                    </div>
                )}
            </div>

            {/* Input Area */}
            <div className="p-4 bg-white border-top">
                <div className="w-100">
                    {errorMessage && (
                        <Alert variant="danger" dismissible onClose={() => setErrorMessage(null)} className="mb-2">
                            {errorMessage}
                        </Alert>
                    )}
                    <Form onSubmit={handleSubmit} className="position-relative">
                        <Form.Control
                            as="textarea"
                            rows={1}
                            placeholder="Type your message..."
                            value={inputMessage}
                            onChange={(e) => setInputMessage(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter' && !e.shiftKey) {
                                    e.preventDefault();
                                    handleSubmit(e);
                                }
                                // Auto-resize
                                e.target.style.height = 'auto';
                                e.target.style.height = Math.min(e.target.scrollHeight, 150) + 'px';
                            }}
                            disabled={sending || isCancelling}
                            className="pr-5 py-3 shadow-sm border-0 bg-light"
                            style={{
                                resize: 'none',
                                paddingRight: '50px',
                                borderRadius: '24px',
                                transition: 'all 0.2s',
                                minHeight: '50px'
                            }}
                        />
                        <div className="position-absolute bottom-0 end-0 p-2 me-1">
                            {sending ? (
                                <Button
                                    variant="primary"
                                    className="rounded-circle d-flex align-items-center justify-content-center p-0 btn-pulse-glow"
                                    style={{ width: '32px', height: '32px' }}
                                    onClick={onCancel}
                                    title="Stop generating"
                                >
                                    <HiStop size={14} />
                                </Button>
                            ) : (
                                <Button
                                    type="submit"
                                    variant="primary"
                                    disabled={!inputMessage.trim() || sending}
                                    className="rounded-circle d-flex align-items-center justify-content-center p-0"
                                    style={{ width: '32px', height: '32px' }}
                                >
                                    <HiPaperAirplane size={14} className="mx-1" />
                                </Button>
                            )}
                        </div>
                    </Form>
                    <div className="text-center mt-2">
                        <small className="text-muted" style={{ fontSize: '0.7rem' }}>
                            AI can make mistakes. Consider checking important information.
                        </small>
                    </div>
                </div>
            </div >
        </div >
    );
};

export default ChatPane;
