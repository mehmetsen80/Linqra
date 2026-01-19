import React, { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Button, Spinner, Alert, Badge } from 'react-bootstrap';
import { HiCollection, HiPlusCircle } from 'react-icons/hi';

const PROVIDER_LABELS = {
  openai: 'OpenAI',
  gemini: 'Gemini',
  cohere: 'Cohere',
  ollama: 'Ollama'
};

const formatPriceLabel = (model) => {
  if (!model) return '';
  const prices = [];
  if (typeof model.inputPricePer1M === 'number') {
    prices.push(`$${model.inputPricePer1M.toFixed(4)} input/1M`);
  }
  if (typeof model.outputPricePer1M === 'number' && model.outputPricePer1M > 0) {
    prices.push(`$${model.outputPricePer1M.toFixed(4)} output/1M`);
  }
  return prices.join(' · ');
};

const buildSchemaFields = (dimension) => [
  { name: 'id', dtype: 'INT64', is_primary: true },
  { name: 'embedding', dtype: 'FLOAT_VECTOR', dim: dimension },
  { name: 'text', dtype: 'VARCHAR', max_length: 5000 },
  { name: 'chunkId', dtype: 'VARCHAR', max_length: 100 },
  { name: 'chunkIndex', dtype: 'INT32' },
  { name: 'documentId', dtype: 'VARCHAR', max_length: 100 },
  { name: 'collectionId', dtype: 'VARCHAR', max_length: 100 },
  { name: 'fileName', dtype: 'VARCHAR', max_length: 500 },
  { name: 'pageNumbers', dtype: 'VARCHAR', max_length: 100 },
  { name: 'tokenCount', dtype: 'INT32' },
  { name: 'language', dtype: 'VARCHAR', max_length: 50 },
  { name: 'createdAt', dtype: 'INT64' },
  { name: 'teamId', dtype: 'VARCHAR', max_length: 100 },
  { name: 'title', dtype: 'VARCHAR', max_length: 500 },
  { name: 'author', dtype: 'VARCHAR', max_length: 200 },
  { name: 'subject', dtype: 'VARCHAR', max_length: 200 },
  { name: 'qualityScore', dtype: 'DOUBLE' },
  { name: 'startPosition', dtype: 'INT32' },
  { name: 'endPosition', dtype: 'INT32' },
  { name: 'category', dtype: 'VARCHAR', max_length: 100 },
  { name: 'metadataOnly', dtype: 'BOOL' },
  { name: 'documentType', dtype: 'VARCHAR', max_length: 50 },
  { name: 'mimeType', dtype: 'VARCHAR', max_length: 150 },
  { name: 'collectionType', dtype: 'VARCHAR', max_length: 50 },
  { name: 'encryptionKeyVersion', dtype: 'VARCHAR', max_length: 50 }
];

const generateSuggestedName = (teamId, providerKey, modelName, dimension) => {
  const sanitizedTeam = (teamId || 'team').slice(-8);
  const providerSegment = providerKey || 'model';
  const modelSegment = (modelName || 'collection').replace(/[^a-zA-Z0-9]+/g, '_').toLowerCase();
  return `${sanitizedTeam}_${providerSegment}_${modelSegment}_${dimension || 'dim'}`;
};

