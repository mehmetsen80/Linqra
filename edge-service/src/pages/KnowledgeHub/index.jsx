import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Container, Card, Table, Spinner, Breadcrumb, OverlayTrigger, Tooltip, Badge } from 'react-bootstrap';
import { HiFolder, HiPlus, HiEye, HiPencil, HiTrash, HiBookOpen } from 'react-icons/hi';
import { useTeam } from '../../contexts/TeamContext';
import { knowledgeHubCollectionService } from '../../services/knowledgeHubCollectionService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import ConfirmationModal from '../../components/common/ConfirmationModal';
import Button from '../../components/common/Button';
import CreateCollectionModal from '../../components/knowledgeHub/CreateCollectionModal';
import EditCollectionModal from '../../components/knowledgeHub/EditCollectionModal';
import './styles.css';

function KnowledgeHub() {
  const navigate = useNavigate();
  const { currentTeam } = useTeam();
  const [collections, setCollections] = useState([]);
  const [loading, setLoading] = useState(true);
  const [operationLoading, setOperationLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedCollection, setSelectedCollection] = useState(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [confirmModal, setConfirmModal] = useState({
    show: false,
    title: '',
    message: '',
    onConfirm: () => {},
    variant: 'danger'
  });

  useEffect(() => {
    if (currentTeam?.id) {
      fetchCollections();
    }
  }, [currentTeam?.id]);

  const fetchCollections = async () => {
    if (!currentTeam?.id) return;
    
    console.log('currentTeam', currentTeam);

    try {
      setLoading(true);
      const { data, error } = await knowledgeHubCollectionService.getAllCollections();
      if (error) throw new Error(error);

      console.log('data', data);
      setCollections(data || []);
      setLoading(false);
    } catch (err) {
      setError('Failed to load knowledge collections');
      setLoading(false);
      console.error('Error fetching collections:', err);
    }
  };

  const handleCreateCollection = async (collectionData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await knowledgeHubCollectionService.createCollection(collectionData);
      if (error) throw new Error(error);
      
      await fetchCollections();
      setShowCreateModal(false);
      showSuccessToast(`Collection "${data.name}" created successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to create collection');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleEditCollection = async (collectionData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await knowledgeHubCollectionService.updateCollection(
        selectedCollection.id,
        collectionData
      );
      if (error) throw new Error(error);
      
      await fetchCollections();
      setShowEditModal(false);
      showSuccessToast(`Collection "${data.name}" updated successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to update collection');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleDeleteCollection = async (collectionId) => {
    const collection = collections.find(c => c.id === collectionId);
    try {
      setOperationLoading(true);
      const { error } = await knowledgeHubCollectionService.deleteCollection(collectionId);
      if (error) throw new Error(error);
      
      setConfirmModal(prev => ({ ...prev, show: false }));
      await fetchCollections();
      showSuccessToast(`Collection "${collection.name}" deleted successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to delete collection');
    } finally {
      setOperationLoading(false);
    }
  };

  const confirmDelete = (collection) => {
    setConfirmModal({
      show: true,
      title: 'Delete Collection',
      message: `Are you sure you want to delete collection "${collection.name}"? This action cannot be undone and all files in this collection will be deleted.`,
      onConfirm: () => handleDeleteCollection(collection.id),
      variant: 'danger',
      confirmLabel: operationLoading ? 'Deleting...' : 'Delete',
      disabled: operationLoading
    });
  };

  const formatDate = (dateInput) => {
    if (!dateInput) return 'N/A';
    
    let date;
    
    if (Array.isArray(dateInput)) {
      const [year, month, day, hour, minute, second] = dateInput;
      date = new Date(year, month - 1, day, hour, minute, second);
    } else {
      date = new Date(dateInput);
    }
    
    if (isNaN(date.getTime())) {
      return 'Invalid Date';
    }
    
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getCategoryBadges = (categories) => {
    if (!categories || categories.length === 0) return null;
    
    return (
      <div className="d-flex flex-wrap gap-1">
        {categories.map((category, idx) => (
          <span key={idx} className="category-badge">
            {category.displayName || category}
          </span>
        ))}
      </div>
    );
  };

  return (
    <Container fluid className="knowledge-hub-container">
      {/* Breadcrumb */}
      <Card className="breadcrumb-card mb-3">
        <Card.Body>
          <Breadcrumb>
            <Breadcrumb.Item linkAs={Link} linkProps={{ to: '/dashboard' }}>
              Home
            </Breadcrumb.Item>
            <Breadcrumb.Item 
              onClick={() => navigate(`/teams/${currentTeam?.id}`)}
              style={{ cursor: 'pointer' }}
            >
              {currentTeam?.name || 'Team'}
            </Breadcrumb.Item>
            <Breadcrumb.Item active>Knowledge Hub</Breadcrumb.Item>
          </Breadcrumb>
        </Card.Body>
      </Card>

      {/* Main Content */}
      <Card className="border-0">
        <Card.Header>
          <div className="d-flex align-items-center">
            <div className="d-flex align-items-center gap-2">
              <HiBookOpen className="page-icon" />
              <h4 className="mb-0">Knowledge Hub</h4>
            </div>
            <div className="ms-auto">
              <Button 
                onClick={() => setShowCreateModal(true)}
                disabled={operationLoading}
                variant="primary"
              >
                {operationLoading ? (
                  <>
                    <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" />
                    <span className="ms-2">Creating...</span>
                  </>
                ) : (
                  <>
                    <HiPlus /> Create Collection
                  </>
                )}
              </Button>
            </div>
          </div>
        </Card.Header>
        <Card.Body>
          {loading ? (
            <div className="text-center py-5">
              <Spinner animation="border" role="status">
                <span className="visually-hidden">Loading collections...</span>
              </Spinner>
            </div>
          ) : error ? (
            <div className="alert alert-danger">{error}</div>
          ) : collections.length === 0 ? (
            <div className="text-center py-5">
              <HiFolder className="empty-state-icon" />
              <h5 className="mt-3">No Collections Yet</h5>
              <p className="text-muted">Create your first knowledge collection to get started.</p>
              <Button 
                variant="primary"
                onClick={() => setShowCreateModal(true)}
              >
                <HiPlus /> Create Collection
              </Button>
            </div>
          ) : (
            <Table responsive hover>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Description</th>
                  <th>Categories</th>
                  <th>Files</th>
                  <th>Created By</th>
                  <th>Created At</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {collections.map((collection) => (
                  <tr 
                    key={collection.id}
                    onClick={() => navigate(`/knowledge-hub/collection/${collection.id}`)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td>
                      <div className="d-flex align-items-center gap-2">
                        <HiFolder className="collection-icon" />
                        <span className="collection-name">{collection.name}</span>
                      </div>
                    </td>
                    <td>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>{collection.description || 'No description'}</Tooltip>}
                      >
                        <span className="collection-description">
                          {collection.description || 'No description'}
                        </span>
                      </OverlayTrigger>
                    </td>
                    <td>
                      {getCategoryBadges(collection.categories)}
                    </td>
                    <td>
                      <Badge bg="secondary" className="files-count-badge">
                        {collection.documentCount || 0}
                      </Badge>
                    </td>
                    <td>
                      <span className="created-by">{collection.createdBy || 'N/A'}</span>
                    </td>
                    <td>
                      <span className="created-date">{formatDate(collection.createdAt)}</span>
                    </td>
                    <td className="text-end">
                      <div className="d-flex gap-2 justify-content-end" onClick={(e) => e.stopPropagation()}>
                        <OverlayTrigger
                          placement="top"
                          overlay={<Tooltip>View Collection</Tooltip>}
                        >
                          <Button
                            variant="outline-primary"
                            size="sm"
                            onClick={() => navigate(`/knowledge-hub/collection/${collection.id}`)}
                          >
                            <HiEye />
                          </Button>
                        </OverlayTrigger>
                        <OverlayTrigger
                          placement="top"
                          overlay={<Tooltip>Edit Collection</Tooltip>}
                        >
                          <Button
                            variant="outline-secondary"
                            size="sm"
                            onClick={() => {
                              setSelectedCollection(collection);
                              setShowEditModal(true);
                            }}
                          >
                            <HiPencil />
                          </Button>
                        </OverlayTrigger>
                        <OverlayTrigger
                          placement="top"
                          overlay={
                            <Tooltip>
                              {collection.documentCount > 0 
                                ? 'Delete all files in this collection before deleting it'
                                : 'Delete Collection'
                              }
                            </Tooltip>
                          }
                        >
                          <span>
                            <Button
                              variant="outline-danger"
                              size="sm"
                              onClick={() => confirmDelete(collection)}
                              disabled={collection.documentCount > 0}
                            >
                              <HiTrash />
                            </Button>
                          </span>
                        </OverlayTrigger>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card.Body>
      </Card>

      {/* Modals */}
      <CreateCollectionModal
        show={showCreateModal}
        onHide={() => setShowCreateModal(false)}
        onSave={handleCreateCollection}
        loading={operationLoading}
      />

      <EditCollectionModal
        show={showEditModal}
        onHide={() => setShowEditModal(false)}
        onSave={handleEditCollection}
        collection={selectedCollection}
        loading={operationLoading}
      />

      <ConfirmationModal
        show={confirmModal.show}
        onHide={() => setConfirmModal(prev => ({ ...prev, show: false }))}
        onConfirm={confirmModal.onConfirm}
        title={confirmModal.title}
        message={confirmModal.message}
        variant={confirmModal.variant}
        confirmLabel={confirmModal.confirmLabel}
        disabled={confirmModal.disabled}
      />
    </Container>
  );
}

export default KnowledgeHub;

