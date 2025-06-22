package org.lite.gateway.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lite.gateway.dto.LinqProtocolExample;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.SwaggerEndpointInfo;
import org.lite.gateway.dto.SwaggerMediaType;
import org.lite.gateway.dto.SwaggerResponse;
import org.lite.gateway.service.LinqProtocolService;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class LinqProtocolServiceImpl implements LinqProtocolService{

    @Override
    public Mono<LinqProtocolExample> convertToLinqProtocol(SwaggerEndpointInfo endpointInfo, String routeIdentifier) {
        return Mono.fromCallable(() -> {
            LinqProtocolExample example = new LinqProtocolExample();
            example.setSummary(endpointInfo.getSummary());

            // Create request example
            LinqRequest linqRequest = createExampleRequest(endpointInfo, routeIdentifier);
            example.setRequest(linqRequest);

            // Create response example
            LinqResponse response = createExampleResponse(endpointInfo, routeIdentifier);
            example.setResponse(response);

            return example;
        });
    }

    private LinqRequest createExampleRequest(SwaggerEndpointInfo endpointInfo, String routeIdentifier) {
        LinqRequest linqRequest = new LinqRequest();

        // Set target as routeIdentifier
        LinqRequest.Link link = new LinqRequest.Link();
        link.setTarget(routeIdentifier);

        // Convert HTTP method to Linq action
        switch (endpointInfo.getMethod().toUpperCase()) {
            case "GET" -> link.setAction("fetch");
            case "POST" -> link.setAction("create");
            case "PUT" -> link.setAction("update");
            case "DELETE" -> link.setAction("delete");
            case "PATCH" -> link.setAction("patch");
            case "OPTIONS" -> link.setAction("options");
            case "HEAD" -> link.setAction("head");
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + endpointInfo.getMethod());
        }
        linqRequest.setLink(link);

        // Set up query with parameters
        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent(endpointInfo.getPath());

        // Extract parameters
        Map<String, Object> params = new HashMap<>();
        if (endpointInfo.getParameters() != null) {
            endpointInfo.getParameters().forEach(param -> {
                params.put(param.getName(), param.getSchema());
            });
        }
        query.setParams(params);

        // Handle request body
        if (endpointInfo.getRequestBody() != null) {
            SwaggerMediaType mediaType = endpointInfo.getRequestBody().getContent().get("application/json");
            if (mediaType != null && mediaType.getExample() != null) {
                query.setPayload(mediaType.getExample());
            }
        }

        linqRequest.setQuery(query);
        return linqRequest;
    }

    private LinqResponse createExampleResponse(SwaggerEndpointInfo endpointInfo, String routeIdentifier) {
        LinqResponse response = new LinqResponse();

        // Set up metadata
        LinqResponse.Metadata metadata = new LinqResponse.Metadata();
        metadata.setSource(routeIdentifier);
        metadata.setStatus("success");
        metadata.setTeamId("67d0aeb17172416c411d419e");
        metadata.setCacheHit(false);
        response.setMetadata(metadata);

        // Handle special cases first
        switch (endpointInfo.getMethod().toUpperCase()) {
            case "DELETE" -> {
                response.setResult(Map.of("message", "Resource successfully deleted"));
                return response;
            }
            case "OPTIONS" -> {
                response.setResult(Map.of("methods", List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")));
                return response;
            }
            case "HEAD" -> {
                // Get the corresponding GET response to generate appropriate headers
                SwaggerResponse getResponse = endpointInfo.getResponses().entrySet().stream()
                        .filter(entry -> {
                            int statusCode = Integer.parseInt(entry.getKey());
                            return statusCode >= 200 && statusCode < 300;
                        })
                        .findFirst()
                        .map(Map.Entry::getValue)
                        .orElse(null);

                Map<String, Object> headers = new HashMap<>();
                if (getResponse != null && getResponse.getContent() != null) {
                    SwaggerMediaType mediaType = getResponse.getContent().get("application/json");
                    if (mediaType != null && mediaType.getExample() != null) {
                        // Use the example from the response directly
                        @SuppressWarnings("unchecked")
                        Map<String, Object> example = (Map<String, Object>) mediaType.getExample();
                        example.forEach((key, value) -> {
                            String headerName = "X-" + key.substring(0, 1).toUpperCase() + key.substring(1);
                            headers.put(headerName, String.valueOf(value));
                        });
                    }
                }

                if (headers.isEmpty()) {
                    // Fallback if no example is available
                    headers.put("X-Resource-Found", "true");
                }

                response.setResult(headers);
                return response;
            }
        }

        // Get the appropriate success response based on the defined responses
        Map<String, SwaggerResponse> responses = endpointInfo.getResponses();
        SwaggerResponse successResponse = responses.entrySet().stream()
                .filter(entry -> {
                    int statusCode = Integer.parseInt(entry.getKey());
                    return statusCode >= 200 && statusCode < 300;
                })
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);

        if (successResponse != null && successResponse.getContent() != null) {
            SwaggerMediaType mediaType = successResponse.getContent().get("application/json");
            if (mediaType != null && mediaType.getExample() != null) {
                response.setResult(mediaType.getExample());
            }
        }

        return response;
    }


    
}