const CreateMilvusCollectionModal = ({
  show,
  onHide,
  onCreate,
  teamId = '',
  embeddingOptions = [],
  loadingEmbeddingOptions = false,
  existingCollectionNames = [],
  creating = false,
  collectionType = 'KNOWLEDGE_HUB'
}) => {
  const [collectionName, setCollectionName] = useState('');
  const [description, setDescription] = useState('');
  const [selectedProvider, setSelectedProvider] = useState('');
  const [selectedModelName, setSelectedModelName] = useState('');
  const [selectedModelCategory, setSelectedModelCategory] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!show) {
      setCollectionName('');
      setDescription('');
      setSelectedProvider('');
      setSelectedModelName('');
      setSelectedModelCategory('');
      setError(null);
      return;
    }

    if (embeddingOptions.length > 0) {
      const provider = embeddingOptions[0];
      setSelectedProvider(provider.provider);
      if (provider.models.length > 0) {
        const model = provider.models[0];
        const modelDimension = model.dimension ?? model.embeddingDimension ?? model.dimensions;
        setSelectedModelName(model.modelName);
        setSelectedModelCategory(model.modelCategory || `${provider.provider}-embed`);
        const suggested = generateSuggestedName(teamId, provider.provider, model.modelName, modelDimension);
        setCollectionName(suggested);
        if (modelDimension) {
          setDescription(`Collection for ${model.modelName} embeddings (${modelDimension} dims)`);
        } else {
          setDescription(`Collection for ${model.modelName} embeddings`);
        }
      }
    }
  }, [show, embeddingOptions, teamId]);

  const providerOptions = useMemo(() => embeddingOptions.map(option => ({
    ...option,
    label: PROVIDER_LABELS[option.provider] || option.label || option.provider
  })), [embeddingOptions]);

  const availableModels = useMemo(() => {
    const provider = providerOptions.find(p => p.provider === selectedProvider);
    return provider ? provider.models : [];
  }, [providerOptions, selectedProvider]);

  const selectedModel = useMemo(() => availableModels.find(model => model.modelName === selectedModelName) || null,
    [availableModels, selectedModelName]);

  const dimension = selectedModel?.dimension ?? selectedModel?.embeddingDimension ?? selectedModel?.dimensions ?? null;
  const priceLabel = selectedModel ? formatPriceLabel(selectedModel) : '';

  const handleProviderChange = (event) => {
    const providerKey = event.target.value;
    setSelectedProvider(providerKey);
    const provider = providerOptions.find(p => p.provider === providerKey);
    if (provider && provider.models.length > 0) {
      const model = provider.models[0];
      const modelDimension = model.dimension ?? model.embeddingDimension ?? model.dimensions;
      setSelectedModelName(model.modelName);
      setSelectedModelCategory(model.modelCategory || `${providerKey}-embed`);
      const suggested = generateSuggestedName(teamId, providerKey, model.modelName, modelDimension);
      setCollectionName(suggested);
      if (modelDimension) {
        setDescription(`Collection for ${model.modelName} embeddings (${modelDimension} dims)`);
      } else {
        setDescription(`Collection for ${model.modelName} embeddings`);
      }
    } else {
      setSelectedModelName('');
      setSelectedModelCategory('');
    }
  };

  const handleModelChange = (event) => {
    const modelName = event.target.value;
    setSelectedModelName(modelName);
    const model = availableModels.find(m => m.modelName === modelName);
    if (model) {
      const modelDimension = model.dimension ?? model.embeddingDimension ?? model.dimensions;
      setSelectedModelCategory(model.modelCategory || `${selectedProvider}-embed`);
      if (!collectionName || collectionName.includes('_dim')) {
        const suggested = generateSuggestedName(teamId, selectedProvider, model.modelName, modelDimension);
        setCollectionName(suggested);
      }
      if (!description) {
        if (modelDimension) {
          setDescription(`Collection for ${model.modelName} embeddings (${modelDimension} dims)`);
        } else {
          setDescription(`Collection for ${model.modelName} embeddings`);
        }
      }
    }
  };

  const handleSubmit = () => {
    if (!collectionName.trim()) {
      setError('Collection name is required.');
      return;
    }
    if (!selectedProvider || !selectedModel || !dimension) {
      setError('Please select an embedding provider and model.');
      return;
    }
    if (existingCollectionNames.includes(collectionName.trim())) {
      setError('A Milvus collection with this name already exists. Please choose another name.');
      return;
    }

    const schemaFields = buildSchemaFields(dimension);
    const properties = {
      embeddingModel: selectedModelCategory || '',
      embeddingModelName: selectedModelName || '',
      embeddingDimension: dimension ? String(dimension) : ''
    };

    const payload = {
      collectionName: collectionName.trim(),
      description: description.trim(),
      schemaFields,
      teamId,
      collectionType,
      properties,
      embeddingModel: selectedModelCategory,
      embeddingModelName: selectedModelName,
      embeddingDimension: dimension
    };

    onCreate(payload);
  };

  return (
    <Modal show={show} onHide={creating ? undefined : onHide} size="lg" centered>
      <Modal.Header closeButton={!creating}>
        <Modal.Title className="d-flex align-items-center gap-2">
          <HiCollection />
          Create Knowledge Hub Milvus Collection
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {error && (
          <Alert variant="danger">{error}</Alert>
        )}

        {loadingEmbeddingOptions ? (
          <div className="text-center py-4">
            <Spinner animation="border" role="status">
              <span className="visually-hidden">Loading embedding models...</span>
            </Spinner>
          </div>
        ) : (
          <Form>
            <Form.Group className="mb-3">
              <Form.Label>Collection Name</Form.Label>
              <Form.Control
                type="text"
                value={collectionName}
                onChange={(e) => setCollectionName(e.target.value)}
                disabled={creating}
                placeholder="e.g., my-team_openai_text-embedding-3-small_1536"
                autoFocus
              />
              <Form.Text muted>
                Must be unique across Milvus. Avoid spaces or special characters.
              </Form.Text>
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Description</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={creating}
                placeholder="Optional description for this Milvus collection"
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Embedding Provider</Form.Label>
              <Form.Select
                value={selectedProvider}
                onChange={handleProviderChange}
                disabled={creating || providerOptions.length === 0}
              >
                {providerOptions.length === 0 ? (
                  <option value="">No embedding providers available</option>
                ) : (
                  providerOptions.map(provider => (
                    <option key={provider.provider} value={provider.provider}>
                      {provider.label}
                    </option>
                  ))
                )}
              </Form.Select>
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Embedding Model</Form.Label>
              <Form.Select
                value={selectedModelName}
                onChange={handleModelChange}
                disabled={creating || availableModels.length === 0}
              >
                {availableModels.length === 0 ? (
                  <option value="">No models available for selected provider</option>
                ) : (
                  availableModels.map(model => {
                    const price = formatPriceLabel(model);
                    return (
                      <option key={`${model.modelCategory}:${model.modelName}`} value={model.modelName}>
                        {model.modelName}
                        {model.dimension ? ` (${model.dimension} dims)` : ''}
                        {price ? ` · ${price}` : ''}
                      </option>
                    );
                  })
                )}
              </Form.Select>
              {priceLabel && (
                <Form.Text muted>Pricing: {priceLabel}</Form.Text>
              )}
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Vector Dimension</Form.Label>
              <Form.Control type="number" value={dimension ?? ''} readOnly disabled />
              <Form.Text muted>
                Derived from the selected embedding model. Schema will be generated automatically.
              </Form.Text>
            </Form.Group>

            <Alert variant="secondary">
              <div className="fw-semibold mb-1">Schema Overview</div>
              <div className="small">
                Creates fields for chunk text, metadata (documentId, collectionId, language, title, author, subject, qualityScore) and Milvus vector index using HNSW/Cosine. You can customize later if needed.
              </div>
            </Alert>
          </Form>
        )}
      </Modal.Body>

      <Modal.Footer className="d-flex justify-content-between">
        <div>
          <Button variant="secondary" onClick={onHide} disabled={creating}>
            Cancel
          </Button>
        </div>
        <div>
          <Button
            variant="primary"
            onClick={handleSubmit}
            disabled={creating || loadingEmbeddingOptions || providerOptions.length === 0 || availableModels.length === 0}
          >
            {creating ? (
              <>
                <Spinner as="span" animation="border" size="sm" className="me-2" />
                Creating...
              </>
            ) : (
              <>
                <HiPlusCircle className="me-2" />
                Create Collection
              </>
            )}
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
};

CreateMilvusCollectionModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onHide: PropTypes.func.isRequired,
  onCreate: PropTypes.func.isRequired,
  teamId: PropTypes.string,
  embeddingOptions: PropTypes.array,
  loadingEmbeddingOptions: PropTypes.bool,
  existingCollectionNames: PropTypes.arrayOf(PropTypes.string),
  creating: PropTypes.bool,
  collectionType: PropTypes.string
};

export default CreateMilvusCollectionModal;
