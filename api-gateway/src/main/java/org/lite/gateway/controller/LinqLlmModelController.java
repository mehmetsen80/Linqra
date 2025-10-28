package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.lite.gateway.entity.LinqLlmModel;
import org.lite.gateway.service.LinqLlmModelService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.dto.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/linq-llm-models")
@RequiredArgsConstructor
public class LinqLlmModelController {

    private static final Logger log = LoggerFactory.getLogger(LinqLlmModelController.class);

    private final LinqLlmModelService linqLlmModelService;
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamService teamService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<LinqLlmModel> createLinqLlmModel(@RequestBody LinqLlmModel linqLlmModel) {
        return linqLlmModelService.saveLinqLlmModel(linqLlmModel);
    }

    @GetMapping("/team/{teamId}")
    public Mono<ResponseEntity<?>> getTeamConfiguration(
            @PathVariable String teamId,
            ServerWebExchange exchange) {
        log.info("Getting LLM model configurations for team: {}", teamId);
        
        return userContextService.getCurrentUsername(exchange)
            .flatMap(userService::findByUsername)
            .flatMap(user -> {
                // For SUPER_ADMIN, proceed directly
                if (user.getRoles().contains("SUPER_ADMIN")) {
                    return linqLlmModelService.findByTeamId(teamId)
                        .collectList()
                        .map(configs -> (ResponseEntity<?>) ResponseEntity.ok(configs));
                }
                
                // For non-SUPER_ADMIN users, check team admin role
                return teamService.hasRole(teamId, user.getId(), "ADMIN")
                    .flatMap(isAdmin -> {
                        if (!isAdmin) {
                            return Mono.just((ResponseEntity<?>) ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(ErrorResponse.fromErrorCode(
                                    ErrorCode.FORBIDDEN,
                                    "Only team administrators can view LLM model configurations",
                                    HttpStatus.FORBIDDEN.value()
                                )));
                        }
                        return linqLlmModelService.findByTeamId(teamId)
                            .collectList()
                            .map(configs -> (ResponseEntity<?>) ResponseEntity.ok(configs));
                    });
            })
            .onErrorResume(error -> {
                log.error("Error getting LLM model configurations: {}", error.getMessage());
                return Mono.just((ResponseEntity<?>) ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.INTERNAL_ERROR,
                        "Error getting LLM model configurations: " + error.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                    )));
            });
    }

    @GetMapping("/team/{teamId}/modelCategory/{modelCategory}")
    public Mono<ResponseEntity<?>> getLlmModelByModelCategory(
            @PathVariable String teamId, 
            @PathVariable String modelCategory,
            ServerWebExchange exchange) {
        log.info("Getting LLM model configurations for team: {} and modelCategory: {}", teamId, modelCategory);
        
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // For SUPER_ADMIN, proceed directly
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        return linqLlmModelService.findByModelCategoryAndTeamId(modelCategory, teamId)
                            .collectList()
                            .map(configs -> (ResponseEntity<?>) ResponseEntity.ok(configs));
                    }
                    
                    // For non-SUPER_ADMIN users, check team admin role
                    return teamService.hasRole(teamId, user.getId(), "ADMIN")
                        .flatMap(isAdmin -> {
                            if (!isAdmin) {
                                return Mono.just((ResponseEntity<?>) ResponseEntity
                                    .status(HttpStatus.FORBIDDEN)
                                    .body(ErrorResponse.fromErrorCode(
                                        ErrorCode.FORBIDDEN,
                                        "Only team administrators can view LLM model configurations",
                                        HttpStatus.FORBIDDEN.value()
                                    )));
                            }
                            return linqLlmModelService.findByModelCategoryAndTeamId(modelCategory, teamId)
                                .collectList()
                                .map(configs -> (ResponseEntity<?>) ResponseEntity.ok(configs));
                        });
                })
            .onErrorResume(error -> {
                log.error("Error getting LLM model configurations: {}", error.getMessage());
                return Mono.just((ResponseEntity<?>) ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.INTERNAL_ERROR,
                        "Error getting LLM model configurations: " + error.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                    )));
            });
    }
    
    @PostMapping("/team/{teamId}/modelCategories")
    public Mono<ResponseEntity<?>> getLlmModelsByModelCategories(
            @PathVariable String teamId, 
            @RequestBody java.util.List<String> modelCategoryList,
            ServerWebExchange exchange) {
        log.info("Getting LLM model configurations for team: {} and modelCategoryList: {}", teamId, modelCategoryList);
        
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // For SUPER_ADMIN, proceed directly
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        return linqLlmModelService.findByModelCategoriesAndTeamId(modelCategoryList, teamId)
                            .collectList()
                            .map(configs -> (ResponseEntity<?>) ResponseEntity.ok(configs));
                    }
                    
                    // For non-SUPER_ADMIN users, check team admin role
                    return teamService.hasRole(teamId, user.getId(), "ADMIN")
                        .flatMap(isAdmin -> {
                            if (!isAdmin) {
                                return Mono.just((ResponseEntity<?>) ResponseEntity
                                    .status(HttpStatus.FORBIDDEN)
                                    .body(ErrorResponse.fromErrorCode(
                                        ErrorCode.FORBIDDEN,
                                        "Only team administrators can view LLM model configurations",
                                        HttpStatus.FORBIDDEN.value()
                                    )));
                            }
                            return linqLlmModelService.findByModelCategoriesAndTeamId(modelCategoryList, teamId)
                                .collectList()
                                .map(configs -> (ResponseEntity<?>) ResponseEntity.ok(configs));
                        });
                })
            .onErrorResume(error -> {
                log.error("Error getting LLM model configurations: {}", error.getMessage());
                return Mono.just((ResponseEntity<?>) ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.INTERNAL_ERROR,
                        "Error getting LLM model configurations: " + error.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                    )));
            });
    }
    
    @GetMapping("/team/{teamId}/modelCategory/{modelCategory}/model/{modelName}")
    public Mono<LinqLlmModel> getLlmModelByModelCategoryAndCategoryName(
            @PathVariable String teamId, 
            @PathVariable String modelCategory,
            @PathVariable String modelName) {
        return linqLlmModelService.findByModelCategoryAndModelNameAndTeamId(modelCategory, modelName, teamId);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<?>> deleteLinqLlmModel(@PathVariable String id, ServerWebExchange exchange) {
        log.info("Deleting LinqLlmModel with ID: {}", id);
        
        // First, get the model to check team ownership
        return linqLlmModelService.findById(id)
            .flatMap(model -> userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // For SUPER_ADMIN, proceed directly
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        return linqLlmModelService.deleteLinqLlmModel(id)
                            .thenReturn((ResponseEntity<?>) ResponseEntity.noContent().build());
                    }
                    
                    // For non-SUPER_ADMIN users, check team admin role
                    return teamService.hasRole(model.getTeamId(), user.getId(), "ADMIN")
                        .flatMap(isAdmin -> {
                            if (!isAdmin) {
                                return Mono.just((ResponseEntity<?>) ResponseEntity
                                    .status(HttpStatus.FORBIDDEN)
                                    .body(ErrorResponse.fromErrorCode(
                                        ErrorCode.FORBIDDEN,
                                        "Only team administrators can delete LLM model configurations",
                                        HttpStatus.FORBIDDEN.value()
                                    )));
                            }
                            return linqLlmModelService.deleteLinqLlmModel(id)
                                .thenReturn((ResponseEntity<?>) ResponseEntity.noContent().build());
                        });
                }))
            .switchIfEmpty(Mono.just((ResponseEntity<?>) ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.fromErrorCode(
                    ErrorCode.INTERNAL_ERROR,
                    "LinqLlmModel not found with ID: " + id,
                    HttpStatus.NOT_FOUND.value()
                ))))
            .onErrorResume(error -> {
                log.error("Error deleting LinqLlmModel: {}", error.getMessage());
                return Mono.just((ResponseEntity<?>) ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.INTERNAL_ERROR,
                        "Error deleting LinqLlmModel: " + error.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                    )));
            });
    }
}

