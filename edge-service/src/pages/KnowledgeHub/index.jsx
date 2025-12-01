import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Container, Card, Table, Spinner, Breadcrumb, OverlayTrigger, Tooltip, Badge } from 'react-bootstrap';
import { HiFolder, HiPlus, HiEye, HiPencil, HiTrash, HiBookOpen, HiLink, HiXCircle, HiCollection, HiCube, HiHashtag } from 'react-icons/hi';
import { useTeam } from '../../contexts/TeamContext';
import { useAuth } from '../../contexts/AuthContext';
import { isSuperAdmin, hasAdminAccess } from '../../utils/roleUtils';
import { knowledgeHubCollectionService } from '../../services/knowledgeHubCollectionService';
import { milvusService } from '../../services/milvusService';
import { llmModelService } from '../../services/llmModelService';
import { knowledgeHubGraphService } from '../../services/knowledgeHubGraphService';
import { showSuccessToast, showErrorToast } from '../../utils/toastConfig';
import ConfirmationModal from '../../components/common/ConfirmationModal';
import ConfirmationModalWithVerification from '../../components/common/ConfirmationModalWithVerification';
import PropertiesViewerModal from '../../components/common/PropertiesViewerModal';
import RelatedEntitiesModal from '../../components/common/RelatedEntitiesModal';
import EncryptionVersionWarningBanner from '../../components/common/EncryptionVersionWarningBanner';
import Button from '../../components/common/Button';
import CreateCollectionModal from '../../components/knowledgeHub/CreateCollectionModal';
import EditCollectionModal from '../../components/knowledgeHub/EditCollectionModal';
import MilvusAssignmentModal from '../../components/knowledgeHub/MilvusAssignmentModal';
import './styles.css';

const PROVIDER_LABELS = {
  openai: 'OpenAI',
  gemini: 'Gemini',
  cohere: 'Cohere'
};

