import React, { useEffect, useMemo, useState } from 'react';
import { Container, Card, Table, Spinner, Badge, Alert, Breadcrumb } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';
import { HiCollection, HiPlusCircle, HiTrash, HiCheckCircle, HiDatabase } from 'react-icons/hi';
import { HiOutlineDatabase } from 'react-icons/hi';
import Button from '../../components/common/Button';
import ConfirmationModal from '../../components/common/ConfirmationModal';
import CreateMilvusCollectionModal from '../../components/knowledgeHub/CreateMilvusCollectionModal';
import MilvusCollectionDetailsModal from '../../components/knowledgeHub/MilvusCollectionDetailsModal';
import CreateCustomMilvusCollectionModal from '../../components/rag/CreateCustomMilvusCollectionModal';
import EditMilvusCollectionMetadataModal from '../../components/rag/EditMilvusCollectionMetadataModal';
import { milvusService } from '../../services/milvusService';
import { llmModelService } from '../../services/llmModelService';
import { showErrorToast, showSuccessToast } from '../../utils/toastConfig';
import { useTeam } from '../../contexts/TeamContext';
import './styles.css';

const PROVIDER_LABELS = {
  openai: 'OpenAI',
  gemini: 'Gemini',
  cohere: 'Cohere'
};

const MILVUS_NAME_PATTERN = /^[A-Za-z0-9_-]+$/;

