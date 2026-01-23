import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Badge,
  Card,
  Row,
  Col,
  Button as BootstrapButton,
  Spinner,
  OverlayTrigger,
  Tooltip
} from 'react-bootstrap';
import Button from '../../../components/common/Button';
import {
  HiArrowLeft,
  HiPencil,
  HiUsers,
  HiUserGroup,
  HiTemplate,
  HiDocumentText,
  HiOfficeBuilding,
  HiClock,
  HiLockClosed,
  HiKey,
  HiSparkles,
  HiDatabase,
  HiEye,
  HiArrowUp,
  HiArrowDown,
  HiSave,
  HiRefresh,
  HiServer
} from 'react-icons/hi';
import { SiOpenai, SiGoogle, SiAnthropic } from 'react-icons/si';
import { FaCloud } from 'react-icons/fa';
import { teamService } from '../../../services/teamService';
import { linqLlmModelService } from '../../../services/linqLlmModelService';
import { milvusService } from '../../../services/milvusService';
import { knowledgeHubCollectionService } from '../../../services/knowledgeHubCollectionService';
import { knowledgeHubDocumentService } from '../../../services/knowledgeHubDocumentService';
import { showSuccessToast, showErrorToast } from '../../../utils/toastConfig';
import TeamMembersModal from '../../../components/teams/TeamMembersModal';
import TeamRoutesModal from '../../../components/teams/TeamRoutesModal';
import TeamEditModal from '../../../components/teams/TeamEditModal';
import TeamApiKeysModal from '../../../components/teams/TeamApiKeysModal';
import OpenAIModal from '../../../components/teams/OpenAIModal';
import GeminiModal from '../../../components/teams/GeminiModal';
import CohereModal from '../../../components/teams/CohereModal';
import ClaudeModal from '../../../components/teams/ClaudeModal';
import OllamaModal from '../../../components/teams/OllamaModal';
import ConfirmationModal from '../../../components/common/ConfirmationModal';
import Footer from '../../../components/common/Footer';
import './styles.css';

const PERMISSION_INFO = {
  VIEW: {
    description: "Can view API endpoints and documentation",
    color: "#0dcaf0" // Info/Cyan
  },
  USE: {
    description: "Can make API calls to these endpoints",
    color: "#0dcaf0" // Info/Cyan
  },
  MANAGE: {
    description: "Can configure and update these endpoints",
    color: "#0dcaf0" // Info/Cyan
  }
};

