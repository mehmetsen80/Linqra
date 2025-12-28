import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    Alert
} from 'react-bootstrap';
import { HiArrowLeft } from 'react-icons/hi';
import AssistantSidebar from './components/AssistantSidebar';
import ConversationPane from './components/ConversationPane';
import ChatPane from './components/ChatPane';
import WidgetEmbedModal from './components/WidgetEmbedModal';
import Button from '../../../components/common/Button';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import { LoadingSpinner } from '../../../components/common/LoadingSpinner';
import aiAssistantService from '../../../services/aiAssistantService';
import conversationService from '../../../services/conversationService';
import { knowledgeHubDocumentService } from '../../../services/knowledgeHubDocumentService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import { useTeam } from '../../../contexts/TeamContext';
import { useAuth } from '../../../contexts/AuthContext';
import { chatWebSocketService } from '../../../services/chatWebSocketService';
import './styles.css';
import { executionMonitoringWebSocket } from '../../../services/executionMonitoringService';
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

    const [error, setError] = useState(null);
    const [conversationId, setConversationId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [sending, setSending] = useState(false);
    const [conversations, setConversations] = useState([]);
    const [loadingConversations, setLoadingConversations] = useState(false);
    const [selectedConversationId, setSelectedConversationId] = useState(null);
    const [streamingMessageId, setStreamingMessageId] = useState(null);
    const [streamingContent, setStreamingContent] = useState('');
    const [isCancelling, setIsCancelling] = useState(false);
    const [statusMessages, setStatusMessages] = useState([]); // Array of status messages to show multiple lines
    const isMonitoringExecutionsRef = useRef(false); // Track if we're monitoring executions
    const [deleteModal, setDeleteModal] = useState({ show: false, conversation: null });
    const [hasAutoSelectedConversation, setHasAutoSelectedConversation] = useState(false);
    const [showCodeTab, setShowCodeTab] = useState(false);
    const [showSidebar, setShowSidebar] = useState(true);
    const [errorMessage, setErrorMessage] = useState(null);

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
                            ? {
                                ...msg,
                                streaming: false,
                                // Capture final metadata/documents if provided in the complete event
                                metadata: {
                                    ...(msg.metadata || {}),
                                    documents: update.documents || update.metadata?.documents || msg.metadata?.documents || []
                                }
                            }
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
                    // Streaming handles content, but this event often carries the full final metadata (documents, etc.)
                    // We update the last assistant message with this metadata.
                    const finalMetadata = update.metadata || {};
                    const finalDocuments = update.documents || finalMetadata.documents;

                    if (finalDocuments || finalMetadata.taskResults) {
                        setMessages(prev => {
                            const updated = [...prev];
                            // Find the last assistant message
                            for (let i = updated.length - 1; i >= 0; i--) {
                                const msg = updated[i];
                                if (msg.role === 'assistant') {
                                    updated[i] = {
                                        ...msg,
                                        metadata: {
                                            ...(msg.metadata || {}),
                                            ...finalMetadata,
                                            documents: finalDocuments || msg.metadata?.documents || []
                                        }
                                    };
                                    break;
                                }
                            }
                            return updated;
                        });
                    }
                    break;

                case 'AGENT_TASKS_EXECUTING':
                    // Initialize status messages array - start tracking execution progress
                    setStatusMessages(['Starting agent tasks...']);
                    isMonitoringExecutionsRef.current = true; // Start monitoring executions
                    // console.log('🤖 Agent tasks executing:', update.taskIds);
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
                    // console.log('✅ Agent tasks completed:', update.executedTasks);
                    break;

                default:
                // console.log('💬 Unhandled chat update type:', update.type);
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


    const loadAssistant = async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await aiAssistantService.getAssistant(assistantId);
            if (response.success) {
                setAssistant(response.data);
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
                    metadata: {
                        ...(msg.metadata || {}),
                        documents: msg.documents || msg.metadata?.documents || []
                    }
                }));

                setMessages(formattedMessages);
            }
        } catch (err) {
            console.error('Error loading conversation:', err);
            showErrorToast('Failed to load conversation');
        }
    };

    const startNewConversation = () => {
        setSelectedConversationId(null);
        setConversationId(null);
        setMessages([]);
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

    const handleSendMessage = async (userMessage) => {
        if (!userMessage.trim() || sending) return;

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
                // Enrich the latest assistant message with taskResults / executedTasks / documents
                const executedTasks = response.data.executedTasks;
                const taskResults = response.data.taskResults;
                const documents = response.data.documents || response.data.metadata?.documents;

                if (taskResults || documents) {
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
                                        taskResults,
                                        documents: documents || msg.metadata?.documents || []
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

    return (
        <div className="view-assistant-page d-flex w-100 bg-white overflow-hidden" style={{ position: 'fixed', top: 0, left: 0, bottom: 0, right: 0, zIndex: 1 }}>
            {/* Column 1: Icon Sidebar */}
            <div style={{ flex: '0 0 60px' }}>
                <AssistantSidebar activeMode="chat" />
            </div>

            {/* Column 2: Conversation List Sidebar */}
            <ConversationPane
                conversations={conversations}
                loading={loadingConversations}
                selectedConversationId={selectedConversationId}
                onSelectConversation={loadConversation}
                onNewConversation={startNewConversation}
                onDeleteConversation={(conv) => setDeleteModal({ show: true, conversation: conv })}
                assistant={assistant}
                showSidebar={showSidebar}
            />

            {/* Column 3: Main Chat Area */}
            <ChatPane
                assistant={assistant}
                user={user}
                messages={messages}
                statusMessages={statusMessages}
                conversationTitle={selectedConversationId ? (conversations.find(c => c.id === selectedConversationId)?.title || 'Current Chat') : 'New Chat'}
                selectedConversationId={selectedConversationId}
                showSidebar={showSidebar}
                onToggleSidebar={() => setShowSidebar(!showSidebar)}
                onToggleCodeTab={() => setShowCodeTab(!showCodeTab)}
                onSendMessage={handleSendMessage}
                onCancel={handleCancel}
                onDownloadDocument={handleDownloadKnowledgeDocument}
                sending={sending}
                isCancelling={isCancelling}
                errorMessage={errorMessage}
                setErrorMessage={setErrorMessage}
            />

            {/* Modals */}
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

            {/* Widget Modal */}
            <WidgetEmbedModal
                show={showCodeTab}
                onHide={() => setShowCodeTab(false)}
                assistant={assistant}
            />
        </div>
    );
}

export default ChatAssistant;
