package org.lite.gateway.service;

import org.lite.gateway.entity.KnowledgeHubDocument;
import reactor.core.publisher.Mono;

public interface KnowledgeHubIngressionService {
    /**
     * Fetches a resource from a URL and saves it to a Knowledge Hub collection.
     *
     * @param url          The URL to fetch content from
     * @param fileName     The name to give the file in Knowledge Hub
     * @param collectionId The target collection ID
     * @param teamId       The team ID owning the collection
     * @param contentType  Optional content type (e.g., application/pdf)
     * @return Mono<KnowledgeHubDocument>
     */
    Mono<KnowledgeHubDocument> ingressFromUrl(String url, String fileName, String collectionId, String teamId,
            String contentType);
}
