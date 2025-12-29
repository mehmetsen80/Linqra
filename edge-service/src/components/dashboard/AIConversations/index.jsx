import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Spinner, Badge, Button } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useTeam } from '../../../contexts/TeamContext';
import aiAssistantService from '../../../services/aiAssistantService';
import conversationService from '../../../services/conversationService';
import { HiChatAlt, HiClock, HiArrowRight } from 'react-icons/hi';
import { formatDistanceToNow } from 'date-fns';
import './styles.css';

const AIConversations = () => {
    const { currentTeam } = useTeam();
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [activeConversations, setActiveConversations] = useState([]);

    useEffect(() => {
        if (currentTeam?.id) {
            fetchActiveAssistants();
        }
    }, [currentTeam?.id]);

    const fetchActiveAssistants = async () => {
        setLoading(true);
        try {
            // 1. Get all assistants
            const assistantsRes = await aiAssistantService.getAllAssistants(currentTeam.id);
            if (!assistantsRes.success) {
                console.error('Failed to fetch assistants');
                setLoading(false);
                return;
            }

            // 2. Filter for ACTIVE status
            const activeAssistants = (assistantsRes.data || []).filter(a => a.status === 'ACTIVE');

            // 3. For each active assistant, find latest conversation
            const conversationPromises = activeAssistants.map(async (assistant) => {
                const convRes = await conversationService.listConversations(assistant.id);
                // Assume returns list or object with content
                const conversationList = Array.isArray(convRes.data) ? convRes.data : (convRes.data?.content || []);

                // Sort by last active or created descending (usually implicit, but good to check)
                const latestConv = conversationList.length > 0 ? conversationList[0] : null;

                return {
                    assistant,
                    conversation: latestConv
                };
            });

            const results = await Promise.all(conversationPromises);
            setActiveConversations(results);
        } catch (error) {
            console.error('Error loading dashboard conversations:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleOpenChat = (assistantId) => {
        navigate(`/ai-assistants/${assistantId}/chat`);
    };

    if (loading) {
        return (
            <div className="text-center py-4">
                <Spinner animation="border" variant="primary" size="sm" />
                <span className="ms-2 text-muted">Loading active conversations...</span>
            </div>
        );
    }

    if (activeConversations.length === 0) {
        return null; // Don't show section if no active assistants
    }

    return (
        <div style={{ marginBottom: '4rem', marginTop: '4rem' }}>
            <h4 className="dashboard-section-title mb-3">
                <HiChatAlt className="me-2 text-brand" />
                Active AI Conversations
                <span className="badge rounded-pill bg-brand ms-2" style={{ fontSize: '0.7em', verticalAlign: 'middle' }}>
                    {activeConversations.length}
                </span>
            </h4>
            <Row xs={1} md={2} lg={3} className="g-4">
                {activeConversations.map(({ assistant, conversation }) => (
                    <Col key={assistant.id}>
                        <Card
                            className="h-100 shadow assistant-card border p-2 ai-conversations-card"
                            onClick={() => handleOpenChat(assistant.id)}
                            style={{ cursor: 'pointer', transition: 'transform 0.2s' }}
                        >
                            <Card.Header className="bg-white border-bottom-0 pt-2 pb-0">
                                <div className="d-flex justify-content-end gap-1 mb-1">
                                    <Badge bg="success" style={{ fontSize: '0.65rem' }}>
                                        ACTIVE
                                    </Badge>
                                </div>
                                <div className="d-flex align-items-center">
                                    <div className="rounded-circle assistant-icon-brand p-2 me-2">
                                        <HiChatAlt size={20} />
                                    </div>
                                    <h6 className="mb-0 fw-bold text-truncate" title={assistant.name}>
                                        {assistant.name}
                                    </h6>
                                </div>
                            </Card.Header>
                            <Card.Body className="pt-2 pb-0 d-flex flex-column">
                                <div className="flex-grow-1">
                                    {conversation ? (
                                        <>
                                            <p className="small text-muted mb-1">Latest Conversation:</p>
                                            <p className="fw-medium mb-1 text-truncate text-dark" style={{ fontSize: '0.9rem' }}>
                                                {conversation.title || 'Untitled Conversation'}
                                            </p>
                                            <div className="d-flex align-items-center small text-muted">
                                                <HiClock className="me-1" />
                                                {(() => {
                                                    const date = conversation.lastMessageAt ? new Date(conversation.lastMessageAt) : null;
                                                    const isValidDate = date && !isNaN(date.getTime());
                                                    return isValidDate ?
                                                        formatDistanceToNow(date, { addSuffix: true })
                                                        : 'Just now';
                                                })()}
                                            </div>
                                        </>
                                    ) : (
                                        <div className="text-center py-2 text-muted small bg-light rounded">
                                            No active conversations yet
                                        </div>
                                    )}
                                </div>
                            </Card.Body>
                            <Card.Footer className="bg-white border-top-0 pt-0 pb-3 mt-auto">
                                <hr className="my-2" />
                                <div className="d-flex justify-content-end align-items-center">
                                    <Button
                                        variant="light"
                                        size="sm"
                                        className="btn-icon-only rounded-circle btn-icon-brand"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            handleOpenChat(assistant.id);
                                        }}
                                        title="Continue Chat"
                                        style={{ width: '32px', height: '32px', padding: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                    >
                                        <HiArrowRight />
                                    </Button>
                                </div>
                            </Card.Footer>
                        </Card>
                    </Col>
                ))}
            </Row>
        </div>
    );
};

export default AIConversations;
