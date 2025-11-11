import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  Container,
  Card,
  Spinner,
  Alert,
  Table,
  Badge,
  Form,
  Tabs,
  Tab
} from 'react-bootstrap';
import { HiArrowLeft, HiDatabase, HiCheckCircle, HiExclamationCircle, HiTrash, HiPlusCircle } from 'react-icons/hi';
import Button from '../../../components/common/Button';
import { milvusService } from '../../../services/milvusService';
import { useTeam } from '../../../contexts/TeamContext';
import { showErrorToast, showSuccessToast } from '../../../utils/toastConfig';
import './styles.css';

const LINQRA_PROPERTY_KEYS = new Set([
  'teamId',
  'collectionType',
  'collectionAlias',
  'collectionDescription',
  'embeddingModel',
  'embeddingModelName',
  'embeddingProvider',
  'embeddingCategory',
  'embeddingDimension'
]);

const DEFAULT_PROPERTY_KEYS = [
  'collection.ttl.seconds',
  'collection.autocompaction.enabled',
  'collection.insertRate.max.mb',
  'collection.insertRate.min.mb',
  'collection.upsertRate.max.mb',
  'collection.upsertRate.min.mb',
  'collection.deleteRate.max.mb',
  'collection.deleteRate.min.mb',
  'collection.bulkLoadRate.max.mb',
  'collection.bulkLoadRate.min.mb',
  'collection.queryRate.max.qps',
  'collection.queryRate.min.qps',
  'collection.searchRate.max.vps',
  'collection.searchRate.min.vps',
  'collection.diskProtection.diskQuota.mb',
  'collection.replica.number',
  'collection.resource_groups',
  'partition.diskProtection.diskQuota.mb',
  'mmap.enabled',
  'lazyload.enabled',
  'partitionkey.isolation',
  'field.skipLoad',
  'indexoffsetcache.enabled',
  'replicate.id',
  'replicate.endTS'
];