const Rag = () => {
  const { currentTeam } = useTeam();
  const navigate = useNavigate();
  const [milvusCollections, setMilvusCollections] = useState([]);
  const [loadingCollections, setLoadingCollections] = useState(false);
  const [error, setError] = useState(null);
  const [showKnowledgeHubModal, setShowKnowledgeHubModal] = useState(false);
  const [showCustomModal, setShowCustomModal] = useState(false);
  const [creatingKnowledgeHub, setCreatingKnowledgeHub] = useState(false);
  const [creatingCustom, setCreatingCustom] = useState(false);
  const [embeddingOptions, setEmbeddingOptions] = useState([]);
  const [loadingEmbeddingOptions, setLoadingEmbeddingOptions] = useState(false);
  const [verifyingCollection, setVerifyingCollection] = useState('');
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [detailsResult, setDetailsResult] = useState(null);
  const [confirmState, setConfirmState] = useState({ show: false, name: '', loading: false });
  const [editingCollection, setEditingCollection] = useState(null);
  const [metadataSaving, setMetadataSaving] = useState(false);


  useEffect(() => {
    if (!currentTeam?.id) return;
    loadCollections();
    loadEmbeddingOptions();
  }, [currentTeam?.id]);

  const loadCollections = async () => {
    if (!currentTeam?.id) return;
    try {
      setLoadingCollections(true);
      const { data, error: fetchError } = await milvusService.getCollectionsForTeam(currentTeam.id);
      if (fetchError) throw new Error(fetchError);
      setMilvusCollections(data || []);
      setError(null);
    } catch (err) {
      console.error('Error fetching Milvus collections:', err);
      setError(err.message || 'Failed to load Milvus collections');
      showErrorToast(err.message || 'Failed to load Milvus collections');
    } finally {
      setLoadingCollections(false);
    }
  };

  const loadEmbeddingOptions = async () => {
    if (!currentTeam?.id) return;
    try {
      setLoadingEmbeddingOptions(true);
      const { data, error: fetchError } = await llmModelService.getEmbeddingModels(currentTeam.id);
      if (fetchError) throw new Error(fetchError);
      setEmbeddingOptions(transformEmbeddingOptions(data || []));
    } catch (err) {
      console.error('Error loading embedding models:', err);
      showErrorToast(err.message || 'Failed to load embedding models');
    } finally {
      setLoadingEmbeddingOptions(false);
    }
  };

  const transformEmbeddingOptions = (models = []) => {
    const grouped = models.reduce((acc, model) => {
      if (!model) return acc;
      const providerKey = model.provider || (model.modelCategory ? model.modelCategory.split('-')[0] : 'unknown');
      if (!acc[providerKey]) {
        acc[providerKey] = {
          provider: providerKey,
          label: PROVIDER_LABELS[providerKey] || providerKey,
          models: []
        };
      }
      acc[providerKey].models.push(model);
      return acc;
    }, {});

    return Object.values(grouped).map(option => ({
      ...option,
      models: option.models.sort((a, b) => a.modelName.localeCompare(b.modelName))
    }));
  };

  const knowledgeHubCollections = useMemo(() => (
    (milvusCollections || []).filter(collection => (collection.collectionType || '').toUpperCase() === 'KNOWLEDGE_HUB')
  ), [milvusCollections]);

  const customCollections = useMemo(() => (
    (milvusCollections || []).filter(collection => (collection.collectionType || '').toUpperCase() !== 'KNOWLEDGE_HUB')
  ), [milvusCollections]);

  const handleOpenKnowledgeHubModal = () => {
    if (!currentTeam?.id) {
      showErrorToast('Select a team to manage Milvus collections');
      return;
    }
    setShowKnowledgeHubModal(true);
    loadEmbeddingOptions();
  };

  const handleOpenCustomModal = () => {
    if (!currentTeam?.id) {
      showErrorToast('Select a team to manage Milvus collections');
      return;
    }
    setShowCustomModal(true);
  };

  const handleCreateKnowledgeHubCollection = async (payload) => {
    try {
      setCreatingKnowledgeHub(true);
      const { success, error: createError } = await milvusService.createCollection(payload);
      if (!success) throw new Error(createError || 'Failed to create collection');
      showSuccessToast(`Milvus collection "${payload.collectionName}" created successfully`);
      setShowKnowledgeHubModal(false);
      await loadCollections();
    } catch (err) {
      console.error('Error creating Knowledge Hub Milvus collection:', err);
      showErrorToast(err.message || 'Failed to create Milvus collection');
    } finally {
      setCreatingKnowledgeHub(false);
    }
  };

  const handleCreateCustomCollection = async (payload) => {
    try {
      setCreatingCustom(true);
      const mergedPayload = {
        ...payload,
        teamId: payload.teamId || currentTeam?.id
      };
      const { success, error: createError } = await milvusService.createCollection(mergedPayload);
      if (!success) throw new Error(createError || 'Failed to create collection');
      showSuccessToast(`Milvus collection "${payload.collectionName}" created successfully`);
      setShowCustomModal(false);
      await loadCollections();
    } catch (err) {
      console.error('Error creating custom Milvus collection:', err);
      showErrorToast(err.message || 'Failed to create Milvus collection');
    } finally {
      setCreatingCustom(false);
    }
  };

  const handleVerifyCollection = async (collectionName) => {
    if (!currentTeam?.id) {
      showErrorToast('Select a team to verify Milvus collections');
      return;
    }
    try {
      setVerifyingCollection(collectionName);
      const { success, data, error: verifyError } = await milvusService.verifyCollection(currentTeam.id, collectionName);
      if (!success) throw new Error(verifyError || 'Verification failed');
      setDetailsResult(data);
      setShowDetailsModal(true);
    } catch (err) {
      console.error('Error verifying Milvus collection:', err);
      showErrorToast(err.message || 'Failed to verify Milvus collection');
    } finally {
      setVerifyingCollection('');
    }
  };

  const handleDeleteCollection = (collectionName) => {
    setConfirmState({ show: true, name: collectionName, loading: false });
  };

  const confirmDeletion = async () => {
    if (!currentTeam?.id || !confirmState.name) {
      setConfirmState({ show: false, name: '', loading: false });
      return;
    }
    try {
      setConfirmState(prev => ({ ...prev, loading: true }));
      const { success, error: deleteError } = await milvusService.deleteCollection(confirmState.name, currentTeam.id);
      if (!success) throw new Error(deleteError || 'Failed to delete collection');
      showSuccessToast(`Milvus collection "${confirmState.name}" deleted`);
      setConfirmState({ show: false, name: '', loading: false });
      await loadCollections();
    } catch (err) {
      console.error('Error deleting Milvus collection:', err);
      showErrorToast(err.message || 'Failed to delete Milvus collection');
      setConfirmState(prev => ({ ...prev, loading: false }));
    }
  };

  const handleSaveMetadata = async () => {
    if (!editingCollection) return;

    const aliasInput = (editingCollection.collectionAlias ?? '').trim();
    if (aliasInput && !MILVUS_NAME_PATTERN.test(aliasInput)) {
      showErrorToast('Collection name can only include letters, numbers, hyphens, or underscores.');
      return;
    }

    const aliasToPersist = aliasInput;
    const descriptionToPersist = (editingCollection.collectionDescription ?? '').trim();
    const collectionTypeRaw = editingCollection.collectionType ?? '';
    const collectionTypeTrimmed = collectionTypeRaw?.trim() ?? '';
    const canEditCollectionType = Boolean(editingCollection.collectionTypeEditable);

    try {
      setMetadataSaving(true);
      const metadataPayload = {
        collectionAlias: aliasToPersist,
        collectionDescription: descriptionToPersist
      };

      if (canEditCollectionType) {
        metadataPayload.collectionType = collectionTypeTrimmed || 'CUSTOM';
      }

      const { success, error: updateError } = await milvusService.updateCollectionMetadata({
        teamId: currentTeam?.id,
        collectionName: editingCollection.name,
        metadata: metadataPayload
      });
      if (!success) throw new Error(updateError || 'Failed to update collection');

      showSuccessToast('Collection metadata updated');
      setEditingCollection(null);
      await loadCollections();
    } catch (err) {
      console.error('Error updating collection metadata:', err);
      showErrorToast(err.message || 'Failed to update collection metadata');
    } finally {
      setMetadataSaving(false);
    }
  };

  const renderCollectionRows = (items, { showVerify = true, allowCollectionTypeEdit = false } = {}) => (
    items.map(collection => {
      const alias = collection.properties?.collectionAlias || collection.collectionAlias || '';
      const displayAlias = alias && alias !== collection.name ? alias : '';
      const descriptionText =
        collection.collectionDescription ??
        collection.properties?.collectionDescription ??
        collection.description ??
        '—';
      const verifying = verifyingCollection === collection.name;
      const propertySummary = collection.properties || {};
      const metadataDetails = [];
      if (propertySummary.embeddingModelName) {
        metadataDetails.push(`Model: ${propertySummary.embeddingModelName}`);
      }
      if (propertySummary.embeddingDimension) {
        metadataDetails.push(`Dims: ${propertySummary.embeddingDimension}`);
      }
      return (
        <tr key={collection.name}>
          <td className="rag-collection-name">
            <div className="fw-semibold small">
              {collection.name}
              {displayAlias && (
                <span className="text-muted ms-2">({displayAlias})</span>
              )}
            </div>
            <div className="text-muted small">Vector field: {collection.vectorFieldName || 'N/A'}</div>
          </td>
          <td>{collection.vectorDimension ?? 'Unknown'}</td>
          <td className="text-muted small rag-collection-description">
            {descriptionText}
            {metadataDetails.length > 0 && (
              <div className="text-secondary mt-1">
                {metadataDetails.join(' · ')}
              </div>
            )}
          </td>
          <td>
            <Badge bg="secondary">{collection.collectionType || 'UNKNOWN'}</Badge>
          </td>
          <td className="text-end">
            <div className="d-flex justify-content-end gap-2">
              <Button
                variant="outline-secondary"
                size="sm"
                onClick={() => setEditingCollection({
                  ...collection,
                  collectionAlias: collection.collectionAlias ?? collection.properties?.collectionAlias ?? null,
                  collectionDescription: collection.collectionDescription ?? collection.properties?.collectionDescription ?? collection.description ?? '',
                  collectionType: collection.collectionType ?? collection.properties?.collectionType ?? '',
                  collectionTypeEditable: allowCollectionTypeEdit
                })}
              >
                Edit
              </Button>
              {showVerify && (
                <Button
                  variant="outline-primary"
                  size="sm"
                  onClick={() => handleVerifyCollection(collection.name)}
                  disabled={Boolean(verifyingCollection)}
                >
                  {verifying ? (
                    <>
                      <Spinner as="span" animation="border" size="sm" className="me-2" />
                      Verifying...
                    </>
                  ) : (
                    <>
                      <HiCheckCircle className="me-1" /> Verify
                    </>
                  )}
                </Button>
              )}
              <Button
                variant="outline-danger"
                size="sm"
                onClick={() => handleDeleteCollection(collection.name)}
              >
                <HiTrash className="me-1" /> Delete
              </Button>
            </div>
          </td>
        </tr>
      );
    })
  );

  const renderEmptyState = (message, icon) => (
    <div className="text-center py-4 text-muted">
      {icon}
      <div className="mt-2">{message}</div>
    </div>
  );

  if (!currentTeam?.id) {
    return (
      <Container fluid className="rag-container">
        <Alert variant="info" className="my-4">
          Select a team to create or manage Milvus collections.
        </Alert>
      </Container>
    );
  }

  return (
    <Container fluid className="rag-container">
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
            <Breadcrumb.Item active>RAG Collections</Breadcrumb.Item>
          </Breadcrumb>
        </Card.Body>
      </Card>

      <Card className="border-0 mb-4">
        <Card.Header>
          <div className="d-flex align-items-center justify-content-between">
            <h5 className="mb-0">Knowledge Hub Collections</h5>
            <div className="d-flex align-items-center gap-2">
              <Button
                variant="link"
                onClick={() => navigate('/knowledge-hub')}
              >
                <HiCollection className="me-1" /> View Knowledge Hub
              </Button>
              <Button
                variant="outline-primary"
                size="sm"
                onClick={handleOpenKnowledgeHubModal}
                disabled={creatingKnowledgeHub || loadingEmbeddingOptions}
              >
                <HiCollection className="me-1" /> Create Knowledge Hub Collection
              </Button>
            </div>
          </div>
        </Card.Header>
        <Card.Body>
          {error && (
            <Alert variant="danger" className="mb-3">{error}</Alert>
          )}
          {loadingCollections ? (
            <div className="text-center py-4">
              <Spinner animation="border" role="status">
                <span className="visually-hidden">Loading Milvus collections...</span>
              </Spinner>
            </div>
          ) : knowledgeHubCollections.length === 0 ? (
            renderEmptyState('No Knowledge Hub Milvus collections yet. Create one to store document embeddings.', <HiCollection className="empty-state-icon" />)
          ) : (
            <Table responsive hover size="sm">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Dimension</th>
                  <th>Description</th>
                  <th>Type</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {renderCollectionRows(knowledgeHubCollections, { showVerify: true })
                }
              </tbody>
            </Table>
          )}
        </Card.Body>
      </Card>

      <Card className="border-0">
        <Card.Header>
          <div className="d-flex align-items-center justify-content-between">
            <h5 className="mb-0">Custom Milvus Collections</h5>
            <Button
              variant="outline-secondary"
              size="sm"
              onClick={handleOpenCustomModal}
              disabled={creatingCustom}
            >
              <HiPlusCircle className="me-1" /> Create Custom Collection
            </Button>
          </div>
        </Card.Header>
        <Card.Body>
          {loadingCollections ? (
            <div className="text-center py-4">
              <Spinner animation="border" role="status">
                <span className="visually-hidden">Loading Milvus collections...</span>
              </Spinner>
            </div>
          ) : customCollections.length === 0 ? (
            renderEmptyState('No custom Milvus collections found. Create one to power bespoke RAG workflows.', <HiDatabase className="empty-state-icon" />)
          ) : (
            <Table responsive hover size="sm">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Dimension</th>
                  <th>Description</th>
                  <th>Type</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {renderCollectionRows(customCollections, { showVerify: false, allowCollectionTypeEdit: true })}
              </tbody>
            </Table>
          )}
        </Card.Body>
      </Card>

      <CreateMilvusCollectionModal
        show={showKnowledgeHubModal}
        onHide={() => setShowKnowledgeHubModal(false)}
        onCreate={(payload) => handleCreateKnowledgeHubCollection({
          ...payload,
          teamId: payload.teamId || currentTeam?.id
        })}
        teamId={currentTeam?.id}
        embeddingOptions={embeddingOptions}
        loadingEmbeddingOptions={loadingEmbeddingOptions}
        existingCollectionNames={milvusCollections.map(col => col.name)}
        creating={creatingKnowledgeHub}
        collectionType="KNOWLEDGE_HUB"
      />

      <CreateCustomMilvusCollectionModal
        show={showCustomModal}
        onHide={() => setShowCustomModal(false)}
        onCreate={handleCreateCustomCollection}
        teamId={currentTeam?.id}
        existingCollectionNames={milvusCollections.map(col => col.name)}
        creating={creatingCustom}
      />

      <MilvusCollectionDetailsModal
        show={showDetailsModal}
        onHide={() => {
          setShowDetailsModal(false);
          setDetailsResult(null);
        }}
        result={detailsResult}
      />

      <EditMilvusCollectionMetadataModal
        show={Boolean(editingCollection)}
        collection={editingCollection ? {
          ...editingCollection,
          collectionAlias: editingCollection.collectionAlias ?? editingCollection.properties?.collectionAlias ?? null,
          collectionDescription: editingCollection.collectionDescription ?? editingCollection.properties?.collectionDescription ?? editingCollection.description ?? '',
          collectionType: editingCollection.collectionType ?? editingCollection.properties?.collectionType ?? '',
          collectionTypeEditable: Boolean(editingCollection.collectionTypeEditable)
        } : null}
        saving={metadataSaving}
        onHide={() => {
          if (!metadataSaving) {
            setEditingCollection(null);
          }
        }}
        onChange={(updated) => setEditingCollection(prev => ({ ...prev, ...updated }))}
        onSave={handleSaveMetadata}
      />

      <ConfirmationModal
        show={confirmState.show}
        onHide={() => setConfirmState({ show: false, name: '', loading: false })}
        onConfirm={confirmDeletion}
        title="Delete Milvus Collection"
        message={`Are you sure you want to delete Milvus collection "${confirmState.name}"? This action cannot be undone.`}
        confirmLabel={confirmState.loading ? 'Deleting...' : 'Delete'}
        cancelLabel="Cancel"
        variant="danger"
        disabled={confirmState.loading}
      />
    </Container>
  );
};

export default Rag;
