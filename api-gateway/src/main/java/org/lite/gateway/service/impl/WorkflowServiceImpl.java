package org.lite.gateway.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.repository.LinqToolRepository;
import org.lite.gateway.service.WorkflowService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.LinqToolService;
import org.lite.gateway.service.LinqMicroService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    @NonNull
    private final LinqToolRepository linqToolRepository;

    @NonNull
    private final TeamContextService teamContextService;

    @NonNull
    private final LinqToolService linqToolService;

    @NonNull
    private final LinqMicroService linqMicroService;

    @Override
    public Mono<LinqResponse> executeWorkflow(LinqRequest request) {
        List<LinqRequest.Query.WorkflowStep> steps = request.getQuery().getWorkflow();
        Map<Integer, Object> stepResults = new HashMap<>();
        List<LinqResponse.WorkflowStepMetadata> stepMetadata = new ArrayList<>();

        // Execute steps synchronously
        Mono<LinqResponse> workflowMono = Mono.just(new LinqResponse());
        for (LinqRequest.Query.WorkflowStep step : steps) {
            workflowMono = workflowMono.flatMap(response -> {
                Instant start = Instant.now();
                // Create a single-step LinqRequest
                LinqRequest stepRequest = new LinqRequest();
                LinqRequest.Link stepLink = new LinqRequest.Link();
                stepLink.setTarget(step.getTarget());
                stepLink.setAction(step.getAction());
                stepRequest.setLink(stepLink);

                LinqRequest.Query stepQuery = new LinqRequest.Query();
                stepQuery.setIntent(step.getIntent());
                stepQuery.setParams(resolvePlaceholdersForMap(step.getParams(), stepResults));
                stepQuery.setPayload(resolvePlaceholders(step.getPayload(), stepResults));
                stepQuery.setToolConfig(step.getToolConfig());
                stepRequest.setQuery(stepQuery);

                // Execute the step
                return teamContextService.getTeamFromContext()
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, 
                            "Team context not found. Please ensure you are authenticated with a valid team.")))
                        .flatMap(teamId -> {
                            log.info("Searching for tool with target: {} and team: {}", step.getTarget(), teamId);
                            return linqToolRepository.findByTargetAndTeam(step.getTarget(), teamId)
                                    .doOnNext(tool -> log.info("Found tool: {}", tool))
                                    .doOnError(error -> log.error("Error finding tool: {}", error.getMessage()))
                                    .doOnSuccess(tool -> {
                                        if (tool == null) {
                                            log.info("No tool found for target: {}", step.getTarget());
                                        }
                                    });
                        })
                        .doOnNext(tool -> log.info("About to execute tool request"))
                        .flatMap(tool -> linqToolService.executeToolRequest(stepRequest, tool))
                        .doOnNext(stepResponse -> log.info("Tool request executed successfully"))
                        .switchIfEmpty(Mono.<LinqResponse>defer(() -> {
                            log.info("No tool found, executing microservice request");
                            return linqMicroService.execute(stepRequest);
                        }))
                        .flatMap(stepResponse -> {
                            // Check if the result contains an error
                            if (stepResponse.getResult() instanceof Map<?, ?> resultMap && 
                                resultMap.containsKey("error")) {
                                return Mono.error(new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    String.format("Workflow step %d failed: %s", 
                                        step.getStep(), 
                                        resultMap.get("error"))
                                ));
                            }
                            
                            stepResults.put(step.getStep(), stepResponse.getResult());
                            long durationMs = Duration.between(start, Instant.now()).toMillis();
                            LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                            meta.setStep(step.getStep());
                            meta.setStatus("success");
                            meta.setDurationMs(durationMs);
                            stepMetadata.add(meta);
                            return Mono.just(response);
                        })
                        .onErrorResume(error -> {
                            long durationMs = Duration.between(start, Instant.now()).toMillis();
                            LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                            meta.setStep(step.getStep());
                            meta.setStatus("error");
                            meta.setDurationMs(durationMs);
                            stepMetadata.add(meta);
                            log.error("Error in workflow step {}: {}", step.getStep(), error.getMessage());
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                String.format("Workflow step %d failed: %s", step.getStep(), error.getMessage())
                            ));
                        });
            });
        }

        return workflowMono.flatMap(response -> 
            teamContextService.getTeamFromContext().map(teamId -> {
                // Build WorkflowResult
                LinqResponse.WorkflowResult workflowResult = new LinqResponse.WorkflowResult();
                List<LinqResponse.StepResult> stepResultList = steps.stream()
                        .map(step -> {
                            LinqResponse.StepResult stepResult = new LinqResponse.StepResult();
                            stepResult.setStep(step.getStep());
                            stepResult.setTarget(step.getTarget());
                            stepResult.setResult(stepResults.get(step.getStep()));
                            return stepResult;
                        })
                        .collect(Collectors.toList());
                workflowResult.setSteps(stepResultList);

                // Set final result (from last step)
                Object lastResult = stepResults.get(steps.getLast().getStep());
                workflowResult.setFinalResult(extractFinalResult(lastResult));

                // Set response
                response.setResult(workflowResult);
                LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                metadata.setSource("workflow");
                metadata.setStatus("success");
                metadata.setTeam(teamId);
                metadata.setCacheHit(false);
                metadata.setWorkflowMetadata(stepMetadata);
                response.setMetadata(metadata);
                return response;
            })
        );
    }

    private Map<String, Object> resolvePlaceholdersForMap(Map<String, Object> input, Map<Integer, Object> stepResults) {
        if (input == null) return new HashMap<>();
        Map<String, Object> resolved = new HashMap<>();
        input.forEach((key, value) -> resolved.put(key, resolvePlaceholders(value, stepResults)));
        return resolved;
    }

    private Object resolvePlaceholders(Object input, Map<Integer, Object> stepResults) {
        if (input == null) return null;
        if (input instanceof String stringInput) {
            return resolvePlaceholder(stringInput, stepResults);
        }
        if (input instanceof List<?> list) {
            return list.stream()
                    .map(item -> resolvePlaceholders(item, stepResults))
                    .collect(Collectors.toList());
        }
        if (input instanceof Map<?, ?> map) {
            return resolvePlaceholdersForMap(map.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            Map.Entry::getValue
                    )), stepResults);
        }
        return input;
    }

    private String resolvePlaceholder(String value, Map<Integer, Object> stepResults) {
        String result = value;
        Pattern pattern = Pattern.compile("\\{\\{step(\\d+)\\.result(?:\\.([\\w.]+))?\\}\\}");
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            int stepNum = Integer.parseInt(matcher.group(1));
            String path = matcher.group(2);
            Object stepResult = stepResults.get(stepNum);
            String replacement = "";
            if (stepResult != null) {
                replacement = path != null ? extractValue(stepResult, path) : String.valueOf(stepResult);
            }
            result = result.replace(matcher.group(0), replacement);
        }
        return result;
    }

    private String extractValue(Object obj, String path) {
        String[] parts = path.split("\\.");
        Object current = obj;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list && part.matches("\\d+")) {
                current = list.get(Integer.parseInt(part));
            } else {
                return "";
            }
            if (current == null) return "";
        }
        return String.valueOf(current);
    }

    private Object extractFinalResult(Object result) {
        if (result instanceof Map<?, ?> resultMap) {
            Object choices = resultMap.get("choices");
            if (choices instanceof List<?> choiceList && !choiceList.isEmpty()) {
                Object firstChoice = choiceList.getFirst();
                if (firstChoice instanceof Map<?, ?> choiceMap) {
                    Object message = choiceMap.get("message");
                    if (message instanceof Map<?, ?> messageMap) {
                        return messageMap.get("content");
                    }
                }
            }
        }
        return String.valueOf(result);
    }
  
}
