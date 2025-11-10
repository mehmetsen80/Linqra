import React, { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Spinner, Alert } from 'react-bootstrap';
import { HiCollection, HiAdjustments } from 'react-icons/hi';
import Button from '../common/Button';

const PROVIDER_LABELS = {
  openai: 'OpenAI',
  gemini: 'Gemini',
  cohere: 'Cohere'
};

const getProviderKey = (modelCategory) => {
  if (!modelCategory) return '';
  const [provider] = modelCategory.split('-');
  return provider || '';
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

  if (prices.length === 0) {
    return '';
  }

  return prices.join(' · ');
};

function MilvusAssignmentModal({
  show,
  onHide,
  collection = null,
  milvusCollections = [],
  embeddingOptions = [],
  loading = false,
  onSave
}) {
  const [selectedCollectionName, setSelectedCollectionName] = useState('');
  const [selectedModelCategory, setSelectedModelCategory] = useState('');
  const [embeddingModelName, setEmbeddingModelName] = useState('');
  const [lateChunkingEnabled, setLateChunkingEnabled] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!show) return;

    setError(null);
    setSelectedCollectionName(collection?.milvusCollectionName || '');
    setSelectedModelCategory(
      collection?.embeddingModel ||
      collection?.properties?.embeddingModel ||
      ''
    );
    setEmbeddingModelName(
      collection?.embeddingModelName ||
      collection?.properties?.embeddingModelName ||
      ''
    );
    setLateChunkingEnabled(
      collection?.lateChunkingEnabled !== undefined
        ? Boolean(collection.lateChunkingEnabled)
        : true
    );
  }, [show, collection]);

  const selectedCollection = useMemo(() => {
    const collectionName = selectedCollectionName || collection?.milvusCollectionName;
    if (!collectionName) return null;
    return milvusCollections.find(item => item.name === collectionName) || null;
  }, [milvusCollections, selectedCollectionName, collection]);

  const collectionDimension = selectedCollection?.vectorDimension ?? null;

  const dimensionFilteredOptions = useMemo(() => {
    if (!selectedCollection) {
      return [];
    }
    if (!embeddingOptions || embeddingOptions.length === 0) {
      return [];
    }

    const dimension = collectionDimension;
    return embeddingOptions
      .map(option => {
        const originalModels = option.models || [];
        const filteredModels = dimension
          ? originalModels.filter(model => model.dimension === dimension)
          : originalModels;

        if (dimension && filteredModels.length === 0) {
          return null;
        }

        return {
          ...option,
          label: PROVIDER_LABELS[option.provider] || option.label || option.provider,
          models: filteredModels
        };
      })
      .filter(Boolean);
  }, [embeddingOptions, collectionDimension, selectedCollection]);

  const collectionEmbeddingModelKey = useMemo(() => {
    if (!selectedCollection) {
      return '';
    }
    return selectedCollection?.properties?.embeddingModel
      || selectedCollection?.embeddingModel
      || '';
  }, [selectedCollection]);

  const collectionProviderKey = useMemo(
    () => getProviderKey(collectionEmbeddingModelKey),
    [collectionEmbeddingModelKey]
  );

  const selectedProviderOption = useMemo(() => {
    if (!dimensionFilteredOptions.length) return null;
    if (!collectionProviderKey) {
      return dimensionFilteredOptions[0];
    }
    return dimensionFilteredOptions.find(option => option.provider === collectionProviderKey) || null;
  }, [dimensionFilteredOptions, collectionProviderKey]);

  const availableModels = selectedProviderOption?.models || [];

  const selectedModel = useMemo(
    () => availableModels.find(m => m.modelName === embeddingModelName) || null,
    [availableModels, embeddingModelName]
  );

  const providerLabel = useMemo(() => {
    if (!selectedCollection) return '';
    if (!collectionProviderKey) return 'Unknown provider';
    return PROVIDER_LABELS[collectionProviderKey] || collectionProviderKey;
  }, [selectedCollection, collectionProviderKey]);

  useEffect(() => {
    if (!show) return;

    if (!selectedCollection) {
      if (embeddingModelName) setEmbeddingModelName('');
      if (selectedModelCategory) setSelectedModelCategory('');
      return;
    }

    if (!dimensionFilteredOptions.length) {
      if (embeddingModelName) setEmbeddingModelName('');
      if (selectedModelCategory) setSelectedModelCategory('');
      return;
    }

    if (!selectedProviderOption) {
      if (embeddingModelName) setEmbeddingModelName('');
      return;
    }

    if (!availableModels.length) {
      if (embeddingModelName) setEmbeddingModelName('');
      return;
    }

    const preferredModelName =
      selectedCollection?.embeddingModelName ||
      selectedCollection?.properties?.embeddingModelName ||
      '';

    const preferredModelCategory =
      selectedCollection?.embeddingModel ||
      selectedCollection?.properties?.embeddingModel ||
      '';

    const matchingModel = availableModels.find(m => m.modelName === preferredModelName);
    const model = matchingModel || selectedModel || availableModels[0];

    if (preferredModelCategory && selectedModelCategory !== preferredModelCategory) {
      setSelectedModelCategory(preferredModelCategory);
    } else if (!preferredModelCategory && model?.modelCategory && selectedModelCategory !== model.modelCategory) {
      setSelectedModelCategory(model.modelCategory);
    }

    if (!embeddingModelName || embeddingModelName !== model?.modelName) {
      setEmbeddingModelName(model?.modelName || '');
    }

    if (model) {
      if (model.supportsLateChunking !== undefined) {
        setLateChunkingEnabled(true);
      } else if (!matchingModel && model.modelName !== embeddingModelName) {
        setLateChunkingEnabled(true);
      }
    }
  }, [
    show,
    dimensionFilteredOptions,
    selectedProviderOption,
    availableModels,
    selectedCollection,
    collection,
    selectedModelCategory,
    embeddingModelName,
    selectedModel
  ]);

  const handleCollectionSelect = (event) => {
    const value = event.target.value;
    setSelectedCollectionName(value);
    setError(null);
    setSelectedModelCategory('');
    setEmbeddingModelName('');
    setLateChunkingEnabled(true);
  };

  const handleModelSelect = (event) => {
    const modelName = event.target.value;
    setEmbeddingModelName(modelName);
    const model = availableModels.find(m => m.modelName === modelName);
    if (model) {
      setSelectedModelCategory(model.modelCategory || '');
      setLateChunkingEnabled(true);
    }
  };

  const effectiveDimension = collectionDimension ?? selectedModel?.dimension ?? collection?.embeddingDimension ?? '';
  const numericDimension = typeof effectiveDimension === 'number'
    ? effectiveDimension
    : effectiveDimension
      ? parseInt(effectiveDimension, 10)
      : null;

  const embeddingModelsConfigured = Boolean(embeddingOptions && embeddingOptions.length > 0);
  const noEmbeddingModelsConfigured = !embeddingModelsConfigured;
  const dimensionMismatch = Boolean(
    selectedCollection && collectionDimension && dimensionFilteredOptions.length === 0 && embeddingModelsConfigured
  );

  const modelDisabled = loading || !selectedProviderOption || availableModels.length === 0;
  const lateChunkingDisabled = loading || availableModels.length === 0;
  const saveDisabled = loading || !selectedCollectionName || !selectedProviderOption || availableModels.length === 0;

  const handleSubmit = () => {
    if (!selectedCollection) {
      setError('Please select a RAG collection.');
      return;
    }

    if (!selectedModelCategory || !embeddingModelName) {
      setError('Please select an embedding model.');
      return;
    }

    if (!numericDimension || Number.isNaN(numericDimension)) {
      setError('Unable to determine a valid embedding dimension for the selected RAG collection.');
      return;
    }

    onSave({
      milvusCollectionName: selectedCollectionName,
      embeddingModel: selectedModelCategory,
      embeddingModelName,
      embeddingDimension: numericDimension,
      lateChunkingEnabled
    });
  };

  return (
    <Modal show={show} onHide={onHide} size="lg" centered>
      <Modal.Header closeButton>
        <Modal.Title className="d-flex align-items-center gap-2">
          <HiCollection />
          Assign RAG Collection
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {error && (
          <Alert variant="danger">{error}</Alert>
        )}

        <Form>
          <Form.Group className="mb-3">
            <Form.Label>RAG Collection</Form.Label>
            <Form.Select value={selectedCollectionName} onChange={handleCollectionSelect} disabled={loading}>
              <option value="">Select a RAG collection</option>
              {milvusCollections.map(item => (
                <option key={item.name} value={item.name}>
                  {item.name}{collection?.milvusCollectionName === item.name ? ' (current)' : ''}
                </option>
              ))}
            </Form.Select>
            <Form.Text muted>
              {selectedCollection
                ? `Vector dimension: ${collectionDimension ?? 'unknown'}`
                : 'Only RAG collections available for this team are listed.'}
            </Form.Text>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Embedding Provider</Form.Label>
            <Form.Control
              type="text"
              value={providerLabel || (selectedCollection ? 'Unknown provider' : 'Select a RAG collection first')}
              readOnly
              plaintext
              className="ps-0"
            />
            <Form.Text muted>
              Derived from the RAG collection&apos;s stored embedding model. To change providers, create a new RAG collection.
            </Form.Text>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Embedding Model</Form.Label>
            <Form.Select
              value={embeddingModelName}
              onChange={handleModelSelect}
              disabled={modelDisabled}
            >
              {availableModels.length === 0 ? (
                <option value="">{selectedProviderOption ? 'No compatible models available' : 'Select a RAG collection first'}</option>
              ) : (
                availableModels.map(model => {
                  const priceLabel = formatPriceLabel(model);
                  return (
                    <option key={`${model.modelCategory}:${model.modelName}`} value={model.modelName}>
                      {model.modelName}
                      {model.dimension ? ` (${model.dimension} dims)` : ''}
                      {priceLabel ? ` · ${priceLabel}` : ''}
                    </option>
                  );
                })
              )}
            </Form.Select>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Embedding Dimension</Form.Label>
            <Form.Control
              type="number"
              value={numericDimension ?? ''}
              readOnly
              disabled
            />
            <Form.Text muted>
              Derived from the RAG collection and selected embedding model.
            </Form.Text>
          </Form.Group>

          <Form.Check
            type="switch"
            id="late-chunking-switch"
            label="Late Chunking"
            checked={lateChunkingEnabled}
            onChange={(e) => setLateChunkingEnabled(e.target.checked)}
            disabled={lateChunkingDisabled}
            className="mb-3"
          />
        </Form>

        {dimensionMismatch && (
          <Alert variant="warning" className="mt-3">
            No embedding models with a vector dimension of {collectionDimension} are configured. Add a compatible embedding model or choose a different RAG collection.
          </Alert>
        )}

        {noEmbeddingModelsConfigured && (
          <Alert variant="warning" className="mt-3">
            No embedding models are configured for this team. Add embedding models in the Knowledge Hub settings before assigning a RAG collection.
          </Alert>
        )}
      </Modal.Body>

      <Modal.Footer className="d-flex justify-content-between">
        <div>
          <Button variant="secondary" onClick={onHide} disabled={loading}>
            Cancel
          </Button>
        </div>
        <div>
          <Button
            variant="primary"
            onClick={handleSubmit}
            disabled={saveDisabled || loading}
          >
            {loading ? (
              <>
                <Spinner as="span" animation="border" size="sm" className="me-2" />
                Saving...
              </>
            ) : (
              <>
                <HiAdjustments className="me-2" />
                Save Assignment
              </>
            )}
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
}

MilvusAssignmentModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onHide: PropTypes.func.isRequired,
  collection: PropTypes.object,
  milvusCollections: PropTypes.array,
  embeddingOptions: PropTypes.array,
  loading: PropTypes.bool,
  onSave: PropTypes.func.isRequired
};

export default MilvusAssignmentModal;

