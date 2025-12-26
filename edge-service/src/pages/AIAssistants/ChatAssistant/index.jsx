import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    Card,
    Breadcrumb,
    Badge,
    Form,
    Spinner,
    Tabs,
    Tab,
    Alert,
    Row,
    Col,
    OverlayTrigger,
    Tooltip,
    Table,
    Accordion
} from 'react-bootstrap';
import { HiArrowLeft, HiChatAlt, HiCode, HiClipboardCopy, HiCheck, HiPlus, HiDownload } from 'react-icons/hi';
import Button from '../../../components/common/Button';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import { LoadingSpinner } from '../../../components/common/LoadingSpinner';
import aiAssistantService from '../../../services/aiAssistantService';
import conversationService from '../../../services/conversationService';
import { knowledgeHubDocumentService } from '../../../services/knowledgeHubDocumentService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { Link } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import { formatDate } from '../../../utils/dateUtils';
import { chatWebSocketService } from '../../../services/chatWebSocketService';
import { executionMonitoringWebSocket } from '../../../services/executionMonitoringService';
import { HiX } from 'react-icons/hi';
import Footer from '../../../components/common/Footer';
import './styles.css';

function ChatAssistant({ assistantId: propAssistantId, assistant: propAssistant, embedded = false }) {
    const { assistantId: paramAssistantId } = useParams();
    const assistantId = propAssistantId || paramAssistantId;
    const navigate = useNavigate();
    const { currentTeam } = useTeam();
    const { user } = useAuth();
    const messagesEndRef = useRef(null);
    const [assistant, setAssistant] = useState(propAssistant || null);
    const [loading, setLoading] = useState(!propAssistant);
    // ... rest of the component

    const [error, setError] = useState(null);
    const [conversationId, setConversationId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [inputMessage, setInputMessage] = useState('');
    const [sending, setSending] = useState(false);
    const [activeTab, setActiveTab] = useState('chat');
    const [copiedCode, setCopiedCode] = useState(false);
    const [widgetScriptUrl, setWidgetScriptUrl] = useState(null);
    const [conversations, setConversations] = useState([]);
    const [loadingConversations, setLoadingConversations] = useState(false);
    const [selectedConversationId, setSelectedConversationId] = useState(null);
    const [streamingMessageId, setStreamingMessageId] = useState(null);
    const [streamingContent, setStreamingContent] = useState('');
    const [isCancelling, setIsCancelling] = useState(false);
    const [statusMessages, setStatusMessages] = useState([]); // Array of status messages to show multiple lines
    const isMonitoringExecutionsRef = useRef(false); // Track if we're monitoring executions
    const isLoadingOlderMessagesRef = useRef(false); // Track if we're loading older messages (to prevent auto-scroll)
    const [deleteModal, setDeleteModal] = useState({ show: false, conversation: null });
    const [hasAutoSelectedConversation, setHasAutoSelectedConversation] = useState(false);
    const [visibleMessageCount, setVisibleMessageCount] = useState(10); // Number of messages to display (starting from latest)

    useEffect(() => {
        if (assistantId && currentTeam) {
            if (!propAssistant) {
                loadAssistant();
            }
            loadConversations();
        }
    }, [assistantId, currentTeam, propAssistant]);

    useEffect(() => {
        // Update local state if prop updates
        if (propAssistant) {
            setAssistant(propAssistant);
            setLoading(false);
        }
    }, [propAssistant]);

    useEffect(() => {
        // Skip auto-scroll and auto-expand if we're loading older messages
        if (isLoadingOlderMessagesRef.current) {
            isLoadingOlderMessagesRef.current = false; // Reset flag after skip
            return;
        }

        // When new messages arrive, ensure they're visible by expanding visibleMessageCount if needed
        // This ensures new/streaming messages are always shown
        if (messages.length > visibleMessageCount) {
            const previousLength = messages.length - 1;
            // If we were already showing all messages before, or if a new message was just added, show it
            if (previousLength <= visibleMessageCount || messages.some(m => m.streaming)) {
                setVisibleMessageCount(messages.length);
            }
        }

        // Auto-scroll to bottom when new messages arrive or streaming content updates
        // Only if we're showing the latest messages (not scrolled up to view older ones)
        if (visibleMessageCount >= messages.length || messages.length <= 10) {
            scrollToBottom();
        }
    }, [messages, streamingContent, visibleMessageCount]);

    // Subscribe to WebSocket chat updates
    useEffect(() => {
        if (!conversationId) return;

        const unsubscribe = chatWebSocketService.subscribeToConversation(conversationId, (update) => {
            // console.log('💬 Received chat update:', update);

            switch (update.type) {
                case 'LLM_RESPONSE_STREAMING_STARTED':
                    // Clear all status messages - answer is about to arrive
                    setStatusMessages([]);
                    setMessages(prev => {
                        const alreadyStreaming = prev.some(msg => msg.streaming === true);
                        if (alreadyStreaming) {
                            return prev;
                        }
                        const streamingMsgId = `streaming-${Date.now()}`;
                        setStreamingMessageId(streamingMsgId);
                        setStreamingContent('');
                        return [
                            ...prev,
                            {
                                id: streamingMsgId,
                                role: 'assistant',
                                content: '',
                                createdAt: new Date().toISOString(),
                                streaming: true
                            }
                        ];
                    });
                    break;

                case 'LLM_RESPONSE_CHUNK':
                    // Update streaming content
                    if (update.accumulated) {
                        setStreamingContent(update.accumulated);
                        // Update the message in the array - find the streaming message by checking for streaming flag
                        setMessages(prev => prev.map(msg =>
                            msg.streaming === true
                                ? { ...msg, content: update.accumulated }
                                : msg
                        ));
                    }
                    break;

                case 'LLM_RESPONSE_STREAMING_COMPLETE':
                    // Mark streaming as complete - find message by streaming flag
                    setStreamingMessageId(null);
                    setStreamingContent('');
                    setMessages(prev => prev.map(msg =>
                        msg.streaming === true
                            ? { ...msg, streaming: false }
                            : msg
                    ));
                    setIsCancelling(false);
                    setSending(false); // Mark sending as complete
                    setStatusMessages([]); // Clear all status messages - answer is complete
                    isMonitoringExecutionsRef.current = false; // Stop monitoring executions
                    break;

                case 'LLM_RESPONSE_STREAMING_CANCELLED':
                    // Remove the streaming message - find by streaming flag
                    setStreamingMessageId(null);
                    setStreamingContent('');
                    setMessages(prev => prev.filter(msg => msg.streaming !== true));
                    setIsCancelling(false);
                    setSending(false); // Mark sending as complete
                    setStatusMessages([]); // Clear all status messages
                    isMonitoringExecutionsRef.current = false; // Stop monitoring executions
                    showErrorToast('Message generation cancelled');
                    break;

                case 'LLM_RESPONSE_RECEIVED':
                    // We intentionally ignore this event type on the frontend.
                    // Streaming is handled via LLM_RESPONSE_STREAMING_* events only.
                    break;

                case 'AGENT_TASKS_EXECUTING':
                    // Initialize status messages array - start tracking execution progress
                    setStatusMessages(['Starting agent tasks...']);
                    isMonitoringExecutionsRef.current = true; // Start monitoring executions
                    console.log('🤖 Agent tasks executing:', update.taskIds);
                    break;

                case 'AGENT_TASKS_COMPLETED':
                    // Add completion message and prepare for answer (avoid duplicates)
                    setStatusMessages(prev => {
                        const completionMessage = 'Agent tasks completed. Preparing answer...';
                        const lastMessage = prev.length > 0 ? prev[prev.length - 1] : '';
                        // Only add if different from last message to prevent duplicates
                        if (completionMessage !== lastMessage) {
                            return [...prev, completionMessage];
                        }
                        return prev;
                    });
                    isMonitoringExecutionsRef.current = false; // Stop monitoring executions
                    console.log('✅ Agent tasks completed:', update.executedTasks);
                    break;

                default:
                    console.log('💬 Unhandled chat update type:', update.type);
            }
        });

        // Connect WebSocket if not connected
        if (!chatWebSocketService.connected) {
            chatWebSocketService.connect();
        }

        return () => {
            unsubscribe();
        };
    }, [conversationId]); // Only depend on conversationId to avoid recreating subscription

    // Subscribe to execution monitoring WebSocket for step-by-step progress updates
    useEffect(() => {
        if (!conversationId || !currentTeam) return;

        let executionUnsubscribe = null;
        const executionStartTime = Date.now(); // Track when we started monitoring

        // Connect execution monitoring WebSocket if not connected
        if (!executionMonitoringWebSocket.connected) {
            executionMonitoringWebSocket.connect();
        }

        // Subscribe to execution updates - we'll filter based on monitoring flag
        executionUnsubscribe = executionMonitoringWebSocket.subscribe(data => {
            try {
                // Only process if we're actively monitoring
                if (!isMonitoringExecutionsRef.current) {
                    return;
                }

                // Filter out health messages and invalid data
                if (Array.isArray(data) || !data?.executionId) {
                    return;
                }

                // Only process updates for our team
                if (data.teamId !== currentTeam?.id) {
                    return;
                }

                // Only track executions that started after we began monitoring (within last 2 minutes)
                // This prevents showing updates from old executions
                const executionStart = data.startedAt ? new Date(data.startedAt).getTime() : 0;
                if (executionStart > 0 && executionStart < executionStartTime - 120000) { // 2 minutes before
                    return;
                }

                // Build user-friendly status message from execution progress
                const stepDescription = formatStepDescription(
                    data.currentStepTarget,
                    data.currentStepAction,
                    data.currentStepName,
                    data.currentStep || 0,
                    data.totalSteps || 0
                );

                // Update status messages - add new one if different from last
                setStatusMessages(prev => {
                    // Avoid duplicates - don't add if last message is the same
                    const lastMessage = prev.length > 0 ? prev[prev.length - 1] : '';

                    // Format the message with step progress if available
                    let newMessage = stepDescription;
                    if (data.status === 'STARTED') {
                        newMessage = 'Starting task execution...';
                    } else if (data.status === 'RUNNING' && data.currentStep > 0) {
                        newMessage = stepDescription;
                    } else if (data.status === 'COMPLETED') {
                        newMessage = 'Task execution completed.';
                    } else if (data.status === 'FAILED') {
                        newMessage = `Task execution failed: ${data.errorMessage || 'Unknown error'}`;
                    }

                    // Only add if different from last message
                    if (newMessage && newMessage !== lastMessage) {
                        return [...prev, newMessage];
                    }
                    return prev;
                });
            } catch (error) {
                console.error('Error processing execution update:', error);
            }
        });

        return () => {
            if (executionUnsubscribe) {
                executionUnsubscribe();
            }
        };
    }, [conversationId, currentTeam]); // Only depend on conversationId and currentTeam

    // Helper function to format user-friendly step description
    const formatStepDescription = (stepTarget, stepAction, stepDescription, currentStep, totalSteps) => {
        // Use description if available, otherwise build from target/action
        if (stepDescription) {
            return `${stepDescription}`;
        }

        // Build user-friendly description from target and action
        let description = '';

        // Handle Milvus operations
        if (stepTarget === 'api-gateway' && stepAction === 'create') {
            description = 'Searching knowledge base for relevant information...';
        } else if (stepTarget?.includes('milvus') || stepTarget === 'api-gateway') {
            if (stepAction === 'create' || stepAction === 'search') {
                description = 'Searching knowledge base...';
            } else {
                description = 'Processing knowledge base data...';
            }
        } else if (stepTarget?.includes('openai') || stepTarget?.includes('gemini') ||
            stepTarget?.includes('claude') || stepTarget?.includes('cohere')) {
            description = 'Generating answer using AI...';
        } else {
            // Generic fallback
            const targetName = stepTarget?.replace(/-/g, ' ') || 'service';
            const actionName = stepAction || 'processing';
            description = `${actionName.charAt(0).toUpperCase() + actionName.slice(1)} ${targetName}...`;
        }

        // Add step progress if available
        if (totalSteps > 0) {
            description = `Step ${currentStep} of ${totalSteps}: ${description}`;
        }

        return description;
    };

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    const loadAssistant = async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await aiAssistantService.getAssistant(assistantId);
            if (response.success) {
                setAssistant(response.data);

                // Load widget script URL if public
                if (response.data.accessControl?.type === 'PUBLIC') {
                    const widgetResponse = await aiAssistantService.getWidgetScript(assistantId);
                    if (widgetResponse.success) {
                        setWidgetScriptUrl(widgetResponse.scriptUrl);
                    }
                }
            } else {
                setError(response.error);
            }
        } catch (err) {
            setError('Failed to load AI assistant');
            console.error('Error loading AI assistant:', err);
        } finally {
            setLoading(false);
        }
    };

    const loadConversations = async () => {
        try {
            setLoadingConversations(true);
            const response = await conversationService.listConversations(assistantId);
            if (response.success && response.data) {
                // Convert Flux to array if needed
                const conversationsList = Array.isArray(response.data)
                    ? response.data
                    : response.data.content || [];
                setConversations(conversationsList);

                // Auto-select the first (latest) conversation on initial load if none is selected
                if (!hasAutoSelectedConversation && conversationsList.length > 0 && !selectedConversationId) {
                    const firstConversation = conversationsList[0];
                    setHasAutoSelectedConversation(true);
                    loadConversation(firstConversation.id);
                }
            }
        } catch (err) {
            console.error('Error loading conversations:', err);
        } finally {
            setLoadingConversations(false);
        }
    };

    /**
     * Download a Knowledge Hub document using its documentId.
     * Uses the existing knowledgeHubDocumentService to get a presigned URL,
     * then opens it in a new tab.
     */
    const handleDownloadKnowledgeDocument = async (documentId, label) => {
        if (!documentId) return;
        try {
            const { data, error } = await knowledgeHubDocumentService.generateDownloadUrl(documentId);
            if (error || !data?.downloadUrl) {
                throw new Error(error || 'Failed to generate download URL');
            }
            window.open(data.downloadUrl, '_blank');
        } catch (err) {
            console.error('Error downloading Knowledge Hub document:', err);
            showErrorToast(err.response?.data?.error || err.message || 'Failed to download document');
        }
    };

    const loadConversation = async (convId) => {
        try {
            setSelectedConversationId(convId);
            setConversationId(convId);
            setMessages([]);
            setVisibleMessageCount(10); // Reset to show latest 10 messages

            // Load messages for this conversation
            const messagesResponse = await conversationService.getMessages(convId);
            if (messagesResponse.success && messagesResponse.data) {
                const messagesList = Array.isArray(messagesResponse.data)
                    ? messagesResponse.data
                    : messagesResponse.data.content || [];

                // Convert to message format
                const formattedMessages = messagesList.map(msg => ({
                    id: msg.id || `msg-${Date.now()}-${Math.random()}`,
                    role: (msg.role || 'USER').toLowerCase() === 'user' ? 'user' : 'assistant',
                    content: msg.content || '',
                    createdAt: msg.timestamp || msg.createdAt || new Date().toISOString(),
                    metadata: msg.metadata
                }));

                setMessages(formattedMessages);
            }
        } catch (err) {
            console.error('Error loading conversation:', err);
            showErrorToast('Failed to load conversation');
        }
    };

    const loadOlderMessages = () => {
        // Set flag to prevent auto-scroll
        isLoadingOlderMessagesRef.current = true;

        // Get the container and save current scroll metrics
        const messagesContainer = document.querySelector('.chat-messages-container');
        if (!messagesContainer) {
            isLoadingOlderMessagesRef.current = false;
            return;
        }

        const previousScrollHeight = messagesContainer.scrollHeight;
        const previousScrollTop = messagesContainer.scrollTop;

        // Show 10 more messages (increase visible count)
        setVisibleMessageCount(prev => {
            const newCount = Math.min(prev + 10, messages.length);

            // After DOM updates, adjust scroll position to maintain view at the top where new messages appear
            // Use requestAnimationFrame to ensure DOM has updated
            requestAnimationFrame(() => {
                setTimeout(() => {
                    if (messagesContainer) {
                        const newScrollHeight = messagesContainer.scrollHeight;
                        const heightDifference = newScrollHeight - previousScrollHeight;

                        // Scroll to maintain the same content position
                        // Add the height difference to keep the same content visible at the top
                        messagesContainer.scrollTop = previousScrollTop + heightDifference;
                    }
                    // Reset flag after scroll adjustment
                    isLoadingOlderMessagesRef.current = false;
                }, 50); // Small delay to ensure DOM is fully updated
            });

            return newCount;
        });
    };

    const startNewConversation = () => {
        setSelectedConversationId(null);
        setConversationId(null);
        setMessages([]);
        setVisibleMessageCount(10); // Reset to show latest 10 messages
        setInputMessage('');
        // Don't reset hasAutoSelectedConversation - we still want to prevent auto-selection after user manually starts new
    };

    const handleConfirmDeleteConversation = async () => {
        const conv = deleteModal.conversation;
        if (!conv) return;
        try {
            await conversationService.deleteConversation(conv.id);
            showSuccessToast('Conversation deleted');

            setConversations(prev => prev.filter(c => c.id !== conv.id));

            if (conversationId === conv.id) {
                startNewConversation();
            }
        } catch (err) {
            console.error('Error deleting conversation:', err);
            showErrorToast('Failed to delete conversation');
        } finally {
            setDeleteModal({ show: false, conversation: null });
        }
    };

    const handleCancel = () => {
        if (!conversationId) return;

        setIsCancelling(true);
        chatWebSocketService.sendCancelRequest(conversationId);
        // Note: The actual cancellation will be handled via WebSocket update
    };

    const handleSendMessage = async (e) => {
        e.preventDefault();
        if (!inputMessage.trim() || sending) return;

        const userMessage = inputMessage.trim();
        setInputMessage('');
        setSending(true);
        setIsCancelling(false);
        setStreamingMessageId(null);
        setStreamingContent('');

        // Add user message to UI immediately
        const newUserMessage = {
            id: `temp-${Date.now()}`,
            role: 'user',
            content: userMessage,
            createdAt: new Date().toISOString()
        };
        setMessages(prev => [...prev, newUserMessage]);

        try {
            let response;
            if (!conversationId) {
                // Start new conversation
                response = await conversationService.startConversation(assistantId, userMessage);
                if (response.success && response.conversationId) {
                    const newConvId = response.conversationId;
                    const nowIso = new Date().toISOString();

                    setConversationId(newConvId);
                    setSelectedConversationId(newConvId);

                    // Optimistically add the new conversation to the sidebar immediately
                    setConversations(prev => {
                        // Avoid duplicates if it somehow already exists
                        if (prev.some(c => c.id === newConvId)) {
                            return prev;
                        }
                        const optimisticConversation = {
                            id: newConvId,
                            assistantId,
                            teamId: currentTeam?.id || currentTeam?._id,
                            title: userMessage.length > 80 ? `${userMessage.slice(0, 77)}...` : userMessage,
                            status: 'ACTIVE',
                            startedAt: nowIso,
                            lastMessageAt: nowIso,
                            messageCount: 1,
                            metadata: {
                                // Basic optimistic metadata; real values will come from reload
                                taskExecutions: 0,
                                successfulTasks: 0,
                                failedTasks: 0
                            }
                        };
                        return [optimisticConversation, ...prev];
                    });

                    // Reload conversations to sync with backend (runs in background)
                    loadConversations();

                    // For the very first message, we may miss WebSocket streaming because
                    // the subscription is attached after the conversation is created.
                    // As a safe fallback, render the assistant's reply from the HTTP response.
                    if (response.data?.message || response.data?.chatResult?.message) {
                        const finalMessage = response.data.message || response.data.chatResult.message;
                        setMessages(prev => {
                            // Avoid duplicating if somehow a streaming assistant message already exists
                            const exists = prev.some(
                                m => m.role === 'assistant' && m.content === finalMessage
                            );
                            if (exists) return prev;
                            return [
                                ...prev,
                                {
                                    id: response.data.messageId || `msg-${Date.now()}`,
                                    role: 'assistant',
                                    content: finalMessage,
                                    createdAt: new Date().toISOString(),
                                    metadata: response.data.chatResult?.metadata
                                }
                            ];
                        });
                    }
                }
            } else {
                // Send message to existing conversation
                response = await conversationService.sendMessage(conversationId, userMessage);
            }

            if (!response.success || !response.data) {
                showErrorToast(response.error || 'Failed to send message');
                // Remove the user message if it failed
                setMessages(prev => prev.filter(m => m.id !== newUserMessage.id));
            } else {
                // Enrich the latest assistant message with taskResults / executedTasks
                const executedTasks = response.data.executedTasks;
                const taskResults = response.data.taskResults;
                if (taskResults) {
                    setMessages(prev => {
                        const updated = [...prev];
                        for (let i = updated.length - 1; i >= 0; i--) {
                            const msg = updated[i];
                            if (msg.role === 'assistant') {
                                updated[i] = {
                                    ...msg,
                                    metadata: {
                                        ...(msg.metadata || {}),
                                        executedTasks,
                                        taskResults
                                    }
                                };
                                break;
                            }
                        }
                        return updated;
                    });
                }
            }
        } catch (err) {
            console.error('Error sending message:', err);
            showErrorToast('Failed to send message');
            // Remove the user message if it failed
            setMessages(prev => prev.filter(m => m.id !== newUserMessage.id));
        } finally {
            setSending(false);
        }
    };

    const handleCopyCode = (code) => {
        navigator.clipboard.writeText(code);
        setCopiedCode(true);
        showSuccessToast('Code copied to clipboard');
        setTimeout(() => setCopiedCode(false), 2000);
    };

    const getWidgetEmbedCode = () => {
        if (!assistant?.accessControl?.publicApiKey) {
            return null;
        }

        const baseUrl = window.location.origin;
        const publicApiKey = assistant.accessControl.publicApiKey;

        return {
            script: `<script src="${baseUrl}/widget/${publicApiKey}/script.js" async></script>`,
            iframe: `<iframe 
  src="${baseUrl}/widget/${publicApiKey}"
  width="400"
  height="600"
  frameborder="0"
  style="position: fixed; bottom: 20px; right: 20px; z-index: 9999;">
</iframe>`
        };
    };

    if (loading) {
        return <LoadingSpinner />;
    }

    if (error || !assistant) {
        return (
            <div className="view-assistant-page">
                <Alert variant="danger">
                    {error || 'AI Assistant not found'}
                </Alert>
                <Button variant="secondary" onClick={() => navigate('/ai-assistants')}>
                    <HiArrowLeft className="me-1" /> Back to AI Assistants
                </Button>
            </div>
        );
    }

    const embedCodes = getWidgetEmbedCode();
    const isPublic = assistant.accessControl?.type === 'PUBLIC';
    const currentConversation = conversations.find(c => c.id === selectedConversationId) || null;

    return (
        <div className={embedded ? "chat-assistant-embedded h-100" : "view-assistant-page"}>
            {!embedded && (
                <Card className="mb-4 border-0">
                    <Card.Body>
                        <div className="d-flex align-items-center gap-2 mb-2">
                            <Button
                                variant="link"
                                onClick={() => navigate('/ai-assistants')}
                                className="p-0"
                            >
                                <HiArrowLeft className="text-primary" size={24} />
                            </Button>
                            <h4 className="mb-0">
                                {assistant.name}
                            </h4>
                            <Badge bg={assistant.status === 'ACTIVE' ? 'success' : assistant.status === 'INACTIVE' ? 'secondary' : 'warning'}>
                                {assistant.status || 'DRAFT'}
                            </Badge>
                            <Badge bg={isPublic ? 'info' : 'secondary'}>
                                {isPublic ? 'Public' : 'Private'}
                            </Badge>
                        </div>
                        {assistant.description && (
                            <div className="text-muted text-start">
                                {assistant.description}
                            </div>
                        )}
                    </Card.Body>
                </Card>
            )}

            {/* Tabs */}
            <Tabs activeKey={activeTab} onSelect={(k) => setActiveTab(k)} className="mb-3">
                <Tab eventKey="chat" title={
                    <span>
                        <HiChatAlt className="me-1" /> Chat
                    </span>
                }>
                    <div className="chat-layout">
                        {/* Conversation History Sidebar */}
                        <div className="conversation-sidebar">
                            <div className="conversation-sidebar-header">
                                <h6 className="mb-0">Conversations</h6>
                                <Button
                                    variant="link"
                                    size="sm"
                                    onClick={startNewConversation}
                                    className="p-0"
                                    title="New Conversation"
                                >
                                    <HiPlus size={20} />
                                </Button>
                            </div>
                            <div className="conversation-list">
                                {loadingConversations ? (
                                    <div className="text-center p-3">
                                        <Spinner size="sm" />
                                    </div>
                                ) : conversations.length === 0 ? (
                                    <div className="text-center p-3 text-muted small">
                                        No conversations yet
                                    </div>
                                ) : (
                                    conversations.map((conv) => (
                                        <div
                                            key={conv.id}
                                            className={`conversation-item ${selectedConversationId === conv.id ? 'active' : ''}`}
                                        >
                                            <div
                                                className="d-flex justify-content-between align-items-center"
                                                onClick={() => loadConversation(conv.id)}
                                                style={{ cursor: 'pointer' }}
                                            >
                                                <div>
                                                    <div className="conversation-title">
                                                        {conv.title || 'New Conversation'}
                                                    </div>
                                                    <div className="conversation-meta">
                                                        <small className="text-muted">
                                                            {formatDate(conv.lastMessageAt || conv.startedAt, 'MMM d, HH:mm')}
                                                        </small>
                                                        {conv.messageCount > 0 && (
                                                            <Badge bg="secondary" className="ms-2" style={{ fontSize: '0.65rem' }}>
                                                                {conv.messageCount}
                                                            </Badge>
                                                        )}
                                                    </div>
                                                </div>
                                                <Button
                                                    variant="link"
                                                    size="sm"
                                                    className="p-0 text-danger"
                                                    title="Delete conversation"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        setDeleteModal({ show: true, conversation: conv });
                                                    }}
                                                >
                                                    <span style={{ fontSize: '1.1rem' }}>×</span>
                                                </Button>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>

                        {/* Chat Area */}
                        <div className="chat-area p-0">
                            {/* Current conversation header with delete action */}
                            {currentConversation && (
                                <div className="d-flex justify-content-between align-items-center px-3 pt-2 pb-1">
                                    <div className="text-muted small text-truncate">
                                        Conversation: {currentConversation.title || 'New Conversation'}
                                    </div>
                                    <Button
                                        variant="outline-danger"
                                        size="sm"
                                        onClick={() => setDeleteModal({ show: true, conversation: currentConversation })}
                                    >
                                        Delete conversation
                                    </Button>
                                </div>
                            )}
                            <Card className="p-0">
                                <Card.Body className="p-0">
                                    {/* Messages Area */}
                                    <div className="chat-messages-container">
                                        {messages.length === 0 ? (
                                            <div className="chat-empty-state">
                                                <HiChatAlt size={48} className="text-muted mb-3" />
                                                <h5 className="text-muted">Start a conversation</h5>
                                                <p className="text-muted">
                                                    {assistant.systemPrompt || 'Ask me anything!'}
                                                </p>
                                            </div>
                                        ) : (
                                            <div className="chat-messages">
                                                {/* Load older messages button - show when there are more messages than visible */}
                                                {messages.length > visibleMessageCount && (
                                                    <div className="text-center my-3">
                                                        <Button
                                                            variant="outline-secondary"
                                                            size="sm"
                                                            onClick={loadOlderMessages}
                                                        >
                                                            Load older messages ({messages.length - visibleMessageCount} more)
                                                        </Button>
                                                    </div>
                                                )}
                                                {/* Show only the last N messages (latest messages at the end of array) */}
                                                {messages.slice(-visibleMessageCount).map((message) => {
                                                    // Treat any message with streaming === true as actively streaming
                                                    const isStreaming = message.streaming === true;
                                                    return (
                                                        <div
                                                            key={message.id}
                                                            className={`chat-message ${message.role === 'user' ? 'user-message' : 'assistant-message'}`}
                                                        >
                                                            <div className={isStreaming ? "message-content d-flex justify-content-between align-items-start" : "message-content"}>
                                                                <div className="flex-grow-1">
                                                                    {message.role === 'assistant' ? (
                                                                        message.content ? (
                                                                            <ReactMarkdown>{message.content}</ReactMarkdown>
                                                                        ) : isStreaming ? (
                                                                            <span className="text-muted">Generating response...</span>
                                                                        ) : null
                                                                    ) : (
                                                                        <p className="mb-0">{message.content}</p>
                                                                    )}
                                                                </div>
                                                                {isStreaming && (
                                                                    <Button
                                                                        variant="link"
                                                                        size="sm"
                                                                        onClick={handleCancel}
                                                                        disabled={isCancelling}
                                                                        className="p-0 ms-2"
                                                                        title="Cancel generation"
                                                                    >
                                                                        <HiX size={20} className="text-danger" />
                                                                    </Button>
                                                                )}
                                                            </div>
                                                            <div className="message-timestamp">
                                                                {formatDate(message.createdAt, 'HH:mm')}
                                                            </div>
                                                            {/* Knowledge Hub document links - show when available from task results */}
                                                            {message.role === 'assistant' && message.metadata?.taskResults && (() => {
                                                                // Collect and deduplicate documents
                                                                // CRITICAL: Deduplicate by documentId FIRST - one documentId = one document
                                                                // Knowledge Graph may extract multiple form entities from the same document,
                                                                // but they all reference the same documentId/fileName
                                                                const docsMapByDocId = new Map(); // Primary dedup by documentId
                                                                const docsMapByFileName = new Map(); // Secondary dedup by fileName
                                                                const taskResults = message.metadata.taskResults;

                                                                Object.values(taskResults || {}).forEach(result => {
                                                                    if (!result) return;

                                                                    // If result has a nested 'documents' array, use that
                                                                    if (result && typeof result === 'object' && Array.isArray(result.documents)) {
                                                                        result.documents.forEach(doc => {
                                                                            if (doc && typeof doc === 'object') {
                                                                                // Handle RAG results structure (from Milvus search)
                                                                                // RAG results have documentId and fileName as direct fields or in metadata
                                                                                const docId = doc.documentId || doc.knowledgeHubDocumentId ||
                                                                                    (doc.metadata && doc.metadata.documentId) ||
                                                                                    (doc.metadatas && doc.metadatas.documentId);
                                                                                const fileName = doc.fileName || doc.title ||
                                                                                    (doc.metadata && doc.metadata.fileName) ||
                                                                                    (doc.metadatas && doc.metadatas.fileName);

                                                                                // PRIMARY: Deduplicate by documentId (one documentId = one document)
                                                                                if (docId) {
                                                                                    if (!docsMapByDocId.has(docId)) {
                                                                                        docsMapByDocId.set(docId, {
                                                                                            documentId: docId,
                                                                                            title: doc.title || fileName,
                                                                                            fileName: fileName,
                                                                                            // Don't store formName - Knowledge Graph joins all entities which causes duplicates
                                                                                            // We'll use fileName for display instead
                                                                                        });
                                                                                    }
                                                                                } else if (fileName) {
                                                                                    // Fallback: deduplicate by fileName if no documentId
                                                                                    const fileNameKey = fileName.toLowerCase();
                                                                                    if (!docsMapByFileName.has(fileNameKey)) {
                                                                                        docsMapByFileName.set(fileNameKey, {
                                                                                            documentId: docId,
                                                                                            title: doc.title || fileName,
                                                                                            fileName: fileName,
                                                                                        });
                                                                                    }
                                                                                }
                                                                            }
                                                                        });
                                                                    }

                                                                    // Also check for 'results' array (RAG search results structure)
                                                                    if (result && typeof result === 'object' && Array.isArray(result.results)) {
                                                                        result.results.forEach(record => {
                                                                            if (record && typeof record === 'object') {
                                                                                // RAG results from Milvus have documentId and fileName as direct fields
                                                                                const docId = record.documentId ||
                                                                                    (record.metadata && record.metadata.documentId);
                                                                                const fileName = record.fileName ||
                                                                                    (record.metadata && record.metadata.fileName);

                                                                                if (docId || fileName) {
                                                                                    if (docId && !docsMapByDocId.has(docId)) {
                                                                                        docsMapByDocId.set(docId, {
                                                                                            documentId: docId,
                                                                                            title: fileName,
                                                                                            fileName: fileName,
                                                                                        });
                                                                                    } else if (fileName && !docId) {
                                                                                        const fileNameKey = fileName.toLowerCase();
                                                                                        if (!docsMapByFileName.has(fileNameKey)) {
                                                                                            docsMapByFileName.set(fileNameKey, {
                                                                                                documentId: docId,
                                                                                                title: fileName,
                                                                                                fileName: fileName,
                                                                                            });
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        });
                                                                    }

                                                                    // Also support the older shape where result itself (or elements) are document objects
                                                                    const items = Array.isArray(result) ? result : [result];
                                                                    items.forEach(item => {
                                                                        if (item && typeof item === 'object') {
                                                                            const docId = item.documentId || item.knowledgeHubDocumentId;
                                                                            const fileName = item.fileName || item.title;

                                                                            // PRIMARY: Deduplicate by documentId (one documentId = one document)
                                                                            if (docId) {
                                                                                if (!docsMapByDocId.has(docId)) {
                                                                                    docsMapByDocId.set(docId, {
                                                                                        documentId: docId,
                                                                                        title: item.title,
                                                                                        fileName: fileName,
                                                                                    });
                                                                                }
                                                                            } else if (fileName) {
                                                                                // Fallback: deduplicate by fileName if no documentId
                                                                                const fileNameKey = fileName.toLowerCase();
                                                                                if (!docsMapByFileName.has(fileNameKey)) {
                                                                                    docsMapByFileName.set(fileNameKey, {
                                                                                        documentId: docId,
                                                                                        title: item.title,
                                                                                        fileName: fileName,
                                                                                    });
                                                                                }
                                                                            }
                                                                        }
                                                                    });
                                                                });

                                                                // Merge both maps (documentId takes precedence)
                                                                const allDocs = Array.from(docsMapByDocId.values());
                                                                docsMapByFileName.forEach((doc, fileNameKey) => {
                                                                    // Only add if not already present by documentId
                                                                    if (!allDocs.some(d => d.documentId === doc.documentId ||
                                                                        (doc.documentId && d.documentId === doc.documentId))) {
                                                                        allDocs.push(doc);
                                                                    }
                                                                });

                                                                const docs = allDocs;
                                                                if (!docs.length) return null;

                                                                // Show documents when available - they're already filtered for relevance by agent tasks
                                                                // Documents in taskResults come from Knowledge Graph or RAG searches that matched the query

                                                                const accordionKey = `docs-${message.id}`;

                                                                return (
                                                                    <div className="knowledge-doc-links mt-2">
                                                                        <Accordion defaultActiveKey="">
                                                                            <Accordion.Item eventKey={accordionKey}>
                                                                                <Accordion.Header className="text-start">
                                                                                    <span className="text-muted small">
                                                                                        {docs.length === 1
                                                                                            ? `Related Knowledge Hub document (${docs.length})`
                                                                                            : `Related Knowledge Hub documents (${docs.length})`}
                                                                                    </span>
                                                                                </Accordion.Header>
                                                                                <Accordion.Body>
                                                                                    {docs.length > 1 ? (
                                                                                        // Table format for multiple documents
                                                                                        <Table striped bordered hover size="sm" className="mb-0">
                                                                                            <thead>
                                                                                                <tr>
                                                                                                    <th style={{ width: '70%' }}>File Name</th>
                                                                                                    <th style={{ width: '30%' }}>Action</th>
                                                                                                </tr>
                                                                                            </thead>
                                                                                            <tbody>
                                                                                                {docs.map(doc => (
                                                                                                    <tr key={doc.documentId || doc.fileName}>
                                                                                                        <td>
                                                                                                            <code className="small">{doc.fileName || doc.title || 'N/A'}</code>
                                                                                                        </td>
                                                                                                        <td>
                                                                                                            <Button
                                                                                                                variant="outline-primary"
                                                                                                                size="sm"
                                                                                                                onClick={() => handleDownloadKnowledgeDocument(doc.documentId, doc.fileName)}
                                                                                                            >
                                                                                                                <HiDownload className="me-1" />
                                                                                                                Download
                                                                                                            </Button>
                                                                                                        </td>
                                                                                                    </tr>
                                                                                                ))}
                                                                                            </tbody>
                                                                                        </Table>
                                                                                    ) : (
                                                                                        // Button link for single document - use fileName, not formName
                                                                                        <div>
                                                                                            {docs.map(doc => (
                                                                                                <Button
                                                                                                    key={doc.documentId || doc.fileName}
                                                                                                    variant="link"
                                                                                                    size="sm"
                                                                                                    className="p-0 me-3 knowledge-doc-link d-inline-flex align-items-center"
                                                                                                    onClick={() => handleDownloadKnowledgeDocument(doc.documentId, doc.fileName)}
                                                                                                >
                                                                                                    <HiDownload className="me-1" />
                                                                                                    {doc.fileName || doc.title || 'Download document'}
                                                                                                </Button>
                                                                                            ))}
                                                                                        </div>
                                                                                    )}
                                                                                </Accordion.Body>
                                                                            </Accordion.Item>
                                                                        </Accordion>
                                                                    </div>
                                                                );
                                                            })()}
                                                        </div>
                                                    );
                                                })}
                                                {sending && !messages.some(msg => msg.role === 'assistant' && (msg.streaming || msg.id === streamingMessageId)) && (
                                                    <div className="chat-message assistant-message">
                                                        <div className="message-content">
                                                            {statusMessages.length > 0 ? (
                                                                <div>
                                                                    {statusMessages.map((statusMsg, index) => (
                                                                        <div key={index} className="d-flex align-items-center mb-1">
                                                                            {index === statusMessages.length - 1 ? (
                                                                                <Spinner size="sm" className="me-2" />
                                                                            ) : (
                                                                                <span className="me-2 text-success" style={{ fontSize: '0.875rem' }}>
                                                                                    ✓
                                                                                </span>
                                                                            )}
                                                                            <span className="text-muted" style={{ fontSize: '0.875rem' }}>
                                                                                {statusMsg}
                                                                            </span>
                                                                        </div>
                                                                    ))}
                                                                </div>
                                                            ) : (
                                                                <div className="d-flex align-items-center">
                                                                    <Spinner size="sm" className="me-2" />
                                                                    <span className="text-muted">Thinking...</span>
                                                                </div>
                                                            )}
                                                        </div>
                                                    </div>
                                                )}
                                                <div ref={messagesEndRef} />
                                            </div>
                                        )}
                                    </div>

                                    {/* Input Area */}
                                    <div className="chat-input-container">
                                        <Form onSubmit={handleSendMessage}>
                                            <div className="d-flex gap-2">
                                                {/*
                                          Consider a response \"in progress\" if there is any streaming message
                                          in the list or a non-null streamingMessageId.
                                        */}
                                                {(() => {
                                                    // We consider a response in progress as soon as the user sends
                                                    // (sending === true) OR when we have a streaming message.
                                                    const hasActiveStreaming =
                                                        sending || messages.some(m => m.streaming) || !!streamingMessageId;
                                                    return (
                                                        <>
                                                            <Form.Control
                                                                as="textarea"
                                                                rows={2}
                                                                value={inputMessage}
                                                                onChange={(e) => setInputMessage(e.target.value)}
                                                                placeholder="Type your message..."
                                                                disabled={hasActiveStreaming}
                                                                onKeyDown={(e) => {
                                                                    if (e.key === 'Enter' && !e.shiftKey) {
                                                                        e.preventDefault();
                                                                        if (!hasActiveStreaming) {
                                                                            handleSendMessage(e);
                                                                        }
                                                                    }
                                                                }}
                                                            />
                                                            {hasActiveStreaming ? (
                                                                <Button
                                                                    type="button"
                                                                    variant="danger"
                                                                    onClick={handleCancel}
                                                                    disabled={isCancelling}
                                                                >
                                                                    {isCancelling ? (
                                                                        <>
                                                                            <Spinner size="sm" className="me-1" />
                                                                            Cancelling...
                                                                        </>
                                                                    ) : (
                                                                        'Cancel'
                                                                    )}
                                                                </Button>
                                                            ) : (
                                                                <Button
                                                                    type="submit"
                                                                    variant="primary"
                                                                    disabled={!inputMessage.trim() || sending}
                                                                >
                                                                    {sending ? (
                                                                        <>
                                                                            <Spinner size="sm" className="me-1" />
                                                                            Sending...
                                                                        </>
                                                                    ) : (
                                                                        'Send'
                                                                    )}
                                                                </Button>
                                                            )}
                                                        </>
                                                    );
                                                })()}
                                            </div>
                                        </Form>
                                    </div>
                                </Card.Body>
                            </Card>
                        </div>
                    </div>
                </Tab>

                {isPublic && (
                    <Tab eventKey="widget" title={
                        <span>
                            <HiCode className="me-1" /> Widget Embedding
                        </span>
                    }>
                        <Card className="p-0">
                            <Card.Header>
                                <h5 className="mb-0">Embed Widget on Your Website</h5>
                            </Card.Header>
                            <Card.Body>
                                <Alert variant="info">
                                    Copy the code below and paste it into your website's HTML to embed this AI Assistant as a chat widget.
                                </Alert>

                                {embedCodes && (
                                    <>
                                        <Row className="mb-4">
                                            <Col md={12}>
                                                <h6>Method 1: JavaScript Widget Script (Recommended)</h6>
                                                <p className="text-muted small">
                                                    Add this script tag to your HTML. The widget will automatically appear.
                                                </p>
                                                <div className="code-block-container">
                                                    <div className="d-flex justify-content-between align-items-center mb-2">
                                                        <small className="text-muted">Embed Script</small>
                                                        <Button
                                                            variant="link"
                                                            size="sm"
                                                            onClick={() => handleCopyCode(embedCodes.script)}
                                                            className="p-0"
                                                        >
                                                            {copiedCode ? (
                                                                <>
                                                                    <HiCheck className="me-1 text-success" /> Copied!
                                                                </>
                                                            ) : (
                                                                <>
                                                                    <HiClipboardCopy className="me-1" /> Copy
                                                                </>
                                                            )}
                                                        </Button>
                                                    </div>
                                                    <pre className="code-block">
                                                        <code>{embedCodes.script}</code>
                                                    </pre>
                                                </div>
                                            </Col>
                                        </Row>

                                        <Row>
                                            <Col md={12}>
                                                <h6>Method 2: Iframe Embedding</h6>
                                                <p className="text-muted small">
                                                    Embed the widget as an iframe for more control over positioning and styling.
                                                </p>
                                                <div className="code-block-container">
                                                    <div className="d-flex justify-content-between align-items-center mb-2">
                                                        <small className="text-muted">Iframe Code</small>
                                                        <Button
                                                            variant="link"
                                                            size="sm"
                                                            onClick={() => handleCopyCode(embedCodes.iframe)}
                                                            className="p-0"
                                                        >
                                                            {copiedCode ? (
                                                                <>
                                                                    <HiCheck className="me-1 text-success" /> Copied!
                                                                </>
                                                            ) : (
                                                                <>
                                                                    <HiClipboardCopy className="me-1" /> Copy
                                                                </>
                                                            )}
                                                        </Button>
                                                    </div>
                                                    <pre className="code-block">
                                                        <code>{embedCodes.iframe}</code>
                                                    </pre>
                                                </div>
                                            </Col>
                                        </Row>
                                    </>
                                )}

                                {!embedCodes && (
                                    <Alert variant="warning">
                                        This assistant is not configured for public access. Please enable public access and generate a public API key in the assistant settings.
                                    </Alert>
                                )}
                            </Card.Body>
                        </Card>
                    </Tab>
                )}
            </Tabs>

            {/* Delete Conversation Confirmation Modal */}
            <ConfirmationModal
                show={deleteModal.show}
                onHide={() => setDeleteModal({ show: false, conversation: null })}
                onConfirm={handleConfirmDeleteConversation}
                title="Delete Conversation"
                message={
                    deleteModal.conversation
                        ? `Are you sure you want to delete this conversation and all of its messages?\n\n"${deleteModal.conversation.title || 'Untitled conversation'}"`
                        : 'Are you sure you want to delete this conversation and all of its messages?'
                }
                confirmLabel="Delete"
                cancelLabel="Cancel"
                variant="danger"
            />
            <Footer />
        </div>
    );
}

export default ChatAssistant;

