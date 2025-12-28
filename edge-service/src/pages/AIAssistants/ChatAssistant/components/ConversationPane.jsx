import React, { useState, useRef } from 'react';
import { Spinner, Badge } from 'react-bootstrap';
import { HiPlus, HiInformationCircle } from 'react-icons/hi';
import Button from '../../../../components/common/Button';
import { formatDate } from '../../../../utils/dateUtils';
import AssistantInfoModal from './AssistantInfoModal';

const ConversationPane = ({
    conversations,
    loading,
    selectedConversationId,
    onSelectConversation,
    onNewConversation,
    onDeleteConversation,
    assistant,
    showSidebar
}) => {
    const [sidebarWidth, setSidebarWidth] = useState(300);
    const [showInfoModal, setShowInfoModal] = useState(false);
    const isResizingRef = useRef(false);

    const startResizing = (mouseDownEvent) => {
        isResizingRef.current = true;
        const startX = mouseDownEvent.clientX;
        const startWidth = sidebarWidth;

        const doResize = (mouseMoveEvent) => {
            if (isResizingRef.current) {
                const newWidth = startWidth + (mouseMoveEvent.clientX - startX);
                // Min width 200px, Max width 600px
                if (newWidth >= 200 && newWidth <= 600) {
                    setSidebarWidth(newWidth);
                }
            }
        };

        const stopResizing = () => {
            isResizingRef.current = false;
            document.removeEventListener('mousemove', doResize);
            document.removeEventListener('mouseup', stopResizing);
            document.body.style.cursor = 'default';
        };

        document.addEventListener('mousemove', doResize);
        document.addEventListener('mouseup', stopResizing);
        document.body.style.cursor = 'col-resize';
    };

    // Helper to get formatted date
    const getFormattedDate = (dateString) => {
        return formatDate(dateString, 'MMM d, HH:mm');
    };

    return (
        <div
            className="d-flex flex-column border-end bg-light position-relative"
            style={{
                flex: `0 0 ${showSidebar ? sidebarWidth : 0}px`,
                maxWidth: showSidebar ? `${sidebarWidth}px` : '0px',
                width: showSidebar ? `${sidebarWidth}px` : '0px',
                overflow: 'hidden',
                transition: isResizingRef.current ? 'none' : 'width 0.3s ease'
            }}
        >
            {/* Drag Handle */}
            {showSidebar && (
                <div
                    style={{
                        position: 'absolute',
                        top: 0,
                        right: 0,
                        width: '4px',
                        height: '100%',
                        cursor: 'col-resize',
                        zIndex: 10,
                        backgroundColor: 'transparent'
                    }}
                    onMouseDown={startResizing}
                    className="hover-bg-primary-subtle"
                />
            )}

            <div className="p-3 border-bottom bg-white d-flex justify-content-between align-items-center" style={{ height: '60px', minWidth: `${sidebarWidth}px` }}>
                <h6 className="mb-0 fw-bold">Conversations</h6>
                <Button
                    variant="link"
                    size="sm"
                    onClick={onNewConversation}
                    className="p-0 text-primary"
                    title="New Conversation"
                >
                    <HiPlus size={20} />
                </Button>
            </div>

            <div className="flex-grow-1 overflow-auto p-2">
                {loading ? (
                    <div className="text-center p-3">
                        <Spinner size="sm" variant="secondary" />
                    </div>
                ) : conversations.length === 0 ? (
                    <div className="text-center p-3 text-muted small">
                        No conversations yet
                    </div>
                ) : (
                    conversations.map((conv) => (
                        <div
                            key={conv.id}
                            className={`p-2 mb-2 rounded cursor-pointer ${selectedConversationId === conv.id ? 'bg-white shadow-sm border-primary border' : 'hover-bg-gray'}`}
                            onClick={() => onSelectConversation(conv.id)}
                            style={{ cursor: 'pointer', borderLeft: selectedConversationId === conv.id ? '3px solid #0d6efd' : '3px solid transparent' }}
                        >
                            <div className="d-flex justify-content-between align-items-start">
                                <div className="text-truncate fw-medium flex-grow-1 min-w-0 me-2 text-start" style={{ fontSize: '0.9rem' }}>
                                    {conv.title || 'New Conversation'}
                                </div>
                                <Button
                                    variant="link"
                                    size="sm"
                                    className="p-0 text-muted opacity-50 hover-opacity-100"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        onDeleteConversation(conv);
                                    }}
                                >
                                    <span aria-hidden="true">&times;</span>
                                </Button>
                            </div>
                            <div className="d-flex justify-content-between mt-1">
                                <small className="text-muted" style={{ fontSize: '0.75rem' }}>
                                    {getFormattedDate(conv.lastMessageAt || conv.startedAt)}
                                </small>
                                {conv.messageCount > 0 && (
                                    <Badge bg="light" text="dark" className="border" style={{ fontSize: '0.65rem' }}>
                                        {conv.messageCount}
                                    </Badge>
                                )}
                            </div>
                        </div>
                    ))
                )}
            </div>

            {/* Assistant Info Footer in Sidebar */}
            <div className="p-3 border-top bg-white">
                <div className="d-flex align-items-center">
                    <div className="flex-grow-1 min-w-0">
                        <div
                            className="fw-bold mb-1 text-break cursor-pointer hover-text-primary d-flex align-items-center gap-1"
                            style={{ fontSize: '0.8rem' }}
                            onClick={() => setShowInfoModal(true)}
                            role="button"
                            title="Click to view full assistant details"
                        >
                            {assistant?.name}
                            <HiInformationCircle className="text-muted" size={14} />
                        </div>

                    </div>
                </div>
            </div>

            {/* Info Modal */}
            <AssistantInfoModal
                show={showInfoModal}
                onHide={() => setShowInfoModal(false)}
                assistant={assistant}
            />
        </div>
    );
};

export default ConversationPane;
