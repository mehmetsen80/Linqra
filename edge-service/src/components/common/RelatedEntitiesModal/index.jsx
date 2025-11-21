import React, { useState, useEffect } from 'react';
import { Modal, Table, Spinner, Badge } from 'react-bootstrap';
import { HiLink } from 'react-icons/hi';
import Button from '../Button';
import PropertiesViewerModal from '../PropertiesViewerModal';
import { knowledgeHubGraphService } from '../../../services/knowledgeHubGraphService';
import './styles.css';

const RelatedEntitiesModal = ({ 
  show, 
  onHide, 
  entityType,
  entityId,
  entityName
}) => {
  const [relatedEntities, setRelatedEntities] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [propertiesModal, setPropertiesModal] = useState({
    show: false,
    title: 'Properties',
    entityType: null,
    entityName: null,
    properties: {}
  });

  useEffect(() => {
    if (show && entityType && entityId) {
      fetchRelatedEntities();
    } else {
      // Reset state when modal is closed
      setRelatedEntities([]);
      setError(null);
    }
  }, [show, entityType, entityId]);

  const fetchRelatedEntities = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const { data, error: fetchError } = await knowledgeHubGraphService.findRelatedEntities(
        entityType, 
        entityId,
        null, // relationshipType - null means all types
        1 // maxDepth
      );
      
      if (fetchError) {
        setError(fetchError);
        setRelatedEntities([]);
      } else {
        // Transform the data to include relationship information
        // The backend returns entities with relationshipType and relationshipProperties for direct relationships
        const transformed = Array.isArray(data) ? data.map(entity => {
          // Extract relationship info (direct relationship for maxDepth=1)
          let relationshipInfo = null;
          if (entity.relationshipType) {
            relationshipInfo = {
              type: entity.relationshipType,
              properties: entity.relationshipProperties || {}
            };
          } else if (entity.relationships && Array.isArray(entity.relationships) && entity.relationships.length > 0) {
            // Fallback for multi-hop paths
            relationshipInfo = {
              type: entity.relationships[0]?.type || 'RELATED_TO',
              properties: entity.relationships[0]?.properties || {}
            };
          }
          
          return {
            id: entity.id,
            name: entity.name || entity.id,
            type: entity.type || 'Unknown',
            properties: Object.entries(entity)
              .filter(([key]) => !['id', 'name', 'type', 'relationshipType', 'relationshipProperties', 'relationships', 'teamId', 'documentId', 'extractedAt', 'createdAt', 'updatedAt'].includes(key))
              .reduce((acc, [key, value]) => {
                acc[key] = value;
                return acc;
              }, {}),
            relationshipInfo: relationshipInfo
          };
        }) : [];
        
        setRelatedEntities(transformed);
      }
    } catch (err) {
      console.error('Error fetching related entities:', err);
      setError(err.message || 'Failed to fetch related entities');
      setRelatedEntities([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      show={show}
      onHide={onHide}
      centered
      size="lg"
      animation={true}
      className="related-entities-modal"
    >
      <Modal.Header closeButton className="border-0 p-3">
        <Modal.Title className="d-flex align-items-center gap-2">
          <HiLink className="text-primary" />
          Related Entities
          {entityName && (
            <code className="small ms-2 text-muted">({entityName})</code>
          )}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {loading ? (
          <div className="text-center py-5">
            <Spinner animation="border" role="status">
              <span className="visually-hidden">Loading related entities...</span>
            </Spinner>
            <div className="mt-3 text-muted">Finding entities related to this entity...</div>
          </div>
        ) : error ? (
          <div className="text-center py-4">
            <div className="text-danger mb-2">Error loading related entities</div>
            <small className="text-muted">{error}</small>
          </div>
        ) : relatedEntities.length === 0 ? (
          <div className="text-center py-4 text-muted">
            No related entities found for this entity
          </div>
        ) : (
          <div className="table-responsive" style={{ maxHeight: '60vh', overflowY: 'auto' }}>
            <Table striped bordered hover size="sm">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Name</th>
                  <th>ID</th>
                  <th>Relationship</th>
                  <th>Properties</th>
                </tr>
              </thead>
              <tbody>
                {relatedEntities.map((entity, idx) => (
                  <tr 
                    key={entity.id || idx}
                    style={{ cursor: 'pointer' }}
                    onClick={() => setPropertiesModal({
                      show: true,
                      title: 'Entity Properties',
                      entityType: entity.type || 'Unknown',
                      entityName: entity.name || entity.id || 'Unnamed',
                      properties: entity.properties || {}
                    })}
                  >
                    <td>
                      <Badge bg="secondary">{entity.type || 'Unknown'}</Badge>
                    </td>
                    <td>{entity.name || 'Unnamed'}</td>
                    <td>
                      <code className="small">{entity.id || 'N/A'}</code>
                    </td>
                    <td>
                      {entity.relationshipInfo ? (
                        <Badge bg="info">{entity.relationshipInfo.type}</Badge>
                      ) : (
                        <span className="text-muted">—</span>
                      )}
                    </td>
                    <td
                      onClick={(e) => {
                        e.stopPropagation();
                        setPropertiesModal({
                          show: true,
                          title: 'Entity Properties',
                          entityType: entity.type || 'Unknown',
                          entityName: entity.name || entity.id || 'Unnamed',
                          properties: entity.properties || {}
                        });
                      }}
                      style={{ cursor: 'pointer' }}
                    >
                      <small className="text-muted">
                        {Object.keys(entity.properties || {}).length} properties
                      </small>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        )}
      </Modal.Body>
      <Modal.Footer className="border-0">
        <Button 
          variant="secondary" 
          onClick={onHide}
          className="px-4"
        >
          Close
        </Button>
      </Modal.Footer>

      <PropertiesViewerModal
        show={propertiesModal.show}
        onHide={() => setPropertiesModal(prev => ({ ...prev, show: false }))}
        title={propertiesModal.title}
        entityType={propertiesModal.entityType}
        entityName={propertiesModal.entityName}
        properties={propertiesModal.properties}
      />
    </Modal>
  );
};

export default RelatedEntitiesModal;

