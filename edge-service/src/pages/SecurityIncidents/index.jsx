import React, { useState, useEffect } from 'react';
import { Table, Badge, Button, Form, Modal, Spinner } from 'react-bootstrap';
import { HiOutlineShieldCheck, HiOutlineExclamation } from 'react-icons/hi';
import { securityIncidentService } from '../../services/securityIncidentService';
import './styles.css';

const SecurityIncidents = () => {
    const [incidents, setIncidents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [filterStatus, setFilterStatus] = useState('');
    const [selectedIncident, setSelectedIncident] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [resolutionNotes, setResolutionNotes] = useState('');

    useEffect(() => {
        fetchIncidents();
    }, [filterStatus]);

    const fetchIncidents = async () => {
        setLoading(true);
        try {
            const data = await securityIncidentService.getAllIncidents(filterStatus || null);
            setIncidents(data);
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const handleResolveClick = (incident) => {
        setSelectedIncident(incident);
        setResolutionNotes('');
        setShowModal(true);
    };

    const handleConfirmResolve = async (status) => {
        try {
            await securityIncidentService.updateIncidentStatus(selectedIncident.id, status, resolutionNotes);
            setShowModal(false);
            fetchIncidents();
        } catch (error) {
            console.error(error);
        }
    };

    const getSeverityBadge = (severity) => {
        let variant = 'secondary';
        if (severity === 'CRITICAL') variant = 'danger';
        if (severity === 'HIGH') variant = 'warning';
        if (severity === 'MEDIUM') variant = 'info';
        return <Badge bg={variant}>{severity}</Badge>;
    };

    return (
        <div className="security-incidents-container">
            <div className="incidents-header">
                <h2><HiOutlineShieldCheck /> Security Incidents</h2>
                <Button variant="outline-primary" onClick={fetchIncidents}>Refresh</Button>
            </div>

            <div className="filter-section">
                <Form.Select
                    value={filterStatus}
                    onChange={(e) => setFilterStatus(e.target.value)}
                    style={{ width: '200px' }}
                >
                    <option value="">All Statuses</option>
                    <option value="OPEN">Open</option>
                    <option value="INVESTIGATING">Investigating</option>
                    <option value="RESOLVED">Resolved</option>
                    <option value="FALSE_POSITIVE">False Positive</option>
                </Form.Select>
            </div>

            <div className="incidents-table-card">
                <Table hover responsive className="mb-0">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Severity</th>
                            <th>Status</th>
                            <th>Rule</th>
                            <th>Description</th>
                            <th>Affected User</th>
                            <th>Detected At</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading ? (
                            <tr><td colSpan="8" className="text-center p-5"><Spinner animation="border" /></td></tr>
                        ) : incidents.length === 0 ? (
                            <tr><td colSpan="8" className="text-center p-5">No incidents found.</td></tr>
                        ) : (
                            incidents.map(incident => (
                                <tr key={incident.id}>
                                    <td><code>{incident.referenceId}</code></td>
                                    <td>{getSeverityBadge(incident.severity)}</td>
                                    <td>
                                        <span className={`status-badge status-${incident.status}`}>
                                            {incident.status}
                                        </span>
                                    </td>
                                    <td>{incident.ruleName}</td>
                                    <td>{incident.description}</td>
                                    <td>{incident.affectedUsername || '-'}</td>
                                    <td>{new Date(incident.detectedAt).toLocaleString()}</td>
                                    <td>
                                        {incident.status !== 'RESOLVED' && incident.status !== 'FALSE_POSITIVE' && (
                                            <Button
                                                size="sm"
                                                variant="outline-success"
                                                className="action-btn"
                                                onClick={() => handleResolveClick(incident)}
                                            >
                                                Resolve
                                            </Button>
                                        )}
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </Table>
            </div>

            <Modal show={showModal} onHide={() => setShowModal(false)}>
                <Modal.Header closeButton>
                    <Modal.Title>Resolve Incident {selectedIncident?.referenceId}</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <Form.Group>
                        <Form.Label>Resolution Notes</Form.Label>
                        <Form.Control
                            as="textarea"
                            rows={3}
                            value={resolutionNotes}
                            onChange={(e) => setResolutionNotes(e.target.value)}
                        />
                    </Form.Group>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowModal(false)}>Cancel</Button>
                    <Button variant="info" onClick={() => handleConfirmResolve('FALSE_POSITIVE')}>Mark False Positive</Button>
                    <Button variant="success" onClick={() => handleConfirmResolve('RESOLVED')}>Resolve Incident</Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
};

export default SecurityIncidents;
