import React, { useState, useEffect } from 'react';
import { Modal, ListGroup, Badge, Spinner } from 'react-bootstrap';
import { HiClock, HiDocumentText } from 'react-icons/hi';
import docReviewService from '../../../../services/docReviewService';
import { useTeam } from '../../../../contexts/TeamContext';
import Button from '../../../../components/common/Button';
import { toast } from 'react-toastify';

const DocReviewHistoryModal = ({ show, onHide, onSelect }) => {
    const { currentTeam } = useTeam();
    const [reviews, setReviews] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (show && currentTeam) {
            fetchReviews();
        }
    }, [show, currentTeam]);

    const fetchReviews = async () => {
        setLoading(true);
        try {
            const response = await docReviewService.getReviewsByTeam(currentTeam.id);
            if (response.success) {
                // Sort by createdAt desc
                const sorted = response.data.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
                setReviews(sorted);
            } else {
                toast.error(response.error || 'Failed to load review history');
            }
        } catch (error) {
            console.error('Error fetching review history:', error);
            toast.error('Failed to load review history');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal show={show} onHide={onHide} centered>
            <Modal.Header closeButton>
                <Modal.Title className="d-flex align-items-center">
                    <HiClock className="me-2 text-primary" />
                    Review History
                </Modal.Title>
            </Modal.Header>
            <Modal.Body style={{ maxHeight: '60vh', overflowY: 'auto' }}>
                {loading ? (
                    <div className="text-center py-4">
                        <Spinner animation="border" variant="primary" />
                        <p className="mt-2 text-muted">Loading history...</p>
                    </div>
                ) : reviews.length === 0 ? (
                    <div className="text-center py-4 text-muted">
                        <p>No previous reviews found.</p>
                    </div>
                ) : (
                    <ListGroup variant="flush">
                        {reviews.map(review => (
                            <ListGroup.Item
                                key={review.id}
                                action
                                onClick={() => {
                                    onSelect(review);
                                    onHide();
                                }}
                                className="d-flex align-items-center justify-content-between py-3"
                            >
                                <div>
                                    <div className="d-flex align-items-center mb-1">
                                        <HiDocumentText className="text-secondary me-2" />
                                        <span className="fw-medium">{review.documentName}</span>
                                    </div>
                                    <small className="text-muted">
                                        {new Date(review.createdAt).toLocaleString()}
                                    </small>
                                </div>
                                <Badge bg={review.status === 'COMPLETED' ? 'success' : 'primary'}>
                                    {review.status}
                                </Badge>
                            </ListGroup.Item>
                        ))}
                    </ListGroup>
                )}
            </Modal.Body>
            <Modal.Footer>
                <Button variant="secondary" onClick={onHide}>
                    Close
                </Button>
            </Modal.Footer>
        </Modal>
    );
};

export default DocReviewHistoryModal;