const ViewRag = () => {
  const { collectionName } = useParams();
  const decodedCollectionName = decodeURIComponent(collectionName || '');
  const navigate = useNavigate();
  const location = useLocation();
  const { currentTeam } = useTeam();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [details, setDetails] = useState(null);
  const [propertyDrafts, setPropertyDrafts] = useState([]);
  const [editingKey, setEditingKey] = useState(null);
  const [editingDraft, setEditingDraft] = useState(null);
  const [propertyError, setPropertyError] = useState(null);
  const [savingKey, setSavingKey] = useState(null);
  const [activeTab, setActiveTab] = useState('data');

  const initialCollection = useMemo(() => location.state?.collection || null, [location.state]);

  const summary = useMemo(() => {
    if (details) {
      const derivedType = details.collectionType || details.properties?.collectionType || '';
      return {
        ...details,
        collectionType: derivedType,
        properties: details.properties || {}
      };
    }

    if (initialCollection) {
      return {
        name: initialCollection.name,
        description:
          initialCollection.collectionDescription ??
          initialCollection.properties?.collectionDescription ??
          initialCollection.description ??
          '',
        collectionType:
          initialCollection.collectionType ??
          initialCollection.properties?.collectionType ??
          '',
        vectorFieldName: initialCollection.vectorFieldName,
        vectorDimension: initialCollection.vectorDimension,
        rowCount: initialCollection.rowCount ?? initialCollection.properties?.rowCount,
        properties: initialCollection.properties ?? {}
      };
    }

    return null;
  }, [details, initialCollection]);

  const schema = details?.schema || [];
  const issues = details?.issues || [];
  const collectionValid = details?.valid ?? true;

  const preparePropertyDrafts = useCallback((propertiesMap = {}) => {
    const keys = new Set([
      ...DEFAULT_PROPERTY_KEYS,
      ...Object.keys(propertiesMap)
    ]);

    return Array.from(keys)
      .map((key) => ({
        key,
        value: propertiesMap[key] != null ? String(propertiesMap[key]) : '',
        locked: LINQRA_PROPERTY_KEYS.has(key)
      }))
      .sort((a, b) => a.key.localeCompare(b.key));
  }, []);

  const loadCollectionDetails = useCallback(async () => {
    if (!currentTeam?.id) {
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const { success, data, error: fetchError } = await milvusService.verifyCollection(
        currentTeam.id,
        decodedCollectionName
      );
      if (!success) {
        throw new Error(fetchError || 'Failed to load collection details');
      }
      setDetails(data);
      if (!editingKey) {
        setPropertyDrafts(preparePropertyDrafts(data.properties || {}));
      }
    } catch (err) {
      console.error('Error loading Milvus collection details:', err);
      setError(err.message || 'Failed to load collection details');
    } finally {
      setLoading(false);
    }
  }, [currentTeam?.id, decodedCollectionName, editingKey, preparePropertyDrafts]);

  useEffect(() => {
    if (!currentTeam?.id) {
      return;
    }
    loadCollectionDetails();
  }, [currentTeam?.id, loadCollectionDetails]);

  useEffect(() => {
    if (!editingKey) {
      const baseProperties =
        details?.properties || summary?.properties || {};
      setPropertyDrafts(preparePropertyDrafts(baseProperties));
    }
  }, [details?.properties, summary?.properties, editingKey, preparePropertyDrafts]);

  const beginEditProperty = (property) => {
    setEditingKey(property.key);
    setEditingDraft({ ...property });
    setPropertyError(null);
  };

  const beginAddProperty = () => {
    setEditingKey('__new__');
    setEditingDraft({
      key: '',
      value: '',
      locked: false
    });
    setPropertyError(null);
  };

  const handleCancelEdit = () => {
    setEditingKey(null);
    setEditingDraft(null);
    setPropertyError(null);
  };

  const handleDraftChange = (field, value) => {
    setEditingDraft((draft) => ({
      ...draft,
      [field]: value
    }));
  };

  const handleSaveProperty = async () => {
    if (!currentTeam?.id || !editingDraft) {
      return;
    }

    const trimmedKey = editingDraft.key?.trim();

    if (!trimmedKey) {
      setPropertyError('Property name cannot be empty.');
      return;
    }

    if (editingKey !== '__new__' && trimmedKey !== editingKey) {
      setPropertyError('Property name cannot be changed.');
      return;
    }

    if (
      editingKey === '__new__' &&
      propertyDrafts.some((property) => property.key === trimmedKey)
    ) {
      setPropertyError(`Property "${trimmedKey}" already exists.`);
      return;
    }

    try {
      setSavingKey(trimmedKey);
      setPropertyError(null);

      const payload = {
        [trimmedKey]: editingDraft.value ?? ''
      };

      const { success, error: updateError } = await milvusService.updateCollectionMetadata({
        teamId: currentTeam.id,
        collectionName: decodedCollectionName,
        metadata: payload
      });

      if (!success) {
        throw new Error(updateError || 'Failed to update collection property');
      }

      showSuccessToast(`Property "${trimmedKey}" updated.`);
      setEditingKey(null);
      setEditingDraft(null);
      await loadCollectionDetails();
    } catch (err) {
      console.error('Failed to update collection property:', err);
      const message = err.message || 'Failed to update collection property';
      setPropertyError(message);
      showErrorToast(message);
    } finally {
      setSavingKey(null);
    }
  };

  const handleBackClick = () => {
    navigate('/rag');
  };

  if (!currentTeam?.id) {
    return (
      <Container fluid className="view-rag-container">
        <Alert variant="info" className="mt-4">
          Select a team to view Milvus collection details.
        </Alert>
      </Container>
    );
  }

  return (
    <Container fluid className="view-rag-container">
      <Card className="mb-4 border-0">
        <Card.Body>
          <div className="d-flex align-items-center justify-content-between">
            <div className="d-flex align-items-center gap-2">
              <Button
                variant="secondary"
                className="btn-sm back-button"
                onClick={handleBackClick}
              >
                <HiArrowLeft className="me-1" size={18} />
                Back to RAG Collections
              </Button>
              <h4 className="mb-0">{summary?.name || decodedCollectionName}</h4>
              {summary?.collectionType && (
                <Badge
                  bg={
                    summary.collectionType.toUpperCase() === 'KNOWLEDGE_HUB'
                      ? 'primary'
                      : 'secondary'
                  }
                >
                  {summary.collectionType}
                </Badge>
              )}
            </div>
            <div className="text-muted small text-end">
              <div>Team: {currentTeam?.name || 'Unknown'}</div>
              <div>Records: {summary?.rowCount ?? 'Unknown'}</div>
            </div>
          </div>
        </Card.Body>
      </Card>

      <Card className="border-0 mb-4">
        <Card.Body>
          {summary ? (
            <div className="d-flex flex-column flex-lg-row justify-content-between gap-3">
              <div>
                <div className="d-flex align-items-center gap-2 mb-2">
                  <HiDatabase className="rag-database-icon" size={24} />
                  <h4 className="mb-0 text-start">{summary.name}</h4>
                </div>
                <div className="text-muted small">
                  {summary.description || 'No description provided for this collection.'}
                </div>
              </div>
              <div className="text-muted small text-lg-end">
                <div>Collection type: {summary.collectionType || 'Unknown'}</div>
                <div>Vector field: {summary.vectorFieldName || 'Unknown'}</div>
                <div>Dimension: {summary.vectorDimension ?? 'Unknown'}</div>
                <div>Records: {summary.rowCount ?? 'Unknown'}</div>
              </div>
            </div>
          ) : (
            <div className="text-muted small">Collection metadata not available.</div>
          )}
        </Card.Body>
      </Card>

      {loading ? (
        <div className="text-center py-5">
          <Spinner animation="border" role="status">
            <span className="visually-hidden">Loading collection details...</span>
          </Spinner>
        </div>
      ) : error ? (
        <Alert variant="danger">{error}</Alert>
      ) : (
        <>
          <Card className="border-0">
            <Card.Body>
              <Tabs
                id="collection-detail-tabs"
                activeKey={activeTab}
                onSelect={(key) => setActiveTab(key || 'data')}
                className="view-rag-tabs"
                justify
              >
                <Tab eventKey="data" title="Data">
                  <div className="mt-3">
                    <Table bordered hover size="sm" className="collection-meta-table">
                      <tbody>
                        <tr>
                          <th>Collection Name</th>
                          <td>{summary?.name || decodedCollectionName}</td>
                        </tr>
                        <tr>
                          <th>Collection Type</th>
                          <td>{summary?.collectionType || 'Unknown'}</td>
                        </tr>
                        <tr>
                          <th>Vector Field</th>
                          <td>{summary?.vectorFieldName || 'Unknown'}</td>
                        </tr>
                        <tr>
                          <th>Vector Dimension</th>
                          <td>{summary?.vectorDimension ?? 'Unknown'}</td>
                        </tr>
                        <tr>
                          <th>Total Records</th>
                          <td>{summary?.rowCount ?? 'Unknown'}</td>
                        </tr>
                        <tr>
                          <th>Team ID</th>
                          <td>{summary?.teamId || currentTeam?.id || 'Unknown'}</td>
                        </tr>
                      </tbody>
                    </Table>
                    <Alert variant="info" className="mt-3 mb-0">
                      Data previews are coming soon. Use Milvus Studio / Attu to explore raw records.
                    </Alert>
                  </div>
                </Tab>

                <Tab eventKey="schema" title="Schema">
                  <div className="mt-3">
                    {summary?.collectionType?.toUpperCase() === 'KNOWLEDGE_HUB' && (
                      <>
                        <div className="d-flex align-items-center gap-2 mb-3">
                          {collectionValid ? (
                            <Badge bg="success">
                              <HiCheckCircle className="me-1" />
                              Schema is valid
                            </Badge>
                          ) : (
                            <Badge bg="warning" text="dark">
                              <HiExclamationCircle className="me-1" />
                              Issues detected
                            </Badge>
                          )}
                        </div>

                        {issues.length > 0 ? (
                          <Alert variant="warning" className="mb-4">
                            <div className="fw-semibold mb-2">Schema Issues</div>
                            <ul className="mb-0">
                              {issues.map((issue, idx) => (
                                <li key={idx}>{issue}</li>
                              ))}
                            </ul>
                          </Alert>
                        ) : (
                          <Alert variant="success" className="mb-4">
                            Schema matches the expected Knowledge Hub requirements.
                          </Alert>
                        )}
                      </>
                    )}

                    <h5 className="mb-3 text-start">Schema Fields</h5>
                    {schema.length > 0 ? (
                      <Table bordered hover size="sm" className="schema-table">
                        <thead>
                          <tr>
                            <th>Field</th>
                            <th>Data Type</th>
                            <th>Primary</th>
                            <th>Parameters</th>
                          </tr>
                        </thead>
                        <tbody>
                          {schema.map((field) => (
                            <tr key={field.name}>
                              <td>{field.name}</td>
                              <td>{field.dataType}</td>
                              <td>{field.primary ? 'Yes' : 'No'}</td>
                              <td>
                                {field.typeParams && Object.keys(field.typeParams).length > 0 ? (
                                  <div className="small text-muted">
                                    {Object.entries(field.typeParams).map(([key, value]) => (
                                      <div key={key}>
                                        {key}: {value}
                                      </div>
                                    ))}
                                  </div>
                                ) : (
                                  <span className="text-muted small">—</span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </Table>
                    ) : (
                      <p className="text-muted mb-0">No schema information available for this collection.</p>
                    )}
                  </div>
                </Tab>

                <Tab eventKey="properties" title="Properties">
                  <div className="mt-3">
                    {details ? (
                      <>
                        <div className="d-flex align-items-center justify-content-between mb-3">
                          <h5 className="mb-0">Collection Properties</h5>
                          <Button
                            variant="secondary"
                            className="btn-sm"
                            onClick={beginAddProperty}
                            disabled={Boolean(editingKey)}
                          >
                            <HiPlusCircle className="me-1" /> Add Property
                          </Button>
                        </div>

                        {propertyError && (
                          <Alert variant="danger" className="mb-3">
                            {propertyError}
                          </Alert>
                        )}

                        {propertyDrafts.length > 0 || editingKey === '__new__' ? (
                          <>
                            <div className="properties-table-wrapper">
                              <Table bordered hover size="sm" className="properties-table">
                                <thead>
                                  <tr>
                                    <th colSpan={3} className="properties-group-header">
                                      Linqra Properties
                                    </th>
                                  </tr>
                                  <tr>
                                    <th>Property</th>
                                    <th>Value</th>
                                    <th style={{ width: '170px' }}>Actions</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {propertyDrafts.filter((property) => property.locked || LINQRA_PROPERTY_KEYS.has(property.key)).length > 0 ? (
                                    propertyDrafts
                                      .filter((property) => property.locked || LINQRA_PROPERTY_KEYS.has(property.key))
                                      .map((property) => (
                                        <tr key={property.key}>
                                          {editingKey === property.key ? (
                                            <>
                                              <td className="property-key-cell">
                                                <Form.Control
                                                  type="text"
                                                  value={editingDraft?.key ?? ''}
                                                  disabled={property.locked || editingKey !== '__new__'}
                                                  onChange={(e) => handleDraftChange('key', e.target.value)}
                                                  placeholder="Property name"
                                                />
                                                {property.locked && (
                                                  <Form.Text className="text-muted">
                                                    Linqra property name cannot be changed.
                                                  </Form.Text>
                                                )}
                                              </td>
                                              <td>
                                                <Form.Control
                                                  as="textarea"
                                                  rows={3}
                                                  value={editingDraft?.value ?? ''}
                                                  onChange={(e) => handleDraftChange('value', e.target.value)}
                                                  placeholder="Enter property value"
                                                />
                                              </td>
                                              <td>
                                                <div className="d-flex gap-2">
                                                  <Button
                                                    variant="secondary"
                                                    className="btn-sm"
                                                    onClick={handleCancelEdit}
                                                    disabled={savingKey !== null}
                                                  >
                                                    Cancel
                                                  </Button>
                                                  <Button
                                                    variant="primary"
                                                    className="btn-sm"
                                                    loading={savingKey === property.key}
                                                    onClick={handleSaveProperty}
                                                  >
                                                    {savingKey === property.key ? 'Saving...' : 'Save'}
                                                  </Button>
                                                </div>
                                              </td>
                                            </>
                                          ) : (
                                            <>
                                              <td className="property-key-cell">
                                                {property.key}
                                                <Badge bg="light" text="dark" className="ms-2">
                                                  Linqra
                                                </Badge>
                                              </td>
                                              <td>
                                                <pre className="property-value">
                                                  {property.value ? property.value : '—'}
                                                </pre>
                                              </td>
                                              <td>
                                                <div className="d-flex gap-2">
                                                  <Button
                                                    variant="secondary"
                                                    className="btn-sm"
                                                    onClick={() => beginEditProperty(property)}
                                                    disabled={Boolean(editingKey)}
                                                  >
                                                    Edit
                                                  </Button>
                                                </div>
                                              </td>
                                            </>
                                          )}
                                        </tr>
                                      ))
                                  ) : (
                                    <tr>
                                      <td colSpan={3} className="text-muted text-center">
                                        No Linqra-managed properties available.
                                      </td>
                                    </tr>
                                  )}
                                </tbody>
                              </Table>
                            </div>

                            <div className="properties-table-wrapper mt-4">
                              <Table bordered hover size="sm" className="properties-table">
                                <thead>
                                  <tr>
                                    <th colSpan={3} className="properties-group-header">
                                      Milvus Properties
                                    </th>
                                  </tr>
                                  <tr>
                                    <th>Property</th>
                                    <th>Value</th>
                                    <th style={{ width: '170px' }}>Actions</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {propertyDrafts.filter((property) => !LINQRA_PROPERTY_KEYS.has(property.key)).length > 0 ? (
                                    propertyDrafts
                                      .filter((property) => !LINQRA_PROPERTY_KEYS.has(property.key))
                                      .map((property) => (
                                        <tr key={property.key}>
                                          {editingKey === property.key ? (
                                            <>
                                              <td className="property-key-cell">
                                                <Form.Control
                                                  type="text"
                                                  value={editingDraft?.key ?? ''}
                                                  disabled={editingKey !== '__new__'}
                                                  onChange={(e) => handleDraftChange('key', e.target.value)}
                                                  placeholder="Property name"
                                                />
                                              </td>
                                              <td>
                                                <Form.Control
                                                  as="textarea"
                                                  rows={3}
                                                  value={editingDraft?.value ?? ''}
                                                  onChange={(e) => handleDraftChange('value', e.target.value)}
                                                  placeholder="Enter property value"
                                                />
                                              </td>
                                              <td>
                                                <div className="d-flex gap-2">
                                                  <Button
                                                    variant="secondary"
                                                    className="btn-sm"
                                                    onClick={handleCancelEdit}
                                                    disabled={savingKey !== null}
                                                  >
                                                    Cancel
                                                  </Button>
                                                  <Button
                                                    variant="primary"
                                                    className="btn-sm"
                                                    loading={savingKey === property.key}
                                                    onClick={handleSaveProperty}
                                                  >
                                                    {savingKey === property.key ? 'Saving...' : 'Save'}
                                                  </Button>
                                                </div>
                                              </td>
                                            </>
                                          ) : (
                                            <>
                                              <td className="property-key-cell">{property.key}</td>
                                              <td>
                                                <pre className="property-value">
                                                  {property.value ? property.value : '—'}
                                                </pre>
                                              </td>
                                              <td>
                                                <div className="d-flex gap-2">
                                                  <Button
                                                    variant="secondary"
                                                    className="btn-sm"
                                                    onClick={() => beginEditProperty(property)}
                                                    disabled={Boolean(editingKey)}
                                                  >
                                                    Edit
                                                  </Button>
                                                </div>
                                              </td>
                                            </>
                                          )}
                                        </tr>
                                      ))
                                  ) : (
                                    <tr>
                                      <td colSpan={3} className="text-muted text-center">
                                        No Milvus properties defined yet.
                                      </td>
                                    </tr>
                                  )}

                                  {editingKey === '__new__' && (
                                    <tr>
                                      <td className="property-key-cell">
                                        <Form.Control
                                          type="text"
                                          value={editingDraft?.key ?? ''}
                                          onChange={(e) => handleDraftChange('key', e.target.value)}
                                          placeholder="Property name"
                                        />
                                      </td>
                                      <td>
                                        <Form.Control
                                          as="textarea"
                                          rows={3}
                                          value={editingDraft?.value ?? ''}
                                          onChange={(e) => handleDraftChange('value', e.target.value)}
                                          placeholder="Enter property value"
                                        />
                                      </td>
                                      <td>
                                        <div className="d-flex gap-2">
                                          <Button
                                            variant="secondary"
                                            className="btn-sm"
                                            onClick={handleCancelEdit}
                                            disabled={savingKey !== null}
                                          >
                                            Cancel
                                          </Button>
                                          <Button
                                            variant="primary"
                                            className="btn-sm"
                                            loading={savingKey === editingDraft?.key}
                                            onClick={handleSaveProperty}
                                          >
                                            {savingKey === editingDraft?.key ? 'Saving...' : 'Save'}
                                          </Button>
                                        </div>
                                      </td>
                                    </tr>
                                  )}
                                </tbody>
                              </Table>
                            </div>
                          </>
                        ) : (
                          <p className="text-muted mb-0">
                            No collection properties defined yet. Click &quot;Add Property&quot; to create one.
                          </p>
                        )}
                      </>
                    ) : (
                      <Alert variant="info" className="mb-0">
                        Collection properties will appear once the collection metadata is available.
                      </Alert>
                    )}
                  </div>
                </Tab>
              </Tabs>
            </Card.Body>
          </Card>
        </>
      )}
    </Container>
  );
};

export default ViewRag;