function KnowledgeHub() {
  const navigate = useNavigate();
  const { currentTeam } = useTeam();
  const { user } = useAuth();
  const [collections, setCollections] = useState([]);
  const [loading, setLoading] = useState(true);
  const [operationLoading, setOperationLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedCollection, setSelectedCollection] = useState(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showMilvusModal, setShowMilvusModal] = useState(false);
  const [selectedCollectionForMilvus, setSelectedCollectionForMilvus] = useState(null);
  const [milvusCollections, setMilvusCollections] = useState([]);
  const [loadingMilvusCollections, setLoadingMilvusCollections] = useState(false);
  const [embeddingOptions, setEmbeddingOptions] = useState([]);
  const [loadingEmbeddingOptions, setLoadingEmbeddingOptions] = useState(false);
  const [graphStatistics, setGraphStatistics] = useState(null);
  const [loadingGraphStats, setLoadingGraphStats] = useState(false);
  const [graphEntities, setGraphEntities] = useState([]);
  const [loadingGraphEntities, setLoadingGraphEntities] = useState(false);
  const [selectedEntityType, setSelectedEntityType] = useState('All');
  const [graphRelationships, setGraphRelationships] = useState([]);
  const [loadingGraphRelationships, setLoadingGraphRelationships] = useState(false);
  const [selectedRelationshipType, setSelectedRelationshipType] = useState('All');
  const [propertiesModal, setPropertiesModal] = useState({
    show: false,
    title: 'Properties',
    entityType: null,
    entityName: null,
    properties: {},
    loading: false
  });
  const [relatedEntitiesModal, setRelatedEntitiesModal] = useState({
    show: false,
    entityType: null,
    entityId: null,
    entityName: null,
    entity: null // Full entity object with encryption version markers
  });
  const [confirmModal, setConfirmModal] = useState({
    show: false,
    title: '',
    message: '',
    onConfirm: () => {},
    variant: 'danger'
  });
  const [showDeleteAllEntitiesModal, setShowDeleteAllEntitiesModal] = useState(false);
  const [deletingAllEntities, setDeletingAllEntities] = useState(false);
  const [currentEncryptionKeyVersion, setCurrentEncryptionKeyVersion] = useState(null);

  // Helper function to truncate encrypted names for display
  const truncateEncryptedName = (name, maxLength = 60) => {
    if (!name || typeof name !== 'string') return name || 'N/A';
    if (name.length <= maxLength) return name;
    return name.substring(0, maxLength) + '...';
  };

  // Helper function to compare version numbers (e.g., "v1" < "v2")
  const compareVersion = (v1, v2) => {
    if (!v1 || !v2) return false;
    const num1 = parseInt(v1.replace('v', '')) || 0;
    const num2 = parseInt(v2.replace('v', '')) || 0;
    return num1 < num2;
  };

  // Helper function to get entity encryption version
  const getEntityEncryptionVersion = (entity) => {
    // Prefer property-level version, fallback to entity-level version
    return entity?.name_encryption_version || entity?.encryptionKeyVersion || null;
  };

  // Helper function to check if entity is using outdated encryption key
  const isOutdatedEncryption = (entity) => {
    if (!currentEncryptionKeyVersion || !entity) return false;
    const entityVersion = getEntityEncryptionVersion(entity);
    if (!entityVersion) return true; // Legacy/unencrypted entities are considered outdated
    return compareVersion(entityVersion, currentEncryptionKeyVersion);
  };

  const handleOpenPropertiesModal = async (title, entityType, entityName, properties, fromEntity = null, toEntity = null) => {
    const isAdmin = isSuperAdmin(user) || hasAdminAccess(user, currentTeam);
    
    if (isAdmin) {
      // For admin users, decrypt properties and entity names before showing
      setPropertiesModal({
        show: true,
        title,
        entityType,
        entityName,
        properties: {},
        loading: true
      });

      try {
        // Build a combined properties map that includes entity names and encryption version markers for decryption
        const propertiesToDecrypt = { ...(properties || {}) };
        
        // Build entity name properties with encryption version markers
        const fromEntityProps = {};
        if (fromEntity?.name) {
          fromEntityProps['name'] = fromEntity.name;
          // Include encryption version markers if present
          if (fromEntity.name_encryption_version) {
            fromEntityProps['name_encryption_version'] = fromEntity.name_encryption_version;
          } else if (fromEntity.encryptionKeyVersion) {
            fromEntityProps['encryptionKeyVersion'] = fromEntity.encryptionKeyVersion;
          }
        }
        
        const toEntityProps = {};
        if (toEntity?.name) {
          toEntityProps['name'] = toEntity.name;
          // Include encryption version markers if present
          if (toEntity.name_encryption_version) {
            toEntityProps['name_encryption_version'] = toEntity.name_encryption_version;
          } else if (toEntity.encryptionKeyVersion) {
            toEntityProps['encryptionKeyVersion'] = toEntity.encryptionKeyVersion;
          }
        }
        
        // Decrypt entity names separately if they exist
        let decryptedFromName = fromEntity?.name || fromEntity?.id || 'N/A';
        let decryptedToName = toEntity?.name || toEntity?.id || 'N/A';
        
        if (Object.keys(fromEntityProps).length > 0) {
          const fromNameResult = await knowledgeHubGraphService.decryptProperties(fromEntityProps);
          if (fromNameResult.success && fromNameResult.data?.name) {
            decryptedFromName = fromNameResult.data.name;
          }
        }
        
        if (Object.keys(toEntityProps).length > 0) {
          const toNameResult = await knowledgeHubGraphService.decryptProperties(toEntityProps);
          if (toNameResult.success && toNameResult.data?.name) {
            decryptedToName = toNameResult.data.name;
          }
        }
        
        // Update subtitle with decrypted names
        const fromType = fromEntity?.type || 'Unknown';
        const toType = toEntity?.type || 'Unknown';
        const updatedEntityName = `${fromType}:${decryptedFromName} → ${toType}:${decryptedToName}`;
        
        // Decrypt relationship properties if they exist
        if (Object.keys(propertiesToDecrypt).length > 0) {
          const result = await knowledgeHubGraphService.decryptProperties(propertiesToDecrypt);
          if (result.success) {
            setPropertiesModal(prev => ({
              ...prev,
              entityName: updatedEntityName,
              properties: result.data || {},
              loading: false
            }));
          } else {
            // If decryption fails, show original properties but with decrypted entity names
            console.warn('Failed to decrypt properties:', result.error);
            setPropertiesModal(prev => ({
              ...prev,
              entityName: updatedEntityName,
              properties: properties || {},
              loading: false
            }));
          }
        } else {
          // No properties to decrypt, but still update entity name
          setPropertiesModal(prev => ({
            ...prev,
            entityName: updatedEntityName,
            properties: properties || {},
            loading: false
          }));
        }
      } catch (error) {
        console.error('Error decrypting properties:', error);
        // On error, show original properties
        setPropertiesModal(prev => ({
          ...prev,
          properties: properties || {},
          loading: false
        }));
      }
    } else {
      // For non-admin users, show as-is
      setPropertiesModal({
        show: true,
        title,
        entityType,
        entityName,
        properties: properties || {},
        loading: false
      });
    }
  };

  useEffect(() => {
    if (currentTeam?.id) {
      fetchCollections();
      loadMilvusCollections();
      loadEmbeddingOptions();
      fetchGraphStatistics();
      
      // Fetch current encryption key version
      const fetchCurrentEncryptionVersion = async () => {
        try {
          const result = await knowledgeHubGraphService.getCurrentEncryptionKeyVersion();
          if (result.success && result.data?.currentKeyVersion) {
            setCurrentEncryptionKeyVersion(result.data.currentKeyVersion);
          }
        } catch (err) {
          console.error('Error fetching current encryption key version:', err);
        }
      };
      fetchCurrentEncryptionVersion();
    }
  }, [currentTeam?.id]);

  useEffect(() => {
    if (currentTeam?.id) {
      fetchGraphEntities(selectedEntityType);
      fetchGraphRelationships(selectedRelationshipType);
    }
  }, [currentTeam?.id, selectedEntityType, selectedRelationshipType]);

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

  const transformEmbeddingOptions = (models = []) => {
    const grouped = models.reduce((acc, model) => {
      if (!model) {
        return acc;
      }

      const providerKey = model.provider || (model.modelCategory ? model.modelCategory.split('-')[0] : 'unknown');
      if (!acc[providerKey]) {
        acc[providerKey] = {
          provider: providerKey,
          label: PROVIDER_LABELS[providerKey] || providerKey,
          models: []
        };
      }

      const rawDimension = model.embeddingDimension ?? model.dimension ?? model.dimensions;
      const dimensionValue = typeof rawDimension === 'number'
        ? rawDimension
        : rawDimension
          ? parseInt(rawDimension, 10)
          : undefined;

      acc[providerKey].models.push({
        modelName: model.modelName,
        modelCategory: model.modelCategory,
        dimension: dimensionValue,
        embeddingDimension: dimensionValue,
        supportsLateChunking: model.supportsLateChunking,
        inputPricePer1M: typeof model.inputPricePer1M === 'number' ? model.inputPricePer1M : undefined,
        outputPricePer1M: typeof model.outputPricePer1M === 'number' ? model.outputPricePer1M : undefined
      });
      return acc;
    }, {});

    return Object.values(grouped).map(option => ({
      ...option,
      models: option.models.sort((a, b) => a.modelName.localeCompare(b.modelName))
    }));
  };

  const loadMilvusCollections = async () => {
    if (!currentTeam?.id) return;

    try {
      setLoadingMilvusCollections(true);
      const { data, error } = await milvusService.getCollectionsForTeam(currentTeam.id, { collectionType: 'KNOWLEDGE_HUB' });
      if (error) throw new Error(error);
      setMilvusCollections(data || []);
    } catch (err) {
      console.error('Error loading RAG collections:', err);
      showErrorToast(err.message || 'Failed to load RAG collections');
    } finally {
      setLoadingMilvusCollections(false);
    }
  };

  const handleAssignMilvus = (collection) => {
    if (!currentTeam?.id) {
      showErrorToast('Select a team to manage RAG collections');
      return;
    }

    setSelectedCollectionForMilvus(collection);
    setShowMilvusModal(true);
    loadMilvusCollections();
    loadEmbeddingOptions();
  };

  const loadEmbeddingOptions = async () => {
    if (!currentTeam?.id) return;

    try {
      setLoadingEmbeddingOptions(true);
      const { data, error } = await llmModelService.getEmbeddingModels(currentTeam.id);
      if (error) throw new Error(error);
      setEmbeddingOptions(transformEmbeddingOptions(data || []));
    } catch (err) {
      console.error('Error loading embedding models:', err);
      showErrorToast(err.message || 'Failed to load embedding models');
    } finally {
      setLoadingEmbeddingOptions(false);
    }
  };

  const handleMilvusAssignmentSave = async (payload) => {
    if (!selectedCollectionForMilvus) return;

    try {
      setOperationLoading(true);
      const { error } = await knowledgeHubCollectionService.assignMilvusCollection(selectedCollectionForMilvus.id, payload);
      if (error) throw new Error(error);
      showSuccessToast('RAG collection assigned successfully');
      setShowMilvusModal(false);
      setSelectedCollectionForMilvus(null);
      fetchCollections();
    } catch (err) {
      console.error('Error assigning RAG collection:', err);
      showErrorToast(err.message || 'Failed to assign RAG collection');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleMilvusAssignmentRemove = async (collection) => {
    try {
      setOperationLoading(true);
      const { error } = await knowledgeHubCollectionService.removeMilvusCollection(collection.id);
      if (error) throw new Error(error);
      showSuccessToast('RAG assignment removed');
      fetchCollections();
    } catch (err) {
      console.error('Error removing RAG assignment:', err);
      showErrorToast(err.message || 'Failed to remove RAG assignment');
    } finally {
      setOperationLoading(false);
    }
  };

  const resolveProviderLabel = (modelCategory) => {
    if (!modelCategory) return 'N/A';
    const providerKey = modelCategory.split('-')[0];
    return PROVIDER_LABELS[providerKey] || providerKey;
  };

  const fetchGraphStatistics = async () => {
    if (!currentTeam?.id) return;

    try {
      setLoadingGraphStats(true);
      const { data, error } = await knowledgeHubGraphService.getGraphStatistics();
      if (error) throw new Error(error);
      setGraphStatistics(data);
    } catch (err) {
      console.error('Error fetching graph statistics:', err);
    } finally {
      setLoadingGraphStats(false);
    }
  };

  const fetchGraphEntities = async (entityType) => {
    if (!currentTeam?.id) return;

    try {
      setLoadingGraphEntities(true);
      
      if (entityType === 'All') {
        // Fetch all entity types and combine them
        const entityTypes = ['Form', 'Organization', 'Person', 'Date', 'Location', 'Document'];
        const entityPromises = entityTypes.map(type => 
          knowledgeHubGraphService.findEntities(type, { teamId: currentTeam.id })
        );
        
        const entityResults = await Promise.all(entityPromises);
        const allEntities = entityResults.reduce((acc, result, index) => {
          if (result.success && Array.isArray(result.data)) {
            // Add type to each entity if not present
            const entityType = entityTypes[index];
            const typedEntities = result.data.map(entity => ({
              ...entity,
              type: entity.type || entityType
            }));
            return [...acc, ...typedEntities];
          }
          return acc;
        }, []);
        
        setGraphEntities(allEntities);
      } else {
        // Fetch entities for specific type - filtered by teamId only (no documentId)
        const { data, error } = await knowledgeHubGraphService.findEntities(entityType, { teamId: currentTeam.id });
        if (error) throw new Error(error);
        const entities = Array.isArray(data) ? data : [];
        // Ensure type is set
        const typedEntities = entities.map(entity => ({
          ...entity,
          type: entity.type || entityType
        }));
        setGraphEntities(typedEntities);
      }
    } catch (err) {
      console.error('Error fetching graph entities:', err);
      showErrorToast(err.message || 'Failed to fetch entities');
      setGraphEntities([]);
    } finally {
      setLoadingGraphEntities(false);
    }
  };

  const handleDeleteAllEntities = () => {
    setShowDeleteAllEntitiesModal(true);
  };

  const handleDeleteAllEntitiesConfirm = async () => {
    setDeletingAllEntities(true);
    
    try {
      const entityTypes = ['Form', 'Organization', 'Person', 'Date', 'Location', 'Document'];
      let totalDeleted = 0;
      const errors = [];

      // Delete all entities of each type
      for (const entityType of entityTypes) {
        const result = await knowledgeHubGraphService.deleteAllEntitiesByType(entityType);
        if (result.success && result.data) {
          totalDeleted += result.data.deletedCount || 0;
        } else {
          errors.push(`${entityType}: ${result.error || 'Unknown error'}`);
        }
      }

      if (errors.length > 0) {
        showErrorToast(`Deleted ${totalDeleted} entities, but some errors occurred: ${errors.join(', ')}`);
      } else {
        showSuccessToast(`Successfully deleted ${totalDeleted} entities and all their relationships.`);
      }

      // Refresh entities and statistics
      await fetchGraphEntities(selectedEntityType);
      await fetchGraphStatistics();
      
      setShowDeleteAllEntitiesModal(false);
    } catch (error) {
      console.error('Error deleting all entities:', error);
      showErrorToast(error.message || 'Failed to delete all entities');
    } finally {
      setDeletingAllEntities(false);
    }
  };

  const fetchGraphRelationships = async (relationshipType) => {
    if (!currentTeam?.id) return;

    try {
      setLoadingGraphRelationships(true);
      const filters = { teamId: currentTeam.id };
      if (relationshipType && relationshipType !== 'All') {
        filters.relationshipType = relationshipType;
      }
      
      const { data, error } = await knowledgeHubGraphService.findRelationships(filters);
      if (error) throw new Error(error);
      setGraphRelationships(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Error fetching graph relationships:', err);
      showErrorToast(err.message || 'Failed to fetch relationships');
      setGraphRelationships([]);
    } finally {
      setLoadingGraphRelationships(false);
    }
  };

  const entityTypes = ['Form', 'Organization', 'Person', 'Date', 'Location', 'Document'];
  const entityCounts = graphStatistics?.entityCounts || {};
  const totalEntities = graphStatistics?.totalEntities || 0;
  const totalRelationships = graphStatistics?.totalRelationships || 0;
  
  // Extract unique relationship types from relationships
  const relationshipTypes = Array.from(new Set(graphRelationships.map(r => r.relationshipType).filter(Boolean))).sort();

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
              <h4 className="mb-0">RAG Collections</h4>
            </div>
            <div className="ms-auto d-flex align-items-center gap-2">
              <Button
                variant="outline-secondary"
                size="sm"
                onClick={() => navigate('/rag')}
              >
                <HiCollection className="me-1" /> Manage RAG
              </Button>
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
            <th>RAG Collection</th>
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
                    <td className="milvus-collection-cell">
                      {collection.milvusCollectionName ? (
                        <OverlayTrigger
                          placement="top"
                          overlay={(
                            <Tooltip id={`milvus-collection-tooltip-${collection.id || collection._id || collection.milvusCollectionName}`}>
                              <div className="text-start">
                                <div><strong>Collection:</strong> {collection.milvusCollectionName}</div>
                                <div><strong>Provider:</strong> {resolveProviderLabel(collection.embeddingModel)}</div>
                                <div><strong>Model:</strong> {collection.embeddingModelName || 'N/A'}</div>
                                <div><strong>Dimension:</strong> {collection.embeddingDimension || 'N/A'}</div>
                                <div><strong>Late chunking:</strong> {collection.lateChunkingEnabled ? 'Enabled' : 'Disabled'}</div>
                              </div>
                            </Tooltip>
                          )}
                        >
                          <div className="milvus-collection-cell-content">
                            <span className="milvus-name text-truncate">{collection.milvusCollectionName}</span>
                            <small className="milvus-details text-muted">
                              <span className="milvus-details-text text-truncate d-inline-block">
                                {resolveProviderLabel(collection.embeddingModel)} · {collection.embeddingModelName || 'N/A'}
                                {collection.embeddingDimension ? ` (${collection.embeddingDimension} dims)` : ''}
                              </span>
                              {collection.lateChunkingEnabled && <Badge bg="info">Late Chunking</Badge>}
                            </small>
                          </div>
                        </OverlayTrigger>
                      ) : (
                        <span className="text-muted">Not assigned</span>
                      )}
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
                          overlay={<Tooltip>{collection.milvusCollectionName ? 'Modify RAG Assignment' : 'Assign RAG Collection'}</Tooltip>}
                        >
                          <Button
                            variant="outline-success"
                            size="sm"
                            onClick={() => handleAssignMilvus(collection)}
                          >
                            <HiLink /> {collection.milvusCollectionName ? 'RAG' : 'Assign'}
                          </Button>
                        </OverlayTrigger>
                        {collection.milvusCollectionName && (
                          <OverlayTrigger
                            placement="top"
                          overlay={<Tooltip>Remove RAG Assignment</Tooltip>}
                          >
                            <span>
                              <Button
                                variant="outline-warning"
                                size="sm"
                                onClick={() => handleMilvusAssignmentRemove(collection)}
                                disabled={operationLoading}
                              >
                                <HiXCircle /> Clear
                              </Button>
                            </span>
                          </OverlayTrigger>
                        )}
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

      {/* Knowledge Graph Section */}
      <Card className="border-0 mt-4">
        <Card.Header>
          <div className="d-flex align-items-center">
            <div className="d-flex align-items-center gap-2">
              <HiHashtag className="page-icon" />
              <h4 className="mb-0">Knowledge Graph</h4>
            </div>
            <div className="ms-auto d-flex align-items-center gap-2">
              <Button
                variant="outline-secondary"
                size="sm"
                className="me-2"
                onClick={() => {
                  // In development, Neo4j Browser is accessible directly on localhost:7474
                  // In production, it's proxied through Nginx at /neo4j/
                  const neo4jUrl = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
                    ? 'http://localhost:7474'
                    : '/neo4j/';
                  window.open(neo4jUrl, '_blank');
                }}
              >
                <HiCube className="me-0" /> View in Neo4j Browser
              </Button>
              <Button
                variant="outline-danger"
                size="sm"
                className="me-2"
                onClick={handleDeleteAllEntities}
                disabled={loadingGraphEntities || loadingGraphStats}
              >
                <HiTrash className="me-0" /> Delete All Entities
              </Button>
              <Button
                variant="outline-primary"
                size="sm"
                onClick={fetchGraphStatistics}
                disabled={loadingGraphStats}
              >
                {loadingGraphStats ? (
                  <>
                    <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" />
                    <span className="ms-2">Loading...</span>
                  </>
                ) : (
                  'Refresh'
                )}
              </Button>
            </div>
          </div>
        </Card.Header>
        <Card.Body>
          {loadingGraphStats ? (
            <div className="text-center py-5">
              <Spinner animation="border" role="status">
                <span className="visually-hidden">Loading graph statistics...</span>
              </Spinner>
            </div>
          ) : (
            <>
              {/* Graph Statistics */}
              <div className="mb-4">
                <div className="row g-3 mb-3">
                  {entityTypes.map((type) => (
                    <div key={type} className="col-md-2">
                      <Card className="text-center h-100">
                        <Card.Body>
                          <div className="text-muted small mb-1">{type}</div>
                          <div className="h4 mb-0">{entityCounts[type] || 0}</div>
                        </Card.Body>
                      </Card>
                    </div>
                  ))}
                </div>
                <div className="row">
                  <div className="col-md-6">
                    <Card className="h-100">
                      <Card.Body>
                        <div className="text-muted small mb-1">Total Entities</div>
                        <div className="h3 mb-0">{totalEntities}</div>
                      </Card.Body>
                    </Card>
                  </div>
                  <div className="col-md-6">
                    <Card className="h-100">
                      <Card.Body>
                        <div className="text-muted small mb-1">Total Relationships</div>
                        <div className="h3 mb-0">{totalRelationships}</div>
                      </Card.Body>
                    </Card>
                  </div>
                </div>
              </div>

              {/* Entity Browser */}
              <div>
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <h5>Entity Browser</h5>
                  <div className="d-flex align-items-center gap-2">
                    <select
                      className="form-select form-select-sm"
                      style={{ width: 'auto' }}
                      value={selectedEntityType}
                      onChange={(e) => setSelectedEntityType(e.target.value)}
                    >
                      <option value="All">All Types</option>
                      {entityTypes.map((type) => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </select>
                  </div>
                </div>

                {(() => {
                  const outdatedCount = graphEntities.filter(e => isOutdatedEncryption(e)).length;
                  return (
                    <EncryptionVersionWarningBanner
                      show={outdatedCount > 0 && currentEncryptionKeyVersion}
                      variant="warning"
                      heading={
                        <>
                          {outdatedCount} {outdatedCount === 1 ? 'entity' : 'entities'} {outdatedCount === 1 ? 'is' : 'are'} encrypted with an outdated key version
                        </>
                      }
                      message={
                        <>
                          These entities are encrypted with an older encryption key version and need to be re-encrypted.
                          To fix this, you should: <strong>1) Hard delete all files</strong>, <strong>2) Re-upload the documents</strong>, and <strong>3) Re-extract the entities</strong>.
                          This will ensure all data is encrypted with the current version ({currentEncryptionKeyVersion}).
                        </>
                      }
                    />
                  );
                })()}

                {loadingGraphEntities ? (
                  <div className="text-center py-5">
                    <Spinner animation="border" role="status">
                      <span className="visually-hidden">Loading entities...</span>
                    </Spinner>
                  </div>
                ) : graphEntities.length === 0 ? (
                  <div className="text-center py-4 text-muted">
                    {selectedEntityType === 'All' 
                      ? 'No entities found'
                      : `No ${selectedEntityType} entities found`}
                  </div>
                ) : (
                  <>
                    <Table responsive hover>
                      <thead>
                        <tr>
                          <th>ID</th>
                          <th>Name</th>
                          <th>Type</th>
                          <th>Encryption Version</th>
                          <th>Properties</th>
                          <th className="text-end">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {graphEntities.slice(0, 100).map((entity, idx) => {
                          // Filter out system fields - same list used in both count and extraction
                          const excludedKeys = ['id', 'name', 'type', 'teamId', 'documentId', 'extractedAt', 'createdAt', 'updatedAt'];
                          const propertyCount = Object.keys(entity).filter(k => !excludedKeys.includes(k)).length;
                          const entityProperties = Object.entries(entity)
                            .filter(([key]) => !excludedKeys.includes(key))
                            .reduce((acc, [key, value]) => {
                              acc[key] = value;
                              return acc;
                            }, {});
                          
                          return (
                            <tr 
                              key={entity.id || idx}
                              style={{ cursor: 'pointer' }}
                              onClick={() => handleOpenPropertiesModal(
                                'Entity Properties',
                                entity.type || 'Unknown',
                                entity.name || entity.id || 'Unnamed',
                                entityProperties
                              )}
                            >
                              <td>
                                <code className="small">{entity.id || 'N/A'}</code>
                              </td>
                              <td>
                                <code className="small" title={entity.name || entity.id || 'Unnamed'}>
                                  {truncateEncryptedName(entity.name || entity.id || 'Unnamed')}
                                </code>
                              </td>
                              <td>
                                <Badge bg="secondary">{entity.type || 'Unknown'}</Badge>
                              </td>
                              <td>
                                {(() => {
                                  const entityVersion = getEntityEncryptionVersion(entity);
                                  const isOutdated = isOutdatedEncryption(entity);
                                  const displayVersion = entityVersion || 'none';
                                  
                                  if (isOutdated && currentEncryptionKeyVersion) {
                                    return (
                                      <div>
                                        <Badge bg="warning" className="me-1" title={`Entity encrypted with ${displayVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`}>
                                          {displayVersion}
                                        </Badge>
                                        <small className="text-warning" title={`Needs re-encryption. Current: ${currentEncryptionKeyVersion}`}>
                                          ⚠️ Outdated
                                        </small>
                                      </div>
                                    );
                                  }
                                  
                                  return (
                                    <Badge bg={entityVersion ? "success" : "secondary"} title={entityVersion ? `Encrypted with ${displayVersion}` : 'Not encrypted (legacy)'}>
                                      {displayVersion}
                                    </Badge>
                                  );
                                })()}
                              </td>
                              <td
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleOpenPropertiesModal(
                                    'Entity Properties',
                                    entity.type || 'Unknown',
                                    entity.name || entity.id || 'Unnamed',
                                    entityProperties
                                  );
                                }}
                                style={{ cursor: 'pointer' }}
                              >
                                <small className="text-muted">
                                  {propertyCount} properties
                                </small>
                              </td>
                              <td className="text-end" onClick={(e) => e.stopPropagation()}>
                                <div className="d-flex gap-2 justify-content-end">
                                  <Button
                                    variant="outline-primary"
                                    size="sm"
                                    onClick={() => {
                                      setRelatedEntitiesModal({
                                        show: true,
                                        entityType: entity.type || 'Unknown',
                                        entityId: entity.id,
                                        entityName: entity.name || entity.id || 'Unnamed',
                                        entity: entity // Pass full entity object for decryption
                                      });
                                    }}
                                    style={{ fontSize: '0.875rem' }}
                                  >
                                    <HiLink className="me-1" /> View Related Entities
                                  </Button>
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </Table>
                    {graphEntities.length > 100 && (
                      <div className="text-center mt-3 text-muted">
                        <small>Showing first 100 of {graphEntities.length} entities</small>
                      </div>
                    )}
                  </>
                )}
              </div>

              {/* Relationship Browser */}
              <div className="mt-5">
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <h5>Relationship Browser</h5>
                  <div className="d-flex align-items-center gap-2">
                    <select
                      className="form-select form-select-sm"
                      style={{ width: 'auto' }}
                      value={selectedRelationshipType}
                      onChange={(e) => setSelectedRelationshipType(e.target.value)}
                      disabled={loadingGraphRelationships}
                    >
                      <option value="All">All Types</option>
                      {relationshipTypes.map((type) => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </select>
                  </div>
                </div>

                {loadingGraphRelationships ? (
                  <div className="text-center py-5">
                    <Spinner animation="border" role="status">
                      <span className="visually-hidden">Loading relationships...</span>
                    </Spinner>
                  </div>
                ) : graphRelationships.length === 0 ? (
                  <div className="text-center py-4 text-muted">
                    {selectedRelationshipType === 'All' 
                      ? 'No relationships found'
                      : `No ${selectedRelationshipType} relationships found`}
                  </div>
                ) : (
                  <>
                    <Table responsive hover>
                      <thead>
                        <tr>
                          <th>Type</th>
                          <th>From Entity</th>
                          <th>From Entity Name</th>
                          <th>From Encryption</th>
                          <th>To Entity</th>
                          <th>To Entity Name</th>
                          <th>To Encryption</th>
                          <th>Properties</th>
                        </tr>
                      </thead>
                      <tbody>
                        {graphRelationships.slice(0, 100).map((relationship, idx) => {
                          const propertyCount = Object.keys(relationship.properties || {}).length;
                          const fromEntityName = relationship.fromEntity?.name || relationship.fromEntity?.id || 'N/A';
                          const toEntityName = relationship.toEntity?.name || relationship.toEntity?.id || 'N/A';
                          const relationshipTitle = `${relationship.relationshipType || 'Unknown'} Relationship`;
                          const relationshipSubtitle = `${relationship.fromEntity?.type || 'Unknown'}:${fromEntityName} → ${relationship.toEntity?.type || 'Unknown'}:${toEntityName}`;
                          
                          return (
                            <tr 
                              key={idx}
                              style={{ cursor: 'pointer' }}
                              onClick={() => handleOpenPropertiesModal(
                                relationshipTitle,
                                relationship.relationshipType || 'Unknown',
                                relationshipSubtitle,
                                relationship.properties || {},
                                relationship.fromEntity,
                                relationship.toEntity
                              )}
                            >
                              <td>
                                <Badge bg="info">{relationship.relationshipType || 'Unknown'}</Badge>
                              </td>
                              <td>
                                <Badge bg="secondary">{relationship.fromEntity?.type || 'Unknown'}</Badge>
                              </td>
                              <td>
                                <code className="small" title={fromEntityName}>
                                  {truncateEncryptedName(fromEntityName)}
                                </code>
                              </td>
                              <td>
                                {(() => {
                                  const fromVersion = getEntityEncryptionVersion(relationship.fromEntity);
                                  const fromOutdated = relationship.fromEntity && isOutdatedEncryption(relationship.fromEntity);
                                  const fromDisplayVersion = fromVersion || 'none';
                                  
                                  if (fromOutdated && currentEncryptionKeyVersion) {
                                    return (
                                      <div>
                                        <Badge bg="warning" className="me-1" title={`Entity encrypted with ${fromDisplayVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`}>
                                          {fromDisplayVersion}
                                        </Badge>
                                        <small className="text-warning">⚠️</small>
                                      </div>
                                    );
                                  }
                                  
                                  return (
                                    <Badge bg={fromVersion ? "success" : "secondary"} title={fromVersion ? `Encrypted with ${fromDisplayVersion}` : 'Not encrypted (legacy)'}>
                                      {fromDisplayVersion}
                                    </Badge>
                                  );
                                })()}
                              </td>
                              <td>
                                <Badge bg="secondary">{relationship.toEntity?.type || 'Unknown'}</Badge>
                              </td>
                              <td>
                                <code className="small" title={toEntityName}>
                                  {truncateEncryptedName(toEntityName)}
                                </code>
                              </td>
                              <td>
                                {(() => {
                                  const toVersion = getEntityEncryptionVersion(relationship.toEntity);
                                  const toOutdated = relationship.toEntity && isOutdatedEncryption(relationship.toEntity);
                                  const toDisplayVersion = toVersion || 'none';
                                  
                                  if (toOutdated && currentEncryptionKeyVersion) {
                                    return (
                                      <div>
                                        <Badge bg="warning" className="me-1" title={`Entity encrypted with ${toDisplayVersion}, but current version is ${currentEncryptionKeyVersion}. Needs re-encryption.`}>
                                          {toDisplayVersion}
                                        </Badge>
                                        <small className="text-warning">⚠️</small>
                                      </div>
                                    );
                                  }
                                  
                                  return (
                                    <Badge bg={toVersion ? "success" : "secondary"} title={toVersion ? `Encrypted with ${toDisplayVersion}` : 'Not encrypted (legacy)'}>
                                      {toDisplayVersion}
                                    </Badge>
                                  );
                                })()}
                              </td>
                              <td
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleOpenPropertiesModal(
                                    relationshipTitle,
                                    relationship.relationshipType || 'Unknown',
                                    relationshipSubtitle,
                                    relationship.properties || {},
                                    relationship.fromEntity,
                                    relationship.toEntity
                                  );
                                }}
                                style={{ cursor: 'pointer' }}
                              >
                                <small className="text-muted">
                                  {propertyCount} properties
                                </small>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </Table>
                    {graphRelationships.length > 100 && (
                      <div className="text-center mt-3 text-muted">
                        <small>Showing first 100 of {graphRelationships.length} relationships</small>
                      </div>
                    )}
                  </>
                )}
              </div>
            </>
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

      <MilvusAssignmentModal
        show={showMilvusModal}
        onHide={() => {
          setShowMilvusModal(false);
          setSelectedCollectionForMilvus(null);
        }}
        milvusCollections={milvusCollections}
        embeddingOptions={embeddingOptions}
        collection={selectedCollectionForMilvus}
        loading={operationLoading || loadingMilvusCollections || loadingEmbeddingOptions}
        onSave={handleMilvusAssignmentSave}
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
      <ConfirmationModalWithVerification
        show={showDeleteAllEntitiesModal}
        onHide={() => setShowDeleteAllEntitiesModal(false)}
        onConfirm={handleDeleteAllEntitiesConfirm}
        title="Delete All Entities"
        message="Are you sure you want to delete ALL entities (Form, Organization, Person, Date, Location, Document) for this team? This action cannot be undone and will also delete all relationships."
        variant="danger"
        confirmLabel="Delete All"
        loading={deletingAllEntities}
      />

      <PropertiesViewerModal
        show={propertiesModal.show}
        onHide={() => setPropertiesModal(prev => ({ ...prev, show: false }))}
        title={propertiesModal.title}
        entityType={propertiesModal.entityType}
        entityName={propertiesModal.entityName}
        properties={propertiesModal.properties}
        loading={propertiesModal.loading}
      />

      <RelatedEntitiesModal
        show={relatedEntitiesModal.show}
        onHide={() => setRelatedEntitiesModal(prev => ({ ...prev, show: false }))}
        entityType={relatedEntitiesModal.entityType}
        entityId={relatedEntitiesModal.entityId}
        entityName={relatedEntitiesModal.entityName}
        entity={relatedEntitiesModal.entity}
      />
    </Container>
  );
}

export default KnowledgeHub;