function ViewTeam() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const [team, setTeam] = useState(null);
  const [llmModels, setLlmModels] = useState([]);
  const [apiKeys, setApiKeys] = useState([]);
  const [loading, setLoading] = useState(true);
  const [operationLoading, setOperationLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showMembersModal, setShowMembersModal] = useState(false);
  const [showRoutesModal, setShowRoutesModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showApiKeysModal, setShowApiKeysModal] = useState(false);
  const [showOpenAIModal, setShowOpenAIModal] = useState(false);
  const [showGeminiModal, setShowGeminiModal] = useState(false);
  const [showCohereModal, setShowCohereModal] = useState(false);
  const [showClaudeModal, setShowClaudeModal] = useState(false);
  const [showOllamaModal, setShowOllamaModal] = useState(false);
  const [confirmModal, setConfirmModal] = useState({
    show: false,
    title: '',
    message: '',
    onConfirm: () => { },
    variant: 'danger'
  });
  const [knowledgeCollections, setKnowledgeCollections] = useState([]);
  const [knowledgeCollectionsLoading, setKnowledgeCollectionsLoading] = useState(false);
  const [knowledgeCollectionsError, setKnowledgeCollectionsError] = useState(null);
  const [milvusCollections, setMilvusCollections] = useState([]);
  const [milvusCollectionsLoading, setMilvusCollectionsLoading] = useState(false);
  const [milvusCollectionsError, setMilvusCollectionsError] = useState(null);
  const [activeKeyVersion, setActiveKeyVersion] = useState(null);
  const [priorityChanged, setPriorityChanged] = useState(false);

  // Sort LLM models by priority (lower = higher priority)
  const sortedLlmModels = useMemo(() => {
    if (!llmModels || llmModels.length === 0) return [];
    return [...llmModels].sort((a, b) => {
      const priorityA = a.priority ?? Number.MAX_SAFE_INTEGER;
      const priorityB = b.priority ?? Number.MAX_SAFE_INTEGER;
      return priorityA - priorityB;
    });
  }, [llmModels]);

  useEffect(() => {
    if (teamId) {
      fetchTeam();
      fetchLlmModels();
      fetchApiKeys();
      fetchKnowledgeCollections();
      fetchMilvusCollections();
      fetchActiveKey();
    }
  }, [teamId]);

  const fetchTeam = async () => {
    try {
      setLoading(true);
      setError(null);
      const { data, error } = await teamService.getTeamById(teamId);
      if (error) throw new Error(error);
      setTeam(data);
      setLoading(false);
    } catch (err) {
      setError('Failed to load team details');
      setLoading(false);
      console.error('Error fetching team:', err);
    }
  };

  const fetchLlmModels = async () => {
    try {
      const data = await linqLlmModelService.getTeamConfiguration(teamId);
      setLlmModels(Array.isArray(data) ? data : []);
      setPriorityChanged(false);
    } catch (err) {
      console.error('Error fetching LLM models:', err);
      setLlmModels([]);
    }
  };

  const fetchApiKeys = async () => {
    try {
      const { data, error } = await teamService.getTeamApiKeys(teamId);
      if (error) throw new Error(error);
      setApiKeys(data || []);
    } catch (err) {
      console.error('Error fetching API keys:', err);
      setApiKeys([]);
    }
  };

  const fetchActiveKey = async () => {
    try {
      const { data, success } = await teamService.getActiveEncryptionKeyVersion(teamId);
      if (success && data) {
        setActiveKeyVersion(data.version);
      }
    } catch (err) {
      console.error('Error fetching active key:', err);
    }
  };

  const fetchMilvusCollections = async () => {
    if (!teamId) return;
    try {
      setMilvusCollectionsLoading(true);
      setMilvusCollectionsError(null);
      const { data, error } = await milvusService.getCollectionsForTeam(teamId);
      if (error) throw new Error(error);
      setMilvusCollections(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Error fetching Milvus collections:', err);
      setMilvusCollections([]);
      setMilvusCollectionsError(err.message || 'Failed to load Milvus collections');
    } finally {
      setMilvusCollectionsLoading(false);
    }
  };

  const fetchKnowledgeCollections = async () => {
    try {
      setKnowledgeCollectionsLoading(true);
      setKnowledgeCollectionsError(null);

      const { success, data, error } = await knowledgeHubCollectionService.getAllCollections();
      if (!success) {
        throw new Error(error || 'Failed to load knowledge hub collections');
      }

      const collections = Array.isArray(data) ? data : [];

      const collectionsWithDocuments = await Promise.all(
        collections.map(async (collection) => {
          try {
            const documentsResponse = await knowledgeHubDocumentService.getAllDocuments({ collectionId: collection.id });
            if (!documentsResponse.success) {
              throw new Error(documentsResponse.error || 'Failed to load documents');
            }
            return {
              ...collection,
              documents: Array.isArray(documentsResponse.data) ? documentsResponse.data : []
            };
          } catch (err) {
            console.error(`Error fetching documents for collection ${collection.id}:`, err);
            return {
              ...collection,
              documents: [],
              documentsError: err.message || 'Failed to load documents'
            };
          }
        })
      );

      setKnowledgeCollections(collectionsWithDocuments);
    } catch (err) {
      console.error('Error fetching knowledge hub collections:', err);
      setKnowledgeCollections([]);
      setKnowledgeCollectionsError(err.message || 'Failed to load knowledge hub collections');
    } finally {
      setKnowledgeCollectionsLoading(false);
    }
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
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const getDocumentStatusVariant = (status) => {
    switch (status) {
      case 'READY':
      case 'PROCESSED':
      case 'AI_READY':
        return 'success';
      case 'UPLOADED':
      case 'PARSING':
      case 'PARSED':
      case 'METADATA_EXTRACTION':
      case 'EMBEDDING':
        return 'info';
      case 'PENDING_UPLOAD':
        return 'warning';
      case 'FAILED':
        return 'danger';
      default:
        return 'secondary';
    }
  };

  const renderPermissionBadges = (permissions) => {
    if (!permissions || !Array.isArray(permissions)) return null;

    return permissions.map((permission, index) => (
      <OverlayTrigger
        key={index}
        placement="top"
        overlay={
          <Tooltip id={`permission-${permission}-${index}`}>
            {PERMISSION_INFO[permission]?.description || permission}
          </Tooltip>
        }
      >
        <span
          className="badge me-1"
          style={{
            backgroundColor: PERMISSION_INFO[permission]?.color || '#6c757d',
            color: 'white'
          }}
        >
          <HiLockClosed className="me-1" size={12} />
          {permission}
        </span>
      </OverlayTrigger>
    ));
  };

  const handleAddMember = async (memberData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.addTeamMember(team.id, memberData);

      if (error) throw new Error(error);

      setTeam(data);
      setShowMembersModal(false);
      showSuccessToast(`Member added to team "${team.name}" successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to add member to team');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleRemoveMember = async (userId) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.removeTeamMember(team.id, userId);
      if (error) throw new Error(error);

      setTeam(data);
      showSuccessToast('Team member removed successfully');
    } catch (err) {
      showErrorToast(err.message || 'Failed to remove team member');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleEditTeam = async (teamData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.updateTeam(team.id, {
        name: teamData.name,
        description: teamData.description,
        organizationId: teamData.organizationId
      });

      if (error) throw new Error(error);

      setTeam(data);
      setShowEditModal(false);
      showSuccessToast(`Team "${data.name}" updated successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to update team');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleAddRoute = async (routeData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.addTeamRoute(
        team.id,
        routeData.routeId,
        routeData.permissions
      );

      if (error) throw new Error(error);

      setTeam(data);
      setShowRoutesModal(false);
      showSuccessToast(`Route added to team "${team.name}" successfully`);
    } catch (err) {
      showErrorToast(err.message || 'Failed to add route to team');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleRemoveRoute = async (routeId) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.removeTeamRoute(team.id, routeId);
      if (error) throw new Error(error);

      setTeam(data);
      showSuccessToast('Route removed successfully');
    } catch (err) {
      showErrorToast(err.message || 'Failed to remove route');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleCreateApiKey = async (apiKeyData) => {
    try {
      setOperationLoading(true);
      const { data, error } = await teamService.createApiKey(apiKeyData);
      if (error) throw new Error(error);

      if (data && data.key) {
        showSuccessToast(
          <div>
            API key created successfully
            <br />
            <small className="text-monospace">Key: {data.key}</small>
          </div>,
          { autoClose: false }
        );
      }

      // Refresh API keys after creation
      await fetchApiKeys();
      setShowApiKeysModal(false);
    } catch (err) {
      showErrorToast(err.message || 'Failed to create API key');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleRotateKey = async () => {
    try {
      setOperationLoading(true);
      const { data, success, error } = await teamService.rotateEncryptionKey(teamId);
      if (!success) throw new Error(error);

      setActiveKeyVersion(data.version);
      showSuccessToast(`Key rotated successfully. New version: ${data.version}`);
      setConfirmModal({ ...confirmModal, show: false });
    } catch (err) {
      showErrorToast(err.message || 'Failed to rotate key');
    } finally {
      setOperationLoading(false);
    }
  };

  const handleApiKeysModalClose = () => {
    setShowApiKeysModal(false);
    // Refresh API keys when modal closes in case changes were made
    fetchApiKeys();
  };

  // Move model priority up (decrease priority number)
  const handleMovePriorityUp = (modelId) => {
    const modelIndex = sortedLlmModels.findIndex(m => m.id === modelId);
    if (modelIndex <= 0) return; // Already at top

    // Create a working copy of the sorted list
    const newSortedList = [...sortedLlmModels];

    // Swap element with the one above it
    const temp = newSortedList[modelIndex];
    newSortedList[modelIndex] = newSortedList[modelIndex - 1];
    newSortedList[modelIndex - 1] = temp;

    // Reassign priorities sequentially to ensure consistency (1, 2, 3...)
    const updatedModels = newSortedList.map((model, index) => ({
      ...model,
      priority: index + 1
    }));

    setLlmModels(updatedModels);
    setPriorityChanged(true);
  };

  // Move model priority down (increase priority number)
  const handleMovePriorityDown = (modelId) => {
    const modelIndex = sortedLlmModels.findIndex(m => m.id === modelId);
    if (modelIndex >= sortedLlmModels.length - 1) return; // Already at bottom

    // Create a working copy of the sorted list
    const newSortedList = [...sortedLlmModels];

    // Swap element with the one below it
    const temp = newSortedList[modelIndex];
    newSortedList[modelIndex] = newSortedList[modelIndex + 1];
    newSortedList[modelIndex + 1] = temp;

    // Reassign priorities sequentially to ensure consistency (1, 2, 3...)
    const updatedModels = newSortedList.map((model, index) => ({
      ...model,
      priority: index + 1
    }));

    setLlmModels(updatedModels);
    setPriorityChanged(true);
  };

  // Save priority changes to backend
  const handleSavePriorities = async () => {
    try {
      setOperationLoading(true);
      // Build priority map: { modelId: priority }
      const priorityUpdates = {};
      llmModels.forEach(model => {
        priorityUpdates[model.id] = model.priority;
      });

      await linqLlmModelService.updatePriorities(teamId, priorityUpdates);
      showSuccessToast('LLM model priorities updated successfully');
      setPriorityChanged(false);
    } catch (err) {
      showErrorToast(err.message || 'Failed to update priorities');
    } finally {
      setOperationLoading(false);
    }
  };

  // Check if priorities need reordering (duplicates or nulls exist)
  const needsPriorityReorder = useMemo(() => {
    if (!llmModels || llmModels.length === 0) return false;
    const priorities = llmModels.map(m => m.priority);
    const hasNull = priorities.some(p => p === null || p === undefined);
    const hasDuplicates = new Set(priorities.filter(p => p !== null && p !== undefined)).size !== priorities.filter(p => p !== null && p !== undefined).length;
    return hasNull || hasDuplicates;
  }, [llmModels]);

  // Auto-reorder priorities to sequential values (1, 2, 3...)
  const handleAutoReorderPriorities = () => {
    const updatedModels = sortedLlmModels.map((model, index) => ({
      ...model,
      priority: index + 1
    }));
    setLlmModels(updatedModels);
    setPriorityChanged(true);
    showSuccessToast('Priorities reordered. Click "Save Priority Order" to persist changes.');
  };

  const knowledgeCollectionRows = knowledgeCollections.flatMap((collection) => {
    const documents = Array.isArray(collection.documents) ? collection.documents : [];

    if (documents.length === 0) {
      return [{
        key: `${collection.id}-empty`,
        collectionName: collection.name,
        documentName: collection.documentsError
          ? `Failed to load documents: ${collection.documentsError}`
          : 'No documents yet',
        status: null,
        uploadedAt: null,
        isEmpty: true,
        isError: Boolean(collection.documentsError)
      }];
    }

    return documents.map((doc) => ({
      key: `${collection.id}-${doc.documentId || doc.id}`,
      collectionName: collection.name,
      documentName: doc.fileName || doc.documentId || 'Unnamed document',
      status: doc.status || null,
      uploadedAt: doc.uploadedAt || doc.createdAt || null,
      documentId: doc.documentId || doc.id || null,
      isEmpty: false,
      isError: false
    }));
  });

  const knowledgeHubMilvusCollections = useMemo(() => (
    (milvusCollections || []).filter(
      (collection) => (collection.collectionType || '').toUpperCase() === 'KNOWLEDGE_HUB'
    )
  ), [milvusCollections]);

  const customMilvusCollections = useMemo(() => (
    (milvusCollections || []).filter(
      (collection) => (collection.collectionType || '').toUpperCase() !== 'KNOWLEDGE_HUB'
    )
  ), [milvusCollections]);

  const renderMilvusSummaryTable = (collections) => (
    <div className="table-responsive">
      <table className="table table-sm mb-3">
        <thead>
          <tr>
            <th>Name</th>
            <th>Dimension</th>
            <th>Description</th>
            <th>Type</th>
            <th>Records</th>
          </tr>
        </thead>
        <tbody>
          {collections.map((collection) => {
            const alias = collection.properties?.collectionAlias || collection.collectionAlias || '';
            const displayAlias = alias && alias !== collection.name ? alias : '';
            const descriptionText =
              collection.collectionDescription ??
              collection.properties?.collectionDescription ??
              collection.description ??
              '—';
            const rowCount = Number(collection.rowCount ?? collection.properties?.rowCount ?? 0);
            const typeLabel = collection.collectionType || collection.properties?.collectionType || 'UNKNOWN';

            return (
              <tr key={collection.name}>
                <td>
                  <div className="fw-semibold small">
                    {collection.name}
                    {displayAlias && <span className="text-muted ms-2">({displayAlias})</span>}
                  </div>
                  <div className="text-muted small">
                    Vector field: {collection.vectorFieldName || 'N/A'}
                  </div>
                </td>
                <td>{collection.vectorDimension ?? 'Unknown'}</td>
                <td className="text-muted small">
                  {descriptionText}
                </td>
                <td>
                  <Badge bg="secondary">{typeLabel}</Badge>
                </td>
                <td>{Number.isFinite(rowCount) ? rowCount : '—'}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );

  if (loading) {
    return (
      <div className="text-center py-5">
        <Spinner animation="border" role="status">
          <span className="visually-hidden">Loading...</span>
        </Spinner>
      </div>
    );
  }

  if (error || !team) {
    return (
      <div className="alert alert-danger" role="alert">
        {error || 'Team not found'}
      </div>
    );
  }

  return (
    <div className="view-team-container">
      {/* Header */}
      <Card className="mb-4 border-0">
        <Card.Body>
          <div className="d-flex align-items-center justify-content-between">
            <div className="d-flex align-items-center gap-2">
              <BootstrapButton
                variant="link"
                className="p-0"
                onClick={() => navigate('/teams')}
              >
                <HiArrowLeft className="text-primary" size={24} />
              </BootstrapButton>
              <h4 className="mb-0">{team.name}</h4>
              <Badge
                bg={team.status === 'ACTIVE' ? 'success' : 'secondary'}
              >
                {team.status}
              </Badge>
            </div>
            <Button
              variant="primary"
              onClick={() => setShowEditModal(true)}
              disabled={team.status === 'INACTIVE' || operationLoading}
            >
              <HiPencil style={{ marginRight: '4px' }} /> Edit Team
            </Button>
          </div>
        </Card.Body>
      </Card>

      {/* Main Content */}
      <Row className="g-4">

        {/* Actions */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center g mb-3">
                <HiUserGroup className="text-primary me-2" size={24} />
                <h5 className="mb-0 fw-semibold">Team</h5>
              </div>
              <div className="d-flex align-items-center gap-2">
                <p className="mb-0 text-muted text-start flex-grow-1">{team.name} / {team.organization?.name || 'No organization assigned'}</p>
                <code className="bg-transparent px-2 py-1 rounded flex-grow-1 text-muted text-start">{team.id}</code>
                <Badge bg={team.status === 'ACTIVE' ? 'success' : 'secondary'}>
                  {team.status}
                </Badge>
              </div>
              <p className="mb-0 mt-3 text-muted small text-start">{team.description || 'No description provided.'}</p>

            </Card.Body>
          </Card>
        </Col>

        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>

              <div className="d-flex align-items-center mb-3">
                <HiKey className="text-primary me-2" size={24} />
                <h5 className="mb-0 fw-semibold">API Keys</h5>
              </div>
              <div className="d-flex flex-column gap-2">
                {apiKeys && apiKeys.length > 0 ? (
                  <>
                    <div className="d-flex justify-content-between align-items-center">
                      <span className="text-muted h6 mb-0">API Key</span>
                      <code className="bg-white px-2 py-1 rounded flex-grow-1 text-truncate text-start" style={{ fontSize: '0.85rem', margin: '0 8px' }}>
                        {apiKeys[0].key}
                      </code>
                      <BootstrapButton
                        size="sm"
                        variant="outline-purple"
                        onClick={() => {
                          navigator.clipboard.writeText(apiKeys[0].key);
                          showSuccessToast('API Key copied to clipboard');
                        }}
                      >
                        <HiDocumentText size={14} className="me-1" />
                        Copy API Key
                      </BootstrapButton>

                      <BootstrapButton
                        className="ms-2"
                        size="sm"
                        variant="outline-purple"
                        onClick={() => setShowApiKeysModal(true)}
                        disabled={team.status === 'INACTIVE' || operationLoading}
                      >
                        <HiKey size={14} className="me-1" />
                        Manage API Key
                      </BootstrapButton>
                    </div>
                  </>
                ) : (
                  <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted small">No API key configured</span>
                    <BootstrapButton
                      size="sm"
                      variant="outline-purple"
                      onClick={() => setShowApiKeysModal(true)}
                      disabled={team.status === 'INACTIVE' || operationLoading}
                    >
                      <HiKey size={14} className="me-1" />
                      Add API Key
                    </BootstrapButton>
                  </div>
                )}
              </div>

            </Card.Body>
          </Card>
        </Col>


        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiLockClosed className="text-primary me-2" size={24} />
                <h5 className="mb-0">Chunk Encryption</h5>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6 mb-0">Active Key Version</span>
                <Badge bg="success" className="px-3 py-2 text-monospace">
                  {activeKeyVersion || 'v1'}
                </Badge>
              </div>

              <div className="d-grid">
                <BootstrapButton
                  variant="outline-danger"
                  size="sm"
                  disabled={operationLoading || team.status === 'INACTIVE'}
                  onClick={() => setConfirmModal({
                    show: true,
                    title: 'Rotate Encryption Key',
                    message: 'Are you sure you want to rotate the encryption key? This will generate a new version.',
                    variant: 'danger',
                    onConfirm: handleRotateKey
                  })}
                >
                  <HiSparkles className="me-1" /> Rotate Chunk Encryption Key
                </BootstrapButton>
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: '0.75rem' }}>
                <small>Rotating creates a new key version. Existing data remains readable using old keys.</small>
              </div>

            </Card.Body>
          </Card>
        </Col>

        {/* Statistics */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiUsers className="text-primary me-2" size={24} />
                <h5 className="mb-0">Team Statistics</h5>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6">Members</span>
                <Badge bg="info" pill className="px-2">
                  {team.members?.length || 0}
                </Badge>
              </div>
              <div className="d-flex justify-content-between align-items-center">
                <span className="text-muted h6">Apps</span>
                <Badge bg="info" pill className="px-2">
                  {team.routes?.length || 0}
                </Badge>
              </div>
            </Card.Body>
          </Card>
        </Col>

        {/* Timestamps */}
        <Col md={6}>
          <Card className="border-0 bg-light h-100">
            <Card.Body>
              <div className="d-flex align-items-center mb-3">
                <HiClock className="text-primary me-2" size={24} />
                <h5 className="mb-0">Timestamps</h5>
              </div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="text-muted h6 mb-0">Created</span>
                <span className="text-secondary">
                  {formatDate(team.createdAt)}
                </span>
              </div>
              {team.updatedAt && (
                <div className="d-flex justify-content-between align-items-center">
                  <span className="text-muted h6 mb-0">Last Updated</span>
                  <span className="text-secondary">
                    {formatDate(team.updatedAt)}
                  </span>
                </div>
              )}
            </Card.Body>
          </Card>
        </Col>





        {/* Members Table */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div className="d-flex align-items-center">
                  <HiUsers className="text-primary me-2" size={24} />
                  <h5 className="mb-0">Team Members</h5>
                </div>
                <BootstrapButton
                  size="sm"
                  variant="outline-secondary"
                  onClick={() => setShowMembersModal(true)}
                >
                  <HiUsers className="me-1" /> Manage Members
                </BootstrapButton>
              </div>
              {team.members && team.members.length > 0 ? (
                <div className="table-responsive">
                  <table className="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>Username</th>
                        <th>Role</th>
                        <th>Status</th>
                        <th>Joined At</th>
                      </tr>
                    </thead>
                    <tbody>
                      {team.members.map(member => (
                        <tr key={member.id}>
                          <td>{member.username}</td>
                          <td>
                            <Badge bg="primary">{member.role}</Badge>
                          </td>
                          <td>
                            <Badge bg={member.status === 'ACTIVE' ? 'success' : 'secondary'}>
                              {member.status}
                            </Badge>
                          </td>
                          <td>{formatDate(member.joinedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-muted mb-0">No members yet. Click "Manage Members" to add members.</p>
              )}
            </Card.Body>
          </Card>
        </Col>

        {/* Routes Table */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div className="d-flex align-items-center">
                  <HiTemplate className="text-primary me-2" size={24} />
                  <h5 className="mb-0">Apps</h5>
                </div>
                <BootstrapButton
                  size="sm"
                  variant="outline-info"
                  onClick={() => setShowRoutesModal(true)}
                >
                  <HiTemplate className="me-1" /> Manage Apps
                </BootstrapButton>
              </div>
              {team.routes && team.routes.length > 0 ? (
                <div className="table-responsive">
                  <table className="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>Route ID</th>
                        <th>Path</th>
                        <th>Version</th>
                        <th>Permissions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {team.routes.map(route => (
                        <tr key={route.id}>
                          <td>{route.routeIdentifier}</td>
                          <td>{route.path}</td>
                          <td>v{route.version}</td>
                          <td>
                            <div className="d-flex align-items-center gap-1">
                              {renderPermissionBadges(route.permissions)}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-muted mb-0">No apps yet. Click "Manage Apps" to add apps.</p>
              )}
            </Card.Body>
          </Card>
        </Col>

        {/* LLM Models Table */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div className="d-flex align-items-center">
                  <HiSparkles className="text-primary me-2" size={24} />
                  <h5 className="mb-0">LLM Models</h5>
                </div>
                <div className="d-flex gap-2">
                  <BootstrapButton
                    size="sm"
                    variant="outline-info"
                    onClick={() => setShowOpenAIModal(true)}
                    disabled={team.status === 'INACTIVE' || operationLoading}
                  >
                    <SiOpenai className="me-1" size={16} /> OpenAI
                  </BootstrapButton>
                  <BootstrapButton
                    size="sm"
                    variant="outline-info"
                    onClick={() => setShowGeminiModal(true)}
                    disabled={team.status === 'INACTIVE' || operationLoading}
                  >
                    <SiGoogle className="me-1" size={14} /> Gemini
                  </BootstrapButton>
                  <BootstrapButton
                    size="sm"
                    variant="outline-info"
                    onClick={() => setShowCohereModal(true)}
                    disabled={team.status === 'INACTIVE' || operationLoading}
                  >
                    <FaCloud className="me-1" size={16} /> Cohere
                  </BootstrapButton>
                  <BootstrapButton
                    size="sm"
                    variant="outline-info"
                    onClick={() => setShowClaudeModal(true)}
                    disabled={team.status === 'INACTIVE' || operationLoading}
                  >
                    <SiAnthropic className="me-1" size={16} /> Claude
                  </BootstrapButton>
                  <BootstrapButton
                    size="sm"
                    variant="outline-info"
                    onClick={() => setShowOllamaModal(true)}
                    disabled={team.status === 'INACTIVE' || operationLoading}
                  >
                    <HiServer className="me-1" size={16} /> Ollama
                  </BootstrapButton>
                  {needsPriorityReorder && (
                    <BootstrapButton
                      size="sm"
                      variant="outline-warning"
                      onClick={handleAutoReorderPriorities}
                      disabled={operationLoading}
                      title="Assign sequential priorities (1, 2, 3...) to fix duplicates or missing values"
                    >
                      <HiRefresh className="me-1" size={14} /> Auto-reorder
                    </BootstrapButton>
                  )}
                </div>
              </div>
              {sortedLlmModels && sortedLlmModels.length > 0 ? (
                <>
                  <div className="table-responsive">
                    <table className="table table-sm mb-0">
                      <thead>
                        <tr>
                          <th style={{ width: '80px' }}>Priority</th>
                          <th>Provider</th>
                          <th>Model Category</th>
                          <th>Model Name</th>
                          <th>Endpoint</th>
                          <th>Auth Type</th>
                          <th>Supported Intents</th>
                          <th style={{ width: '100px' }}>Reorder</th>
                        </tr>
                      </thead>
                      <tbody>
                        {sortedLlmModels.map((model, index) => (
                          <tr key={model.id}>
                            <td>
                              <Badge bg="dark" className="px-2">
                                #{index + 1}
                              </Badge>
                            </td>
                            <td>
                              <Badge bg={
                                model.provider?.toLowerCase() === 'openai' ? 'primary' :
                                  model.provider?.toLowerCase() === 'gemini' ? 'warning' :
                                    model.provider?.toLowerCase() === 'cohere' ? 'info' :
                                      model.provider?.toLowerCase() === 'anthropic' ? 'danger' : 'secondary'
                              }>
                                {model.provider}
                              </Badge>
                            </td>
                            <td>
                              <Badge bg="secondary">{model.modelCategory}</Badge>
                            </td>
                            <td>{model.modelName}</td>
                            <td>
                              <OverlayTrigger
                                placement="top"
                                overlay={<Tooltip id={`tooltip-endpoint-${model.id}`}>{model.endpoint}</Tooltip>}
                              >
                                <code className="text-truncate" style={{ maxWidth: '300px', display: 'block' }}>
                                  {model.endpoint}
                                </code>
                              </OverlayTrigger>
                            </td>
                            <td>
                              <Badge bg="secondary">{model.authType}</Badge>
                            </td>
                            <td>
                              {model.supportedIntents?.map(intent => (
                                <Badge
                                  key={intent}
                                  bg="info"
                                  className="me-1"
                                >
                                  {intent}
                                </Badge>
                              ))}
                            </td>
                            <td>
                              <div className="d-flex gap-1">
                                <BootstrapButton
                                  size="sm"
                                  variant="outline-secondary"
                                  disabled={index === 0 || operationLoading}
                                  onClick={() => handleMovePriorityUp(model.id)}
                                  title="Move up (higher priority)"
                                >
                                  <HiArrowUp size={14} />
                                </BootstrapButton>
                                <BootstrapButton
                                  size="sm"
                                  variant="outline-secondary"
                                  disabled={index === sortedLlmModels.length - 1 || operationLoading}
                                  onClick={() => handleMovePriorityDown(model.id)}
                                  title="Move down (lower priority)"
                                >
                                  <HiArrowDown size={14} />
                                </BootstrapButton>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {priorityChanged && (
                    <div className="mt-3 d-flex justify-content-end">
                      <BootstrapButton
                        size="sm"
                        variant="success"
                        onClick={handleSavePriorities}
                        disabled={operationLoading}
                      >
                        <HiSave className="me-1" size={14} />
                        {operationLoading ? 'Saving...' : 'Save Priority Order'}
                      </BootstrapButton>
                    </div>
                  )}
                </>
              ) : (
                <p className="text-muted mb-0">No LLM models configured yet. Click the provider buttons above to configure.</p>
              )}
            </Card.Body>
          </Card>
        </Col>

        {/* Knowledge Hub Collections Summary */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div className="d-flex align-items-center">
                  <HiDocumentText className="text-primary me-2" size={24} />
                  <h5 className="mb-0">Knowledge Hub Collections</h5>
                </div>
              </div>
              {knowledgeCollectionsLoading ? (
                <div className="text-center py-3">
                  <Spinner animation="border" size="sm" role="status" />
                  <span className="ms-2">Loading knowledge hub collections...</span>
                </div>
              ) : knowledgeCollectionsError ? (
                <p className="text-danger mb-0">{knowledgeCollectionsError}</p>
              ) : knowledgeCollections.length === 0 ? (
                <p className="text-muted mb-0">
                  No knowledge hub collections configured yet. Create a collection to start ingesting documents.
                </p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>Collection</th>
                        <th>Document</th>
                        <th>Status</th>
                        <th>Uploaded</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {knowledgeCollectionRows.map((row) => {
                        const isClickable = row.documentId && !row.isError && !row.isEmpty;
                        return (
                          <tr
                            key={row.key}
                            className={isClickable ? 'table-row-clickable' : undefined}
                            onClick={() => {
                              if (isClickable) {
                                navigate(`/knowledge-hub/document/${row.documentId}`);
                              }
                            }}
                            style={isClickable ? { cursor: 'pointer' } : undefined}
                          >
                            <td>{row.collectionName}</td>
                            <td className={row.isError ? 'text-danger' : undefined}>
                              {row.documentName}
                            </td>
                            <td>
                              {row.status ? (
                                <Badge bg={getDocumentStatusVariant(row.status)}>{row.status}</Badge>
                              ) : (
                                '—'
                              )}
                            </td>
                            <td>{row.uploadedAt && !row.isEmpty ? formatDate(row.uploadedAt) : '—'}</td>
                            <td className="text-end">
                              {isClickable ? (
                                <Button
                                  variant="outline-primary"
                                  size="sm"
                                  onClick={(event) => {
                                    event.stopPropagation();
                                    navigate(`/knowledge-hub/document/${row.documentId}`);
                                  }}
                                >
                                  <HiEye className="me-1" /> View
                                </Button>
                              ) : (
                                '—'
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </Card.Body>
          </Card>
        </Col>

        {/* Milvus Collections Overview */}
        <Col md={12}>
          <Card className="border-0 bg-light">
            <Card.Body>
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div className="d-flex align-items-center">
                  <HiDatabase className="text-primary me-2" size={24} />
                  <h5 className="mb-0">RAG Collections</h5>
                </div>
                <Button variant="link" onClick={() => navigate('/rag')}>
                  Manage in RAG Console
                </Button>
              </div>
              {milvusCollectionsLoading ? (
                <div className="text-center py-3">
                  <Spinner animation="border" size="sm" role="status" />
                  <span className="ms-2">Loading Milvus collections...</span>
                </div>
              ) : milvusCollectionsError ? (
                <p className="text-danger mb-0">{milvusCollectionsError}</p>
              ) : milvusCollections.length === 0 ? (
                <p className="text-muted mb-0">
                  No Milvus collections yet. Use the RAG console to create knowledge hub or custom collections.
                </p>
              ) : (
                <>
                  {knowledgeHubMilvusCollections.length > 0 && (
                    <>
                      <div className="text-muted text-uppercase small fw-semibold mb-2 text-start">
                        Knowledge Hub Collections
                      </div>
                      {renderMilvusSummaryTable(knowledgeHubMilvusCollections)}
                    </>
                  )}
                  {customMilvusCollections.length > 0 && (
                    <>
                      <div className="text-muted text-uppercase small fw-semibold mt-3 mb-2 text-start">
                        Custom Collections
                      </div>
                      {renderMilvusSummaryTable(customMilvusCollections)}
                    </>
                  )}
                  {knowledgeHubMilvusCollections.length === 0 && customMilvusCollections.length === 0 && (
                    <p className="text-muted mb-0">
                      No Milvus collections found for this team.
                    </p>
                  )}
                </>
              )}
            </Card.Body>
          </Card>
        </Col>
      </Row>

      {/* Modals */}
      {showMembersModal && (
        <TeamMembersModal
          show={true}
          onHide={() => setShowMembersModal(false)}
          team={team}
          onAddMember={handleAddMember}
          onRemoveMember={handleRemoveMember}
          loading={operationLoading}
        />
      )}

      {showRoutesModal && (
        <TeamRoutesModal
          show={true}
          onHide={() => setShowRoutesModal(false)}
          team={team}
          onAddRoute={handleAddRoute}
          onRemoveRoute={handleRemoveRoute}
          loading={operationLoading}
        />
      )}

      <TeamEditModal
        show={showEditModal}
        onHide={() => setShowEditModal(false)}
        onSubmit={handleEditTeam}
        loading={operationLoading}
        team={team}
      />

      <TeamApiKeysModal
        show={showApiKeysModal}
        onHide={handleApiKeysModalClose}
        team={team}
        onCreateApiKey={handleCreateApiKey}
        loading={operationLoading}
      />

      {showOpenAIModal && (
        <OpenAIModal
          show={true}
          onHide={() => setShowOpenAIModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      {showGeminiModal && (
        <GeminiModal
          show={true}
          onHide={() => setShowGeminiModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      {showCohereModal && (
        <CohereModal
          show={true}
          onHide={() => setShowCohereModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      {showClaudeModal && (
        <ClaudeModal
          show={true}
          onHide={() => setShowClaudeModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      {showOllamaModal && (
        <OllamaModal
          show={true}
          onHide={() => setShowOllamaModal(false)}
          team={team}
          onTeamUpdate={fetchLlmModels}
        />
      )}

      <ConfirmationModal
        show={confirmModal.show}
        onHide={() => setConfirmModal(prev => ({ ...prev, show: false }))}
        onConfirm={confirmModal.onConfirm}
        title={confirmModal.title}
        message={confirmModal.message}
        variant={confirmModal.variant}
      />
      <Footer />
    </div>
  );
}

export default ViewTeam;
